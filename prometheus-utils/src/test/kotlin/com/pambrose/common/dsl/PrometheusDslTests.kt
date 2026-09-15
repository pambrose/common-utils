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

package com.pambrose.common.dsl

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.prometheus.client.CollectorRegistry

class PrometheusDslTests : StringSpec() {
  private val defaultRegistry = CollectorRegistry.defaultRegistry

  init {
    "counter creation" {
      val counter =
        PrometheusDsl.counter {
          name("test_dsl_counter_creation")
          help("Test counter for DSL creation")
        }
      counter.get() shouldBe 0.0
      counter.inc()
      counter.get() shouldBe 1.0
      defaultRegistry.getSampleValue("test_dsl_counter_creation_total") shouldBe 1.0
    }

    "gauge creation" {
      val gauge =
        PrometheusDsl.gauge {
          name("test_dsl_gauge_creation")
          help("Test gauge for DSL creation")
        }
      gauge.get() shouldBe 0.0
      gauge.inc()
      gauge.get() shouldBe 1.0
      gauge.dec()
      gauge.get() shouldBe 0.0
      gauge.set(42.0)
      defaultRegistry.getSampleValue("test_dsl_gauge_creation") shouldBe 42.0
    }

    "summary creation" {
      val summary =
        PrometheusDsl.summary {
          name("test_dsl_summary_creation")
          help("Test summary for DSL creation")
        }
      summary.observe(10.0)
      summary.observe(20.0)
      summary.get().count shouldBe 2
      summary.get().sum shouldBe 30.0
      defaultRegistry.getSampleValue("test_dsl_summary_creation_sum") shouldBe 30.0
    }

    "histogram creation" {
      val histogram =
        PrometheusDsl.histogram {
          name("test_dsl_histogram_creation")
          help("Test histogram for DSL creation")
          buckets(1.0, 5.0, 10.0, 50.0, 100.0)
        }
      histogram.observe(7.5)
      histogram.observe(25.0)
      defaultRegistry.getSampleValue("test_dsl_histogram_creation_count") shouldBe 2.0
      defaultRegistry.getSampleValue("test_dsl_histogram_creation_bucket", arrayOf("le"), arrayOf("10.0")) shouldBe 1.0
    }

    "counter with labels" {
      val counter =
        PrometheusDsl.counter {
          name("test_dsl_counter_with_labels")
          help("Test counter with labels")
          labelNames("method", "status")
        }
      counter.labels("GET", "200").inc()
      counter.labels("GET", "200").inc()
      counter.labels("POST", "500").inc()

      counter.labels("GET", "200").get() shouldBe 2.0
      defaultRegistry.getSampleValue(
        "test_dsl_counter_with_labels_total",
        arrayOf("method", "status"),
        arrayOf("POST", "500"),
      ) shouldBe 1.0
    }

    "each builder can register with a given registry instead of the default one" {
      val registry = CollectorRegistry()
      PrometheusDsl
        .counter(registry) {
          name("isolated_dsl_counter")
          help("isolated counter")
        }.inc()
      PrometheusDsl
        .gauge(registry) {
          name("isolated_dsl_gauge")
          help("isolated gauge")
        }.set(2.0)
      PrometheusDsl
        .summary(registry) {
          name("isolated_dsl_summary")
          help("isolated summary")
        }.observe(3.0)
      PrometheusDsl
        .histogram(registry) {
          name("isolated_dsl_histogram")
          help("isolated histogram")
        }.observe(4.0)

      registry.getSampleValue("isolated_dsl_counter_total") shouldBe 1.0
      registry.getSampleValue("isolated_dsl_gauge") shouldBe 2.0
      registry.getSampleValue("isolated_dsl_summary_sum") shouldBe 3.0
      registry.getSampleValue("isolated_dsl_histogram_count") shouldBe 1.0
      defaultRegistry.getSampleValue("isolated_dsl_counter_total") shouldBe null
    }
  }
}
