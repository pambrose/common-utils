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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.prometheus.metrics.model.registry.MetricType
import io.prometheus.metrics.model.registry.PrometheusRegistry

class SamplerGaugeCollectorTests : StringSpec() {
  init {
    "sampler gauge collector creation" {
      var value = 42.0
      val collector = SamplerGaugeCollector(
        name = "test_sampler_gauge_creation",
        help = "Test sampler gauge collector",
        registry = PrometheusRegistry(),
        data = { value },
      )
      val snapshot = collector.collect()
      snapshot.metadata.prometheusName shouldBe "test_sampler_gauge_creation"
      snapshot.metadata.help shouldBe "Test sampler gauge collector"
      collector.metricFamilyDescriptor?.type shouldBe MetricType.GAUGE
      snapshot.dataPoints shouldHaveSize 1
      snapshot.dataPoints[0].value shouldBe 42.0

      value = 100.0
      collector.collect().dataPoints[0].value shouldBe 100.0
    }

    "sampler gauge collector with labels" {
      val registry = PrometheusRegistry()
      val collector = SamplerGaugeCollector(
        name = "test_sampler_gauge_with_labels",
        help = "Test sampler gauge with labels",
        registry = registry,
        labelNames = ["region", "instance"],
        labelValues = ["us-east-1", "i-12345"],
        data = { 55.5 },
      )
      val dataPoints = collector.collect().dataPoints
      dataPoints shouldHaveSize 1
      dataPoints[0].labels.get("region") shouldBe "us-east-1"
      dataPoints[0].labels.get("instance") shouldBe "i-12345"
      dataPoints[0].value shouldBe 55.5
      registry.sampleValue(
        "test_sampler_gauge_with_labels",
        "region" to "us-east-1",
        "instance" to "i-12345",
      ) shouldBe 55.5
    }

    "rejects mismatched label names and values at construction" {
      val ex = shouldThrow<IllegalArgumentException> {
        SamplerGaugeCollector(
          name = "test_sampler_gauge_mismatch",
          help = "mismatched labels",
          registry = PrometheusRegistry(),
          labelNames = ["region", "instance"],
          labelValues = ["us-east-1"],
          data = { 1.0 },
        )
      }
      ex.message shouldContain "label"
    }

    "sampler gauge collector dynamic value" {
      var counter = 0
      val collector = SamplerGaugeCollector(
        name = "test_sampler_gauge_dynamic",
        help = "Test dynamic sampler gauge",
        registry = PrometheusRegistry(),
        data = { (++counter).toDouble() },
      )
      // The data lambda is called on each collect(), so values should increment
      val value1 = collector.collect().dataPoints[0].value
      val value2 = collector.collect().dataPoints[0].value
      val value3 = collector.collect().dataPoints[0].value
      (value2 - value1) shouldBe 1.0
      (value3 - value2) shouldBe 1.0
    }

    "a throwing sampler reports NaN instead of aborting the scrape" {
      val registry = PrometheusRegistry()
      val collector = SamplerGaugeCollector(
        name = "test_sampler_gauge_throws",
        help = "throwing sampler",
        registry = registry,
        data = { throw RuntimeException("boom") },
      )
      SamplerGaugeCollector(name = "test_sampler_gauge_healthy", help = "healthy", registry = registry) { 5.0 }

      // collect() must not propagate the sampler's exception (that would take down the whole scrape).
      collector.collect().dataPoints[0].value.isNaN() shouldBe true
      // A scrape of the registry still succeeds and still carries the other metrics.
      registry.sampleValue("test_sampler_gauge_throws")?.isNaN() shouldBe true
      registry.sampleValue("test_sampler_gauge_healthy") shouldBe 5.0
    }

    "the sampler does not run when the collector is constructed and registered" {
      val sampler = mockk<() -> Double>()
      every { sampler() } returns 7.0
      val registry = PrometheusRegistry()

      SamplerGaugeCollector(
        name = "test_sampler_gauge_lazy",
        help = "lazy sampler",
        registry = registry,
        data = sampler,
      )
      verify(exactly = 0) { sampler() }

      // A rejected duplicate is detected from the descriptor as well, without sampling either collector.
      shouldThrow<IllegalArgumentException> {
        SamplerGaugeCollector(
          name = "test_sampler_gauge_lazy",
          help = "lazy sampler",
          registry = registry,
          data = sampler,
        )
      }
      verify(exactly = 0) { sampler() }

      // It is still registered: scraping the registry runs the sampler.
      registry.sampleValue("test_sampler_gauge_lazy") shouldBe 7.0
      verify(exactly = 1) { sampler() }
    }

    "an unregistered collector is no longer scraped and its name can be registered again" {
      var sampled = 0
      val registry = PrometheusRegistry()
      val collector =
        SamplerGaugeCollector(name = "test_sampler_gauge_unregister", help = "h", registry = registry) {
          sampled++
          1.0
        }
      registry.metricNames() shouldContain "test_sampler_gauge_unregister"
      sampled shouldBe 1

      registry.unregister(collector)

      // The scrape no longer includes it, so it no longer runs the sampler.
      registry.metricNames() shouldNotContain "test_sampler_gauge_unregister"
      sampled shouldBe 1
      SamplerGaugeCollector(name = "test_sampler_gauge_unregister", help = "h", registry = registry) { 2.0 }
      registry.sampleValue("test_sampler_gauge_unregister") shouldBe 2.0
    }

    "a second collector with the same name and label names is rejected even with other label values" {
      val registry = PrometheusRegistry()
      SamplerGaugeCollector("test_sampler_gauge_dup", "h", ["node"], ["a"], registry) { 1.0 }

      shouldThrow<IllegalArgumentException> {
        SamplerGaugeCollector("test_sampler_gauge_dup", "h", ["node"], ["b"], registry) { 2.0 }
      }.message shouldContain "test_sampler_gauge_dup"
    }

    // Names are validated as the 1.x client defines them: any non-empty UTF-8 metric name, and a label name that is
    // valid UTF-8 without a reserved "__" prefix. Repeated label names are rejected too.
    "invalid or repeated metric and label names are rejected at construction" {
      listOf(
        { SamplerGaugeCollector(name = "", help = "h", registry = PrometheusRegistry()) { 1.0 } },
        { SamplerGaugeCollector("q", "h", ["__x"], ["x"], PrometheusRegistry()) { 1.0 } },
        { SamplerGaugeCollector("q", "h", [""], ["x"], PrometheusRegistry()) { 1.0 } },
        { SamplerGaugeCollector("q", "h", ["a", "a"], ["x", "y"], PrometheusRegistry()) { 1.0 } },
        { SamplerGaugeCollector("q", "h", ["a.b", "a_b"], ["x", "y"], PrometheusRegistry()) { 1.0 } },
      ).forEach { create -> shouldThrow<IllegalArgumentException> { create() } }
    }

    "a construction that fails validation registers nothing" {
      val registry = PrometheusRegistry()
      shouldThrow<IllegalArgumentException> {
        SamplerGaugeCollector("test_sampler_gauge_invalid", "h", ["a", "a"], ["x", "y"], registry) { 1.0 }
      }
      registry.scrape().size() shouldBe 0
    }

    "the constructors without a registry register with the default one" {
      val labelled = SamplerGaugeCollector("test_sampler_gauge_default_labelled", "h", ["a"], ["x"]) { 1.0 }
      val unlabelled = SamplerGaugeCollector("test_sampler_gauge_default_plain", "h") { 2.0 }
      val registry = PrometheusRegistry.defaultRegistry
      try {
        registry.sampleValue("test_sampler_gauge_default_labelled", "a" to "x") shouldBe 1.0
        registry.sampleValue("test_sampler_gauge_default_plain") shouldBe 2.0
      } finally {
        // Leave the default registry as it was, so a re-run in the same JVM can register the names again.
        registry.unregister(labelled)
        registry.unregister(unlabelled)
      }
    }

    "the collector can register with a given registry instead of the default one" {
      val registry = PrometheusRegistry()
      SamplerGaugeCollector(name = "test_sampler_gauge_isolated", help = "isolated", registry = registry) { 3.0 }

      registry.sampleValue("test_sampler_gauge_isolated") shouldBe 3.0
      PrometheusRegistry.defaultRegistry.metricNames() shouldNotContain "test_sampler_gauge_isolated"
    }
  }
}
