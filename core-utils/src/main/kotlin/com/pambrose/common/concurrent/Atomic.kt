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

package com.pambrose.common.concurrent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A coroutine-safe wrapper around a mutable value, protected by a [Mutex].
 *
 * Provides suspending functions for reading and writing the value with mutual exclusion,
 * as well as a non-locking [value] property for quick reads (volatile visibility only).
 *
 * @param T the type of the wrapped value
 * @param initValue the initial value
 * @see <a href="https://medium.com/swlh/kotlin-for-lunch-atomic-t-261351048fad">Kotlin for Lunch: Atomic&lt;T&gt;</a>
 */
class Atomic<T>(
  initValue: T,
) {
  @PublishedApi
  internal val mutex = Mutex()

  @PublishedApi
  @Volatile
  internal var _value: T = initValue

  /** The current value. Reads are volatile but not mutex-protected. */
  val value: T
    get() = _value

  /**
   * Atomically updates the value by applying [action] to the current value while holding the [Mutex].
   *
   * @param owner optional owner for the mutex lock
   * @param action a function that receives the current value and returns the new value
   * @return the new value
   */
  suspend inline fun setWithLock(
    owner: Any? = null,
    action: (T) -> T,
  ): T = mutex.withLock(owner) { action(_value).also { _value = it } }

  /**
   * Executes [action] on the current value while holding the [Mutex] and returns the result.
   *
   * @param V the return type
   * @param owner optional owner for the mutex lock
   * @param action the action to execute with the current value as receiver
   * @return the result of [action]
   */
  suspend inline fun <V> withLock(
    owner: Any? = null,
    action: T.() -> V,
  ): V = mutex.withLock(owner) { _value.action() }
}
