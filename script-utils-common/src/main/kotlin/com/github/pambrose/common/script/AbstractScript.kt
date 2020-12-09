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

import com.github.pambrose.common.script.ScriptUtils.resetContext
import com.github.pambrose.common.util.pluralize
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.pambrose.common.util.typeParameterCount
import java.util.concurrent.atomic.AtomicBoolean
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import kotlin.reflect.KType

// https://docs.oracle.com/en/java/javase/14/scripting/java-scripting-api.html#GUID-C4A6EB7C-0AEA-45EC-8662-099BDEFC361A

abstract class AbstractScript(protected val engine: ScriptEngine) {
  constructor(extension: String) : this(scriptManager.getEngineByExtension(extension)
                                        ?: throw ScriptException("Unrecognized script extension: $extension"))

  private val _initialized = AtomicBoolean(false)
  private val typeMap = mutableMapOf<String, Array<out KType>>()

  protected val valueMap = mutableMapOf<String, Any>()

  protected var initialized
    get() = _initialized.get()
    set(value) = _initialized.set(value)

  init {
    resetContext()
  }

  fun resetContext(nullGlobalContext: Boolean = false) {
    initialized = false
    engine.resetContext(nullGlobalContext)
  }

  open fun params(name: String, types: Array<out KType> = typeMap[name]!!): String {
    val params = types.map { type -> type.toString().removePrefix("kotlin.") }
    return if (params.isNotEmpty()) "<${params.joinToString(", ")}>" else ""
  }

  open fun add(name: String, value: Any, vararg types: KType) {
    val paramCnt = value.typeParameterCount
    val qname = name.toDoubleQuoted()

    return when {
      value.javaClass.kotlin.qualifiedName == null ->
        throw ScriptException("Variable $qname is a local or an anonymous class")

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

  companion object {
    val scriptManager by lazy { ScriptEngineManager() }
  }
}
