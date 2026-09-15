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
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * Abstract base class that wraps a JSR 223 [ScriptEngine] resolved by file extension.
 *
 * Subclasses specify the script language via the [extension] parameter (e.g., `"kts"` for Kotlin,
 * `"py"` for Python, `"java"` for Java).
 *
 * @param extension the file extension used to look up the script engine (e.g., `"kts"`, `"py"`, `"java"`)
 * @throws ScriptException if no engine is found for the given extension
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractEngine(
  extension: String,
) : Closeable {
  /** The underlying JSR 223 script engine for this extension. */
  protected val scriptEngine: ScriptEngine =
    scriptManager.getEngineByExtension(extension)
      ?: throw ScriptException("Unrecognized script extension: $extension")

  /**
   * The underlying JSR 223 script engine for this extension.
   *
   * Using it directly bypasses the variable bindings and context resets that this class manages.
   */
  @Deprecated(
    "Using the engine directly bypasses variable bindings and context resets; " +
      "use the evaluation and resetContext methods instead.",
  )
  val engine: ScriptEngine get() = scriptEngine

  /**
   * Throws a [ScriptException] if [code] must not be evaluated. The default rejects literal JVM-termination calls with
   * [ScriptGuards] on a best-effort basis; subclasses add checks for their language.
   *
   * @param code the code about to be evaluated
   */
  protected open fun checkCode(code: String) = ScriptGuards.checkNoJvmExit(code)

  /** Releases resources held by the engine. The default does nothing; engines that hold resources override it. */
  override fun close() {
    // Nothing to release by default.
  }

  companion object {
    private val scriptManager by lazy { ScriptEngineManager() }
  }
}
