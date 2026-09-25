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

import com.pambrose.common.util.toDoubleQuoted

// See: https://github.com/Kotlin/kotlin-script-examples/blob/master/jvm/jsr223/jsr223-simple/build.gradle.kts
// See: https://kotlinexpertise.com/run-kotlin-scripts-from-kotlin-programs/
// Use of bindings explained here: https://discuss.kotlinlang.org/t/jsr223-bindings/9556
// https://github.com/JetBrains/kotlin/tree/master/libraries/examples/scripting

/**
 * A script engine wrapper for dynamically evaluating Kotlin source code using the `kts` extension.
 *
 * Stores the variables in a single [ScriptVariables] holder, the only JSR 223 binding, and generates Kotlin `val`
 * declarations that read each one back and cast it to a type the script can name. `bindings` and `__variables` are
 * reserved names. A value whose runtime class cannot be named, such as the private list
 * class behind `listOf(1, 2)`, is cast to its nearest public class or interface.
 *
 * Common literal JVM-termination calls (`System.exit`, `exitProcess`, `Runtime.getRuntime().exit/halt`)
 * are rejected on a best-effort basis via [ScriptGuards]. This is a convenience against accidental termination,
 * **not** a security sandbox (see [ScriptGuards]) — run untrusted scripts in an isolated process or JVM.
 *
 * [close] does nothing, because the Kotlin engine holds no resources to release.
 *
 * @param nullGlobalContext if `true`, sets the global scope bindings to `null` on initialization
 * @see AbstractScript
 */
class KotlinScript(
  nullGlobalContext: Boolean = false,
) : AbstractScript("kts", nullGlobalContext) {
  // bindings is the script's own view of the engine bindings, and the holder's name is taken by the holder, so a
  // variable named either would shadow what the generated declarations read.
  override fun isReserved(name: String) = name in KOTLIN_KEYWORDS || name == "bindings" || name == VARIABLES_BINDING

  /**
   * Generates Kotlin `val` declarations that retrieve every bound variable from the [ScriptVariables] holder
   * and cast it to a type the script can name, with its type arguments.
   */
  val varDecls: String
    get() = declarations(valueMap.keys)

  private fun declarations(names: Collection<String>) =
    names.joinToString("\n") { name ->
      "val $name = $HOLDER[${name.toDoubleQuoted()}] as ${castType(name)}"
    }

  // The accessible class with the registered type arguments, or with star projections when none were registered.
  // Any, the fallback when no nameable class takes the registered type arguments, is used without them.
  private fun castType(name: String): String {
    val clazz = accessibleClass(name, valueMap.getValue(name))
    val typeParameterCount = clazz.java.typeParameters.size
    val typeArguments =
      if (clazz == Any::class) {
        ""
      } else {
        params(name).ifEmpty {
          if (typeParameterCount > 0) List(typeParameterCount) { "*" }.joinToString(", ", "<", ">") else ""
        }
      }
    return "${clazz.qualifiedName}$typeArguments"
  }

  // Stores each value in the holder, which is the only engine binding, then declares a typed val with the variable's
  // own name. A reset clears the engine scope, so a missing holder is replaced by a fresh, empty one.
  override fun bindVariables(variables: Map<String, Any>) {
    val holder =
      scriptEngine.get(VARIABLES_BINDING) as? ScriptVariables
        ?: ScriptVariables().also { scriptEngine.put(VARIABLES_BINDING, it) }
    variables.forEach { (name, value) -> holder[name] = value }
    scriptEngine.eval(declarations(variables.keys))
  }

  /**
   * Evaluates Kotlin [code] and returns its result.
   *
   * Variables added with [add] are bound first, including any added after an earlier evaluation.
   *
   * @param code the Kotlin source code to evaluate
   * @return the value of the last expression, or `null`
   * @throws javax.script.ScriptException if [code] contains a literal JVM-termination call, or fails to compile or run
   */
  @Synchronized
  fun eval(code: String): Any? {
    prepare(code)
    return scriptEngine.eval(code)
  }

  private companion object {
    // The engine binding that holds every variable.
    const val VARIABLES_BINDING = "__variables"

    // Read through the script's bindings rather than the property the engine also derives from the binding: after a
    // context reset, Kotlin 2.4.20 no longer provides that property, while bindings always reflects the current context.
    val HOLDER = "(bindings[\"$VARIABLES_BINDING\"] as ${ScriptVariables::class.qualifiedName})"

    // Kotlin's hard keywords, which cannot be used as identifiers.
    val KOTLIN_KEYWORDS =
      (
        "as break class continue do else false for fun if in interface is null object package return super this " +
          "throw true try typealias typeof val var when while"
      ).split(" ").toSet()
  }
}
