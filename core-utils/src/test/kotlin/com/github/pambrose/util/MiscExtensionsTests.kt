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

import com.github.pambrose.common.util.simpleClassName
import com.github.pambrose.common.util.stackTraceAsString
import com.github.pambrose.common.util.toCsv
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class MiscExtensionsTests {
  @Test
  fun stackTraceAsStringTest() {
    val exception = RuntimeException("test error")
    val stackTrace = exception.stackTraceAsString

    stackTrace shouldContain "RuntimeException"
    stackTrace shouldContain "test error"
    stackTrace shouldContain "MiscExtensionsTests"
  }

  @Test
  fun simpleClassNameTest() {
    "hello".simpleClassName shouldBe "String"
    42.simpleClassName shouldBe "Int"
    listOf(1, 2, 3).simpleClassName shouldBe "ArrayList"
    // Single entry maps may use optimized implementations like SingletonMap
    mapOf("a" to 1, "b" to 2).simpleClassName shouldBe "LinkedHashMap"
  }

  @Test
  fun toCsvTest() {
    emptyList<String>().toCsv() shouldBe ""
    listOf("a").toCsv() shouldBe "a"
    listOf("a", "b", "c").toCsv() shouldBe "a, b, c"
    listOf(1, 2, 3).toCsv() shouldBe "1, 2, 3"
  }
}
