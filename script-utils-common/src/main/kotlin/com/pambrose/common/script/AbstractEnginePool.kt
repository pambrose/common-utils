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
 * Abstract base class for a fixed-size pool of script engines, shared by [AbstractScriptPool] and
 * [AbstractExprEvaluatorPool].
 *
 * Uses a coroutine [Channel] as a bounded buffer of instances. Subclasses create the instances with [populate] in their
 * `init` block, borrow one with [withInstance], and prepare a returned instance for its next borrower in [reset]. An
 * instance handed to a borrower that is cancelled before it resumes goes back into the pool instead of being lost.
 * Closing the pool closes its instances.
 *
 * @param T the type of engine in the pool
 * @param size the number of instances in the pool; must be positive
 * @throws IllegalArgumentException if [size] is not positive
 */
abstract class AbstractEnginePool<T : AbstractEngine>(
  val size: Int,
) : Closeable {
  init {
    require(size > 0) { "Pool size must be positive, but was $size" }
  }

  // Bounded buffer holding the idle instances. Private, so every instance goes out through borrow and back through
  // the reset in returnToPool.
  private val channel: Channel<T> = Channel(size) { returnToPool(it) }

  /**
   * Returns an approximate, point-in-time indication of whether the pool currently has no instances
   * available. Delegates to [Channel.isEmpty], which is racy under concurrent borrow/recycle and may
   * return a stale result, so do not rely on it for correctness.
   */
  val isEmpty get() = channel.isEmpty

  /**
   * Prepares [instance] for its next borrower. Called each time an instance is returned to the pool.
   *
   * @param instance the instance being returned
   */
  protected abstract fun reset(instance: T)

  /**
   * Creates the pool's [size] instances with [factory] and adds them to the pool. If creating one fails, the instances
   * already created are closed, with any failure to close one attached to the original exception, before it
   * propagates.
   *
   * @param factory creates one instance
   */
  protected fun populate(factory: () -> T) {
    val created: MutableList<T> = []
    runCatching { repeat(size) { created += factory() } }.exceptionOrNull()?.let { e ->
      created.forEach { instance -> runCatching { instance.close() }.exceptionOrNull()?.let(e::addSuppressed) }
      throw e
    }
    created.forEach { channel.trySend(it).getOrThrow() }
  }

  /**
   * Suspends until an instance can be borrowed, runs [block] with it, then resets it with [reset] and returns it to the
   * pool, even if [block] or [reset] throws. An exception from [reset] propagates to the caller, unless [block] threw
   * too: then the block's exception propagates, with the reset failure attached as suppressed.
   *
   * @param block the work to do with the borrowed instance
   * @return the result of [block]
   * @throws kotlinx.coroutines.channels.ClosedReceiveChannelException if the pool has been closed
   */
  protected suspend fun <R> withInstance(block: (T) -> R): R {
    val instance = channel.receive()
    // use() keeps the block's exception, with a reset failure attached as suppressed. The finally returns the instance
    // even when reset throws, so a failing reset cannot keep it out of the pool: a size-1 pool would then wait forever.
    return AutoCloseable {
      try {
        reset(instance)
      } finally {
        returnToPool(instance)
      }
    }.use { block(instance) }
  }

  // Puts instance back into the channel, or closes it once the pool has been closed.
  private fun returnToPool(instance: T) {
    if (channel.trySend(instance).isFailure)
      instance.close()
  }

  /**
   * Closes the pool and the instances it holds. An instance still borrowed is closed when it is returned, and later
   * borrows throw [kotlinx.coroutines.channels.ClosedReceiveChannelException].
   */
  override fun close() {
    channel.close()
    generateSequence { channel.tryReceive().getOrNull() }.forEach { it.close() }
  }
}
