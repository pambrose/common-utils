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

package com.pambrose.common.service

import com.pambrose.common.dsl.PrometheusDsl
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.prometheus.metrics.expositionformats.OpenMetricsTextFormatWriter
import io.prometheus.metrics.model.registry.PrometheusRegistry

class MetricsServiceTests : StringSpec() {
  init {
    "a plain GET with no Accept header returns the Prometheus text format" {
      val registry = PrometheusRegistry()
      PrometheusDsl.counter(registry) {
        name("metrics_service_plain_text")
        help("Plain-text format check")
      }.inc()

      val service = MetricsService(0, "metrics", LOOPBACK, registry)
      service.whileRunning {
        val response = httpGet(service.boundPort, "/metrics")
        response.headers().firstValue("Content-Type").orElse("") shouldStartWith "text/plain; version=0.0.4"
        response.body() shouldContain "metrics_service_plain_text_total"
      }
    }

    "a GET with an OpenMetrics Accept header returns the OpenMetrics text format" {
      val registry = PrometheusRegistry()
      PrometheusDsl.counter(registry) {
        name("metrics_service_openmetrics")
        help("OpenMetrics format check")
      }.inc()

      val service = MetricsService(0, "metrics", LOOPBACK, registry)
      service.whileRunning {
        val response =
          httpGet(service.boundPort, "/metrics", headers = mapOf("Accept" to OpenMetricsTextFormatWriter.CONTENT_TYPE))
        response.headers().firstValue("Content-Type").orElse("") shouldStartWith "application/openmetrics-text"
        response.body() shouldContain "metrics_service_openmetrics_total"
      }
    }

    "a non-default registry passed to MetricsService is the one served" {
      val customRegistry = PrometheusRegistry()
      PrometheusDsl.counter(customRegistry) {
        name("metrics_service_custom_registry")
        help("Only registered on the custom registry")
      }.inc()

      val defaultOnlyCounter =
        PrometheusDsl.counter {
          name("metrics_service_default_registry_leak_check")
          help("Registered only on the default registry")
        }
      try {
        val service = MetricsService(0, "metrics", LOOPBACK, customRegistry)
        service.whileRunning {
          val body = httpGet(service.boundPort, "/metrics").body()
          body shouldContain "metrics_service_custom_registry_total"
          // A metric registered only on the default registry does not leak through the custom one.
          body shouldNotContain "metrics_service_default_registry_leak_check"
        }
      } finally {
        PrometheusRegistry.defaultRegistry.unregister(defaultOnlyCounter)
      }
    }

    "constructing with only port, path, and a trailing initBlock defaults to the default registry" {
      var initRan = false
      val counter =
        PrometheusDsl.counter {
          name("metrics_service_default_registry_trailing_lambda")
          help("Registered on the default registry to confirm it is the one served")
        }
      counter.inc()
      try {
        val service = MetricsService(0, "metrics", LOOPBACK) { initRan = true }
        initRan shouldBe true
        service.whileRunning {
          val body = httpGet(service.boundPort, "/metrics").body()
          body shouldContain "metrics_service_default_registry_trailing_lambda_total"
        }
      } finally {
        PrometheusRegistry.defaultRegistry.unregister(counter)
      }
    }
  }
}
