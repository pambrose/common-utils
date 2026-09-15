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
 * Abstract base class for a pool of [AbstractScript] instances.
 *
 * When a script is returned to the pool, [AbstractScript.resetForReuse] prepares it for the next borrower. Subclasses
 * populate the pool with [populate] in their `init` block. [AbstractEnginePool] describes borrowing and closing.
 *
 * @param T the concrete type of [AbstractScript] managed by this pool
 * @param size the number of script instances in the pool; must be positive
 * @param nullGlobalContext if `true`, resets the global scope bindings to `null` when recycling
 * @throws IllegalArgumentException if [size] is not positive
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractScriptPool<T : AbstractScript>(
  size: Int,
  private val nullGlobalContext: Boolean,
) : AbstractEnginePool<T>(size) {
  override fun reset(instance: T) = instance.resetForReuse(nullGlobalContext)

  /**
   * Suspends until a script instance can be borrowed, runs [block] on it, and returns the instance to the pool
   * afterwards, even if [block] throws.
   *
   * @param block the work to do with the borrowed instance
   * @return the result of [block]
   * @throws kotlinx.coroutines.channels.ClosedReceiveChannelException if the pool has been closed
   */
  suspend fun <R> eval(block: T.() -> R): R = withInstance(block)
}
