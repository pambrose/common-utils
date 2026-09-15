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

import com.google.common.util.concurrent.AbstractExecutionThreadService
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract base class that wraps Guava's [AbstractExecutionThreadService] with
 * Kotlin [Duration]-based synchronous start and stop methods.
 */
abstract class GenericExecutionThreadService : AbstractExecutionThreadService() {
  /**
   * Starts the service asynchronously and blocks until it is running.
   *
   * @param timeout the maximum duration to wait for the service to start. Defaults to 30 seconds.
   * @throws TimeoutException if the service is not running within [timeout].
   * @throws IllegalStateException if the service fails, or reaches a state from which it cannot start.
   */
  @Throws(TimeoutException::class)
  fun startSync(timeout: Duration = 30.seconds) {
    startAsync()
    awaitRunning(timeout.inWholeNanoseconds, NANOSECONDS)
  }

  /**
   * Stops the service asynchronously and blocks until it has terminated.
   *
   * @param timeout the maximum duration to wait for the service to stop. Defaults to 30 seconds.
   * @throws TimeoutException if the service has not terminated within [timeout].
   * @throws IllegalStateException if the service fails.
   */
  @Throws(TimeoutException::class)
  fun stopSync(timeout: Duration = 30.seconds) {
    stopAsync()
    awaitTerminated(timeout.inWholeNanoseconds, NANOSECONDS)
  }
}
