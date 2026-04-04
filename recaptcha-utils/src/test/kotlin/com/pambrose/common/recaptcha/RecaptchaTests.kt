/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.recaptcha

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

class RecaptchaTests : StringSpec() {
  init {
    val jsonParser = Json { ignoreUnknownKeys = true }

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
  }
}
