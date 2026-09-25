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

package com.pambrose.common.metrics

import com.pambrose.common.dsl.PrometheusDsl
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import io.prometheus.metrics.model.registry.Collector
import io.prometheus.metrics.model.registry.MultiCollector
import io.prometheus.metrics.model.registry.PrometheusRegistry

// Runs block and verifies that it made no registration attempt on the spied registry.
private fun shouldRegisterNothing(
  registry: PrometheusRegistry,
  block: () -> Unit,
) {
  clearMocks(registry, answers = false)
  block()
  verify(exactly = 0) { registry.register(any<Collector>()) }
  verify(exactly = 0) { registry.register(any<MultiCollector>()) }
}

class SystemMetricsTests : StringSpec() {
  // The exposed series names each flag contributes: these are the documented 0.16 -> 1.x renames. process_* gains
  // process_virtual_memory_bytes and process_resident_memory_bytes on Linux, so these are minimum sets.
  private val expectedSeries =
    mapOf(
      "standard" to
        setOf("process_cpu_seconds_total", "process_start_time_seconds", "process_open_fds", "process_max_fds"),
      "memory" to
        setOf(
          "jvm_memory_used_bytes",
          "jvm_memory_committed_bytes",
          "jvm_memory_max_bytes",
          "jvm_memory_init_bytes",
          "jvm_memory_objects_pending_finalization",
          "jvm_memory_pool_used_bytes",
          "jvm_memory_pool_committed_bytes",
          "jvm_memory_pool_max_bytes",
          "jvm_memory_pool_init_bytes",
          "jvm_memory_pool_collection_used_bytes",
          "jvm_memory_pool_collection_committed_bytes",
          "jvm_memory_pool_collection_max_bytes",
          "jvm_memory_pool_collection_init_bytes",
        ),
      "gc" to setOf("jvm_gc_collection_seconds_count", "jvm_gc_collection_seconds_sum"),
      "thread" to
        setOf(
          "jvm_threads_current",
          "jvm_threads_daemon",
          "jvm_threads_peak",
          "jvm_threads_started_total",
          "jvm_threads_deadlocked",
          "jvm_threads_deadlocked_monitor",
          "jvm_threads_state",
        ),
      "classLoading" to
        setOf("jvm_classes_currently_loaded", "jvm_classes_loaded_total", "jvm_classes_unloaded_total"),
      "versionInfo" to setOf("jvm_runtime_info"),
      "bufferPool" to
        setOf("jvm_buffer_pool_used_bytes", "jvm_buffer_pool_capacity_bytes", "jvm_buffer_pool_used_buffers"),
      "compilation" to setOf("jvm_compilation_time_seconds_total"),
    )

