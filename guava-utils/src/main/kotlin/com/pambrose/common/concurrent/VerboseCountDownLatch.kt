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
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * A [CountDownLatch] that logs a message on each wait timeout and retries until the latch reaches zero.
 *
 * Useful for long-running waits where periodic logging is desired to indicate the system is still waiting.
 *
 * @param count the number of times [countDown] must be invoked before threads can pass through [await].
 */
class VerboseCountDownLatch(
  count: Int,
) : CountDownLatch(count) {
  /**
   * Waits repeatedly for the latch to reach zero, logging [msg] at INFO level each time
   * the [timeout] expires without the latch being satisfied.
   *
   * @param timeout the duration of each individual wait attempt.
   * @param msg the message to log on each timeout.
   * @throws InterruptedException if the current thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun await(
    timeout: Duration,
    msg: String,
  ) = await(timeout) { msg }

  /**
   * Waits repeatedly for the latch to reach zero, logging the result of [msg] at INFO level
   * each time the [timeout] expires without the latch being satisfied.
   *
   * @param timeout the duration of each individual wait attempt.
   * @param msg a lambda producing the message to log on each timeout.
   * @throws InterruptedException if the current thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun await(
    timeout: Duration,
    msg: () -> Any?,
  ) {
    while (true) {
      val satisfied = await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      if (satisfied)
        break
      logger.info(msg)
    }
  }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
