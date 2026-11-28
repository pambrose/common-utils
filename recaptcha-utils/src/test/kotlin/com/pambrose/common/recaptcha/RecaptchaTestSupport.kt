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

// DEPRECATION: mutableOriginConnectionPoint is how Ktor itself overrides the remote address.
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "DEPRECATION")

package com.pambrose.common.recaptcha

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.plugins.mutableOriginConnectionPoint
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory

// Shared by the reCAPTCHA specs, which all need the same three pieces: a config literal, a MockEngine that fakes
// Google's siteverify endpoint, and one token pushed through validateRecaptcha.

internal fun recaptchaConfig(
  enabled: Boolean,
  siteKey: String?,
  secretKey: String?,
) = object : RecaptchaConfig {
  override val isRecaptchaEnabled = enabled
  override val recaptchaSiteKey = siteKey
  override val recaptchaSecretKey = secretKey
}

// Runs block with a function that snapshots the log events emitted by the recaptcha package in the meantime.
internal inline fun <T> capturingRecaptchaLogs(block: (logs: () -> List<ILoggingEvent>) -> T): T {
  val appender = ListAppender<ILoggingEvent>().apply { start() }
  val logger = LoggerFactory.getLogger(RecaptchaService::class.java.packageName) as Logger
  logger.addAppender(appender)
  return try {
    block { synchronized(appender) { appender.list.toList() } }
  } finally {
    logger.detachAppender(appender)
  }
}

// An engine that answers every request with the given reply.
internal fun respondingEngine(
  content: String,
  status: HttpStatusCode = HttpStatusCode.OK,
  contentType: ContentType = ContentType.Application.Json,
) = MockEngine {
  respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, contentType.toString()),
  )
}

// An engine that answers as Google does for a valid token.
internal fun successEngine() = respondingEngine("""{"success": true, "hostname": "example.com"}""")

/**
 * Swaps the service's client, and the factory that replaces it after `close()`, for [clientFactory] (by default a
 * client built on [engine] with the production configuration), runs a single token through `validateRecaptcha`,
 * and restores the original client and factory. The swapped-in client is built up front, as if an earlier
 * verification had built it, and [beforePost] runs once it is in place.
 * [remoteAddress] is set explicitly, because the Ktor test host reports the same value for `remoteHost` and
 * `remoteAddress`. [validate] is the route's call into the service; a spec replaces it to make that call from a
 * coroutine of its own.
 *
 * The returned body is `"passed"` when validation succeeded, `"propagated <exception>"` when it threw, and
 * whatever `validateRecaptcha` itself wrote (a 400 page) when it returned false.
 */
internal fun postToken(
  engine: MockEngine,
  config: RecaptchaConfig = recaptchaConfig(enabled = true, siteKey = "site", secretKey = "secret"),
  remoteAddress: String = "203.0.113.7",
  beforePost: () -> Unit = {},
  clientFactory: () -> HttpClient = { RecaptchaService.verificationClient(engine) },
  validate: suspend RoutingContext.(RecaptchaConfig, Parameters) -> Boolean = { recaptchaConfig, params ->
    with(RecaptchaService) { validateRecaptcha(recaptchaConfig, params) }
  },
): Pair<HttpStatusCode, String> {
  val previous = RecaptchaService.httpClient
  val previousFactory = RecaptchaService.clientFactory
  RecaptchaService.clientFactory = clientFactory
  RecaptchaService.httpClient = RecaptchaService.clientFactory()
  try {
    beforePost()
    var result: Pair<HttpStatusCode, String>? = null
    testApplication {
      routing {
        post("/v") {
          call.mutableOriginConnectionPoint.remoteAddress = remoteAddress
          val outcome = runCatching { validate(config, call.receiveParameters()) }
          val failure = outcome.exceptionOrNull()
          when {
            failure != null -> call.respondText("propagated ${failure::class.simpleName}")

            outcome.getOrThrow() -> call.respondText("passed")

            // validateRecaptcha already wrote its own 400 body.
            else -> Unit
          }
        }
      }
      client.post("/v") {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody("g-recaptcha-response=test-token")
      }.apply {
        result = status to bodyAsText()
      }
    }
    return result!!
  } finally {
    RecaptchaService.httpClient?.close()
    RecaptchaService.httpClient = previous
    RecaptchaService.clientFactory = previousFactory
  }
}
