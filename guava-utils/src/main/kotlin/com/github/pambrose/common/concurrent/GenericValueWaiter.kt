/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose.common.concurrent

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class BooleanWaiter(
  initValue: Boolean,
) : GenericValueWaiter<Boolean>(initValue) {
  @Volatile
  private var predicate: () -> Boolean = { currValue != initValue }

  override fun monitorSatisfied() = predicate()

  suspend fun setValue(value: Boolean) {
    checkCondition(value)
  }

  suspend fun waitUntilTrue(timeoutDuration: Duration = Long.MAX_VALUE.days): Boolean {
    predicate = { currValue }
    return waitForCondition(timeoutDuration)
  }

  suspend fun waitUntilFalse(timeoutDuration: Duration = Long.MAX_VALUE.days): Boolean {
    predicate = { !currValue }
    return waitForCondition(timeoutDuration)
  }
}

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
