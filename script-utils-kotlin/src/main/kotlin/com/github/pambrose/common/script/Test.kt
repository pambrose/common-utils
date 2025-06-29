/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

fun main() {
  repeat(100) { i ->
    println("Invocation: $i")
    try {
      val c = 1 * 4
      KotlinExprEvaluator().eval("1 == wrong")
    } catch (e: ScriptException) {
      println("Caught it")
    }
    println(KotlinExprEvaluator().eval("1 == 1"))
  }
}
