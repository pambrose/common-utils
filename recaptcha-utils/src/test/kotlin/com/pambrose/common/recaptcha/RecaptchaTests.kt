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

import com.pambrose.common.recaptcha.RecaptchaService.recaptchaWidget
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.html.body
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json

class RecaptchaTests : StringSpec() {
  init {
    val jsonParser = Json { ignoreUnknownKeys = true }

    fun config(
      enabled: Boolean,
      siteKey: String?,
      secretKey: String?,
    ) = object : RecaptchaConfig {
      override val isRecaptchaEnabled = enabled
      override val recaptchaSiteKey = siteKey
      override val recaptchaSecretKey = secretKey
    }

    fun renderWidget(config: RecaptchaConfig): String =
      with(RecaptchaService) {
        createHTML().body { recaptchaWidget(config) }
      }

    // Bug #7: the widget was rendered when only the site key was present, but validation requires
    // the secret key. A missing secret key therefore rendered a widget whose response was never
    // validated (fail-open). Rendering and validation now share the same "fully configured" gate.

    "widget renders when fully configured" {
      renderWidget(config(enabled = true, siteKey = "site", secretKey = "secret")) shouldContain "g-recaptcha"
    }

    "widget is not rendered when secret key is missing" {
      renderWidget(config(enabled = true, siteKey = "site", secretKey = null)) shouldNotContain "g-recaptcha"
      renderWidget(config(enabled = true, siteKey = "site", secretKey = "")) shouldNotContain "g-recaptcha"
    }

    "widget is not rendered when disabled or site key is missing" {
      renderWidget(config(enabled = false, siteKey = "site", secretKey = "secret")) shouldNotContain "g-recaptcha"
      renderWidget(config(enabled = true, siteKey = null, secretKey = "secret")) shouldNotContain "g-recaptcha"
    }

    "recaptcha config enabled" {
      val config = object : RecaptchaConfig {
        override val isRecaptchaEnabled = true
        override val recaptchaSiteKey = "test-site-key"
        override val recaptchaSecretKey = "test-secret-key"
      }
      config.isRecaptchaEnabled shouldBe true
      config.recaptchaSiteKey shouldBe "test-site-key"
      config.recaptchaSecretKey shouldBe "test-secret-key"
    }

    "recaptcha config disabled" {
      val config = object : RecaptchaConfig {
        override val isRecaptchaEnabled = false
        override val recaptchaSiteKey: String? = null
        override val recaptchaSecretKey: String? = null
      }
      config.isRecaptchaEnabled shouldBe false
      config.recaptchaSiteKey shouldBe null
      config.recaptchaSecretKey shouldBe null
    }

    "recaptcha response deserialization success" {
      val json = """
      {
        "success": true,
        "hostname": "localhost",
        "challenge_ts": "2024-01-01T00:00:00Z"
      }
    """.trimIndent()

      val response = jsonParser.decodeFromString<RecaptchaService.RecaptchaResponse>(json)

      response.success shouldBe true
      response.hostname shouldBe "localhost"
      response.challengeTs shouldBe "2024-01-01T00:00:00Z"
      response.errorCodes.shouldBeEmpty()
    }

    "recaptcha response deserialization failure" {
      val json = """
      {
        "success": false,
        "error-codes": ["invalid-input-response", "timeout-or-duplicate"]
      }
    """.trimIndent()

      val response = jsonParser.decodeFromString<RecaptchaService.RecaptchaResponse>(json)

      response.success shouldBe false
      response.errorCodes shouldBe listOf("invalid-input-response", "timeout-or-duplicate")
      response.hostname shouldBe null
    }

    "recaptcha response deserialization minimal" {
      val json = """{"success": true}"""

      val response = jsonParser.decodeFromString<RecaptchaService.RecaptchaResponse>(json)

      response.success shouldBe true
      response.errorCodes.shouldBeEmpty()
    }

    "recaptcha config partial keys" {
      val config = object : RecaptchaConfig {
        override val isRecaptchaEnabled = true
        override val recaptchaSiteKey = "site-key-only"
        override val recaptchaSecretKey: String? = null
      }
      config.isRecaptchaEnabled shouldBe true
      config.recaptchaSiteKey shouldNotBe null
      config.recaptchaSecretKey shouldBe null
    }

    // Bug #9: the singleton's HttpClient was never closed. close() now releases it; verify it runs
    // without throwing and is idempotent. Kept last so it does not close the shared client before
    // the other tests in this spec run (none of which touch the client anyway).
    "close releases the http client without throwing and is idempotent" {
      shouldNotThrowAny {
        RecaptchaService.close()
        RecaptchaService.close()
      }
    }
  }
}
