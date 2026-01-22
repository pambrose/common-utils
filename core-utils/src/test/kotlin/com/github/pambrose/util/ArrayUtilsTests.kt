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

package com.github.pambrose.util

import com.github.pambrose.common.util.ArrayUtils
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ArrayUtilsTests {
  @Test
  fun booleanArrayTest() {
    ArrayUtils.asString(booleanArrayOf()) shouldBe "[]"
    ArrayUtils.asString(booleanArrayOf(true)) shouldBe "[true]"
    ArrayUtils.asString(booleanArrayOf(true, false, true)) shouldBe "[true, false, true]"
  }

  @Test
  fun charArrayTest() {
    ArrayUtils.asString(charArrayOf()) shouldBe "[]"
    ArrayUtils.asString(charArrayOf('a')) shouldBe "[a]"
    ArrayUtils.asString(charArrayOf('a', 'b', 'c')) shouldBe "[a, b, c]"
  }

  @Test
  fun byteArrayTest() {
    ArrayUtils.asString(byteArrayOf()) shouldBe "[]"
    ArrayUtils.asString(byteArrayOf(1)) shouldBe "[1]"
    ArrayUtils.asString(byteArrayOf(1, 2, 3)) shouldBe "[1, 2, 3]"
  }

  @Test
  fun shortArrayTest() {
    ArrayUtils.asString(shortArrayOf()) shouldBe "[]"
    ArrayUtils.asString(shortArrayOf(1)) shouldBe "[1]"
    ArrayUtils.asString(shortArrayOf(1, 2, 3)) shouldBe "[1, 2, 3]"
  }

  @Test
  fun intArrayTest() {
    ArrayUtils.asString(intArrayOf()) shouldBe "[]"
    ArrayUtils.asString(intArrayOf(1)) shouldBe "[1]"
    ArrayUtils.asString(intArrayOf(1, 2, 3)) shouldBe "[1, 2, 3]"
  }

  @Test
  fun longArrayTest() {
    ArrayUtils.asString(longArrayOf()) shouldBe "[]"
    ArrayUtils.asString(longArrayOf(1L)) shouldBe "[1]"
    ArrayUtils.asString(longArrayOf(1L, 2L, 3L)) shouldBe "[1, 2, 3]"
  }

  @Test
  fun floatArrayTest() {
    ArrayUtils.asString(floatArrayOf()) shouldBe "[]"
    ArrayUtils.asString(floatArrayOf(1.0f)) shouldBe "[1.0]"
    ArrayUtils.asString(floatArrayOf(1.5f, 2.5f, 3.5f)) shouldBe "[1.5, 2.5, 3.5]"
  }

  @Test
  fun doubleArrayTest() {
    ArrayUtils.asString(doubleArrayOf()) shouldBe "[]"
    ArrayUtils.asString(doubleArrayOf(1.0)) shouldBe "[1.0]"
    ArrayUtils.asString(doubleArrayOf(1.5, 2.5, 3.5)) shouldBe "[1.5, 2.5, 3.5]"
  }

  @Test
  fun stringArrayTest() {
    ArrayUtils.asString(arrayOf<String>()) shouldBe "[]"
    ArrayUtils.asString(arrayOf("hello")) shouldBe "[\"hello\"]"
    ArrayUtils.asString(arrayOf("a", "b", "c")) shouldBe "[\"a\", \"b\", \"c\"]"
  }
}
