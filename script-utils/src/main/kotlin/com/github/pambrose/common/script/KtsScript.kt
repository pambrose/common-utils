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
    valueMap[name] = value
    typeMap[name] = clazz
  }

  val varDecls: String
    get() {
      val assigns = mutableListOf<String>()

      valueMap.forEach { entry ->
        val name = entry.key
        val javaClazz = entry.value.javaClass
        val kotlinClazz = javaClazz.kotlin
        val kotlinQualified = kotlinClazz.qualifiedName

        if (kotlinQualified == null)
          throw ScriptException("Variable $name is a local or anonymous class")

        val asType =
          when {
            kotlinQualified.startsWith("kotlin.") -> kotlinClazz.simpleName
            Collection::class.isInstance(entry.value) -> {
              val collectionType =
                typeMap[name]
                  ?: throw ScriptException("Collection type missing in ${KtsScript::class.simpleName}.add() for variable ${name.doubleQuoted()}")
              "$kotlinQualified<${collectionType.simpleName}>"
            }
            else -> kotlinQualified
          }

        assigns += "val $name = bindings[\"$name\"] as $asType"
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