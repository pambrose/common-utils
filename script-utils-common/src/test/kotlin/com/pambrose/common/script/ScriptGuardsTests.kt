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

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import io.kotest.core.spec.style.StringSpec
import javax.script.ScriptException

class ScriptGuardsTests : StringSpec() {
  init {
    // These tests only run the matcher on strings; they never evaluate scripts, so they cannot
    // terminate the test JVM even when the input is a real termination call.

    "rejects System.exit in its common forms" {
      listOf(
        "System.exit(0)",
        "java.lang.System.exit(1)",
        "System . exit ( 0 )",
        "return System.exit(0);",
      ).forEach { code ->
        shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit(code) }
      }
    }

    "rejects kotlin exitProcess" {
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("exitProcess(0)") }
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("kotlin.system.exitProcess(1)") }
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("exitProcess (2)") }
    }

    "rejects Runtime exit and halt" {
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("Runtime.getRuntime().exit(0)") }
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("Runtime.getRuntime().halt(0)") }
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("Runtime . getRuntime ( ) . halt ( 1 )") }
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("java.lang.Runtime.getRuntime().halt(0)") }
    }

    "rejects statically-imported bare getRuntime().exit/halt" {
      // `import static java.lang.Runtime.getRuntime;` then `getRuntime().halt(0)` would kill the JVM.
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("getRuntime().exit(0)") }
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("getRuntime().halt(0)") }
    }

    "detects a termination call split across fragments" {
      shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("0", "System.exit(1);") }
    }

    "error message names the termination methods" {
      val ex = shouldThrow<ScriptException> { ScriptGuards.checkNoJvmExit("System.exit(0)") }
      ex.message shouldContain "exit"
    }

    "does not reject safe code or unrelated identifiers" {
      listOf(
        "val x = 1 + 2",
        "mySystem.exit(0)",
        "obj.exitProcess(args)",
        "return value;",
        "doExit()",
        "println(\"done\")",
      ).forEach { code ->
        shouldNotThrow<ScriptException> { ScriptGuards.checkNoJvmExit(code) }
      }
    }
  }
}
