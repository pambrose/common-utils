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

import com.pambrose.common.util.simpleClassName
import com.pambrose.common.util.toCsv
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private class NamedForTest

class MiscExtensionsTests : StringSpec() {
  init {
    // Only classes whose simple name agrees across platforms; a map's implementation class, for one, does not.
    "simple class name test" {
      "hello".simpleClassName shouldBe "String"
      42.simpleClassName shouldBe "Int"
      NamedForTest().simpleClassName shouldBe "NamedForTest"
      arrayListOf(1, 2, 3).simpleClassName shouldBe "ArrayList"
    }

    "simple class name is None for an anonymous object" {
      object {}.simpleClassName shouldBe "None"
    }

    "to csv test" {
      emptyList<String>().toCsv() shouldBe ""
      ["a"].toCsv() shouldBe "a"
      ["a", "b", "c"].toCsv() shouldBe "a, b, c"
      [1, 2, 3].toCsv() shouldBe "1, 2, 3"
    }

    "toCsv does not quote or escape elements" {
      // toCsv is a display helper, not a CSV encoder.
      ["a,b", "c"].toCsv() shouldBe "a,b, c"
    }
  }
}
