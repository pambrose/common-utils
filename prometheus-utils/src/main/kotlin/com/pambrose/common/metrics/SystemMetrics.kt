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
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.ClassLoadingExports
import io.prometheus.client.hotspot.GarbageCollectorExports
import io.prometheus.client.hotspot.MemoryPoolsExports
import io.prometheus.client.hotspot.StandardExports
import io.prometheus.client.hotspot.ThreadExports
import io.prometheus.client.hotspot.VersionInfoExports
import java.util.WeakHashMap

/**
 * Registers Prometheus JVM hotspot metric exporters.
 *
 * Calling [initialize] multiple times is safe; each exporter is registered at most once per registry.
 */
object SystemMetrics {
  private val logger = KotlinLogging.logger {}

  // The exporters registered so far, per registry, so a repeat call registers only the newly requested ones.
  private val registeredExporters = WeakHashMap<CollectorRegistry, MutableSet<String>>()

  /**
   * Registers the selected Prometheus JVM hotspot metric exporters with [registry].
   *
   * This method is synchronized and safe to call repeatedly: an exporter already registered by an earlier call is
   * skipped, and one requested for the first time is registered. An exporter whose metrics another collector
   * already provides, such as one registered by `DefaultExports.initialize()`, is skipped with a warning instead
   * of failing the call.
   *
   * @param enableStandardExports whether to register standard JMX metrics (process CPU, open file descriptors, etc.).
   * @param enableMemoryPoolsExports whether to register memory pool JMX metrics.
   * @param enableGarbageCollectorExports whether to register garbage collector JMX metrics.
   * @param enableThreadExports whether to register thread JMX metrics.
   * @param enableClassLoadingExports whether to register class loading JMX metrics.
   * @param enableVersionInfoExports whether to register JVM version info metrics.
   * @param registry the registry to register with. Defaults to [CollectorRegistry.defaultRegistry].
   */
  @Synchronized
  @JvmOverloads
  fun initialize(
    enableStandardExports: Boolean = false,
    enableMemoryPoolsExports: Boolean = false,
    enableGarbageCollectorExports: Boolean = false,
    enableThreadExports: Boolean = false,
    enableClassLoadingExports: Boolean = false,
    enableVersionInfoExports: Boolean = false,
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
  ) {
    val registered = registeredExporters.getOrPut(registry) { mutableSetOf() }

    fun register(
      enabled: Boolean,
      description: String,
      exporter: () -> Collector,
    ) {
      if (enabled && description !in registered) {
        logger.info { "Enabling $description metrics" }
        when (val failure = runCatching { exporter().register<Collector>(registry) }.exceptionOrNull()) {
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

    register(enableStandardExports, "standard JMX") { StandardExports() }
    register(enableMemoryPoolsExports, "memory pool JMX") { MemoryPoolsExports() }
    register(enableGarbageCollectorExports, "garbage collector JMX") { GarbageCollectorExports() }
    register(enableThreadExports, "thread JMX") { ThreadExports() }
    register(enableClassLoadingExports, "class loading JMX") { ClassLoadingExports() }
    register(enableVersionInfoExports, "version info") { VersionInfoExports() }
  }
}
