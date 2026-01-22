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

import com.github.pambrose.common.util.ListUtils
import com.github.pambrose.common.util.captureStdout
import org.junit.jupiter.api.Test

class ListUtilsTests {
  @Test
  fun listPrintStringTest() {
    val output = captureStdout {
      ListUtils.listPrint(listOf("a", "b", "c"))
    }
    assert(output.trim() == "[\"a\", \"b\", \"c\"]")
  }

  @Test
  fun listPrintIntTest() {
    val output = captureStdout {
      ListUtils.listPrint(listOf(1, 2, 3))
    }
    assert(output.trim() == "[1, 2, 3]")
  }

  @Test
  fun listPrintEmptyTest() {
    val output = captureStdout {
      ListUtils.listPrint(emptyList<String>())
    }
    assert(output.trim() == "[]")
  }

  @Test
  fun listPrintMixedTypesTest() {
    // When list contains non-String types, should use toString()
    val output = captureStdout {
      ListUtils.listPrint(listOf(1.5, 2.5, 3.5))
    }
    assert(output.trim() == "[1.5, 2.5, 3.5]")
  }
}
