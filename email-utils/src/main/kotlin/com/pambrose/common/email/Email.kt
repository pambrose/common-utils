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

@Serializable
@JvmInline
value class Email(
  val value: String,
) {
  fun isBlank() = value.isBlank()

  fun isBlankOrEmpty() = value.isBlank() || value.isEmpty()

  fun isNotBlank() = value.isNotBlank()

  fun isNotBlankOrEmpty() = value.isNotBlank() && value.isNotEmpty()

  fun isNotValidEmail() = value.isNotValidEmail()

  override fun toString() = value

  companion object {
    // This is used to represent ALL_USER_IDS
    val EMPTY_EMAIL = Email("")
    val UNKNOWN_EMAIL = Email("Unknown")

    fun String.toResendEmail() = Email(this.lowercase().trim())

    fun Parameters.getEmail(name: String) = this[name]?.let { Email(it) } ?: EMPTY_EMAIL
  }
}
