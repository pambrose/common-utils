@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.service

import com.google.common.util.concurrent.AbstractIdleService
import io.kotest.matchers.shouldBe
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Test

class BugFixVerificationTests {
  // Bug #7: Shutdown hook called stopAsync() without waiting for completion
  // Before fix: stopAsync() returned immediately, JVM could exit before cleanup
  // After fix: awaitTerminated() is called after stopAsync() to block until done

  @Test
  fun shutDownHookActionWaitsForServiceToStop() {
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
