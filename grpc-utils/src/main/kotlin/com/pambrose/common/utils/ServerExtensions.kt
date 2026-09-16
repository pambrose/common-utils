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

package com.pambrose.common.utils

import io.grpc.Server
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Registers a JVM shutdown hook that gracefully shuts down this gRPC [Server].
 *
 * The hook waits up to [maxWaitTime] for in-flight RPCs, then forces the server down. Because it runs as the
 * JVM exits, with no caller left to report to, a failure on the graceful path is swallowed rather than
 * propagated — but the server is still stopped.
 *
 * Extension function on [Server].
 *
 * @param maxWaitTime the maximum duration to wait for in-flight RPCs to complete before forcing shutdown
 * @throws IllegalArgumentException if [maxWaitTime] is not positive. This is checked here, when the hook is
 *   registered, rather than at JVM exit, where it would stop the shutdown from running at all.
 */
fun Server.shutdownWithJvm(maxWaitTime: Duration) {
  require(maxWaitTime.isPositive()) { "maxWaitTime must be greater than 0" }
  Runtime.getRuntime().addShutdownHook(
    Thread {
      // Whatever the graceful path does, including an InterruptedException from awaitTermination or a server
      // that is already terminated, shutdownNow() still runs exactly once.
      val _ =
        runCatching {
          shutdown()
          awaitTermination(maxWaitTime.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        }
      shutdownNow()
    },
  )
}

/**
 * Gracefully shuts down this gRPC [Server], waiting up to [maxWaitTime] for in-flight RPCs to complete.
 *
 * Extension function on [Server]. Delegates to [shutdownGracefully] with millisecond conversion.
 *
 * @param maxWaitTime the maximum duration to wait before forcing shutdown
 * @throws InterruptedException if the current thread is interrupted while waiting
 */
@Throws(InterruptedException::class)
fun Server.shutdownGracefully(maxWaitTime: Duration) =
  shutdownGracefully(maxWaitTime.inWholeMilliseconds, TimeUnit.MILLISECONDS)

/**
 * Gracefully shuts down this gRPC [Server], waiting up to the specified [timeout] for termination.
 *
 * Extension function on [Server]. Calls [Server.shutdown], then [Server.awaitTermination], and
 * finally [Server.shutdownNow] in a `finally` block to ensure the server is fully stopped.
 *
 * @param timeout the maximum time to wait for graceful termination
 * @param unit the time unit of the [timeout] argument
 * @throws InterruptedException if the current thread is interrupted while waiting
 */
@Throws(InterruptedException::class)
fun Server.shutdownGracefully(
  timeout: Long,
  unit: TimeUnit,
) {
  require(timeout > 0) { "timeout must be greater than 0" }
  shutdown()
  try {
    awaitTermination(timeout, unit)
  } finally {
    shutdownNow()
  }
}
