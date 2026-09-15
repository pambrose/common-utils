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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.ThreadExports

class SystemMetricsTests : StringSpec() {
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

      val registry = CollectorRegistry.defaultRegistry
      registry.getSampleValue("process_cpu_seconds_total").shouldNotBeNull()
      registry.getSampleValue("jvm_threads_current").shouldNotBeNull()
      registry.getSampleValue("jvm_classes_currently_loaded").shouldNotBeNull()
    }

    "calling initialize again with the same exporters is safe" {
      val registry = CollectorRegistry(true)
      SystemMetrics.initialize(enableThreadExports = true, registry = registry)
      SystemMetrics.initialize(enableThreadExports = true, registry = registry)

      registry.getSampleValue("jvm_threads_current").shouldNotBeNull()
    }

    "a later call registers exporters that were not requested before" {
      val registry = CollectorRegistry(true)
      SystemMetrics.initialize(enableThreadExports = true, registry = registry)
      registry.getSampleValue("jvm_classes_currently_loaded") shouldBe null

      SystemMetrics.initialize(enableThreadExports = true, enableClassLoadingExports = true, registry = registry)

      registry.getSampleValue("jvm_classes_currently_loaded").shouldNotBeNull()
    }

    "an exporter already registered elsewhere is skipped without blocking the others" {
      val registry = CollectorRegistry(true)
      ThreadExports().register<Collector>(registry)

      SystemMetrics.initialize(enableThreadExports = true, enableClassLoadingExports = true, registry = registry)

      registry.getSampleValue("jvm_classes_currently_loaded").shouldNotBeNull()
    }

    "initialize with no exports enabled registers nothing" {
      val registry = CollectorRegistry(true)
      SystemMetrics.initialize(registry = registry)

      registry.metricFamilySamples().hasMoreElements() shouldBe false
    }
  }
}
