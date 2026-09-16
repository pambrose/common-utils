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

import com.pambrose.common.util.ListUtils
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// listPrint only prints, so the formatting it prints is checked here through ListUtils.asString, on every
// platform. ListUtilsJvmTests checks that listPrint writes it to stdout.
class ListUtilsTests : StringSpec() {
  init {
    "strings are quoted" {
      ListUtils.asString(["a", "b", "c"]) shouldBe "[\"a\", \"b\", \"c\"]"
    }

    "an empty list is an empty pair of brackets" {
      ListUtils.asString(emptyList<String>()) shouldBe "[]"
    }

    "non-strings use toString" {
      ListUtils.asString([1, 2, 3]) shouldBe "[1, 2, 3]"
      ListUtils.asString([1.5, 2.5]) shouldBe "[1.5, 2.5]"
    }

    // A list containing a String once cast every element to String?, throwing on the others.
    "strings are quoted per element in a mixed list, and null is printed bare" {
      ListUtils.asString([1, "a", 2.5]) shouldBe "[1, \"a\", 2.5]"
      ListUtils.asString(["x", 1, true]) shouldBe "[\"x\", 1, true]"
      ListUtils.asString(["a", null, 1]) shouldBe "[\"a\", null, 1]"
    }

    // Elements use the platform's toString, and JS prints a whole-number Double without ".0".
    "whole-number doubles follow the platform's toString" {
      ListUtils.asString([1.0]) shouldBe if (testPlatform == TestPlatform.JS) "[1]" else "[1.0]"
    }
  }
}
