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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.recaptcha

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

// Runs block with a function that snapshots the log events emitted by the recaptcha package in the meantime.
private inline fun <T> capturingRecaptchaLogs(block: (logs: () -> List<ILoggingEvent>) -> T): T {
  val appender = ListAppender<ILoggingEvent>().apply { start() }
  val logger = LoggerFactory.getLogger(RecaptchaService::class.java.packageName) as Logger
  logger.addAppender(appender)
  return try {
    block { synchronized(appender) { appender.list.toList() } }
  } finally {
    logger.detachAppender(appender)
  }
}

/**
 * Covers verification behavior that the routing tests do not: coroutine cancellation, what is sent as
 * `remoteip`, and the enabled-but-misconfigured case.
 */
class RecaptchaVerificationTests : StringSpec() {
  init {
    // Cancellation (a disconnected client, or shutdown) must not be swallowed and turned into "not a human".
    "a cancelled verification propagates instead of returning false" {
      val engine = MockEngine { throw CancellationException("client disconnected") }

      val (status, body) = postToken(engine)

      status shouldBe HttpStatusCode.OK
      body shouldContain "Cancellation"
    }

    // Google expects an IP for remoteip; origin.remoteHost can be a reverse-DNS hostname.
    "the verification request sends the remote IP address" {
      val engine = successEngine()

      val (status, body) = postToken(engine)

      status shouldBe HttpStatusCode.OK
      body shouldBe "passed"
      val form = (engine.requestHistory.single().body as FormDataContent).formData
      form["remoteip"] shouldBe "203.0.113.7"
    }

    // Enabled but missing a key means no bot protection at all, so it must not pass silently.
    "an enabled but misconfigured reCAPTCHA warns once and keeps passing requests" {
      val misconfigured = recaptchaConfig(enabled = true, siteKey = "site", secretKey = null)
      RecaptchaService.misconfiguredWarningLogged.store(false)

      val events =
        capturingRecaptchaLogs { logs ->
          testApplication {
            routing {
              post("/v") {
                val ok = with(RecaptchaService) { validateRecaptcha(misconfigured, call.receiveParameters()) }
                if (ok) call.respondText("passed")
              }
            }
            // Two requests, one warning: the gate runs per call, not per application.
            repeat(2) {
              client.post("/v") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("")
              }.apply {
                status shouldBe HttpStatusCode.OK
                bodyAsText() shouldBe "passed"
              }
            }
          }
          logs()
        }

      val warnings = events.filter { it.level == Level.WARN }
      warnings.size shouldBe 1
      warnings.single().formattedMessage shouldContain "reCAPTCHA"
    }

    "a fully unconfigured reCAPTCHA passes without warning" {
      val events =
        capturingRecaptchaLogs { logs ->
          testApplication {
            routing {
              post("/v") {
                val ok =
                  with(RecaptchaService) {
                    validateRecaptcha(
                      recaptchaConfig(enabled = false, siteKey = null, secretKey = null),
                      call.receiveParameters(),
                    )
                  }
                if (ok) call.respondText("passed")
              }
            }
            client.post("/v") {
              contentType(ContentType.Application.FormUrlEncoded)
              setBody("")
            }.apply {
              bodyAsText() shouldBe "passed"
            }
          }
          logs()
        }

      events.count { it.level == Level.WARN } shouldBe 0
    }
  }
}
