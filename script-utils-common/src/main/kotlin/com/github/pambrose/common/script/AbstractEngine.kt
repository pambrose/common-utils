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

import javax.script.ScriptEngineManager
import javax.script.ScriptException

abstract class AbstractEngine(
  extension: String,
) {
  val engine =
    scriptManager.getEngineByExtension(extension)
      ?: throw ScriptException("Unrecognized script extension: $extension")

  companion object {
    private val scriptManager by lazy { ScriptEngineManager() }
  }
}
