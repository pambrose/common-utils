/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose.common.script

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

abstract class AbstractExprEvaluator(protected val engine: ScriptEngine) {
  constructor(extension: String) : this(scriptManager.getEngineByExtension(extension)
                                        ?: throw ScriptException("Unrecognized script extension: $extension"))

  fun eval(expr: String) = engine.eval(expr) as Boolean

  companion object {
    val scriptManager by lazy { ScriptEngineManager() }
  }
}
