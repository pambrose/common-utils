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

import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A coroutine-based waiter that suspends until a boolean value changes.
 *
 * Extends [GenericValueWaiter] to provide [waitUntilTrue] and [waitUntilFalse]
 * convenience methods with optional timeout support.
 *
 * @param initValue the initial boolean value.
 */
class BooleanWaiter(
  initValue: Boolean,
) : GenericValueWaiter<Boolean>(initValue) {
  @Volatile
  private var predicate: () -> Boolean = { currValue != initValue }

  override fun monitorSatisfied() = predicate()

  /**
   * Sets the boolean value and notifies any waiting coroutines if the condition is satisfied.
   *
   * @param value the new boolean value.
   */
  suspend fun setValue(value: Boolean) {
    checkCondition(value)
  }

  /**
   * Suspends until the value becomes `true` or the timeout expires.
   *
   * @param timeoutDuration the maximum duration to wait. Defaults to [Duration.INFINITE].
   * @return `true` if the value became `true` before the timeout, `false` if the timeout expired.
   */
  suspend fun waitUntilTrue(timeoutDuration: Duration = Duration.INFINITE): Boolean {
    predicate = { currValue }
    return waitForCondition(timeoutDuration)
  }

  /**
   * Suspends until the value becomes `false` or the timeout expires.
   *
   * @param timeoutDuration the maximum duration to wait. Defaults to [Duration.INFINITE].
   * @return `true` if the value became `false` before the timeout, `false` if the timeout expired.
   */
  suspend fun waitUntilFalse(timeoutDuration: Duration = Duration.INFINITE): Boolean {
    predicate = { !currValue }
    return waitForCondition(timeoutDuration)
  }
}

/**
 * Abstract coroutine-based waiter that suspends until a monitored value satisfies a condition.
 *
 * Subclasses define the satisfaction condition via [monitorSatisfied]. The value can be updated
 * with [checkCondition], which notifies any waiting coroutine when the condition is met.
 *
 * @param T the type of the monitored value.
 * @param initValue the initial value.
 */
abstract class GenericValueWaiter<T>(
  protected val initValue: T,
) {
  private val mutex = Mutex()
  private var onConditionChanged: (() -> Unit)? = null

  protected var currValue = initValue

  protected abstract fun monitorSatisfied(): Boolean

  /**
   * Suspends until the condition becomes true.
   */
  protected suspend fun waitForCondition(timeoutDuration: Duration): Boolean =
    coroutineScope {
      suspendCancellableCoroutine { continuation ->
        val timeoutJob = launch {
          delay(timeoutDuration)
          mutex.withLock {
            onConditionChanged = null
            continuation.resume(false)
          }
        }

        launch {
          mutex.withLock {
            if (monitorSatisfied()) {
              timeoutJob.cancel()
              continuation.resume(true)
            } else {
              onConditionChanged = {
                continuation.resume(true)
                timeoutJob.cancel()
              }
            }
          }
        }

        continuation.invokeOnCancellation {
          timeoutJob.cancel()
          onConditionChanged = null
        }
      }
    }

  /**
   * Updates the value and then checks the condition to see if it's satisfied.
   */
  suspend fun checkCondition(value: T) {
    mutex.withLock {
      currValue = value
      if (monitorSatisfied()) {
        onConditionChanged?.invoke() // Notify waiting coroutine
        onConditionChanged = null // Clear the callback
      }
    }
  }
}

fun main() =
  runBlocking {
    val waiter = BooleanWaiter(true)

    // Launch a coroutine that waits for the condition to become true
    launch {
      println("Waiting for condition to become true...")
      waiter.waitUntilFalse(20.seconds).also { if (!it) println("Timed out") }
      println("Condition is now true!")
    }

    delay(5.seconds)
    println("Setting condition to true.")
    waiter.setValue(false)

    delay(15.seconds)
    println("Finished.")
  }
