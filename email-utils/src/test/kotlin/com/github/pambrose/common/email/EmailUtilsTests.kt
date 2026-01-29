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

package com.github.pambrose.common.email

import com.github.pambrose.common.email.EmailUtils.isNotValidEmail
import com.github.pambrose.common.email.EmailUtils.isValidEmail
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EmailUtilsTests {
  @Test
  fun isValidEmailSimpleTest() {
    "test@example.com".isValidEmail() shouldBe true
    "user.name@domain.org".isValidEmail() shouldBe true
    // Note: The email pattern doesn't support + character in local part
    "user@example.co.uk".isValidEmail() shouldBe true
  }

  @Test
  fun isValidEmailWithSubdomainTest() {
    "user@subdomain.example.com".isValidEmail() shouldBe true
    "admin@mail.company.org".isValidEmail() shouldBe true
  }

  @Test
  fun isNotValidEmailInvalidFormatTest() {
    "not-an-email".isNotValidEmail() shouldBe true
    "missing@".isNotValidEmail() shouldBe true
    "@nodomain.com".isNotValidEmail() shouldBe true
    "spaces in@email.com".isNotValidEmail() shouldBe true
  }

  @Test
  fun isNotValidEmailEmptyTest() {
    "".isNotValidEmail() shouldBe true
    "   ".isNotValidEmail() shouldBe true
  }

  @Test
  fun isValidEmailWithNumbersTest() {
    "user123@example.com".isValidEmail() shouldBe true
    "123user@test.org".isValidEmail() shouldBe true
  }

  @Test
  fun isValidEmailWithHyphensTest() {
    "user-name@example.com".isValidEmail() shouldBe true
    "test@my-domain.com".isValidEmail() shouldBe true
  }
}
