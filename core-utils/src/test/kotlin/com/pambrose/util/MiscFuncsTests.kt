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

package com.pambrose.util

import com.pambrose.common.util.capitalizeFirstChar
import com.pambrose.common.util.hostInfo
import com.pambrose.common.util.isNotNull
import com.pambrose.common.util.isNull
import com.pambrose.common.util.lpad
import com.pambrose.common.util.randomId
import com.pambrose.common.util.rpad
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

class MiscFuncsTests : StringSpec() {
  init {
    "host info test" {
      hostInfo shouldNotBe null
      hostInfo.hostName shouldNotBe null
      hostInfo.ipAddress shouldNotBe null
    }

    "random id default test" {
      val id = randomId()
      id shouldHaveLength 10
      id shouldMatch Regex("[a-zA-Z0-9]+")
    }

    "random id custom length test" {
      randomId(5) shouldHaveLength 5
      randomId(20) shouldHaveLength 20
      randomId(1) shouldHaveLength 1
    }

    "random id custom char pool test" {
      val numericId = randomId(10, ('0'..'9').toList())
      numericId shouldMatch Regex("[0-9]+")

      val lowercaseId = randomId(10, ('a'..'z').toList())
      lowercaseId shouldMatch Regex("[a-z]+")
    }

    "random id uniqueness test" {
      val ids = (1..100).map { randomId() }.toSet()
      ids.size shouldBeGreaterThan 95 // Should be mostly unique
    }

    "is null test" {
      val nullValue: String? = null
      val nonNullValue: String? = "test"

      nullValue.isNull() shouldBe true
      nonNullValue.isNull() shouldBe false

      nullValue.isNotNull() shouldBe false
      nonNullValue.isNotNull() shouldBe true
    }

    "lpad test" {
      1.lpad(3) shouldBe "001"
      42.lpad(5) shouldBe "00042"
      123.lpad(2) shouldBe "123" // When number is longer, no padding
      0.lpad(3) shouldBe "000"
      1.lpad(3, ' ') shouldBe "  1"
    }

    "rpad test" {
      1.rpad(3) shouldBe "100"
      42.rpad(5) shouldBe "42000"
      123.rpad(2) shouldBe "123" // When number is longer, no padding
      0.rpad(3) shouldBe "000"
      1.rpad(3, ' ') shouldBe "1  "
    }

    "capitalize first char test" {
      "hello".capitalizeFirstChar() shouldBe "Hello"
      "Hello".capitalizeFirstChar() shouldBe "Hello"
      "h".capitalizeFirstChar() shouldBe "H"
      "".capitalizeFirstChar() shouldBe ""
      "123abc".capitalizeFirstChar() shouldBe "123abc"
    }
  }
}
