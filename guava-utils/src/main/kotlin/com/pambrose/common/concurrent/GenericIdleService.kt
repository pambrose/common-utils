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

import com.google.common.util.concurrent.AbstractIdleService
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract base class that wraps Guava's [AbstractIdleService] with
 * Kotlin [Duration]-based synchronous start and stop methods.
 */
abstract class GenericIdleService : AbstractIdleService() {
  /**
   * Starts the service asynchronously and blocks until it is running or the timeout expires.
   *
   * @param maxWait the maximum duration to wait for the service to start. Defaults to 15 seconds.
   */
  fun startSync(maxWait: Duration = 15.seconds) {
    startAsync()
    awaitRunning(maxWait.inWholeMilliseconds, MILLISECONDS)
  }

  /**
   * Stops the service asynchronously and blocks until it has terminated or the timeout expires.
   *
   * @param maxWait the maximum duration to wait for the service to stop. Defaults to 15 seconds.
   */
  fun stopSync(maxWait: Duration = 15.seconds) {
    stopAsync()
    awaitTerminated(maxWait.inWholeMilliseconds, MILLISECONDS)
  }
}
