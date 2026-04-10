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

import com.pambrose.common.script.ScriptUtils.resetContext
import com.pambrose.common.util.isNull
import com.pambrose.common.util.pluralize
import com.pambrose.common.util.toDoubleQuoted
import com.pambrose.common.util.typeParameterCount
import javax.script.ScriptException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.reflect.KType

// https://docs.oracle.com/en/java/javase/14/scripting/java-scripting-api.html#GUID-C4A6EB7C-0AEA-45EC-8662-099BDEFC361A

/**
 * Abstract base class for script execution that supports named variable bindings with type parameters.
 *
 * Manages a map of named values and their associated type parameters, generates parameter
 * declarations for the target language, and handles context resets between evaluations.
 *
 * @param extension the file extension used to look up the script engine (e.g., `"kts"`, `"py"`, `"java"`)
 * @param nullGlobalContext if `true`, sets the global scope bindings to `null` on initialization
 */
abstract class AbstractScript(
  extension: String,
  nullGlobalContext: Boolean,
) : AbstractEngine(extension) {
  private val _initialized = AtomicBoolean(false)
  private val typeMap = mutableMapOf<String, Array<out KType>>()

  protected val valueMap = mutableMapOf<String, Any>()

  protected var initialized
    get() = _initialized.load()
    set(value) = _initialized.store(value)

  init {
    resetContext(nullGlobalContext)
  }

  /**
   * Resets the script engine context, clearing all variable and type mappings.
   *
   * @param nullGlobalContext if `true`, sets the global scope bindings to `null`
   */
  fun resetContext(nullGlobalContext: Boolean) {
    initialized = false
    valueMap.clear()
    typeMap.clear()
    engine.resetContext(nullGlobalContext)
  }

  /**
   * Generates the type parameter string (e.g., `<Int, String>`) for the variable with the given [name].
   *
   * @param name the variable name
   * @param types the type parameters; defaults to those previously registered for [name]
   * @return a formatted type parameter string, or an empty string if there are no parameters
   */
  open fun params(
    name: String,
    types: Array<out KType> = typeMap[name]!!,
  ): String {
    val params = types.map { type -> type.toString().removePrefix("kotlin.") }
    return if (params.isNotEmpty()) "<${params.joinToString(", ")}>" else ""
  }

  /**
   * Adds a named variable with an associated value and optional type parameters to the script context.
   *
   * Validates that the number of type parameters matches the value's generic type parameter count.
   *
   * @param name the variable name to bind in the script
   * @param value the value to associate with the variable
   * @param types the type parameters for generic types (e.g., for `List<String>`, pass `typeOf<String>()`)
   * @throws ScriptException if the value is a local/anonymous class, or if the type parameter count is invalid
   */
  open fun add(
    name: String,
    value: Any,
    vararg types: KType,
  ) {
    val paramCnt = value.typeParameterCount
    val qname = name.toDoubleQuoted()

    return when {
      value.javaClass.kotlin.qualifiedName.isNull() -> {
        throw ScriptException("Variable $qname is a local or an anonymous class")
      }

      paramCnt > 0 && types.isEmpty() -> {
        val plural = "parameter".pluralize(paramCnt)
        throw ScriptException("Expected $paramCnt type $plural to be specified for $qname")
      }

      paramCnt == 0 && types.isNotEmpty() -> {
        val plural = "parameter".pluralize(types.size)
        val found = params(name, types)
        throw ScriptException("Invalid type $plural $found specified for $qname")
      }

      paramCnt != types.size -> {
        val plural = "parameter".pluralize(paramCnt)
        val found = "${types.size}: ${params(name, types)}"
        throw ScriptException("Expected $paramCnt type $plural for $qname but found $found")
      }

      else -> {
        valueMap[name] = value
        typeMap[name] = types
      }
    }
  }
}
