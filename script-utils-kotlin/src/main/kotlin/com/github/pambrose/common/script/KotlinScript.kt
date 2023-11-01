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

import com.github.pambrose.common.util.toDoubleQuoted
import java.io.Closeable
import javax.script.ScriptException

// See: https://github.com/Kotlin/kotlin-script-examples/blob/master/jvm/jsr223/jsr223-simple/build.gradle.kts
// See: https://kotlinexpertise.com/run-kotlin-scripts-from-kotlin-programs/
// Use of bindings explained here: https://discuss.kotlinlang.org/t/jsr223-bindings/9556
// https://github.com/JetBrains/kotlin/tree/master/libraries/examples/scripting

class KotlinScript(
  nullGlobalContext: Boolean = false,
) : AbstractScript("kts", nullGlobalContext), Closeable {
  private val imports = mutableListOf(System::class.qualifiedName)

  val varDecls: String
    get() {
      val assigns = mutableListOf<String>()

      valueMap
        .forEach { (name, value) ->
          val kotlinClazz = value.javaClass.kotlin
          val kotlinQualified = kotlinClazz.qualifiedName!!
          val type = kotlinQualified.removePrefix("kotlin.")
          val p = params(name)
          assigns += "val $name = bindings[${name.toTempName().toDoubleQuoted()}] as $type$p"
        }

      return assigns.joinToString("\n")
    }

  internal fun String.toTempName() = "${this}_tmp"

  val importDecls: String
    get() = imports.joinToString("\n") { "import $it" }

  @Synchronized
  fun eval(code: String): Any? {
    if ("java.lang.System.exit" in code)
      throw ScriptException("Illegal call to System.exit()")

    if (!initialized) {
      if (valueMap.isNotEmpty()) {
        valueMap.forEach { (name, value) -> engine.put(name.toTempName(), value) }
        engine.eval(varDecls)
      }
      initialized = true
    }

    val script = "$importDecls\n\n$code"
    return engine.eval(script)
  }

  override fun close() {
    // Placeholder
  }
}
