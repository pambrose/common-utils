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

import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A [ConditionalValue] specialized for boolean values, providing [waitUntilTrue] and [waitUntilFalse]
 * convenience methods.
 *
 * @param initValue the initial boolean value.
 */
class ConditionalBoolean(
  initValue: Boolean,
) : ConditionalValue<Boolean>(initValue) {
  /**
   * Suspends until the value becomes `true` or the timeout expires.
   *
   * @param timeoutDuration the maximum duration to wait. Defaults to [Duration.INFINITE].
   * @return `true` if the value became `true` before the timeout, `false` if the timeout expired.
   */
  suspend fun waitUntilTrue(timeoutDuration: Duration = Duration.INFINITE): Boolean = waitUntil(timeoutDuration) { it }

  /**
   * Suspends until the value becomes `false` or the timeout expires.
   *
   * @param timeoutDuration the maximum duration to wait. Defaults to [Duration.INFINITE].
   * @return `true` if the value became `false` before the timeout, `false` if the timeout expired.
   */
  suspend fun waitUntilFalse(timeoutDuration: Duration = Duration.INFINITE): Boolean =
    waitUntil(timeoutDuration) {
      !it
    }
}

/**
 * A coroutine-based conditional waiter backed by [MutableStateFlow].
 *
 * Holds a value of type [T] and allows coroutines to suspend until the value satisfies
 * an arbitrary predicate, with optional timeout support.
 *
 * Every [set] notifies waiters, even when the new value equals the current one or is the same object changed in
 * place, so each waiter re-checks its predicate. Waiters observe only the latest value: a value replaced before a
 * waiter sees it can be missed, so wait for conditions that stay true once reached.
 *
 * @param T the type of the monitored value.
 * @param initValue the initial value.
 */
open class ConditionalValue<T>(
  initValue: T,
) {
  // Each value is boxed so that every set() emits. MutableStateFlow skips a value equal to the current one, which
  // would strand a waiter after an in-place change followed by set(sameObject).
  private class Box<T>(
    val value: T,
  )

  private val flowValue = MutableStateFlow(Box(initValue))

  /**
   * Returns the current value.
   *
   * @return the current value of type [T].
   */
  fun get(): T = flowValue.value.value

  /**
   * Suspends until [predicate] holds for the current value or [timeoutDuration] elapses.
   *
   * The current value is checked first, so a condition that already holds returns `true` even for a zero timeout.
   *
   * @param timeoutDuration the maximum duration to wait. Defaults to [Duration.INFINITE].
   * @param predicate the condition to wait for.
   * @return `true` if the predicate was satisfied, `false` if the timeout expired.
   */
  suspend fun waitUntil(
    timeoutDuration: Duration = Duration.INFINITE,
    predicate: (T) -> Boolean,
  ): Boolean =
    predicate(get()) ||
      (
        withTimeoutOrNull(timeoutDuration) {
          flowValue.first { predicate(it.value) }
          true
        } ?: false
      )

  /**
   * Sets the value and notifies waiting coroutines. It does not suspend, so any thread can call it.
   *
   * @param value the new value.
   */
  fun set(value: T) {
    flowValue.value = Box(value)
  }
}
