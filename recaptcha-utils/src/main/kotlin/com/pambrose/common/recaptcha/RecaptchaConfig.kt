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

package com.pambrose.common.recaptcha

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
