package com.github.pambrose.common.script

import com.github.pambrose.common.util.doubleQuoted
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.SimpleBindings
import kotlin.reflect.KClass

class KtsScript {
  private val manager = ScriptEngineManager()
  private val engine = manager.getEngineByExtension("kts")
  private val valueMap = mutableMapOf<String, Any>()
  private val typeMap = mutableMapOf<String, KClass<*>?>()
  private val imports = mutableListOf("import ${System::class.qualifiedName}")
  private val bindings = SimpleBindings(valueMap)
  private var initialized = false

  fun add(name: String, value: Any, clazz: KClass<*>? = null) {
    if (Collection::class.isInstance(value) && clazz == null)
      throw ScriptException("Collection type missing in ${KtsScript::class.simpleName}.add() for variable ${name.doubleQuoted()}")

    if (value.javaClass.kotlin.qualifiedName == null)
      throw ScriptException("Variable ${name.doubleQuoted()} is a local or an anonymous class")

    valueMap[name] = value
    typeMap[name] = clazz
  }

  val varDecls: String
    get() {
      val assigns = mutableListOf<String>()

      valueMap.forEach { entry ->
        val name = entry.key
        val kotlinClazz = entry.value.javaClass.kotlin
        val kotlinQualified = kotlinClazz.qualifiedName!!

        val asType =
          when {
            kotlinQualified.startsWith("kotlin.") -> kotlinClazz.simpleName
            Collection::class.isInstance(entry.value) -> "$kotlinQualified<${typeMap[name]?.simpleName}>"
            else -> kotlinQualified
          }

        assigns += "val $name = bindings[${name.doubleQuoted()}] as $asType"
      }

      return assigns.joinToString("\n")
    }

  val importDecls: String
    get() = imports.joinToString("\n")

  @Synchronized
  fun eval(code: String): Any? {
    if (!initialized) {
      engine.eval(varDecls, bindings)
      initialized = true
    }

    return engine.eval(importDecls + "\n\n" + code, bindings)
  }
}
