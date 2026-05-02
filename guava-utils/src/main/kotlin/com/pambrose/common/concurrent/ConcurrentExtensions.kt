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
@file:JvmName("ConcurrentUtils")
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.concurrent.thread
import kotlin.time.Duration

/** Whether this [CountDownLatch] has reached zero. */
val CountDownLatch.isFinished: Boolean get() = count == 0L

/**
 * Executes [block] and calls [CountDownLatch.countDown] in a `finally` block,
 * ensuring the latch is decremented even if the block throws.
 *
 * @param block the code to execute before counting down.
 */
fun CountDownLatch.countDown(block: () -> Unit) {
  try {
    block()
  } finally {
    countDown()
  }
}

/**
 * Waits for the `CountDownLatch` to reach zero, or for the specified duration to elapse.
 *
 * @param duration the maximum time to wait, specified as a [Duration].
 * @return `true` if the `CountDownLatch` reached zero within the specified duration,
 *         `false` if the waiting time elapsed before the count reached zero.
 */
fun CountDownLatch.await(duration: Duration): Boolean = await(duration.inWholeMilliseconds, MILLISECONDS)

/**
 * Acquires a permit from this [Semaphore], executes [block], and releases the permit in a `finally` block.
 *
 * @param T the return type of [block].
 * @param block the code to execute while holding the permit.
 * @return the result of [block].
 */
fun <T> Semaphore.withLock(block: () -> T): T {
  acquire()
  return try {
    block()
  } finally {
    release()
  }
}

/**
 * Creates a new thread that automatically counts down the given [latch] when [block] completes.
 *
 * This is a convenience wrapper around [kotlin.concurrent.thread] that ensures
 * [CountDownLatch.countDown] is called in a `finally` block after [block] finishes.
 *
 * @param latch the [CountDownLatch] to count down upon completion.
 * @param start whether to start the thread immediately. Defaults to `true`.
 * @param isDaemon whether the thread is a daemon thread. Defaults to `false`.
 * @param contextClassLoader the context class loader for the thread, or `null`.
 * @param name the name of the thread, or `null`.
 * @param priority the thread priority, or `-1` to use the default.
 * @param block the code to execute in the thread.
 * @return the created [Thread].
 */
fun thread(
  latch: CountDownLatch,
  start: Boolean = true,
  isDaemon: Boolean = false,
  contextClassLoader: ClassLoader? = null,
  name: String? = null,
  priority: Int = -1,
  block: () -> Unit,
): Thread =
  thread(start, isDaemon, contextClassLoader, name, priority) {
    try {
      block()
    } finally {
      latch.countDown()
    }
  }
