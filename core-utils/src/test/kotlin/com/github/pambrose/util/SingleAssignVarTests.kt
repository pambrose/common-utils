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

import com.github.pambrose.common.delegate.SingleAssignVar
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SingleAssignVarTests : StringSpec() {
  init {
    "single assign basic test" {
      var value: String? by SingleAssignVar.singleAssign()
      value shouldBe null

      value = "assigned"
      value shouldBe "assigned"

      // Second assignment should throw
      shouldThrow<IllegalStateException> {
        value = "second"
      }
      value shouldBe "assigned"
    }

    "single assign null value test" {
      var value: String? by SingleAssignVar.singleAssign()
      value shouldBe null

      // Assigning null should work and count as assignment
      value = null
      value shouldBe null

      // Second assignment (even null) should throw
      shouldThrow<IllegalStateException> {
        value = "something"
      }
    }

    "single assign with different types test" {
      var intValue: Int? by SingleAssignVar.singleAssign()
      intValue shouldBe null
      intValue = 42
      intValue shouldBe 42

      var listValue: List<String>? by SingleAssignVar.singleAssign()
      listValue shouldBe null
      listValue = listOf("a", "b", "c")
      listValue shouldBe listOf("a", "b", "c")
    }
  }
}
