@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import com.pambrose.common.dsl.PrometheusDsl
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.concurrent.atomics.AtomicReference

class InstrumentedThreadFactoryTests : StringSpec() {
  init {
    "newThread creates a non-null thread" {
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_creates_thread",
        help = "Test",
      )
      factory.newThread {}.shouldNotBeNull()
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

      val thread =
        factory
          .newThread {
            startedLatch.countDown()
            proceedLatch.await()
          }.shouldNotBeNull()
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

    // Bug #10: the finally block decremented running before incrementing terminated, so a scrape could see a
    // finished thread as neither running nor terminated. terminated.inc() now runs first, and once every created
    // thread has finished, created == running + terminated.
    "running plus terminated equals created after completion" {
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_invariant",
        help = "Test",
      )
      val thread = factory.newThread {}.shouldNotBeNull()
      thread.start()
      thread.join()

      val registry = CollectorRegistry.defaultRegistry
      val created = registry.getSampleValue("itf_invariant_threads_created_total")
      val running = registry.getSampleValue("itf_invariant_threads_running")
      val terminated = registry.getSampleValue("itf_invariant_threads_terminated_total")

      created shouldBe 1.0
      running shouldBe 0.0
      terminated shouldBe 1.0
      (running + terminated) shouldBe created
    }

    "invariant holds after multiple threads complete" {
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_invariant_multi",
        help = "Test",
      )
      val threads = (1..5).map { factory.newThread {}.shouldNotBeNull() }
      threads.forEach { it.start() }
      threads.forEach { it.join() }

      val registry = CollectorRegistry.defaultRegistry
      val created = registry.getSampleValue("itf_invariant_multi_threads_created_total")
      val running = registry.getSampleValue("itf_invariant_multi_threads_running")
      val terminated = registry.getSampleValue("itf_invariant_multi_threads_terminated_total")

      created shouldBe 5.0
      running shouldBe 0.0
      terminated shouldBe 5.0
      (running + terminated) shouldBe created
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
      val thread = factory.newThread {}.shouldNotBeNull()
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
      val thread = factory.newThread {}.shouldNotBeNull()
      customFactoryCalled shouldBe true
      thread.name shouldBe "custom-thread"
    }

    // The ThreadFactory contract lets a delegate reject a request by returning null.
    "a delegate that rejects the thread yields null and is not counted as created" {
      val factory = InstrumentedThreadFactory(delegate = { null }, name = "itf_rejected", help = "Test")

      factory.newThread {} shouldBe null
      CollectorRegistry.defaultRegistry.getSampleValue("itf_rejected_threads_created_total") shouldBe 0.0
    }

    "factories with the same name can coexist in separate registries" {
      val first = CollectorRegistry()
      val second = CollectorRegistry()
      InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_isolated", "Test", first).newThread {}
      InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_isolated", "Test", second).apply {
        newThread {}
        newThread {}
      }

      first.getSampleValue("itf_isolated_threads_created_total") shouldBe 1.0
      second.getSampleValue("itf_isolated_threads_created_total") shouldBe 2.0
    }

    "a second factory with the same name in the same registry is rejected" {
      val registry = CollectorRegistry()
      InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_duplicate", "Test", registry).newThread {}

      shouldThrow<IllegalArgumentException> {
        InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_duplicate", "Test", registry)
      }.message shouldContain "itf_duplicate_threads_created"

      // The first factory's metrics are untouched.
      registry.getSampleValue("itf_duplicate_threads_created_total") shouldBe 1.0
    }

    // Registration is all or nothing. When only a later name is taken, the metrics registered before it are removed
    // again; left behind, they would make the factory impossible to build even after the conflict was resolved.
    "a factory whose running or terminated name is taken leaves no metrics behind" {
      listOf("itf_partial_threads_running", "itf_partial_threads_terminated").forEach { takenName ->
        val registry = CollectorRegistry()
        val existing =
          PrometheusDsl.gauge(registry) {
            name(takenName)
            help("Taken")
          }

        shouldThrow<IllegalArgumentException> {
          InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_partial", "Test", registry)
        }.message shouldContain takenName

        // Only the collector that already owned the name is left.
        registry.metricFamilySamples().toList().map { it.name } shouldBe [takenName]

        // Once the conflict is gone, the factory can be built under the same name.
        registry.unregister(existing)
        InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_partial", "Test", registry).newThread {}
        registry.getSampleValue("itf_partial_threads_created_total") shouldBe 1.0
      }
    }

    "a runnable that throws still counts its thread as terminated and no longer running" {
      val registry = CollectorRegistry()
      val uncaught = AtomicReference<Throwable?>(null)
      val delegate =
        ThreadFactory { runnable ->
          Thread(runnable).apply { setUncaughtExceptionHandler { _, e -> uncaught.store(e) } }
        }
      val factory = InstrumentedThreadFactory(delegate, "itf_throwing", "Test", registry)

      val thread = factory.newThread { throw IllegalStateException("simulated task failure") }.shouldNotBeNull()
      thread.start()
      thread.join()

      uncaught.load().shouldBeInstanceOf<IllegalStateException>().message shouldBe "simulated task failure"
      registry.getSampleValue("itf_throwing_threads_created_total") shouldBe 1.0
      registry.getSampleValue("itf_throwing_threads_running") shouldBe 0.0
      registry.getSampleValue("itf_throwing_threads_terminated_total") shouldBe 1.0
    }
  }
}
