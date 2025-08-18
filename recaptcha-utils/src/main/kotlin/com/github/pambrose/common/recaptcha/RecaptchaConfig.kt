package com.github.pambrose.common.recaptcha

/**
 * Configuration interface for Google reCAPTCHA integration.
 *
 * Implement this interface to provide the necessary keys and enable/disable reCAPTCHA functionality.
 *
 * @property isRecaptchaEnabled Indicates whether reCAPTCHA validation is enabled.
 * @property recaptchaSiteKey The public site key for reCAPTCHA, or null if not set.
 * @property recaptchaSecretKey The secret key for reCAPTCHA, or null if not set.
 */
interface RecaptchaConfig {
  val isRecaptchaEnabled: Boolean
  val recaptchaSiteKey: String?
  val recaptchaSecretKey: String?
}
