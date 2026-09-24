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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry

class SamplerGaugeCollectorTests : StringSpec() {
  init {
    "sampler gauge collector creation" {
      var value = 42.0
      val collector = SamplerGaugeCollector(
        name = "test_sampler_gauge_creation",
        help = "Test sampler gauge collector",
        registry = CollectorRegistry(),
        data = { value },
      )
      collector shouldNotBe null
      val samples = collector.collect()
      samples shouldHaveSize 1
      samples[0].name shouldBe "test_sampler_gauge_creation"
      samples[0].type shouldBe Collector.Type.GAUGE
      samples[0].samples[0].value shouldBe 42.0

      value = 100.0
      val updatedSamples = collector.collect()
      updatedSamples[0].samples[0].value shouldBe 100.0
    }

    "sampler gauge collector with labels" {
      val collector = SamplerGaugeCollector(
        name = "test_sampler_gauge_with_labels",
        help = "Test sampler gauge with labels",
        registry = CollectorRegistry(),
        labelNames = ["region", "instance"],
        labelValues = ["us-east-1", "i-12345"],
        data = { 55.5 },
      )
      val samples = collector.collect()
      samples shouldHaveSize 1
      samples[0].samples[0].labelNames shouldBe ["region", "instance"]
      samples[0].samples[0].labelValues shouldBe ["us-east-1", "i-12345"]
      samples[0].samples[0].value shouldBe 55.5
    }

    "rejects mismatched label names and values at construction" {
      // Previously the mismatch was undetected until MetricFamilySamples.Sample threw on every scrape.
      val ex = shouldThrow<IllegalArgumentException> {
        SamplerGaugeCollector(
          name = "test_sampler_gauge_mismatch",
          help = "mismatched labels",
          registry = CollectorRegistry(),
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
        registry = CollectorRegistry(),
        data = { (++counter).toDouble() },
      )
      // The data lambda is called on each collect(), so values should increment
      val value1 = collector.collect()[0].samples[0].value
      val value2 = collector.collect()[0].samples[0].value
      val value3 = collector.collect()[0].samples[0].value
      // Each collect() should increment the counter
      (value2 - value1) shouldBe 1.0
      (value3 - value2) shouldBe 1.0
    }

    "a throwing sampler reports NaN instead of aborting the scrape" {
      val collector = SamplerGaugeCollector(
        name = "test_sampler_gauge_throws",
        help = "throwing sampler",
        registry = CollectorRegistry(),
        data = { throw RuntimeException("boom") },
      )
      // collect() must not propagate the sampler's exception (that would take down the whole scrape).
      val samples = collector.collect()
      samples[0].samples[0].value.isNaN() shouldBe true
    }

    "the sampler does not run when the collector is constructed and registered" {
      var sampled = 0
      val collector =
        SamplerGaugeCollector(name = "test_sampler_gauge_lazy", help = "lazy sampler") {
          sampled++
          7.0
        }
      try {
        sampled shouldBe 0

        // It is still registered: scraping the default registry runs the sampler.
        CollectorRegistry.defaultRegistry.getSampleValue("test_sampler_gauge_lazy") shouldBe 7.0
        sampled shouldBe 1
      } finally {
        // Leave the default registry as it was, so a re-run in the same JVM can register the name again.
        CollectorRegistry.defaultRegistry.unregister(collector)
      }
    }

    // The registry and the text format never check names, so an invalid one made Prometheus reject the whole scrape.
    "invalid or repeated metric and label names are rejected at construction" {
      listOf(
        { SamplerGaugeCollector(name = "queue-depth", help = "h", registry = CollectorRegistry()) { 1.0 } },
        { SamplerGaugeCollector("q", "h", ["__x"], ["x"], CollectorRegistry()) { 1.0 } },
        { SamplerGaugeCollector("q", "h", ["a", "a"], ["x", "y"], CollectorRegistry()) { 1.0 } },
      ).forEach { create -> shouldThrow<IllegalArgumentException> { create() } }
    }

    "the constructors without a registry register with the default one" {
      val labelled = SamplerGaugeCollector("test_sampler_gauge_default_labelled", "h", ["a"], ["x"]) { 1.0 }
      val unlabelled = SamplerGaugeCollector(name = "test_sampler_gauge_default_plain", help = "h", labelNames = []) {
        2.0
      }
      try {
        val registry = CollectorRegistry.defaultRegistry
        registry.getSampleValue("test_sampler_gauge_default_labelled", arrayOf("a"), arrayOf("x")) shouldBe 1.0
        registry.getSampleValue("test_sampler_gauge_default_plain") shouldBe 2.0
      } finally {
        CollectorRegistry.defaultRegistry.unregister(labelled)
        CollectorRegistry.defaultRegistry.unregister(unlabelled)
      }
    }

    "the collector can register with a given registry instead of the default one" {
      val registry = CollectorRegistry()
      SamplerGaugeCollector(name = "test_sampler_gauge_isolated", help = "isolated", registry = registry) { 3.0 }

      registry.getSampleValue("test_sampler_gauge_isolated") shouldBe 3.0
      CollectorRegistry.defaultRegistry.getSampleValue("test_sampler_gauge_isolated") shouldBe null
    }
  }
}
