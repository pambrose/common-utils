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
 * Best-effort checks applied to Python code by [PythonScript] and [PythonExprEvaluator] before it runs.
 *
 * - **JVM termination:** literal calls such as `java.lang.System.exit(...)` and `Runtime.getRuntime().halt(...)` are
 *   rejected with [ScriptGuards], because from Jython they really terminate the JVM.
 * - **Python exits:** `sys.exit()`, `exit()`, `quit()`, and `raise SystemExit` are rejected too. Under JSR 223 they
 *   only raise `SystemExit`, which the engine reports as a [ScriptException], so rejecting them gives a clearer
 *   error rather than preventing termination.
 *
 * The checks match text, including inside string literals and comments, so `print('exit(1)')` is rejected. Method
 * calls and definitions such as `obj.exit()` and `def exit(self):` are allowed. This is **not** a security sandbox:
 * it is trivially bypassed, for example with `os._exit(0)`, `getattr(sys, 'ex' + 'it')(0)`, or reflection. Run
 * untrusted scripts in an isolated process or JVM.
 */
internal object PythonGuards {
  private val SYS_EXIT_PATTERN = Regex("""(?<!\w)sys\.exit\s*\(""")
  private val SYSTEM_EXIT_PATTERN = Regex("""(?<!\w)raise\s+SystemExit\b""")

  // (?<![\w.]) skips method calls on objects (obj.exit()) and longer identifiers; the def lookbehind skips definitions.
  private val EXIT_PATTERN = Regex("""(?<![\w.])(?<!\bdef\s{1,32})exit\s*\(""")
  private val QUIT_PATTERN = Regex("""(?<![\w.])(?<!\bdef\s{1,32})quit\s*\(""")

  /** Python 2.7 keywords, which cannot be used as variable names. */
  val KEYWORDS =
    setOf(
      "and",
      "as",
      "assert",
      "break",
      "class",
      "continue",
      "def",
      "del",
      "elif",
      "else",
      "except",
      "exec",
      "finally",
      "for",
      "from",
      "global",
      "if",
      "import",
      "in",
      "is",
      "lambda",
      "not",
      "or",
      "pass",
      "print",
      "raise",
      "return",
      "try",
      "while",
      "with",
      "yield",
    )

  /**
   * Throws a [ScriptException] if [code] contains a call rejected by the checks described above.
   *
   * @param code the Python code about to run
   */
  fun check(code: String) {
    ScriptGuards.checkNoJvmExit(code)

    if (SYS_EXIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal call to sys.exit()")

    // sys.exit()/exit()/quit() are all implemented as `raise SystemExit`, which is the same exit written directly.
    if (SYSTEM_EXIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal 'raise SystemExit'")

    if (EXIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal call to exit()")

    if (QUIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal call to quit()")
  }
}
