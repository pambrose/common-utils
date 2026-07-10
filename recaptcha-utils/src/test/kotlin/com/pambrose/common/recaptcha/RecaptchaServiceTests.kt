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

import com.pambrose.common.recaptcha.RecaptchaService.loadRecaptchaScript
import com.pambrose.common.recaptcha.RecaptchaService.validateRecaptcha
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.html.head
import kotlinx.html.stream.createHTML

/**
 * Covers the previously-untested routing logic of [RecaptchaService.validateRecaptcha] and the
 * HEAD script injection. Only the network-free branches are exercised: the disabled-gate
 * short-circuit, the missing/blank-token 400 path, and the verification-error 400 path (driven by
 * closing the service's HttpClient so the request fails before any I/O). The success/failure
 * *response* branches flow through a private, hardcoded CIO [io.ktor.client.HttpClient] against the
 * real Google siteverify URL and are not testable without a production injection seam.
 */
class RecaptchaServiceTests : StringSpec() {
  init {
    fun config(
      enabled: Boolean,
      siteKey: String?,
      secretKey: String?,
    ) = object : RecaptchaConfig {
      override val isRecaptchaEnabled = enabled
      override val recaptchaSiteKey = siteKey
      override val recaptchaSecretKey = secretKey
    }

    "validateRecaptcha passes through without network when reCAPTCHA is not configured" {
      testApplication {
        routing {
          post("/v") {
            val ok =
              with(RecaptchaService) {
                validateRecaptcha(config(enabled = false, siteKey = null, secretKey = null), call.receiveParameters())
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
                  config(enabled = true, siteKey = "site", secretKey = "secret"),
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
                  config(enabled = true, siteKey = "site", secretKey = "secret"),
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
      renderHead(config(enabled = true, siteKey = "site", secretKey = "secret")) shouldContain
        "https://www.google.com/recaptcha/api.js"
    }

    "loadRecaptchaScript emits nothing when disabled or a key is missing" {
      renderHead(config(enabled = false, siteKey = "site", secretKey = "secret")) shouldNotContain "api.js"
      renderHead(config(enabled = true, siteKey = null, secretKey = "secret")) shouldNotContain "api.js"
      renderHead(config(enabled = true, siteKey = "site", secretKey = null)) shouldNotContain "api.js"
    }

    // Kept last because it closes the singleton's HttpClient (close() is idempotent, and no other
    // test performs a live verification). With the client closed, submitForm fails immediately with
    // ClientEngineClosedException before any network I/O, driving verifyRecaptcha through its
    // catch branch and validateRecaptcha through the verification-failed 400 response.
    "validateRecaptcha responds 400 when configured and verification errors out" {
      RecaptchaService.close()
      testApplication {
        routing {
          post("/v") {
            val ok =
              with(RecaptchaService) {
                validateRecaptcha(
                  config(enabled = true, siteKey = "site", secretKey = "secret"),
                  call.receiveParameters(),
                )
              }
            if (ok) call.respondText("passed")
          }
        }
        client.post("/v") {
          contentType(ContentType.Application.FormUrlEncoded)
          setBody("g-recaptcha-response=test-token")
        }.apply {
          status shouldBe HttpStatusCode.BadRequest
          bodyAsText() shouldContain "reCAPTCHA verification failed"
        }
      }
    }
  }
}
