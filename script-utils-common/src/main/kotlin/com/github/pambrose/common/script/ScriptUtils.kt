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

import javax.script.Bindings
import javax.script.ScriptContext.ENGINE_SCOPE
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptEngine
import javax.script.SimpleScriptContext

object ScriptUtils {
  val ScriptEngine.engineBindings get() = bindings(ENGINE_SCOPE)
  val ScriptEngine.globalBindings get() = bindings(GLOBAL_SCOPE)

  fun ScriptEngine.bindings(scope: Int = ENGINE_SCOPE): Bindings = getBindings(scope)

  fun ScriptEngine.resetContext(nullGlobalContext: Boolean = false) {
    context =
      SimpleScriptContext()
        .apply {
          setBindings(createBindings(), ENGINE_SCOPE)
          setBindings(if (nullGlobalContext) null else createBindings(), GLOBAL_SCOPE)
        }
  }
}