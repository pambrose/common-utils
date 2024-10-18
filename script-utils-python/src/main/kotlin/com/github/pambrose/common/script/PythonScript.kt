/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import org.python.jsr223.PyScriptEngine
import java.io.Closeable
import javax.script.ScriptException
import kotlin.reflect.KType

class PythonScript(
  nullGlobalContext: Boolean = false,
) : AbstractScript("py", nullGlobalContext),
    Closeable {
  override fun add(
    name: String,
    value: Any,
    vararg types: KType,
  ) {
    valueMap[name] = value
  }

  @Synchronized
  fun eval(code: String): Any? {
    if ("sys.exit(" in code)
      throw ScriptException("Illegal call to sys.exit()")

    if ("exit(" in code)
      throw ScriptException("Illegal call to exit()")

    if ("quit(" in code)
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
}
