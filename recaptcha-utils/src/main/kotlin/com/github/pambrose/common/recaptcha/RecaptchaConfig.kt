package com.github.pambrose.common.recaptcha

interface RecaptchaConfig {
  val isRecaptchaEnabled: Boolean
  val recaptchaSiteKey: String?
  val recaptchaSecretKey: String?
}
