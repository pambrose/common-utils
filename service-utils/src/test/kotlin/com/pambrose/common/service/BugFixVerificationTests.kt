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

package com.pambrose.common.service

import com.google.common.util.concurrent.AbstractIdleService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicBoolean

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #7: Shutdown hook called stopAsync() without waiting for completion
    // Before fix: stopAsync() returned immediately, JVM could exit before cleanup
    // After fix: awaitTerminated() is called after stopAsync() to block until done

    "shut down hook action waits for service to stop" {
      val shutdownCompleted = AtomicBoolean(false)

      val service =
        object : AbstractIdleService() {
          override fun startUp() {
            // Empty
          }

          override fun shutDown() {
            // Simulate some cleanup work
            Thread.sleep(100)
            shutdownCompleted.set(true)
          }
        }

      service.startAsync().awaitRunning()
      service.isRunning shouldBe true

      // Run the shutdown hook action
      val thread = GenericService.shutDownHookAction(service)
      thread.start()
      thread.join(5000)

      // After the hook completes, the service should be fully terminated
      shutdownCompleted.get() shouldBe true
      service.isRunning shouldBe false
    }
  }
}
