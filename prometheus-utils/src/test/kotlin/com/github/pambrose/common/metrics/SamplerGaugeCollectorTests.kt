/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.metrics

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.prometheus.client.Collector
import org.junit.jupiter.api.Test

class SamplerGaugeCollectorTests {
  @Test
  fun samplerGaugeCollectorCreationTest() {
    var value = 42.0
    val collector = SamplerGaugeCollector(
      name = "test_sampler_gauge_creation",
      help = "Test sampler gauge collector",
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

  @Test
  fun samplerGaugeCollectorWithLabelsTest() {
    val collector = SamplerGaugeCollector(
      name = "test_sampler_gauge_with_labels",
      help = "Test sampler gauge with labels",
      labelNames = listOf("region", "instance"),
      labelValues = listOf("us-east-1", "i-12345"),
      data = { 55.5 },
    )
    val samples = collector.collect()
    samples shouldHaveSize 1
    samples[0].samples[0].labelNames shouldBe listOf("region", "instance")
    samples[0].samples[0].labelValues shouldBe listOf("us-east-1", "i-12345")
    samples[0].samples[0].value shouldBe 55.5
  }

  @Test
  fun samplerGaugeCollectorDynamicValueTest() {
    var counter = 0
    val collector = SamplerGaugeCollector(
      name = "test_sampler_gauge_dynamic",
      help = "Test dynamic sampler gauge",
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
}
