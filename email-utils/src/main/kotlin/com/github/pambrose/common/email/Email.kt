package com.github.pambrose.common.email

import com.github.pambrose.common.email.EmailUtils.isNotValidEmail
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

    fun String.toEmail() = Email(this.lowercase().trim())

    fun Parameters.getEmail(name: String) = this[name]?.let { Email(it) } ?: EMPTY_EMAIL
  }
}
