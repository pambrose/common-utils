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

package com.pambrose.util

import com.pambrose.common.util.capitalizeFirstChar
import com.pambrose.common.util.isNotNull
import com.pambrose.common.util.isNull
import com.pambrose.common.util.lpad
import com.pambrose.common.util.rpad
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MiscFuncsTests : StringSpec() {
  init {
    "is null test" {
      val nullValue: String? = null
      val nonNullValue = "test"

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

    // As with "%04d", the zeros go after the sign and the sign counts toward the width. They used to go in front
    // of it ("00-1").
    "lpad zero-pads a negative number after its sign" {
      (-1).lpad(4) shouldBe "-001"
      (-42).lpad(2) shouldBe "-42"
      Int.MIN_VALUE.lpad(12) shouldBe "-02147483648"
      (-1).lpad(4, ' ') shouldBe "  -1"
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

    // capitalizeFirstChar uses title case, not upper case: the two differ for digraphs, and a character whose
    // title case is several characters expands.
    "capitalizeFirstChar uses title case for non-ASCII letters" {
      "\u01C6a".capitalizeFirstChar() shouldBe "\u01C5a" // ǆ -> ǅ (upper case would be Ǆ)
      "\u00DFa".capitalizeFirstChar() shouldBe "Ssa" // ß has no single-character title case
      "\u00E9t\u00E9".capitalizeFirstChar() shouldBe "\u00C9t\u00E9" // été -> Été
    }
  }
}
