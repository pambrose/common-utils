/*
 *   Copyright © 2026 Paul Ambrose (pambrose@mac.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.pambrose.common.recaptcha

import com.pambrose.common.util.runCatchingCancellable
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import java.io.Closeable
import kotlin.concurrent.atomics.AtomicBoolean
import kotlinx.coroutines.isActive
import kotlinx.html.FlowContent
import kotlinx.html.HEAD
import kotlinx.html.div
import kotlinx.html.script
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Provides Google reCAPTCHA verification and HTML widget rendering for Ktor applications.
 *
 * Includes server-side token verification via the Google reCAPTCHA API, a Ktor route-level
 * validation extension, and kotlinx.html helpers for embedding the reCAPTCHA script and widget.
 *
 * Holds a long-lived [HttpClient]; call [close] on application shutdown to release its resources.
 */
object RecaptchaService : Closeable {
  private val logger = KotlinLogging.logger {}
  private const val RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify"

  // Internal (not private) so tests can swap in a MockEngine-backed client to exercise the
  // verification response branches hermetically; production code always uses this CIO client.
  internal var httpClient = HttpClient(CIO) { configureVerification() }

  // Whether the enabled-but-misconfigured warning has been logged; internal so tests can reset it.
  internal val misconfiguredWarningLogged = AtomicBoolean(false)

