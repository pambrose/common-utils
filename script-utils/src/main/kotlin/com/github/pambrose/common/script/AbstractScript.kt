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

import com.github.pambrose.common.util.pluralize
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.pambrose.common.util.typeParameterCount
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import java.util.concurrent.atomic.AtomicBoolean
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.SimpleBindings
import kotlin.reflect.KType

abstract class AbstractScript(extension: String) {
  private val manager = ScriptEngineManager()
  protected val engine = manager.getEngineByExtension(extension)
  private val typeMap = mutableMapOf<String, Array<out KType>>()
  protected val valueMap = mutableMapOf<String, Any>()
  protected val bindings = SimpleBindings(valueMap)
  protected var initialized = AtomicBoolean(false)

  init {
    setIdeaIoUseFallback()
  }

  protected fun params(name: String, types: Array<out KType> = typeMap[name]!!): String {
    val params = types.map { type -> type.toString().removePrefix("kotlin.") }
    return if (params.isNotEmpty()) "<${params.joinToString(", ")}>" else ""
  }

  fun add(name: String, value: Any, vararg types: KType) {
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

}