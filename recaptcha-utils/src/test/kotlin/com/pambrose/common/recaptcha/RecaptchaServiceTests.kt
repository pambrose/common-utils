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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import kotlinx.html.head
import kotlinx.html.stream.createHTML

/**
 * Covers the routing logic of [RecaptchaService.validateRecaptcha] and the HEAD script injection:
 * the disabled-gate short-circuit, the missing/blank-token 400 path, the verification-error 400
 * path, and the success/failure *response* branches, exercised hermetically through the shared
 * [postToken] helper, which fakes Google's siteverify endpoint with a MockEngine.
 */
class RecaptchaServiceTests : StringSpec() {
  init {
    "validateRecaptcha passes through without network when reCAPTCHA is not configured" {
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
            call.respondText(if (ok) "passed" else "blocked")
          }
        }
        // No g-recaptcha-response sent, but the disabled gate short-circuits to true.
        client.post("/v") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody("")
        }.apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "passed"
        }
      }
    }

    "validateRecaptcha responds 400 when configured and the token is missing" {
      testApplication {
        routing {
          post("/v") {
            val ok =
              with(RecaptchaService) {
                validateRecaptcha(
                  recaptchaConfig(enabled = true, siteKey = "site", secretKey = "secret"),
                  call.receiveParameters(),
                )
              }
            // When ok is false the extension has already written the 400 body; do not write again.
            if (ok) call.respondText("passed")
          }
        }
        client.post("/v") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody("")
        }.apply {
          status shouldBe HttpStatusCode.BadRequest
          bodyAsText() shouldContain "reCAPTCHA verification required"
        }
      }
    }

    "validateRecaptcha responds 400 when configured and the token is blank" {
      testApplication {
        routing {
          post("/v") {
            val ok =
              with(RecaptchaService) {
                validateRecaptcha(
                  recaptchaConfig(enabled = true, siteKey = "site", secretKey = "secret"),
                  call.receiveParameters(),
                )
              }
            if (ok) call.respondText("passed")
          }
        }
        client.post("/v") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody("g-recaptcha-response=")
        }.apply {
          status shouldBe HttpStatusCode.BadRequest
          bodyAsText() shouldContain "reCAPTCHA verification required"
        }
      }
    }

    fun renderHead(config: RecaptchaConfig): String =
      with(RecaptchaService) {
        createHTML().head { loadRecaptchaScript(config) }
      }

    "loadRecaptchaScript emits the api.js script when fully configured" {
      renderHead(recaptchaConfig(enabled = true, siteKey = "site", secretKey = "secret")) shouldContain
        "https://www.google.com/recaptcha/api.js"
    }

    "loadRecaptchaScript emits nothing when disabled or a key is missing" {
      renderHead(recaptchaConfig(enabled = false, siteKey = "site", secretKey = "secret")) shouldNotContain "api.js"
      renderHead(recaptchaConfig(enabled = true, siteKey = null, secretKey = "secret")) shouldNotContain "api.js"
      renderHead(recaptchaConfig(enabled = true, siteKey = "site", secretKey = null)) shouldNotContain "api.js"
    }

    "validateRecaptcha passes when the verification response reports success" {
      val engine = successEngine()

      val (status, body) = postToken(engine)

      status shouldBe HttpStatusCode.OK
      body shouldBe "passed"

      // The verification request carried the secret, token, and remote ip as form parameters.
      val sent = engine.requestHistory.single()
      sent.url.toString() shouldBe "https://www.google.com/recaptcha/api/siteverify"
      val form = (sent.body as FormDataContent).formData
      form["secret"] shouldBe "secret"
      form["response"] shouldBe "test-token"
      form["remoteip"].shouldNotBeNull()
    }

    "validateRecaptcha responds 400 when the verification response reports failure" {
      val engine =
        MockEngine {
          respond(
            content = """{"success": false, "error-codes": ["invalid-input-response"]}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
          )
        }

      val (status, body) = postToken(engine)

      status shouldBe HttpStatusCode.BadRequest
      body shouldContain "reCAPTCHA verification failed"
    }

    // A verification that fails for an ordinary reason, such as an I/O error reaching Google, drives
    // verifyRecaptcha through its catch branch and validateRecaptcha through the 400 response. Cancellation
    // is deliberately not exercised here: it propagates instead (see RecaptchaVerificationTests).
    "validateRecaptcha responds 400 when configured and verification errors out" {
      val engine = MockEngine { throw IllegalStateException("connection reset") }

      val (status, body) = postToken(engine)

      status shouldBe HttpStatusCode.BadRequest
      body shouldContain "reCAPTCHA verification failed"
    }
  }
}
