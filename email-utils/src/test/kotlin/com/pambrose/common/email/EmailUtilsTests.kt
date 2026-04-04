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

import com.pambrose.common.email.EmailUtils.isNotValidEmail
import com.pambrose.common.email.EmailUtils.isValidEmail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EmailUtilsTests : StringSpec() {
  init {
    "is valid email simple" {
      "test@example.com".isValidEmail() shouldBe true
      "user.name@domain.org".isValidEmail() shouldBe true
      // Note: The email pattern doesn't support + character in local part
      "user@example.co.uk".isValidEmail() shouldBe true
    }

    "is valid email with subdomain" {
      "user@subdomain.example.com".isValidEmail() shouldBe true
      "admin@mail.company.org".isValidEmail() shouldBe true
    }

    "is not valid email invalid format" {
      "not-an-email".isNotValidEmail() shouldBe true
      "missing@".isNotValidEmail() shouldBe true
      "@nodomain.com".isNotValidEmail() shouldBe true
      "spaces in@email.com".isNotValidEmail() shouldBe true
    }

    "is not valid email empty" {
      "".isNotValidEmail() shouldBe true
      "   ".isNotValidEmail() shouldBe true
    }

    "is valid email with numbers" {
      "user123@example.com".isValidEmail() shouldBe true
      "123user@test.org".isValidEmail() shouldBe true
    }

    "is valid email with hyphens" {
      "user-name@example.com".isValidEmail() shouldBe true
      "test@my-domain.com".isValidEmail() shouldBe true
    }
  }
}
