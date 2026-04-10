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

package com.pambrose.common.email

import com.pambrose.common.email.EmailUtils.isNotValidEmail
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

/**
 * A serializable inline value class wrapping an email address string.
 *
 * Provides convenience methods for checking blank/empty state and email validation.
 *
 * @property value the raw email address string.
 */
@Serializable
@JvmInline
value class Email(
  val value: String,
) {
  /** Returns `true` if the email address string is blank. */
  fun isBlank() = value.isBlank()

  /** Returns `true` if the email address string is blank or empty. */
  fun isBlankOrEmpty() = value.isBlank() || value.isEmpty()

  /** Returns `true` if the email address string is not blank. */
  fun isNotBlank() = value.isNotBlank()

  /** Returns `true` if the email address string is neither blank nor empty. */
  fun isNotBlankOrEmpty() = value.isNotBlank() && value.isNotEmpty()

  /** Returns `true` if the email address is not a valid email format. */
  fun isNotValidEmail() = value.isNotValidEmail()

  override fun toString() = value

  companion object {
    /** Sentinel representing an empty/unset email address. */
    val EMPTY_EMAIL = Email("")

    /** Sentinel representing an unknown email address. */
    val UNKNOWN_EMAIL = Email("Unknown")

    /**
     * Converts this [String] to an [Email], applying lowercase and trimming whitespace.
     *
     * @return a normalized [Email] instance.
     */
    fun String.toResendEmail() = Email(this.lowercase().trim())

    /**
     * Extracts an [Email] from Ktor [Parameters] by the given parameter [name].
     *
     * @param name the parameter key to look up.
     * @return the [Email] value, or [EMPTY_EMAIL] if the parameter is absent.
     */
    fun Parameters.getEmail(name: String) = this[name]?.let { Email(it) } ?: EMPTY_EMAIL
  }
}
