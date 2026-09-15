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
import io.mockk.verify
import java.util.concurrent.CountDownLatch
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

class GenericServicesTests : StringSpec() {
  init {
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
