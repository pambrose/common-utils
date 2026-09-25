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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.prometheus.metrics.model.registry.MultiCollector
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.MetricSnapshots

class AllOrNothingRegistrationTests : StringSpec() {
  // A MultiCollector exposing one gauge, so a scrape shows whether it is still registered.
  private fun multiCollector(name: String) =
    MultiCollector {
      MetricSnapshots.of(
        GaugeSnapshot
          .builder()
          .name(name)
          .dataPoint(GaugeSnapshot.GaugeDataPointSnapshot.builder().value(1.0).build())
          .build(),
      )
    }

  init {
    "everything the block registers stays registered when it completes" {
      val registry = PrometheusRegistry()
      val result =
        registry.registerAllOrNothing { tracking ->
          PrometheusDsl.counter(tracking) { name("aon_counter").help("h") }
          tracking.register(multiCollector("aon_multi"))
          "done"
        }

      result shouldBe "done"
      registry.metricNames() shouldBe setOf("aon_counter", "aon_multi")
    }

    "a failure unregisters the collectors and multi-collectors registered before it" {
      val registry = PrometheusRegistry()
      PrometheusDsl.gauge(registry) { name("aon_taken").help("h") }

      shouldThrow<IllegalArgumentException> {
        registry.registerAllOrNothing { tracking ->
          PrometheusDsl.counter(tracking) { name("aon_counter").help("h") }
          tracking.register(multiCollector("aon_multi"))
          PrometheusDsl.gauge(tracking) { name("aon_taken").help("h") }
        }
      }

      registry.metricNames() shouldBe setOf("aon_taken")
    }
  }
}
