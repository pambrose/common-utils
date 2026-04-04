@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.recaptcha

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RecaptchaConfigTests : StringSpec() {
  init {
    "implementation provides correct values" {
      val config = object : RecaptchaConfig {
        override val isRecaptchaEnabled = true
        override val recaptchaSiteKey = "my-site-key-123"
        override val recaptchaSecretKey = "my-secret-key-456"
      }

      config.isRecaptchaEnabled shouldBe true
      config.recaptchaSiteKey shouldBe "my-site-key-123"
      config.recaptchaSecretKey shouldBe "my-secret-key-456"
    }

    "disabled config returns false for isRecaptchaEnabled" {
      val config = object : RecaptchaConfig {
        override val isRecaptchaEnabled = false
        override val recaptchaSiteKey: String? = null
        override val recaptchaSecretKey: String? = null
      }

      config.isRecaptchaEnabled shouldBe false
      config.recaptchaSiteKey shouldBe null
      config.recaptchaSecretKey shouldBe null
    }

    "enabled config returns true with keys" {
      val config = object : RecaptchaConfig {
        override val isRecaptchaEnabled = true
        override val recaptchaSiteKey = "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI"
        override val recaptchaSecretKey = "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe"
      }

      config.isRecaptchaEnabled shouldBe true
      config.recaptchaSiteKey shouldBe "6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI"
      config.recaptchaSecretKey shouldBe "6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe"
    }
  }
}
