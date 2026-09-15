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

import java.io.Closeable
import kotlinx.coroutines.channels.Channel

/**
 * Abstract base class for a pool of [AbstractScript] instances.
 *
 * Uses a coroutine [Channel] as a bounded buffer to manage script engine instances. When a script is returned to the
 * pool, [AbstractScript.resetForReuse] prepares it for the next borrower. Subclasses populate the pool with [populate]
 * in their `init` block. Closing the pool closes its instances.
 *
 * @param T the concrete type of [AbstractScript] managed by this pool
 * @param size the number of script instances in the pool; must be positive
 * @param nullGlobalContext if `true`, resets the global scope bindings to `null` when recycling
 * @throws IllegalArgumentException if [size] is not positive
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractScriptPool<T : AbstractScript>(
  val size: Int,
  private val nullGlobalContext: Boolean,
) : Closeable {
  init {
    require(size > 0) { "Pool size must be positive, but was $size" }
  }

  /**
   * Channel used as a bounded buffer for pooling script instances. An instance handed to a borrower that is cancelled
   * before it resumes goes back into the channel instead of being lost.
   */
  protected val channel: Channel<T> = Channel(size) { returnToPool(it) }

  private suspend fun borrow() = channel.receive()

  /**
   * Returns an approximate, point-in-time indication of whether the pool currently has no script
   * instances available. Delegates to [Channel.isEmpty], which is racy under concurrent borrow/recycle
   * and may return a stale result, so do not rely on it for correctness.
   */
  val isEmpty get() = channel.isEmpty

  private fun returnToPool(script: T): Unit = channel.returnOrClose(script)

  private fun recycle(script: T) {
    script.resetForReuse(nullGlobalContext)
    returnToPool(script)
  }

  /**
   * Creates the pool's [size] instances with [factory] and adds them to the pool. If creating one fails, the
   * instances already created are closed before the exception propagates.
   *
   * @param factory creates one script instance
   */
  protected fun populate(factory: () -> T) = channel.populate(size, factory)

  /**
   * Suspends until a script instance can be borrowed, runs [block] on it, and returns the instance to the pool
   * afterwards, even if [block] throws.
   *
   * @param block the work to do with the borrowed instance
   * @return the result of [block]
   * @throws kotlinx.coroutines.channels.ClosedReceiveChannelException if the pool has been closed
   */
  suspend fun <R> eval(block: T.() -> R): R =
    borrow().let { script ->
      try {
        block.invoke(script)
      } finally {
        recycle(script)
      }
    }

  /**
   * Closes the pool and the instances it holds. An instance still borrowed is closed when it is returned, and later
   * borrows throw [kotlinx.coroutines.channels.ClosedReceiveChannelException].
   */
  override fun close() = channel.closeAndDrain()
}
