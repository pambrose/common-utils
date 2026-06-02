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

import java.io.Closeable
import javax.script.ScriptException
import kotlin.reflect.KType
import org.python.jsr223.PyScriptEngine

/**
 * A script engine wrapper for dynamically evaluating Python source code using the Jython engine.
 *
 * Variables are bound directly to the engine without type parameter support, since Python
 * is dynamically typed.
 *
 * Common literal calls to `sys.exit()`, `exit()`, and `quit()` are rejected on a best-effort basis.
 * This is a convenience against accidental termination, **not** a security sandbox: it is trivially
 * bypassed (e.g. `os._exit(0)`, `getattr(sys, 'ex' + 'it')(0)`, reflection, or any JVM access exposed
 * by Jython), and it does not inspect string literals or comments. Run untrusted scripts in an isolated
 * process or JVM. Method calls on user objects (for example `obj.exit()`) are intentionally allowed.
 *
 * @param nullGlobalContext if `true`, sets the global scope bindings to `null` on initialization
 * @see AbstractScript
 */
class PythonScript(
  nullGlobalContext: Boolean = false,
) : AbstractScript("py", nullGlobalContext),
  Closeable {
  /**
   * Adds a named variable to the script context.
   *
   * Type parameters are ignored for Python since it is dynamically typed.
   *
   * @param name the variable name to bind in the script
   * @param value the value to associate with the variable
   * @param types ignored for Python scripts
   */
  override fun add(
    name: String,
    value: Any,
    vararg types: KType,
  ) {
    valueMap[name] = value
  }

  @Synchronized
  fun eval(code: String): Any? {
    if (SYS_EXIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal call to sys.exit()")

    // sys.exit()/exit()/quit() are all implemented as `raise SystemExit`, which is the same
    // termination written directly; reject it too.
    if (SYSTEM_EXIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal 'raise SystemExit'")

    if (EXIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal call to exit()")

    if (QUIT_PATTERN.containsMatchIn(code))
      throw ScriptException("Illegal call to quit()")

    if (!initialized) {
      valueMap.forEach { (name, value) -> engine.put(name, value) }
      initialized = true
    }

    return engine.eval(code)
  }

  override fun close() {
    (engine as PyScriptEngine).close()
  }

  companion object {
    // (?<![\w.]) excludes a preceding word char *or* '.', so a method call on a user object
    // (e.g. obj.exit(), queue.quit()) is not mistaken for the bare Python builtin. Best-effort only.
    private val SYS_EXIT_PATTERN = Regex("""(?<!\w)sys\.exit\s*\(""")
    private val SYSTEM_EXIT_PATTERN = Regex("""(?<!\w)raise\s+SystemExit\b""")
    private val EXIT_PATTERN = Regex("""(?<![\w.])exit\s*\(""")
    private val QUIT_PATTERN = Regex("""(?<![\w.])quit\s*\(""")
  }
}
