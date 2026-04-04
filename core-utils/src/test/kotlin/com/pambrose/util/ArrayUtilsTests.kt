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

import com.pambrose.common.util.ArrayUtils
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ArrayUtilsTests : StringSpec() {
  init {
    "boolean array test" {
      ArrayUtils.asString(booleanArrayOf()) shouldBe "[]"
      ArrayUtils.asString(booleanArrayOf(true)) shouldBe "[true]"
      ArrayUtils.asString(booleanArrayOf(true, false, true)) shouldBe "[true, false, true]"
    }

    "char array test" {
      ArrayUtils.asString(charArrayOf()) shouldBe "[]"
      ArrayUtils.asString(charArrayOf('a')) shouldBe "[a]"
      ArrayUtils.asString(charArrayOf('a', 'b', 'c')) shouldBe "[a, b, c]"
    }

    "byte array test" {
      ArrayUtils.asString(byteArrayOf()) shouldBe "[]"
      ArrayUtils.asString(byteArrayOf(1)) shouldBe "[1]"
      ArrayUtils.asString(byteArrayOf(1, 2, 3)) shouldBe "[1, 2, 3]"
    }

    "short array test" {
      ArrayUtils.asString(shortArrayOf()) shouldBe "[]"
      ArrayUtils.asString(shortArrayOf(1)) shouldBe "[1]"
      ArrayUtils.asString(shortArrayOf(1, 2, 3)) shouldBe "[1, 2, 3]"
    }

    "int array test" {
      ArrayUtils.asString(intArrayOf()) shouldBe "[]"
      ArrayUtils.asString(intArrayOf(1)) shouldBe "[1]"
      ArrayUtils.asString(intArrayOf(1, 2, 3)) shouldBe "[1, 2, 3]"
    }

    "long array test" {
      ArrayUtils.asString(longArrayOf()) shouldBe "[]"
      ArrayUtils.asString(longArrayOf(1L)) shouldBe "[1]"
      ArrayUtils.asString(longArrayOf(1L, 2L, 3L)) shouldBe "[1, 2, 3]"
    }

    "float array test" {
      ArrayUtils.asString(floatArrayOf()) shouldBe "[]"
      ArrayUtils.asString(floatArrayOf(1.0f)) shouldBe "[1.0]"
      ArrayUtils.asString(floatArrayOf(1.5f, 2.5f, 3.5f)) shouldBe "[1.5, 2.5, 3.5]"
    }

    "double array test" {
      ArrayUtils.asString(doubleArrayOf()) shouldBe "[]"
      ArrayUtils.asString(doubleArrayOf(1.0)) shouldBe "[1.0]"
      ArrayUtils.asString(doubleArrayOf(1.5, 2.5, 3.5)) shouldBe "[1.5, 2.5, 3.5]"
    }

    "string array test" {
      ArrayUtils.asString(arrayOf<String>()) shouldBe "[]"
      ArrayUtils.asString(arrayOf("hello")) shouldBe "[\"hello\"]"
      ArrayUtils.asString(arrayOf("a", "b", "c")) shouldBe "[\"a\", \"b\", \"c\"]"
    }
  }
}
