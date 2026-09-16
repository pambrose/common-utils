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

// The asString formatting is covered on every platform by the common ArrayUtilsTests.
class ArrayUtilsJvmTests : StringSpec() {
  init {
    "arrayPrint writes asString output to stdout for every primitive overload" {
      val originalOut = System.out
      val captured = java.io.ByteArrayOutputStream()
      System.setOut(java.io.PrintStream(captured))
      try {
        ArrayUtils.arrayPrint(booleanArrayOf(true, false))
        ArrayUtils.arrayPrint(charArrayOf('a', 'b'))
        ArrayUtils.arrayPrint(byteArrayOf(1, 2))
        ArrayUtils.arrayPrint(shortArrayOf(3, 4))
        ArrayUtils.arrayPrint(intArrayOf(5, 6))
        ArrayUtils.arrayPrint(longArrayOf(7L, 8L))
        ArrayUtils.arrayPrint(floatArrayOf(1.5f, 2.5f))
        ArrayUtils.arrayPrint(doubleArrayOf(1.5, 2.5))
        ArrayUtils.arrayPrint(arrayOf("x", "y"))
      } finally {
        System.setOut(originalOut)
      }
      val lines = captured.toString().lines()
      lines[0] shouldBe "[true, false]"
      lines[1] shouldBe "[a, b]"
      lines[2] shouldBe "[1, 2]"
      lines[3] shouldBe "[3, 4]"
      lines[4] shouldBe "[5, 6]"
      lines[5] shouldBe "[7, 8]"
      lines[6] shouldBe "[1.5, 2.5]"
      lines[7] shouldBe "[1.5, 2.5]"
      lines[8] shouldBe "[\"x\", \"y\"]"
    }
  }
}
