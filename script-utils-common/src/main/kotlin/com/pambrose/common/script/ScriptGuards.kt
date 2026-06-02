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

import javax.script.ScriptException

/**
 * Best-effort guards that reject the most common, literal JVM-termination calls in evaluated JVM
 * (Kotlin/Java) scripts: `System.exit(...)`, `kotlin.system.exitProcess(...)`, and
 * `Runtime.getRuntime().exit(...)` / `.halt(...)`.
 *
 * **These guards are NOT a security sandbox.** They match only obvious, literal call forms by simple
 * pattern matching and are trivially bypassed — for example by building the call from string fragments
 * (`"ex" + "it"`), reflection, aliasing the runtime (`val r = Runtime.getRuntime(); r.halt(0)`), or any
 * other indirection. They also match calls that appear inside string literals or comments. Their purpose
 * is to catch *accidental* JVM termination during development, not to safely run untrusted code. To run
 * untrusted scripts, isolate them in a separate process or JVM with a restrictive security policy.
 */
object ScriptGuards {
  private val jvmExitPatterns =
    listOf(
      // System.exit(...), including fully-qualified java.lang.System.exit(...); not mySystem.exit(...)
      Regex("""(?<!\w)System\s*\.\s*exit\s*\("""),
      // bare exitProcess(...) (the imported Kotlin idiom); excludes obj.exitProcess(...) on a user object
      Regex("""(?<![\w.])exitProcess\s*\("""),
      // fully-qualified kotlin.system.exitProcess(...)
      Regex("""(?<!\w)system\s*\.\s*exitProcess\s*\("""),
      // Runtime.getRuntime().exit(...) / Runtime.getRuntime().halt(...)
      Regex("""(?<!\w)Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*(?:exit|halt)\s*\("""),
      // statically-imported getRuntime().exit/halt (no `Runtime.` prefix); excludes obj.getRuntime()
      Regex("""(?<![\w.])getRuntime\s*\(\s*\)\s*\.\s*(?:exit|halt)\s*\("""),
    )

  /**
   * Throws a [ScriptException] if any of [fragments] contains a recognized literal JVM-termination call.
   *
   * All fragments are scanned together, so a termination call split across, for example, an expression
   * and a separate action/statement block is still detected.
   *
   * @param fragments the code fragments to scan (joined before matching)
   * @throws ScriptException if a literal `System.exit`, `exitProcess`, or `Runtime.getRuntime().exit/halt`
   *   call is found. This is a best-effort check, not a security boundary; see the class documentation.
   */
  fun checkNoJvmExit(vararg fragments: String) {
    val code = fragments.joinToString("\n")
    if (jvmExitPatterns.any { it.containsMatchIn(code) }) {
      throw ScriptException(
        "Illegal call to a JVM termination method (System.exit / exitProcess / Runtime.exit / Runtime.halt)",
      )
    }
  }
}
