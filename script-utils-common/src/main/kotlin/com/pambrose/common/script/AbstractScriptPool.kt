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

import kotlinx.coroutines.channels.Channel

/**
 * Abstract base class for a pool of [AbstractScript] instances.
 *
 * Uses a coroutine [Channel] as a bounded buffer to manage script engine instances.
 * When a script is returned to the pool, its context is automatically reset. Subclasses
 * are responsible for populating the pool in their `init` block.
 *
 * @param T the concrete type of [AbstractScript] managed by this pool
 * @param size the number of script instances in the pool
 * @param nullGlobalContext if `true`, resets the global scope bindings to `null` when recycling
 */
abstract class AbstractScriptPool<T : AbstractScript>(
  val size: Int,
  private val nullGlobalContext: Boolean,
) {
  /** Channel used as a bounded buffer for pooling script instances. */
  protected val channel = Channel<T>(size)

  private suspend fun borrow() = channel.receive()

  /** Returns `true` if there are no script instances currently available in the pool. */
  val isEmpty get() = channel.isEmpty

  // Reset the context before returning to pool
  private suspend fun recycle(scriptObject: T) = channel.send(scriptObject.apply { resetContext(nullGlobalContext) })

  suspend fun <R> eval(block: T.() -> R): R =
    borrow().let { engine ->
      try {
        block.invoke(engine)
      } finally {
        recycle(engine)
      }
    }
}
