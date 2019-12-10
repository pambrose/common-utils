package com.github.pambrose.common.script

import com.github.pambrose.common.util.doubleQuoted
import com.github.pambrose.common.util.pluralize
import com.github.pambrose.common.util.typeParameterCount
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import java.util.concurrent.atomic.AtomicBoolean
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.SimpleBindings
import kotlin.reflect.KType

class KtsScript {
  private val manager = ScriptEngineManager()
  private val engine = manager.getEngineByExtension("kts")
  private val valueMap = mutableMapOf<String, Any>()
  private val typeMap = mutableMapOf<String, Array<out KType>>()
  private val imports = mutableListOf("import ${System::class.qualifiedName}")
  private val bindings = SimpleBindings(valueMap)
  private var initialized = AtomicBoolean(false)

  init {
    setIdeaIoUseFallback()
  }

  fun add(name: String, value: Any, vararg types: KType) {
    val paramCnt = value.typeParameterCount
    return when {
      value.javaClass.kotlin.qualifiedName == null ->
        throw ScriptException("Variable ${name.doubleQuoted()} is a local or an anonymous class")
      paramCnt > 0 && types.size == 0 -> {
        val plural = "parameter".pluralize(paramCnt)
        throw ScriptException("Expected $paramCnt type $plural to be specified for ${name.doubleQuoted()}")
      }
      paramCnt == 0 && types.size > 0 -> {
        val plural = "parameter".pluralize(types.size)
        val found = params(name, types)
        throw ScriptException("Invalid type $plural $found specified for ${name.doubleQuoted()}")
      }
      paramCnt != types.size -> {
        val plural = "parameter".pluralize(paramCnt)
        val found = "${types.size}: ${params(name, types)}"
        throw ScriptException("Expected $paramCnt type $plural for ${name.doubleQuoted()} but found $found")
      }
      else -> {
        valueMap[name] = value
        typeMap[name] = types
      }
    }
  }

  private fun params(name: String, types: Array<out KType> = typeMap[name]!!): String {
    val params = types.map { type -> type.toString().removePrefix("kotlin.") }
    return if (params.isNotEmpty()) "<${params.joinToString(", ")}>" else ""
  }

  val varDecls: String
    get() {
      val assigns = mutableListOf<String>()

      valueMap.forEach { entry ->
        val name = entry.key
        val kotlinClazz = entry.value.javaClass.kotlin
        val kotlinQualified = kotlinClazz.qualifiedName!!
        val type = kotlinQualified.removePrefix("kotlin.")
        assigns += "val $name = bindings[${name.doubleQuoted()}] as $type${params(name)}"
      }

      return assigns.joinToString("\n")
    }

  val importDecls: String
    get() = imports.joinToString("\n")

  @Synchronized
  fun eval(code: String): Any? {

    if ("java.lang.System.exit" in code)
      throw ScriptException(SYSTEM_ERROR)

    if (!initialized.get()) {
      engine.eval(varDecls, bindings)
      initialized.set(true)
    }

    return engine.eval(importDecls + "\n\n" + code, bindings)
  }

  companion object {
    internal const val SYSTEM_ERROR = "Illegal call to System.exit()"
  }
}