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

import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.metrics.instrumentation.jvm.JvmBufferPoolMetrics
import io.prometheus.metrics.instrumentation.jvm.JvmClassLoadingMetrics
import io.prometheus.metrics.instrumentation.jvm.JvmCompilationMetrics
import io.prometheus.metrics.instrumentation.jvm.JvmGarbageCollectorMetrics
import io.prometheus.metrics.instrumentation.jvm.JvmMemoryMetrics
import io.prometheus.metrics.instrumentation.jvm.JvmMemoryPoolAllocationMetrics
import io.prometheus.metrics.instrumentation.jvm.JvmNativeMemoryMetrics
import io.prometheus.metrics.instrumentation.jvm.JvmRuntimeInfoMetric
import io.prometheus.metrics.instrumentation.jvm.JvmThreadsMetrics
import io.prometheus.metrics.instrumentation.jvm.ProcessMetrics
import io.prometheus.metrics.model.registry.PrometheusRegistry
import java.util.WeakHashMap

/**
 * Registers the Prometheus Java client's JVM and process metrics (`prometheus-metrics-instrumentation-jvm`).
 *
 * Calling [initialize] multiple times is safe; each metric set is registered at most once per registry.
 */
object SystemMetrics {
  private val logger = KotlinLogging.logger {}

  // The metric sets registered so far, per registry, so a repeat call registers only the newly requested ones.
  private val registeredSets = WeakHashMap<PrometheusRegistry, MutableSet<String>>()

  // Each registration of the client's JvmMemoryPoolAllocationMetrics adds a GC notification listener, holding a counter
  // of its own, to every GarbageCollectorMXBean, and nothing ever removes it: the listener class is package-private,
  // register() returns no handle, and unregistering or clearing only drops the counter from the registry. Registering
  // the set once per registry would leave another listener on the GC beans for the rest of the JVM's life after every
  // clear() and forget() cycle and for every isolated registry. So it is registered once per process, here, and every
  // registry that asks for it gets these same collectors. A failed registration is not kept, so the next call retries.
  private val sharedAllocationMetrics by lazy {
    TrackingRegistry(PrometheusRegistry()).also { JvmMemoryPoolAllocationMetrics.builder().register(it) }
  }

