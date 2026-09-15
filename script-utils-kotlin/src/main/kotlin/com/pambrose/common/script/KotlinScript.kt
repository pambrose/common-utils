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
 * Manages variable bindings via the JSR 223 `Bindings` mechanism and generates Kotlin `val` declarations that cast
 * bound values to types the script can name. A value whose runtime class cannot be named, such as the private list
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
  override fun isReserved(name: String) = name in KOTLIN_KEYWORDS

  /**
   * Generates Kotlin `val` declarations that retrieve every bound variable from the engine's bindings
   * and cast it to a type the script can name, with its type arguments.
   */
  val varDecls: String
    get() = declarations(valueMap.keys)

  private fun declarations(names: Collection<String>) =
    names.joinToString("\n") { name ->
      "val $name = bindings[${name.toTempName().toDoubleQuoted()}] as ${castType(name)}"
    }

  // The accessible class with the registered type arguments, or with star projections when none were registered.
  private fun castType(name: String): String {
    val clazz = accessibleClass(name, valueMap.getValue(name))
    val typeParameterCount = clazz.java.typeParameters.size
    val typeArguments =
      params(name).ifEmpty {
        if (typeParameterCount > 0) List(typeParameterCount) { "*" }.joinToString(", ", "<", ">") else ""
      }
    return "${clazz.qualifiedName}$typeArguments"
  }

  private fun String.toTempName() = "${this}_tmp"

  // Binds each value under a temporary name, then declares a typed val with the variable's own name.
  override fun bindVariables(variables: Map<String, Any>) {
    variables.forEach { (name, value) -> scriptEngine.put(name.toTempName(), value) }
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
    // Kotlin's hard keywords, which cannot be used as identifiers.
    val KOTLIN_KEYWORDS =
      (
        "as break class continue do else false for fun if in interface is null object package return super this " +
          "throw true try typealias typeof val var when while"
      ).split(" ").toSet()
  }
}
