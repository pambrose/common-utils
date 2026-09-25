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

import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.core.metrics.Histogram
import io.prometheus.metrics.core.metrics.Summary
import io.prometheus.metrics.model.registry.PrometheusRegistry

/**
 * Provides a Kotlin DSL for building and registering Prometheus metric collectors.
 *
 * Each factory function builds the metric with `X.builder()`, applies the configuration lambda to that Prometheus
 * Java client 1.x builder, and registers the metric with the given [PrometheusRegistry], which defaults to
 * [PrometheusRegistry.defaultRegistry].
 *
 * The receivers are the 1.x builders from `io.prometheus.metrics.core.metrics`: label names are declared with
 * `labelNames(...)` and a labelled child is reached with `labelValues(...)` on the returned metric. The builders have
 * no `namespace()` or `subsystem()`, so put the full name in `name(...)`. A counter is exposed as `<name>_total`
 * whether or not [name][Counter.Builder.name] already ends in `_total`.
 *
 * Registration fails with [IllegalArgumentException] when the registry already holds a metric whose exposed series
 * names collide with the new one (for example the same name and label names, or a counter `x` and a gauge
 * `x_total`).
 */
object PrometheusDsl {
  /**
   * Creates, configures, and registers a Prometheus [Counter].
   *
   * @param registry the registry to register the counter with.
   * @param block a lambda with [Counter.Builder] as receiver for configuring name, help, and label names.
   * @return the registered [Counter] instance.
   */
  @JvmOverloads
  fun counter(
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
    block: Counter.Builder.() -> Unit,
  ): Counter = Counter.builder().apply(block).register(registry)

  /**
   * Creates, configures, and registers a Prometheus [Summary].
   *
   * @param registry the registry to register the summary with.
   * @param block a lambda with [Summary.Builder] as receiver for configuring name, help, quantiles
   *   (`quantile(...)`), and label names.
   * @return the registered [Summary] instance.
   */
  @JvmOverloads
  fun summary(
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
    block: Summary.Builder.() -> Unit,
  ): Summary = Summary.builder().apply(block).register(registry)

  /**
   * Creates, configures, and registers a Prometheus [Gauge].
   *
   * @param registry the registry to register the gauge with.
   * @param block a lambda with [Gauge.Builder] as receiver for configuring name, help, and label names.
   * @return the registered [Gauge] instance.
   */
  @JvmOverloads
  fun gauge(
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
    block: Gauge.Builder.() -> Unit,
  ): Gauge = Gauge.builder().apply(block).register(registry)

  /**
   * Creates, configures, and registers a Prometheus [Histogram].
   *
   * @param registry the registry to register the histogram with.
   * @param block a lambda with [Histogram.Builder] as receiver for configuring name, help, buckets
   *   (`classicUpperBounds(...)`), and label names.
   * @return the registered [Histogram] instance.
   */
  @JvmOverloads
  fun histogram(
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
    block: Histogram.Builder.() -> Unit,
  ): Histogram = Histogram.builder().apply(block).register(registry)
}
