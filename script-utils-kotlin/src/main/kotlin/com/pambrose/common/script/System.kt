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

import javax.script.ScriptException

/**
 * A replacement for [java.lang.System] that prevents scripts from terminating the JVM.
 *
 * When imported into a Kotlin script context, calls to `System.exit()` will throw a
 * [ScriptException] instead of actually exiting.
 */
class System {
  companion object {
    /**
     * Throws a [ScriptException] to prevent scripts from calling `System.exit()`.
     *
     * @param status the exit status code that was attempted
     * @throws ScriptException always thrown to block the exit call
     */
    fun exit(status: Int): Unit = throw ScriptException("Illegal call to System.exit() - $status")
  }
}
