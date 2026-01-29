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

package com.github.pambrose.common.dsl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class PrometheusDslTests {
  @Test
  fun counterCreationTest() {
    val counter = PrometheusDsl.counter {
      name("test_dsl_counter_creation")
      help("Test counter for DSL creation")
    }
    counter shouldNotBe null
    counter.get() shouldBe 0.0
    counter.inc()
    counter.get() shouldBe 1.0
  }

  @Test
  fun gaugeCreationTest() {
    val gauge = PrometheusDsl.gauge {
      name("test_dsl_gauge_creation")
      help("Test gauge for DSL creation")
    }
    gauge shouldNotBe null
    gauge.get() shouldBe 0.0
    gauge.inc()
    gauge.get() shouldBe 1.0
    gauge.dec()
    gauge.get() shouldBe 0.0
    gauge.set(42.0)
    gauge.get() shouldBe 42.0
  }

  @Test
  fun summaryCreationTest() {
    val summary = PrometheusDsl.summary {
      name("test_dsl_summary_creation")
      help("Test summary for DSL creation")
    }
    summary shouldNotBe null
    summary.observe(10.0)
    summary.observe(20.0)
    summary.get().count shouldBe 2
    summary.get().sum shouldBe 30.0
  }

  @Test
  fun histogramCreationTest() {
    val histogram = PrometheusDsl.histogram {
      name("test_dsl_histogram_creation")
      help("Test histogram for DSL creation")
      buckets(1.0, 5.0, 10.0, 50.0, 100.0)
    }
    histogram shouldNotBe null
    histogram.observe(7.5)
    histogram.observe(25.0)
    // Histogram doesn't have a get() method like Counter/Gauge/Summary
    // Values are collected via the Collector interface
  }

  @Test
  fun counterWithLabelsTest() {
    val counter = PrometheusDsl.counter {
      name("test_dsl_counter_with_labels")
      help("Test counter with labels")
      labelNames("method", "status")
    }
    counter shouldNotBe null
    val getSuccess = counter.labels("GET", "200")
    val postError = counter.labels("POST", "500")
    getSuccess.inc()
    getSuccess.inc()
    postError.inc()
    // Child objects track their own values
    getSuccess shouldNotBe null
    postError shouldNotBe null
  }
}
