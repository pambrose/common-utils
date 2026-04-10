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

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
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
 */
object RecaptchaService {
  private val logger = KotlinLogging.logger {}
  private const val RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify"
  private val httpClient =
    HttpClient(CIO) {
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
          },
        )
      }
    }

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
    config: RecaptchaConfig,
    recaptchaResponse: String,
    remoteIp: String? = null,
  ): Boolean {
    if (!isRecaptchaConfigured(config)) {
      logger.debug { "reCAPTCHA is disabled, skipping verification" }
      return true
    }

    val secretKey = config.recaptchaSecretKey
    if (secretKey.isNullOrBlank()) {
      logger.warn { "reCAPTCHA secret key is not configured" }
      return false
    }

    return try {
      val parameters =
        Parameters.build {
          append("secret", secretKey)
          append("response", recaptchaResponse)
          if (!remoteIp.isNullOrBlank()) {
            append("remoteip", remoteIp)
          }
        }

      logger.info { "Verifying reCAPTCHA" }
      val response: RecaptchaResponse =
        httpClient.submitForm(
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
    } catch (e: Exception) {
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
   * @param config the [RecaptchaConfig] providing keys and enabled status.
   * @param params the form [Parameters] containing the `g-recaptcha-response` token.
   * @return `true` if validation passed or reCAPTCHA is disabled, `false` otherwise.
   */
  suspend fun RoutingContext.validateRecaptcha(
    config: RecaptchaConfig,
    params: Parameters,
  ): Boolean {
    if (isRecaptchaConfigured(config)) {
      val recaptchaResponse = params["g-recaptcha-response"]

      if (recaptchaResponse.isNullOrBlank()) {
        call.respondText(
          "reCAPTCHA verification required",
          status = HttpStatusCode.BadRequest,
        )
        return false
      }

      val remoteIp = call.request.origin.remoteHost
      val isValid = verifyRecaptcha(config, recaptchaResponse, remoteIp)

      if (!isValid) {
        call.respondText(
          "reCAPTCHA verification failed",
          status = HttpStatusCode.BadRequest,
        )
        return false
      }
    }

    return true
  }

  /**
   * Adds the Google reCAPTCHA JavaScript to the HTML [HEAD] if reCAPTCHA is enabled and a site key is configured.
   *
   * This is an extension function on kotlinx.html [HEAD].
   *
   * @param config the [RecaptchaConfig] providing the site key and enabled status.
   */
  fun HEAD.loadRecaptchaScript(config: RecaptchaConfig) {
    if (config.isRecaptchaEnabled) {
      if (!config.recaptchaSiteKey.isNullOrBlank()) {
        script {
          src = "https://www.google.com/recaptcha/api.js"
          async = true
          defer = true
        }
      }
    }
  }

  /**
   * Renders the reCAPTCHA widget `<div>` in the HTML body if reCAPTCHA is enabled and a site key is configured.
   *
   * This is an extension function on kotlinx.html [FlowContent].
   *
   * @param config the [RecaptchaConfig] providing the site key and enabled status.
   */
  fun FlowContent.recaptchaWidget(config: RecaptchaConfig) {
    if (config.isRecaptchaEnabled) {
      val siteKey = config.recaptchaSiteKey
      if (!siteKey.isNullOrBlank()) {
        div(classes = "g-recaptcha") {
          attributes["data-sitekey"] = siteKey
        }
      }
    }
  }

  private fun isRecaptchaConfigured(config: RecaptchaConfig): Boolean =
    config.isRecaptchaEnabled && config.recaptchaSecretKey?.isNotBlank() == true
}
