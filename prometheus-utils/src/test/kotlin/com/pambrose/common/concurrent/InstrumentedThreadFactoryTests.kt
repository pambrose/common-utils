@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import com.pambrose.common.dsl.PrometheusDsl
import com.pambrose.common.metrics.metricNames
import com.pambrose.common.metrics.openMetricsText
import com.pambrose.common.metrics.prometheusText
import com.pambrose.common.metrics.sampleValue
import com.pambrose.common.metrics.seriesNames
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.prometheus.metrics.model.registry.PrometheusRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.concurrent.atomics.AtomicReference

class InstrumentedThreadFactoryTests : StringSpec() {
  init {
    "newThread creates a non-null thread" {
      val registry = PrometheusRegistry()
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_creates_thread",
        help = "Test",
        registry = registry,
      )
      factory.newThread {}.shouldNotBeNull()
    }

    "tracks created count" {
      val registry = PrometheusRegistry()
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_created_count",
        help = "Test",
        registry = registry,
      )
      factory.newThread {}
      factory.newThread {}
      factory.newThread {}

      registry.sampleValue("itf_created_count_threads_created_total") shouldBe 3.0
    }

    "tracks running and terminated counts after thread execution" {
      val registry = PrometheusRegistry()
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_running_terminated",
        help = "Test",
        registry = registry,
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

      registry.sampleValue("itf_running_terminated_threads_running") shouldBe 1.0

      proceedLatch.countDown()
      thread.join()

      registry.sampleValue("itf_running_terminated_threads_running") shouldBe 0.0
      registry.sampleValue("itf_running_terminated_threads_terminated_total") shouldBe 1.0
    }

    // Bug #10: the finally block decremented running before incrementing terminated, so a scrape could see a
    // finished thread as neither running nor terminated. terminated.inc() now runs first, and once every created
    // thread has finished, created == running + terminated.
    "running plus terminated equals created after completion" {
      val registry = PrometheusRegistry()
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_invariant",
        help = "Test",
        registry = registry,
      )
      val thread = factory.newThread {}.shouldNotBeNull()
      thread.start()
      thread.join()

      val created = registry.sampleValue("itf_invariant_threads_created_total").shouldNotBeNull()
      val running = registry.sampleValue("itf_invariant_threads_running").shouldNotBeNull()
      val terminated = registry.sampleValue("itf_invariant_threads_terminated_total").shouldNotBeNull()

      created shouldBe 1.0
      running shouldBe 0.0
      terminated shouldBe 1.0
      (running + terminated) shouldBe created
    }

    "invariant holds after multiple threads complete" {
      val registry = PrometheusRegistry()
      val factory = InstrumentedThreadFactory(
        delegate = Executors.defaultThreadFactory(),
        name = "itf_invariant_multi",
        help = "Test",
        registry = registry,
      )
      val threads = (1..5).map { factory.newThread {}.shouldNotBeNull() }
      threads.forEach { it.start() }
      threads.forEach { it.join() }

      val created = registry.sampleValue("itf_invariant_multi_threads_created_total").shouldNotBeNull()
      val running = registry.sampleValue("itf_invariant_multi_threads_running").shouldNotBeNull()
      val terminated = registry.sampleValue("itf_invariant_multi_threads_terminated_total").shouldNotBeNull()

      created shouldBe 5.0
      running shouldBe 0.0
      terminated shouldBe 5.0
      (running + terminated) shouldBe created
    }

    "daemon flag is propagated from delegate factory" {
      val registry = PrometheusRegistry()
      val daemonFactory = ThreadFactory { r ->
        Thread(r).apply { isDaemon = true }
      }
      val factory = InstrumentedThreadFactory(
        delegate = daemonFactory,
        name = "itf_daemon_flag",
        help = "Test",
        registry = registry,
      )
      val thread = factory.newThread {}.shouldNotBeNull()
      thread.isDaemon shouldBe true
    }

    "uses custom thread factory when provided" {
      val registry = PrometheusRegistry()
      var customFactoryCalled = false
      val customFactory = ThreadFactory { r ->
        customFactoryCalled = true
        Thread(r, "custom-thread")
      }
      val factory = InstrumentedThreadFactory(
        delegate = customFactory,
        name = "itf_custom_factory",
        help = "Test",
        registry = registry,
      )
      val thread = factory.newThread {}.shouldNotBeNull()
      customFactoryCalled shouldBe true
      thread.name shouldBe "custom-thread"
    }

    // The ThreadFactory contract lets a delegate reject a request by returning null.
    "a delegate that rejects the thread yields null and is not counted as created" {
      val registry = PrometheusRegistry()
      val factory = InstrumentedThreadFactory(
        delegate = { null },
        name = "itf_rejected",
        help = "Test",
        registry = registry,
      )

      factory.newThread {} shouldBe null
      registry.sampleValue("itf_rejected_threads_created_total") shouldBe 0.0
    }

    "factories with the same name can coexist in separate registries" {
      val first = PrometheusRegistry()
      val second = PrometheusRegistry()
      InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_isolated", "Test", first).newThread {}
      InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_isolated", "Test", second).apply {
        newThread {}
        newThread {}
      }

      first.sampleValue("itf_isolated_threads_created_total") shouldBe 1.0
      second.sampleValue("itf_isolated_threads_created_total") shouldBe 2.0
    }

    "a second factory with the same name in the same registry is rejected" {
      val registry = PrometheusRegistry()
      InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_duplicate", "Test", registry).newThread {}

      shouldThrow<IllegalArgumentException> {
        InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_duplicate", "Test", registry)
      }.message shouldContain "itf_duplicate_threads_created"

      // The first factory's metrics are untouched.
      registry.sampleValue("itf_duplicate_threads_created_total") shouldBe 1.0
    }

    // Registration is all or nothing. When only a later name is taken, the metrics registered before it are removed
    // again; left behind, they would make the factory impossible to build even after the conflict was resolved.
    "a factory whose running or terminated name is taken leaves no metrics behind" {
      listOf("itf_partial_threads_running", "itf_partial_threads_terminated").forEach { takenName ->
        val registry = PrometheusRegistry()
        val existing =
          PrometheusDsl.gauge(registry) {
            name(takenName)
            help("Taken")
          }

        shouldThrow<IllegalArgumentException> {
          InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_partial", "Test", registry)
        }.message shouldContain takenName

        // Only the collector that already owned the name is left.
        registry.metricNames() shouldBe setOf(takenName)

        // Once the conflict is gone, the factory can be built under the same name.
        registry.unregister(existing)
        InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_partial", "Test", registry).newThread {}
        registry.sampleValue("itf_partial_threads_created_total") shouldBe 1.0
      }
    }

    // Pins the series names the KDoc documents, as the servlet exporter's default writers produce them.
    "the metrics are exposed as <name>_threads_created_total, _running and _terminated_total" {
      val registry = PrometheusRegistry()
      InstrumentedThreadFactory(Executors.defaultThreadFactory(), "itf_exposed", "Test", registry)
        .newThread {}
        .shouldNotBeNull()
        .apply {
          start()
          join()
        }
      val expected =
        setOf(
          "itf_exposed_threads_created_total",
          "itf_exposed_threads_running",
          "itf_exposed_threads_terminated_total",
        )

      // Prometheus text format: the counters' families are named with _total, and no _created series is written.
      val text = registry.prometheusText()
      seriesNames(text) shouldBe expected
      text shouldContain "# TYPE itf_exposed_threads_created_total counter"

      // OpenMetrics: the same series, but the counter families are named without _total.
      val openMetrics = registry.openMetricsText()
      seriesNames(openMetrics) shouldBe expected
      openMetrics shouldContain "# TYPE itf_exposed_threads_created counter"
    }

    "a runnable that throws still counts its thread as terminated and no longer running" {
      val registry = PrometheusRegistry()
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
      registry.sampleValue("itf_throwing_threads_created_total") shouldBe 1.0
      registry.sampleValue("itf_throwing_threads_running") shouldBe 0.0
      registry.sampleValue("itf_throwing_threads_terminated_total") shouldBe 1.0
    }
  }
}
