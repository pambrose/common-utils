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

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A coroutine-based waiter that suspends until a boolean value satisfies a condition.
 *
 * Provides [waitUntilTrue] and [waitUntilFalse] convenience methods with optional timeout support.
 * Any number of coroutines may wait concurrently; each is resumed independently when its own
 * condition is met.
 *
 * @param initValue the initial boolean value.
 */
class BooleanWaiter(
  initValue: Boolean,
) : GenericValueWaiter<Boolean>(initValue) {
  /**
   * Sets the boolean value and resumes any waiting coroutines whose condition is now satisfied.
   *
   * @param value the new boolean value.
   */
  fun setValue(value: Boolean) = checkCondition(value)

  /**
   * Suspends until the value is (or becomes) `true`, or the timeout expires.
   *
   * @param timeoutDuration the maximum duration to wait. Defaults to [Duration.INFINITE].
   * @return `true` if the value was `true` before the timeout, `false` if the timeout expired.
   */
  suspend fun waitUntilTrue(timeoutDuration: Duration = Duration.INFINITE): Boolean =
    waitForCondition({ currValue }, timeoutDuration)

  /**
   * Suspends until the value is (or becomes) `false`, or the timeout expires.
   *
   * @param timeoutDuration the maximum duration to wait. Defaults to [Duration.INFINITE].
   * @return `true` if the value was `false` before the timeout, `false` if the timeout expired.
   */
  suspend fun waitUntilFalse(timeoutDuration: Duration = Duration.INFINITE): Boolean =
    waitForCondition({ !currValue }, timeoutDuration)
}

/**
 * Abstract coroutine-based waiter that suspends callers until a per-call condition is satisfied.
 *
 * Each call to [waitForCondition] registers its own predicate and continuation, so any number of
 * coroutines may wait concurrently — each on a possibly-different condition — and each is resumed
 * independently. [checkCondition] updates the monitored value and resumes every registered waiter
 * whose predicate is now satisfied.
 *
 * It is `abstract` by design — a base class for typed waiters (such as [BooleanWaiter]) that expose the
 * protected [waitForCondition] through their own typed wait methods — even though it declares no abstract
 * members, since each wait supplies its own predicate.
 *
 * @param T the type of the monitored value.
 * @param initValue the initial value.
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class GenericValueWaiter<T>(
  protected val initValue: T,
) {
  private val lock = ReentrantLock()
  private val waiters = mutableListOf<Waiter>()

  protected var currValue = initValue

  private class Waiter(
    val predicate: () -> Boolean,
    val continuation: CancellableContinuation<Boolean>,
  ) {
    var timeoutJob: Job? = null
  }

  /**
   * Suspends until [predicate] holds for the current value, or [timeoutDuration] expires.
   *
   * [predicate] is evaluated immediately (under the lock) and again on each [checkCondition]; it
   * should read only the monitored value, not block or suspend.
   *
   * @param predicate the condition this caller is waiting for.
   * @param timeoutDuration the maximum duration to wait; a non-finite duration waits indefinitely.
   * @return `true` if the predicate was satisfied, `false` if the timeout expired.
   */
  protected suspend fun waitForCondition(
    predicate: () -> Boolean,
    timeoutDuration: Duration,
  ): Boolean =
    coroutineScope {
      suspendCancellableCoroutine { continuation ->
        val waiter = Waiter(predicate, continuation)

        val alreadySatisfied =
          lock.withLock {
            if (predicate()) {
              true
            } else {
              // Arm the timeout and register the waiter under the same lock. checkCondition removes the
              // waiter under this lock too, so it is guaranteed to observe a non-null timeoutJob and
              // cancel it; otherwise a satisfied waiter could stall until the full timeout elapsed,
              // because the structured coroutineScope cannot return while the orphaned delay is running.
              if (timeoutDuration.isFinite()) {
                waiter.timeoutJob =
                  launch {
                    delay(timeoutDuration)
                    if (lock.withLock { waiters.remove(waiter) })
                      continuation.resume(false)
                  }
              }
              waiters += waiter
              false
            }
          }

        if (alreadySatisfied) {
          continuation.resume(true)
        } else {
          continuation.invokeOnCancellation {
            lock.withLock { waiters.remove(waiter) }
          }
        }
      }
    }

  /**
   * Updates the monitored value and resumes every waiting coroutine whose predicate is now satisfied.
   *
   * @param value the new value.
   */
  fun checkCondition(value: T) {
    val satisfied = mutableListOf<Waiter>()
    val failed = mutableListOf<Pair<Waiter, Throwable>>()
    lock.withLock {
      currValue = value
      // Evaluate each predicate in isolation so a single misbehaving one cannot starve the others, and
      // cancel the timeout job under the lock (where it is guaranteed visible) before the waiter resumes.
      waiters.removeAll { waiter ->
        runCatching { waiter.predicate() }.fold(
          onSuccess = { matched ->
            if (matched) {
              waiter.timeoutJob?.cancel()
              satisfied += waiter
            }
            matched
          },
          onFailure = { error ->
            waiter.timeoutJob?.cancel()
            failed += waiter to error
            true
          },
        )
      }
    }
    // Resume outside the lock so a resumed coroutine that re-enters cannot deadlock. Each waiter was
    // removed from the list exactly once (under the lock), so it is resumed at most once.
    satisfied.forEach { it.continuation.resume(true) }
    failed.forEach { (waiter, error) -> waiter.continuation.resumeWithException(error) }
  }
}
