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
import java.lang.ref.WeakReference

private const val GC_ATTEMPTS = 100
private const val GC_PAUSE_MILLIS = 50L

// Requests garbage collection until this reference is cleared, and returns whether it was.
@Suppress("ExplicitGarbageCollectionCall")
private fun WeakReference<*>.isClearedAfterGc(): Boolean {
  repeat(GC_ATTEMPTS) {
    System.gc()
    if (get() == null) return true
    Thread.sleep(GC_PAUSE_MILLIS)
  }
  return false
}

// Binds a new object in script and evaluates it, returning only a weak reference to it, so that no frame of the caller
// keeps it reachable.
private fun bindAndEvaluate(script: PythonScript): WeakReference<Any> {
  val variable = StringBuilder("held")
  script.add("variable", variable)
  script.eval("variable.length()") shouldBe 4
  return WeakReference(variable)
}

/** Tests, on the real Jython engine, that script-utils-common's context reset releases the replaced context. */
class PythonContextReleaseTests : StringSpec() {
  init {
    releaseTests()
  }

  private fun releaseTests() {
    // Jython keeps the context of its last evaluation in the interpreter's locals until the next one, so a reset used
    // to leave the previous variables reachable for as long as the script sat idle.
    "resetContext releases the variables of the context it replaces" {
      PythonScript().use { script ->
        val variable = bindAndEvaluate(script)
        script.resetContext(false)
        variable.isClearedAfterGc() shouldBe true
        script.eval("1 + 1") shouldBe 2
      }
    }
  }
}