  /**
   * Registers the selected JVM and process metric sets with [registry].
   *
   * This method is synchronized and safe to call repeatedly: a metric set already registered by an earlier call is
   * skipped, and one requested for the first time is registered. Registrations are tracked per registry, so a metric
   * set later removed with [PrometheusRegistry.clear] is not registered again until [forget] is called for that
   * registry.
   *
   * Each metric set is registered all or nothing. The client's `register(registry)` for a set adds its metrics one
   * at a time and returns no handle, so a name clash part-way through would leave the earlier metrics registered and
   * impossible to remove. Each set therefore registers through a wrapper that passes every registration on to
   * [registry] and remembers it; if one is rejected, those added before it are unregistered again.
   *
   * A metric set whose names another collector already provides, such as one registered by
   * `JvmMetrics.builder().register()`, is skipped with a warning instead of failing the call, and is not tried again.
   * Any other registration failure is logged and retried by the next call.
   *
   * @param enableStandardExports whether to register the process metrics (`ProcessMetrics`: CPU time, start time,
   *   open and max file descriptors, and, on Linux, virtual and resident memory).
   * @param enableMemoryPoolsExports whether to register the JVM memory metrics (`JvmMemoryMetrics`) and the per-pool
   *   allocation counter (`JvmMemoryPoolAllocationMetrics`), each as its own all-or-nothing set. The client updates
   *   the allocation counter from a GC notification listener that it never removes, so the counter is created once
   *   per process, with a single listener, and every registry gets that same counter. It counts from the first
   *   registration in the JVM: a registry that registers it later, or again after a [PrometheusRegistry.clear] and
   *   [forget], shows the running total rather than starting from zero.
   * @param enableGarbageCollectorExports whether to register the garbage collector metrics
   *   (`JvmGarbageCollectorMetrics`).
   * @param enableThreadExports whether to register the thread metrics (`JvmThreadsMetrics`).
   * @param enableClassLoadingExports whether to register the class loading metrics (`JvmClassLoadingMetrics`).
   * @param enableVersionInfoExports whether to register the JVM runtime info metric (`JvmRuntimeInfoMetric`).
   * @param enableBufferPoolExports whether to register the buffer pool metrics (`JvmBufferPoolMetrics`).
   * @param enableCompilationExports whether to register the JIT compilation metrics (`JvmCompilationMetrics`).
   * @param enableNativeMemoryExports whether to register the native memory metrics (`JvmNativeMemoryMetrics`). They
   *   are only present when the JVM runs with `-XX:NativeMemoryTracking=summary` (or `detail`); otherwise the set
   *   registers nothing.
   * @param registry the registry to register with. Defaults to [PrometheusRegistry.defaultRegistry].
   */
  @Synchronized
  @JvmOverloads
  @Suppress("LongParameterList")
  fun initialize(
    enableStandardExports: Boolean = false,
    enableMemoryPoolsExports: Boolean = false,
    enableGarbageCollectorExports: Boolean = false,
    enableThreadExports: Boolean = false,
    enableClassLoadingExports: Boolean = false,
    enableVersionInfoExports: Boolean = false,
    enableBufferPoolExports: Boolean = false,
    enableCompilationExports: Boolean = false,
    enableNativeMemoryExports: Boolean = false,
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
  ) {
    val registered = registeredSets.getOrPut(registry) { mutableSetOf() }

    fun register(
      enabled: Boolean,
      description: String,
      registerSet: (PrometheusRegistry) -> Unit,
    ) {
      if (enabled && description !in registered) {
        logger.info { "Enabling $description metrics" }
        when (val failure = runCatching { registry.registerAllOrNothing(registerSet) }.exceptionOrNull()) {
          null -> {
            registered += description
          }

          // The registry rejects metric names another collector already provides, so these metrics are already
          // exported and retrying would only fail again.
          is IllegalArgumentException -> {
            registered += description
            logger.warn { "Skipping $description metrics: ${failure.message}" }
          }

          else -> {
            logger.warn(failure) { "Could not register $description metrics; a later call will retry" }
          }
        }
      }
    }

    register(enableStandardExports, "process") { ProcessMetrics.builder().register(it) }
    register(enableMemoryPoolsExports, "JVM memory") { JvmMemoryMetrics.builder().register(it) }
    register(enableMemoryPoolsExports, "JVM memory pool allocation") { sharedAllocationMetrics.registerAllWith(it) }
    register(enableGarbageCollectorExports, "JVM garbage collector") {
      JvmGarbageCollectorMetrics.builder().register(it)
    }
    register(enableThreadExports, "JVM thread") { JvmThreadsMetrics.builder().register(it) }
    register(enableClassLoadingExports, "JVM class loading") { JvmClassLoadingMetrics.builder().register(it) }
    register(enableVersionInfoExports, "JVM runtime info") { JvmRuntimeInfoMetric.builder().register(it) }
    register(enableBufferPoolExports, "JVM buffer pool") { JvmBufferPoolMetrics.builder().register(it) }
    register(enableCompilationExports, "JVM compilation") { JvmCompilationMetrics.builder().register(it) }
    register(enableNativeMemoryExports, "JVM native memory") { JvmNativeMemoryMetrics.builder().register(it) }
  }

  /**
   * Forgets which metric sets [initialize] registered with [registry], so the next call registers them again.
   *
   * Call it after [PrometheusRegistry.clear] on a registry that is reused, as a test harness does between specs.
   * Without it, the cleared metric sets are never registered with that registry again. It does not unregister
   * anything, so calling it while the sets are still registered makes the next [initialize] skip them with a warning,
   * since their names are taken. What was registered with other registries is kept.
   *
   * Repeated clear and forget cycles leave nothing behind but one thing that is not per registry: the GC notification
   * listener behind the allocation counter of `enableMemoryPoolsExports`, which is attached once per process and stays
   * attached for the life of the JVM, however many registries use the counter.
   *
   * @param registry the registry to forget. Defaults to [PrometheusRegistry.defaultRegistry].
   */
  @Synchronized
  @JvmOverloads
  fun forget(registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry) {
    registeredSets.remove(registry)
  }
}
