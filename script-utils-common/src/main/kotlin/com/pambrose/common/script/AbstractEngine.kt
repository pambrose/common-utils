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

import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * Abstract base class that wraps a JSR 223 [javax.script.ScriptEngine] resolved by file extension.
 *
 * Subclasses specify the script language via the [extension] parameter (e.g., `"kts"` for Kotlin,
 * `"py"` for Python, `"java"` for Java).
 *
 * @param extension the file extension used to look up the script engine (e.g., `"kts"`, `"py"`, `"java"`)
 * @throws ScriptException if no engine is found for the given extension
 */
abstract class AbstractEngine(
  extension: String,
) {
  /** The underlying JSR 223 script engine for this extension. */
  val engine =
    scriptManager.getEngineByExtension(extension)
      ?: throw ScriptException("Unrecognized script extension: $extension")

  companion object {
    private val scriptManager by lazy { ScriptEngineManager() }
  }
}
