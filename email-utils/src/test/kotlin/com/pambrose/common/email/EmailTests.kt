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

package com.pambrose.common.email

import com.pambrose.common.email.Email.Companion.EMPTY_EMAIL
import com.pambrose.common.email.Email.Companion.UNKNOWN_EMAIL
import com.pambrose.common.email.Email.Companion.getEmail
import com.pambrose.common.email.Email.Companion.toResendEmail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.parametersOf

class EmailTests : StringSpec() {
  init {
    "email creation" {
      val email = Email("test@example.com")
      email.value shouldBe "test@example.com"
      email.toString() shouldBe "test@example.com"
    }

    "email is blank" {
      val blank = Email("")
      blank.isBlank() shouldBe true
      blank.isNotBlank() shouldBe false

      val notBlank = Email("test@example.com")
      notBlank.isBlank() shouldBe false
      notBlank.isNotBlank() shouldBe true
    }

    "email is blank or empty" {
      val empty = Email("")
      empty.isBlankOrEmpty() shouldBe true
      empty.isNotBlankOrEmpty() shouldBe false

      val whitespace = Email("   ")
      whitespace.isBlankOrEmpty() shouldBe true
      whitespace.isNotBlankOrEmpty() shouldBe false

      val valid = Email("test@example.com")
      valid.isBlankOrEmpty() shouldBe false
      valid.isNotBlankOrEmpty() shouldBe true
    }

    "email constants" {
      EMPTY_EMAIL.value shouldBe ""
      UNKNOWN_EMAIL.value shouldBe "Unknown"
    }

    "to resend email" {
      val email = "TEST@EXAMPLE.COM".toResendEmail()
      email.value shouldBe "test@example.com"

      val trimmed = "  user@test.com  ".toResendEmail()
      trimmed.value shouldBe "user@test.com"
    }

    "email is not valid email" {
      val valid = Email("test@example.com")
      valid.isNotValidEmail() shouldBe false

      val invalid = Email("not-an-email")
      invalid.isNotValidEmail() shouldBe true
    }

    "Parameters.getEmail returns the value when present" {
      val params = parametersOf("user", "alice@example.com")
      params.getEmail("user") shouldBe Email("alice@example.com")
    }

    "Parameters.getEmail returns EMPTY_EMAIL when absent" {
      val params = parametersOf("user", "alice@example.com")
      params.getEmail("missing") shouldBe EMPTY_EMAIL
    }
  }
}
