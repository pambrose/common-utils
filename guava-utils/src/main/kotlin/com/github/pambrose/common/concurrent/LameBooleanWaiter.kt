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

class LameBooleanWaiter(
  private var value: Boolean,
) {
  private val mutex = Mutex()
  private var onValueChanged: (() -> Unit)? = null

  /**
   * Suspends until the boolean value changes to the opposite of the current value.
   */
  suspend fun waitForChangeInValue() {
    val initialValue: Boolean
    val targetValue: Boolean

    // First, acquire the mutex to check the current value
    mutex.withLock {
      initialValue = value
      targetValue = !initialValue

      // If the value is already the opposite of what it was when this method was called, return immediately
      if (value == targetValue) return
    }

    // Use coroutineScope for structured concurrency
    coroutineScope {
      suspendCancellableCoroutine { continuation ->
        val job = launch {
          mutex.withLock {
            // Check again if the value has changed while we were setting up
            if (value == targetValue) {
              continuation.resume(Unit)
            } else {
              onValueChanged = {
                if (value == targetValue) {
                  continuation.resume(Unit)
                }
              }
            }
          }
        }

        continuation.invokeOnCancellation {
          job.cancel()
          onValueChanged = null
        }
      }
    }
  }

  /**
   * Updates the boolean value and notifies waiting coroutines if the value changes.
   */
  suspend fun changeValue(newValue: Boolean) {
    mutex.withLock {
      if (value != newValue) {
        val oldValue = value
        value = newValue

        // Notify waiting coroutine if the value has changed
        onValueChanged?.invoke()

        // Only clear the callback if we've changed to the opposite value
        // This ensures that waitForChangeInValue() will continue to wait until the value changes to the opposite
        if (newValue == !oldValue) {
          onValueChanged = null
        }
      }
    }
  }
}

fun main() =
  runBlocking {
    // Test with initial value false
    val waiter = LameBooleanWaiter(false)

    // Launch a coroutine that waits for the value to change
    val job1 = launch {
      println("Coroutine 1: Waiting for value to change from false to true...")
      waiter.waitForChangeInValue()
      println("Coroutine 1: Value has changed to true!")
    }

    delay(1000) // Simulate some delay before changing the value
    println("Main: Changing value to true.")
    waiter.changeValue(true)

    job1.join() // Wait for the first coroutine to complete

    // Launch another coroutine that waits for the value to change back to false
    val job2 = launch {
      println("Coroutine 2: Waiting for value to change from true to false...")
      waiter.waitForChangeInValue()
      println("Coroutine 2: Value has changed to false!")
    }

    delay(1000) // Simulate some delay before changing the value
    println("Main: Changing value to false.")
    waiter.changeValue(false)

    job2.join() // Wait for the second coroutine to complete

    // Test with multiple value changes
    val waiter2 = LameBooleanWaiter(true)

    val job3 = launch {
      println("Coroutine 3: Waiting for value to change from true to false...")
      waiter2.waitForChangeInValue()
      println("Coroutine 3: Value has changed to false!")
    }

    delay(500)
    println("Main: Changing value to true (no change).")
    waiter2.changeValue(true) // This should not trigger the waiting coroutine

    delay(500)
    println("Main: Changing value to false.")
    waiter2.changeValue(false) // This should trigger the waiting coroutine

    job3.join() // Wait for the third coroutine to complete

    println("All tests completed successfully!")
  }
