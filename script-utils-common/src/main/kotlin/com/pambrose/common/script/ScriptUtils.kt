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

import javax.script.Bindings
import javax.script.ScriptContext.ENGINE_SCOPE
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptEngine
import javax.script.SimpleScriptContext

/**
 * Utility object providing extension functions for [ScriptEngine].
 */
object ScriptUtils {
  /**
   * Returns the engine-scope [Bindings] for this [ScriptEngine]. A script context always has them.
   */
  val ScriptEngine.engineBindings: Bindings get() = getBindings(ENGINE_SCOPE)

  /**
   * Returns the global-scope [Bindings] for this [ScriptEngine], or `null` when its context has none, as after
   * [resetContext] with `nullGlobalContext = true`.
   */
  val ScriptEngine.globalBindings: Bindings? get() = bindings(GLOBAL_SCOPE)

  /**
   * Returns the [Bindings] for the specified [scope] on this [ScriptEngine].
   *
   * @param scope the scope to retrieve bindings for (defaults to [ENGINE_SCOPE])
   * @return the [Bindings] for the given scope, or `null` when the context has none for it, which only the global
   *   scope can
   */
  fun ScriptEngine.bindings(scope: Int = ENGINE_SCOPE): Bindings? = getBindings(scope)

  /**
   * Resets the context of this [ScriptEngine] by creating a new [SimpleScriptContext]
   * with fresh bindings.
   *
   * The engine-scope bindings being replaced are cleared first. Some engines keep the context of their last evaluation
   * until the next one, as the Kotlin engine (with its REPL state) and Jython do, so otherwise everything bound in
   * that scope would stay reachable while the engine sat idle. The global-scope bindings being replaced are left as
   * they are, since `ScriptEngineManager` gives the same ones to every engine it creates.
   *
   * @param nullGlobalContext if `true`, sets the global scope bindings to `null`;
   *   otherwise creates new empty bindings for the global scope
   */
  fun ScriptEngine.resetContext(nullGlobalContext: Boolean = false) {
    getBindings(ENGINE_SCOPE)?.clear()
    context =
      SimpleScriptContext()
        .apply {
          setBindings(createBindings(), ENGINE_SCOPE)
          setBindings(if (nullGlobalContext) null else createBindings(), GLOBAL_SCOPE)
        }
  }
}
