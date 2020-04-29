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

import javax.script.ScriptException

class PyScript : AbstractScript("py") {
  //private val imports = mutableListOf("import ${System::class.qualifiedName}")

  fun assignBindings() {
    //val assigns = mutableListOf<String>()

    valueMap.forEach { (name, value) ->
      val kotlinClazz = value.javaClass.kotlin
      val kotlinQualified = kotlinClazz.qualifiedName!!
      val type = kotlinQualified.removePrefix("kotlin.")
      //assigns += "$name = bindings[${name.toDoubleQuoted()}] " // as $type${params(name)}

      bindings.put(name, value)
    }

    //return assigns.joinToString("\n")
  }

  /*
  val importDecls: String
    get() = imports.joinToString("\n")
*/
  @Synchronized
  fun eval(code: String): Any? {

    if ("sys.exit(" in code)
      throw ScriptException(SYSTEM_ERROR)

    if (!initialized.get()) {
      assignBindings()
      initialized.set(true)
    }

    return engine.eval(code, bindings)
  }

  companion object {
    internal const val SYSTEM_ERROR = "Illegal call to sys.exit()"
  }
}