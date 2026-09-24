/*
 *   Copyright © 2026 Paul Ambrose (pambrose@mac.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.pambrose.common.script

/**
 * Holds the variables a [KotlinScript] binds, under a single engine binding.
 *
 * The Kotlin JSR-223 engine turns every binding into a script property typed by the value's runtime class. A lambda
 * or a JDK-internal class then makes every later evaluation fail to compile, and on Kotlin 2.4.20 so does any generic
 * Java class such as `ArrayList`. Binding only this public, non-generic holder avoids all of that: generated code
 * reads each variable from it and casts it to a type the script can name.
 *
 * It is public only because generated script code names it; there is no reason to use it directly.
 */
class ScriptVariables internal constructor() {
  private val values = mutableMapOf<String, Any>()

  /**
   * Returns the value bound under [name], or `null` if there is none.
   *
   * @param name the variable name
   */
  operator fun get(name: String): Any? = values[name]

  internal operator fun set(
    name: String,
    value: Any,
  ) {
    values[name] = value
  }
}
