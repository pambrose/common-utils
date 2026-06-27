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

import com.pambrose.common.util.with
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// Helpers exercise the context parameters supplied by with(a, b) { ... }.
// Each is resolved by type, so a and b satisfy separate context(...) parameters.

context(text: String)
private fun shout(): String = text.uppercase()

context(value: Int)
private fun doubled(): Int = value * 2

context(prefix: String, repeat: Int)
private fun render(): String = prefix.repeat(repeat)

class ScopeFunctionsTests : StringSpec() {
  init {
    "with makes both values available as context parameters" {
      with("ab", 3) { render() } shouldBe "ababab"
    }

    "with resolves each receiver to a separate single-context function by type" {
      with("hi", 21) { "${shout()}-${doubled()}" } shouldBe "HI-42"
    }

    "with returns the block result and propagates its type" {
      val length: Int = with("hello", 4) { shout().length + doubled() }
      length shouldBe 13
    }

    "with keeps the two receivers distinct rather than collapsing to a supertype" {
      // a: String and b: Int share only Any/Comparable as a supertype; a vararg
      // variant would lose their concrete types and break the calls above.
      with("Alice", 30) { "${shout()} is ${doubled()}" } shouldBe "ALICE is 60"
    }
  }
}
