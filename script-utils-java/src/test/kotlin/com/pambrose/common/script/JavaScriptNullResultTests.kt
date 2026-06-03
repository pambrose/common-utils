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

package com.pambrose.common.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class JavaScriptNullResultTests : StringSpec() {
  init {
    "eval returns null when the expression evaluates to null" {
      JavaScript().use { it.eval("null") shouldBe null }
    }

    "eval returns null when the action produces a null result" {
      JavaScript().use { it.eval("s", "String s = null;") shouldBe null }
    }

    "evalScript returns null when the callable method returns null" {
      JavaScript().use {
        it.evalScript(
          """
          public class Main {
            public Object getValue() {
              return null;
            }
          }
          """.trimIndent(),
        ) shouldBe null
      }
    }
  }
}
