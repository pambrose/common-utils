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

import com.pambrose.common.util.ListUtils
import com.pambrose.common.util.captureStdout
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ListUtilsTests : StringSpec() {
  init {
    "list print string test" {
      val output = captureStdout {
        ListUtils.listPrint(listOf("a", "b", "c"))
      }
      output.trim() shouldBe "[\"a\", \"b\", \"c\"]"
    }

    "list print int test" {
      val output = captureStdout {
        ListUtils.listPrint(listOf(1, 2, 3))
      }
      output.trim() shouldBe "[1, 2, 3]"
    }

    "list print empty test" {
      val output = captureStdout {
        ListUtils.listPrint(emptyList<String>())
      }
      output.trim() shouldBe "[]"
    }

    "list print mixed types test" {
      // When list contains non-String types, should use toString()
      val output = captureStdout {
        ListUtils.listPrint(listOf(1.5, 2.5, 3.5))
      }
      output.trim() shouldBe "[1.5, 2.5, 3.5]"
    }
  }
}
