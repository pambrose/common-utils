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

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import javax.script.ScriptException

/**
 * Tests for the [System] shadow object that [KotlinScript] auto-imports to block
 * `System.exit()` calls in scripts.
 *
 * Because this test class is in the same package, the unqualified `System` below resolves to
 * [com.pambrose.common.script.System] (package declarations take precedence over the default
 * `java.lang` import) — exactly how it shadows [java.lang.System] inside a script context.
 */
class SystemTests : StringSpec() {
  init {
    "System.exit throws a ScriptException instead of terminating the JVM" {
      val e = shouldThrow<ScriptException> { System.exit(0) }
      e.message shouldContain "Illegal call to System.exit()"
    }

    "System.exit includes the attempted status code in the message" {
      shouldThrow<ScriptException> { System.exit(42) }.message shouldContain "- 42"
    }
  }
}
