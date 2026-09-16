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

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import io.github.oshai.kotlinlogging.KLogger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class SlowIdleService(
  private val startupDelay: Duration,
) : GenericIdleService() {
  override fun startUp() {
    Thread.sleep(startupDelay.inWholeMilliseconds)
  }

  override fun shutDown() = Unit
}

private class BlockingService : GenericExecutionThreadService() {
  private val stopRequested = CountDownLatch(1)

  override fun run() {
    stopRequested.await()
  }

  override fun triggerShutdown() {
    stopRequested.countDown()
  }
}

private class FailingIdleService : GenericIdleService() {
  override fun startUp() {
    error("startUp failed")
  }

  override fun shutDown() = Unit
}

private class FailingExecutionThreadService : GenericExecutionThreadService() {
  override fun startUp() {
    error("startUp failed")
  }

  override fun run() = Unit
}

private class FailingShutdownService : GenericIdleService() {
  override fun startUp() = Unit

  override fun shutDown() {
    error("shutDown failed")
  }
}

// Services that cannot finish stopping until the test releases them.
private class SlowStoppingIdleService : GenericIdleService() {
  val release = CountDownLatch(1)

  override fun startUp() = Unit

  override fun shutDown() = release.await()
}

private class SlowStoppingExecutionThreadService : GenericExecutionThreadService() {
  val release = CountDownLatch(1)

  // triggerShutdown is left as the no-op default, so run ignores the stop request until released.
  override fun run() = release.await()
}

class GenericServicesTests : StringSpec() {
  init {
    "startSync throws IllegalStateException carrying the cause when startUp fails" {
      fun startFails(
        service: Service,
        startSync: () -> Unit,
      ) {
        shouldThrow<IllegalStateException> { startSync() }.cause?.message shouldBe "startUp failed"
        service.state() shouldBe Service.State.FAILED
      }

      val idle = FailingIdleService()
      startFails(idle) { idle.startSync(timeout = HANG_GUARD_SECONDS.seconds) }
      val executing = FailingExecutionThreadService()
      startFails(executing) { executing.startSync(timeout = HANG_GUARD_SECONDS.seconds) }
    }

    "stopSync throws TimeoutException when the service does not stop in time" {
      val idle = SlowStoppingIdleService()
      idle.startSync()
      shouldThrow<TimeoutException> { idle.stopSync(timeout = 50.milliseconds) }
      idle.state() shouldBe Service.State.STOPPING
      idle.release.countDown()
      idle.awaitTerminated(HANG_GUARD_SECONDS, TimeUnit.SECONDS)

      val executing = SlowStoppingExecutionThreadService()
      executing.startSync()
      shouldThrow<TimeoutException> { executing.stopSync(timeout = 50.milliseconds) }
      executing.state() shouldBe Service.State.STOPPING
      executing.release.countDown()
      executing.awaitTerminated(HANG_GUARD_SECONDS, TimeUnit.SECONDS)
    }

    "stopSync throws IllegalStateException when shutDown fails" {
      val service = FailingShutdownService()
      service.startSync()
      val ex = shouldThrow<IllegalStateException> { service.stopSync(timeout = HANG_GUARD_SECONDS.seconds) }
      ex.cause?.message shouldBe "shutDown failed"
      service.state() shouldBe Service.State.FAILED
    }

    "genericServiceListener logs a failure at error level with its cause" {
      val logger = mockk<KLogger>(relaxed = true)
      val service = FailingIdleService()
      service.addListener(service.genericServiceListener(logger), MoreExecutors.directExecutor())

      shouldThrow<IllegalStateException> { service.startSync(timeout = HANG_GUARD_SECONDS.seconds) }

      val cause = slot<Throwable>()
      val message = slot<() -> Any?>()
      verify(exactly = 1, timeout = 5_000) { logger.error(capture(cause), capture(message)) }
      cause.captured.message shouldBe "startUp failed"
      message.captured() shouldBe "Failed on STARTING $service"
      // Only "Starting" was logged at info level: the service never ran, stopped, or terminated.
      verify(exactly = 1) { logger.info(any<() -> Any?>()) }
    }

    "startSync and stopSync declare TimeoutException for Java callers" {
      [GenericIdleService::class.java, GenericExecutionThreadService::class.java].forEach { type ->
        val methods =
          type.declaredMethods.filter {
            !it.isSynthetic && (it.name.startsWith("startSync") || it.name.startsWith("stopSync"))
          }
        methods.size shouldBe 2
        methods.forEach { it.exceptionTypes.toList() shouldContain TimeoutException::class.java }
      }
    }

    "startSync throws TimeoutException when the service does not start in time" {
      val service = SlowIdleService(startupDelay = 500.milliseconds)
      shouldThrow<TimeoutException> { service.startSync(timeout = 50.milliseconds) }
      service.awaitRunning()
      service.stopSync(timeout = 5.seconds)
    }

    "startSync and stopSync bring a service up and down" {
      val service = BlockingService()
      service.startSync()
      service.isRunning shouldBe true
      service.stopSync()
      service.state() shouldBe Service.State.TERMINATED
    }

    "genericServiceListener logs each lifecycle transition once it is added" {
      val logger = mockk<KLogger>(relaxed = true)
      val service = BlockingService()
      service.addListener(service.genericServiceListener(logger), MoreExecutors.directExecutor())

      service.startSync()
      service.stopSync()

      // starting, running, stopping, and terminated
      verify(exactly = 4, timeout = 5_000) { logger.info(any<() -> Any?>()) }
    }
  }
}
