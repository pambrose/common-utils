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

import com.github.pambrose.common.util.toDoubleQuoted
import javax.script.ScriptException

// See: https://kotlinexpertise.com/run-kotlin-scripts-from-kotlin-programs/
// Use of bindings explained here: https://discuss.kotlinlang.org/t/jsr223-bindings/9556

class KtsScript : AbstractScript("kts") {
  private val imports = mutableListOf("import ${System::class.qualifiedName}")

  val varDecls: String
    get() {
      val assigns = mutableListOf<String>()

      valueMap.forEach { (name, value) ->
        val kotlinClazz = value.javaClass.kotlin
        val kotlinQualified = kotlinClazz.qualifiedName!!
        val type = kotlinQualified.removePrefix("kotlin.")
        assigns += "val $name = bindings[${name.toDoubleQuoted()}] as $type${params(name)}"
      }

      return assigns.joinToString("\n")
    }

  val importDecls: String
    get() = imports.joinToString("\n")

  @Synchronized
  fun eval(code: String): Any? {

    if ("java.lang.System.exit" in code)
      throw ScriptException("Illegal call to System.exit()")

    if (!initialized.get()) {
      engine.eval(varDecls, bindings)
      initialized.set(true)
    }

    return engine.eval("$importDecls\n\n$code", bindings)
  }

}