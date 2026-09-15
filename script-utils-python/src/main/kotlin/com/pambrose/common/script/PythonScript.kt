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

import kotlin.reflect.KType
import org.python.jsr223.PyScriptEngine

/**
 * A script engine wrapper for dynamically evaluating Python source code using the Jython engine.
 *
 * Variables are bound directly to the engine without type parameter support, since Python
 * is dynamically typed.
 *
 * Code is checked before it runs, on a best-effort basis: literal JVM-termination calls and Python's `sys.exit()`,
 * `exit()`, `quit()`, and `raise SystemExit` are rejected. The checks match text, including inside string literals and
 * comments, and they are **not** a security sandbox; see [PythonGuards] for what they do and do not catch. Run
 * untrusted scripts in an isolated process or JVM. Method calls and definitions such as `obj.exit()` and
 * `def exit(self):` are allowed.
 *
 * @param nullGlobalContext if `true`, sets the global scope bindings to `null` on initialization
 * @see AbstractScript
 */
class PythonScript(
  nullGlobalContext: Boolean = false,
) : AbstractScript("py", nullGlobalContext) {
  override fun isReserved(name: String) = name in PYTHON_KEYWORDS

  override fun checkCode(code: String) = PythonGuards.check(code)

  /**
   * Adds a named variable to the script context.
   *
   * Type parameters are ignored for Python since it is dynamically typed.
   *
   * @param name the variable name to bind in the script
   * @param value the value to associate with the variable
   * @param types ignored for Python scripts
   * @throws javax.script.ScriptException if [name] is not a valid Python identifier
   */
  @Synchronized
  override fun add(
    name: String,
    value: Any,
    vararg types: KType,
  ) {
    checkName(name)
    register(name, value)
  }

  /**
   * Evaluates Python [code] and returns its result.
   *
   * Variables added with [add] are bound first, including any added after an earlier evaluation.
   *
   * @param code the Python source code to evaluate
   * @return the result of the evaluation, or `null`
   * @throws javax.script.ScriptException if [code] fails the checks described above, or fails to run
   */
  @Synchronized
  fun eval(code: String): Any? {
    prepare(code)
    return scriptEngine.eval(code)
  }

  override fun close() {
    (scriptEngine as PyScriptEngine).close()
  }

  private companion object {
    // Python 2.7 keywords, which cannot be used as variable names.
    val PYTHON_KEYWORDS =
      (
        "and as assert break class continue def del elif else except exec finally for from global if import in is " +
          "lambda not or pass print raise return try while with yield"
      ).split(" ").toSet()
  }
}
