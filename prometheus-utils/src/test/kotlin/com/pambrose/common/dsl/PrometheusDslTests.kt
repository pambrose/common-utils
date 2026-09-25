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

import com.pambrose.common.metrics.metricNames
import com.pambrose.common.metrics.sampleValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.prometheus.metrics.model.registry.PrometheusRegistry

class PrometheusDslTests : StringSpec() {
  init {
    "counter creation" {
      val registry = PrometheusRegistry()
      val counter =
        PrometheusDsl.counter(registry) {
          name("test_dsl_counter_creation")
          help("Test counter for DSL creation")
        }
      counter.get() shouldBe 0.0
      counter.inc()
      counter.get() shouldBe 1.0
      registry.sampleValue("test_dsl_counter_creation_total") shouldBe 1.0
    }

    "a counter named with _total is exposed under the same name" {
      val registry = PrometheusRegistry()
      PrometheusDsl
        .counter(registry) {
          name("test_dsl_counter_suffixed_total")
          help("Counter whose name already ends in _total")
        }.inc()
      registry.sampleValue("test_dsl_counter_suffixed_total") shouldBe 1.0
      registry.sampleValue("test_dsl_counter_suffixed_total_total") shouldBe null
    }

    "gauge creation" {
      val registry = PrometheusRegistry()
      val gauge =
        PrometheusDsl.gauge(registry) {
          name("test_dsl_gauge_creation")
          help("Test gauge for DSL creation")
        }
      gauge.get() shouldBe 0.0
      gauge.inc()
      gauge.get() shouldBe 1.0
      gauge.dec()
      gauge.get() shouldBe 0.0
      gauge.set(42.0)
      registry.sampleValue("test_dsl_gauge_creation") shouldBe 42.0
    }

    "summary creation" {
      val registry = PrometheusRegistry()
      val summary =
        PrometheusDsl.summary(registry) {
          name("test_dsl_summary_creation")
          help("Test summary for DSL creation")
          quantile(0.5, 0.01)
        }
      summary.observe(10.0)
      summary.observe(20.0)
      registry.sampleValue("test_dsl_summary_creation_count") shouldBe 2.0
      registry.sampleValue("test_dsl_summary_creation_sum") shouldBe 30.0
      registry.sampleValue("test_dsl_summary_creation", "quantile" to "0.5").shouldNotBeNull()
    }

    "histogram creation" {
      val registry = PrometheusRegistry()
      val histogram =
        PrometheusDsl.histogram(registry) {
          name("test_dsl_histogram_creation")
          help("Test histogram for DSL creation")
          classicUpperBounds(1.0, 5.0, 10.0, 50.0, 100.0)
        }
      histogram.observe(7.5)
      histogram.observe(25.0)
      registry.sampleValue("test_dsl_histogram_creation_count") shouldBe 2.0
      registry.sampleValue("test_dsl_histogram_creation_bucket", "le" to "10.0") shouldBe 1.0
      registry.sampleValue("test_dsl_histogram_creation_bucket", "le" to "+Inf") shouldBe 2.0
    }

    "counter with labels" {
      val registry = PrometheusRegistry()
      val counter =
        PrometheusDsl.counter(registry) {
          name("test_dsl_counter_with_labels")
          help("Test counter with labels")
          labelNames("method", "status")
        }
      counter.labelValues("GET", "200").inc()
      counter.labelValues("GET", "200").inc()
      counter.labelValues("POST", "500").inc()

      counter.labelValues("GET", "200").get() shouldBe 2.0
      registry.sampleValue(
        "test_dsl_counter_with_labels_total",
        "method" to "POST",
        "status" to "500",
      ) shouldBe 1.0
    }

    "each builder registers with the default registry when none is given" {
      val counter = PrometheusDsl.counter { name("default_dsl_counter").help("default counter") }
      val gauge = PrometheusDsl.gauge { name("default_dsl_gauge").help("default gauge") }
      val summary = PrometheusDsl.summary { name("default_dsl_summary").help("default summary") }
      val histogram = PrometheusDsl.histogram { name("default_dsl_histogram").help("default histogram") }
      val registry = PrometheusRegistry.defaultRegistry
      try {
        counter.inc()
        registry.sampleValue("default_dsl_counter_total") shouldBe 1.0
        registry.metricNames().shouldContainAll("default_dsl_gauge", "default_dsl_summary", "default_dsl_histogram")
      } finally {
        // Leave the default registry as it was, so a re-run in the same JVM can register the names again.
        listOf(counter, gauge, summary, histogram).forEach { registry.unregister(it) }
      }
    }

    "each builder can register with a given registry instead of the default one" {
      val registry = PrometheusRegistry()
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

      registry.sampleValue("isolated_dsl_counter_total") shouldBe 1.0
      registry.sampleValue("isolated_dsl_gauge") shouldBe 2.0
      registry.sampleValue("isolated_dsl_summary_sum") shouldBe 3.0
      registry.sampleValue("isolated_dsl_histogram_count") shouldBe 1.0
      PrometheusRegistry.defaultRegistry.metricNames() shouldNotContain "isolated_dsl_counter"
    }

    // Each builder registers the metric, so the registry rejects a name that is already in use.
    "a builder whose name is already registered throws IllegalArgumentException" {
      val registry = PrometheusRegistry()
      PrometheusDsl.counter(registry) {
        name("duplicate_dsl_metric")
        help("first")
      }

      listOf(
        { PrometheusDsl.counter(registry) { name("duplicate_dsl_metric").help("first") } },
        { PrometheusDsl.gauge(registry) { name("duplicate_dsl_metric").help("again") } },
        { PrometheusDsl.summary(registry) { name("duplicate_dsl_metric").help("again") } },
        { PrometheusDsl.histogram(registry) { name("duplicate_dsl_metric").help("again") } },
      ).forEach { register ->
        shouldThrow<IllegalArgumentException> { register() }.message shouldContain "duplicate_dsl_metric"
      }
    }

    // A gauge x_total would expose the same series name as a counter x, so the registry rejects it too.
    "a builder whose exposed series name collides with another metric's throws IllegalArgumentException" {
      val registry = PrometheusRegistry()
      PrometheusDsl.counter(registry) { name("colliding_dsl_metric").help("counter") }

      shouldThrow<IllegalArgumentException> {
        PrometheusDsl.gauge(registry) { name("colliding_dsl_metric_total").help("gauge") }
      }.message shouldContain "colliding_dsl_metric_total"
    }
  }
}
