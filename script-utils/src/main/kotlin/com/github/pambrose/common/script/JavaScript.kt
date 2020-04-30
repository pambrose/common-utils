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

import javax.script.ScriptEngineManager
import javax.script.ScriptException

// See: https://github.com/eobermuhlner/java-scriptengine

class JavaScript : AbstractScript("java") {

  @Synchronized
  fun eval(code: String): Any? {

    if ("sys.exit(" in code)
      throw ScriptException("Illegal call to sys.exit()")

    if ("exit(" in code)
      throw ScriptException("Illegal call to exit()")

    if ("quit(" in code)
      throw ScriptException("Illegal call to quit()")

    if (!initialized.get()) {
      valueMap.forEach { (name, value) -> bindings.put(name, value) }
      initialized.set(true)
    }

    return engine.eval(code, bindings)
  }

}

fun main() {
  val manager = ScriptEngineManager()
  println(manager.engineFactories)
  val engine = manager.getEngineByExtension("java")
  println(engine.factory.extensions)
  println("done")

}