  init {
    "initialize registers the requested exporters with the default registry" {
      SystemMetrics.initialize(
        enableStandardExports = true,
        enableMemoryPoolsExports = true,
        enableGarbageCollectorExports = true,
        enableThreadExports = true,
        enableClassLoadingExports = true,
        enableVersionInfoExports = true,
      )

      val registry = PrometheusRegistry.defaultRegistry
      registry.sampleValue("process_cpu_seconds_total").shouldNotBeNull()
      registry.sampleValue("jvm_threads_current").shouldNotBeNull()
      registry.sampleValue("jvm_classes_currently_loaded").shouldNotBeNull()
    }

    "each flag registers its metric set under the 1.x names" {
      expectedSeries.forEach { (flag, series) ->
        val registry = PrometheusRegistry()
        SystemMetrics.initialize(
          enableStandardExports = flag == "standard",
          enableMemoryPoolsExports = flag == "memory",
          enableGarbageCollectorExports = flag == "gc",
          enableThreadExports = flag == "thread",
          enableClassLoadingExports = flag == "classLoading",
          enableVersionInfoExports = flag == "versionInfo",
          enableBufferPoolExports = flag == "bufferPool",
          enableCompilationExports = flag == "compilation",
          registry = registry,
        )
        seriesNames(registry.prometheusText()) shouldContainAll series
      }
    }

    // The allocation counter has no series until a garbage collection updates it, so check its family instead.
    "the memory pools flag also registers the per-pool allocation counter" {
      val registry = PrometheusRegistry()
      SystemMetrics.initialize(enableMemoryPoolsExports = true, registry = registry)

      registry.metricNames() shouldContain "jvm_memory_pool_allocated_bytes"
    }

    // Native memory metrics exist only when the JVM runs with -XX:NativeMemoryTracking, which the tests do not use.
    "the native memory flag registers nothing without native memory tracking" {
      val registry = spyk(PrometheusRegistry())
      SystemMetrics.initialize(enableNativeMemoryExports = true, registry = registry)

      registry.metricNames().shouldBeEmpty()

      // It still counts as registered, so a later call does not try again.
      shouldRegisterNothing(registry) {
        SystemMetrics.initialize(enableNativeMemoryExports = true, registry = registry)
      }
    }

    // The registry rejects a duplicate with IllegalArgumentException, which initialize also swallows, so only the
    // number of registration attempts tells skipping an exporter apart from trying it again.
    "calling initialize again with the same exporters makes no second registration attempt" {
      val registry = spyk(PrometheusRegistry())
      SystemMetrics.initialize(enableThreadExports = true, registry = registry)
      registry.sampleValue("jvm_threads_current").shouldNotBeNull()

      shouldRegisterNothing(registry) { SystemMetrics.initialize(enableThreadExports = true, registry = registry) }
    }

    "an exporter that fails to register for another reason is retried by the next call" {
      val registry = spyk(PrometheusRegistry())
      every { registry.register(any<Collector>()) } throws IllegalStateException("simulated failure") andThenAnswer
        { callOriginal() }

      SystemMetrics.initialize(enableClassLoadingExports = true, registry = registry)
      registry.metricNames().shouldBeEmpty()

      SystemMetrics.initialize(enableClassLoadingExports = true, registry = registry)
      registry.sampleValue("jvm_classes_currently_loaded").shouldNotBeNull()

      // Registered now, so a third call makes no further attempt.
      shouldRegisterNothing(registry) {
        SystemMetrics.initialize(enableClassLoadingExports = true, registry = registry)
      }
    }

    // The client registers a set's metrics one at a time; a failure part-way through must not leave the first ones
    // behind, or the retry would collide with them.
    "a failure part-way through a metric set leaves none of its metrics registered" {
      val registry = spyk(PrometheusRegistry())
      every { registry.register(any<Collector>()) } answers { callOriginal() } andThenThrows
        IllegalStateException("simulated failure") andThenAnswer { callOriginal() }

      SystemMetrics.initialize(enableClassLoadingExports = true, registry = registry)
      registry.metricNames().shouldBeEmpty()

      SystemMetrics.initialize(enableClassLoadingExports = true, registry = registry)
      seriesNames(registry.prometheusText()) shouldContainAll expectedSeries.getValue("classLoading")
    }

    "an exporter removed with clear is not registered again" {
      val registry = PrometheusRegistry()
      SystemMetrics.initialize(enableThreadExports = true, registry = registry)
      registry.clear()

      SystemMetrics.initialize(enableThreadExports = true, registry = registry)

      registry.sampleValue("jvm_threads_current") shouldBe null
    }

    "a later call registers exporters that were not requested before" {
      val registry = PrometheusRegistry()
      SystemMetrics.initialize(enableThreadExports = true, registry = registry)
      registry.sampleValue("jvm_classes_currently_loaded") shouldBe null

      SystemMetrics.initialize(enableThreadExports = true, enableClassLoadingExports = true, registry = registry)

      registry.sampleValue("jvm_classes_currently_loaded").shouldNotBeNull()
    }

    // jvm_threads_peak is the third of the thread metrics the client registers, so without the all-or-nothing
    // handling jvm_threads_current and jvm_threads_daemon would be left behind.
    "an exporter whose names are already taken is skipped whole without blocking the others" {
      val registry = spyk(PrometheusRegistry())
      PrometheusDsl.gauge(registry) { name("jvm_threads_peak").help("Taken") }

      SystemMetrics.initialize(enableThreadExports = true, enableClassLoadingExports = true, registry = registry)

      registry.sampleValue("jvm_classes_currently_loaded").shouldNotBeNull()
      val names = registry.metricNames()
      names shouldContain "jvm_threads_peak"
      names shouldNotContain "jvm_threads_current"
      names shouldNotContain "jvm_threads_daemon"

      // The rejected exporter counts as registered, so a later call does not try it again.
      shouldRegisterNothing(registry) {
        SystemMetrics.initialize(enableThreadExports = true, enableClassLoadingExports = true, registry = registry)
      }
    }

    "the two memory pool metric sets are registered independently" {
      val registry = PrometheusRegistry()
      PrometheusDsl.counter(registry) { name("jvm_memory_pool_allocated_bytes").help("Taken") }

      SystemMetrics.initialize(enableMemoryPoolsExports = true, registry = registry)

      seriesNames(registry.prometheusText()) shouldContainAll expectedSeries.getValue("memory")
    }

    "initialize with no exports enabled registers nothing" {
      val registry = PrometheusRegistry()
      SystemMetrics.initialize(registry = registry)

      registry.scrape().size() shouldBe 0
    }
  }
}
