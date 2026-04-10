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

import kotlinx.coroutines.runBlocking

/**
 * A pre-populated pool of [KotlinScript] instances backed by a coroutine
 * [Channel][kotlinx.coroutines.channels.Channel].
 *
 * Instances are created eagerly during initialization and recycled with context resets
 * after each use.
 *
 * @param size the number of [KotlinScript] instances to create in the pool
 * @param nullGlobalContext if `true`, resets the global scope bindings to `null` when recycling
 */
class KotlinScriptPool(
  size: Int,
  nullGlobalContext: Boolean,
) : AbstractScriptPool<KotlinScript>(size, nullGlobalContext) {
  init {
    runBlocking {
      repeat(size) { channel.send(KotlinScript()) }
    }
  }
}