  /**
   * Builds a client around [engine] configured exactly like [httpClient], so tests run against the production
   * configuration rather than a copy of it. The caller owns [engine]: closing the client does not close it.
   */
  internal fun verificationClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configureVerification() }

  private fun HttpClientConfig<*>.configureVerification() {
    // Only a 2xx reply counts. Without this, an error or redirect whose body parses would still be believed.
    expectSuccess = true
    install(ContentNegotiation) {
      json(
        Json {
          ignoreUnknownKeys = true
          coerceInputValues = true
        },
      )
    }
  }

  // The keys of a fully configured reCAPTCHA, each read from the config exactly once.
  private class ConfiguredKeys(
    val siteKey: String,
    val secretKey: String,
  )

  /**
   * Represents the JSON response from Google's reCAPTCHA verification endpoint.
   *
   * @property success whether the reCAPTCHA token was valid.
   * @property errorCodes error codes returned by the API, if any.
   * @property hostname the hostname of the site where the reCAPTCHA was solved.
   * @property challengeTs the timestamp of the challenge in ISO 8601 format.
   */
  @Serializable
  data class RecaptchaResponse(
    val success: Boolean,
    @SerialName("error-codes")
    val errorCodes: List<String> = emptyList(),
    val hostname: String? = null,
    @SerialName("challenge_ts")
    val challengeTs: String? = null,
  )

  private suspend fun verifyRecaptcha(
    secretKey: String,
    recaptchaResponse: String,
    remoteIp: String,
  ): Boolean {
    val client = httpClient

    // A closed client fails every request with a CancellationException, which would be mistaken for a
    // cancelled call and rethrown. Fail the verification instead.
    if (!client.isActive) {
      logger.error { "reCAPTCHA verification attempted after RecaptchaService.close()" }
      return false
    }

    return runCatchingCancellable {
      val parameters =
        Parameters.build {
          append("secret", secretKey)
          append("response", recaptchaResponse)
          if (remoteIp.isNotBlank()) {
            append("remoteip", remoteIp)
          }
        }

      logger.info { "Verifying reCAPTCHA" }
      val response: RecaptchaResponse =
        client.submitForm(
          url = RECAPTCHA_VERIFY_URL,
          formParameters = parameters,
        ).body()

      if (response.success) {
        logger.debug { "reCAPTCHA verification successful" }
        true
      } else {
        logger.warn { "reCAPTCHA verification failed: ${response.errorCodes.joinToString()}" }
        false
      }
    }.getOrElse { e ->
      logger.error(e) { "Error verifying reCAPTCHA" }
      false
    }
  }

  /**
   * Validates the reCAPTCHA response token from form parameters within a Ktor [RoutingContext].
   *
   * If reCAPTCHA is configured and the token is missing or invalid, this function responds
   * with an HTTP 400 Bad Request and returns `false`. Returns `true` when validation succeeds
   * or reCAPTCHA is not configured.
   *
   * Cancellation is not swallowed: if the call is cancelled while Google is being contacted, for example
   * because the client disconnected, the [kotlinx.coroutines.CancellationException] propagates instead of
   * being reported as a failed verification.
   *
   * @param config the [RecaptchaConfig] providing keys and enabled status.
   * @param params the form [Parameters] containing the `g-recaptcha-response` token.
   * @return `true` if validation passed or reCAPTCHA is disabled, `false` otherwise.
   * @throws kotlinx.coroutines.CancellationException if the surrounding coroutine is cancelled.
   */
  suspend fun RoutingContext.validateRecaptcha(
    config: RecaptchaConfig,
    params: Parameters,
  ): Boolean {
    val keys = configuredKeys(config) ?: return true
    val recaptchaResponse = params["g-recaptcha-response"]

    if (recaptchaResponse.isNullOrBlank()) {
      call.respondText(
        "reCAPTCHA verification required",
        status = HttpStatusCode.BadRequest,
      )
      return false
    }

    // Google expects an IP address here; remoteHost can be a reverse-DNS hostname.
    val remoteIp = call.request.origin.remoteAddress
    val isValid = verifyRecaptcha(keys.secretKey, recaptchaResponse, remoteIp)

    if (!isValid) {
      call.respondText(
        "reCAPTCHA verification failed",
        status = HttpStatusCode.BadRequest,
      )
      return false
    }

    return true
  }

  /**
   * Adds the Google reCAPTCHA JavaScript to the HTML [HEAD] if reCAPTCHA is enabled and both the site key and
   * secret key are configured (kept in lockstep with server-side verification).
   *
   * This is an extension function on kotlinx.html [HEAD].
   *
   * @param config the [RecaptchaConfig] providing the site key and enabled status.
   */
  fun HEAD.loadRecaptchaScript(config: RecaptchaConfig) {
    if (configuredKeys(config) != null) {
      script {
        src = "https://www.google.com/recaptcha/api.js"
        async = true
        defer = true
      }
    }
  }

  /**
   * Renders the reCAPTCHA widget `<div>` in the HTML body if reCAPTCHA is enabled and both the site key and
   * secret key are configured (kept in lockstep with server-side verification).
   *
   * This is an extension function on kotlinx.html [FlowContent].
   *
   * @param config the [RecaptchaConfig] providing the site key and enabled status.
   */
  fun FlowContent.recaptchaWidget(config: RecaptchaConfig) {
    val keys = configuredKeys(config) ?: return
    div(classes = "g-recaptcha") {
      attributes["data-sitekey"] = keys.siteKey
    }
  }

  /**
   * Returns the keys only when reCAPTCHA is enabled *and* both the site key and secret key are present,
   * and `null` otherwise.
   *
   * Requiring both keys keeps rendering and validation in lockstep: the widget is never shown unless
   * its response can actually be verified server-side, closing a fail-open gap where a missing secret
   * key would render a widget but silently skip validation.
   *
   * Each key is read once and the caller uses the value that passed this check, so a config whose getters
   * return different values on each read cannot pass the check with a key and then supply `null`.
   *
   * Enabled with a key missing is a configuration mistake that leaves no bot protection at all, so it logs a
   * warning the first time it is seen rather than passing silently.
   */
  private fun configuredKeys(config: RecaptchaConfig): ConfiguredKeys? {
    if (!config.isRecaptchaEnabled)
      return null

    val siteKey = config.recaptchaSiteKey
    val secretKey = config.recaptchaSecretKey

    return if (!siteKey.isNullOrBlank() && !secretKey.isNullOrBlank()) {
      ConfiguredKeys(siteKey, secretKey)
    } else {
      if (misconfiguredWarningLogged.compareAndSet(expectedValue = false, newValue = true))
        logger.warn {
          "reCAPTCHA is enabled but the site key or secret key is missing: " +
            "no widget is rendered and no verification is performed"
        }
      null
    }
  }

  /**
   * Releases the underlying [HttpClient] and its connection/thread pool.
   *
   * Call this when the application that uses reCAPTCHA verification shuts down. After [close] is
   * invoked, [validateRecaptcha] can no longer perform server-side verification: while reCAPTCHA is
   * configured, every token it is given fails verification and the request gets a 400.
   */
  override fun close() {
    httpClient.close()
  }
}
