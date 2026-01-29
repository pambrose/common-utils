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

package com.github.pambrose.common.recaptcha

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class RecaptchaTests {
  private class TestRecaptchaConfig(
    override val isRecaptchaEnabled: Boolean,
    override val recaptchaSiteKey: String?,
    override val recaptchaSecretKey: String?,
  ) : RecaptchaConfig

  @Test
  fun recaptchaConfigEnabledTest() {
    val config = TestRecaptchaConfig(
      isRecaptchaEnabled = true,
      recaptchaSiteKey = "test-site-key",
      recaptchaSecretKey = "test-secret-key",
    )
    config.isRecaptchaEnabled shouldBe true
    config.recaptchaSiteKey shouldBe "test-site-key"
    config.recaptchaSecretKey shouldBe "test-secret-key"
  }

  @Test
  fun recaptchaConfigDisabledTest() {
    val config = TestRecaptchaConfig(
      isRecaptchaEnabled = false,
      recaptchaSiteKey = null,
      recaptchaSecretKey = null,
    )
    config.isRecaptchaEnabled shouldBe false
    config.recaptchaSiteKey shouldBe null
    config.recaptchaSecretKey shouldBe null
  }

  @Test
  fun recaptchaResponseDeserializationSuccessTest() {
    val json = """
      {
        "success": true,
        "hostname": "localhost",
        "challenge_ts": "2024-01-01T00:00:00Z"
      }
    """.trimIndent()

    val jsonParser = Json { ignoreUnknownKeys = true }
    val response = jsonParser.decodeFromString<RecaptchaService.RecaptchaResponse>(json)

    response.success shouldBe true
    response.hostname shouldBe "localhost"
    response.challengeTs shouldBe "2024-01-01T00:00:00Z"
    response.errorCodes.shouldBeEmpty()
  }

  @Test
  fun recaptchaResponseDeserializationFailureTest() {
    val json = """
      {
        "success": false,
        "error-codes": ["invalid-input-response", "timeout-or-duplicate"]
      }
    """.trimIndent()

    val jsonParser = Json { ignoreUnknownKeys = true }
    val response = jsonParser.decodeFromString<RecaptchaService.RecaptchaResponse>(json)

    response.success shouldBe false
    response.errorCodes shouldBe listOf("invalid-input-response", "timeout-or-duplicate")
    response.hostname shouldBe null
  }

  @Test
  fun recaptchaResponseDeserializationMinimalTest() {
    val json = """{"success": true}"""

    val jsonParser = Json { ignoreUnknownKeys = true }
    val response = jsonParser.decodeFromString<RecaptchaService.RecaptchaResponse>(json)

    response.success shouldBe true
    response.errorCodes.shouldBeEmpty()
  }

  @Test
  fun recaptchaConfigPartialKeysTest() {
    val config = TestRecaptchaConfig(
      isRecaptchaEnabled = true,
      recaptchaSiteKey = "site-key-only",
      recaptchaSecretKey = null,
    )
    config.isRecaptchaEnabled shouldBe true
    config.recaptchaSiteKey shouldNotBe null
    config.recaptchaSecretKey shouldBe null
  }
}
