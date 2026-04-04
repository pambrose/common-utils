@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

class InstrumentedThreadFactoryTests : StringSpec() {
  init {
    "newThread creates a non-null thread" {
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_creates_thread",
        help = "Test",
      )
      val thread = factory.newThread {}
      thread shouldNotBe null
    }

    "tracks created count" {
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_created_count",
        help = "Test",
      )
      factory.newThread {}
      factory.newThread {}
      factory.newThread {}

      val registry = CollectorRegistry.defaultRegistry
      val createdSample = registry.getSampleValue("itf_created_count_threads_created_total")
      createdSample shouldBe 3.0
    }

    "tracks running and terminated counts after thread execution" {
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_running_terminated",
        help = "Test",
      )
      val startedLatch = CountDownLatch(1)
      val proceedLatch = CountDownLatch(1)

      val thread = factory.newThread {
        startedLatch.countDown()
        proceedLatch.await()
      }
      thread.start()
      startedLatch.await()

      val registry = CollectorRegistry.defaultRegistry
      val runningDuring = registry.getSampleValue("itf_running_terminated_threads_running")
      runningDuring shouldBe 1.0

      proceedLatch.countDown()
      thread.join()

      val runningAfter = registry.getSampleValue("itf_running_terminated_threads_running")
      runningAfter shouldBe 0.0

      val terminated = registry.getSampleValue("itf_running_terminated_threads_terminated_total")
      terminated shouldBe 1.0
    }

    "daemon flag is propagated from delegate factory" {
      val daemonFactory = ThreadFactory { r ->
        Thread(r).apply { isDaemon = true }
      }
      val factory = InstrumentedThreadFactory(
        delegate = daemonFactory,
        name = "itf_daemon_flag",
        help = "Test",
      )
      val thread = factory.newThread {}
      thread.isDaemon shouldBe true
    }

    "uses custom thread factory when provided" {
      var customFactoryCalled = false
      val customFactory = ThreadFactory { r ->
        customFactoryCalled = true
        Thread(r, "custom-thread")
      }
      val factory = InstrumentedThreadFactory(
        delegate = customFactory,
        name = "itf_custom_factory",
        help = "Test",
      )
      val thread = factory.newThread {}
      customFactoryCalled shouldBe true
      thread.name shouldBe "custom-thread"
    }
  }
}
