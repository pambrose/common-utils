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

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary

/**
 * Provides a Kotlin DSL for building and registering Prometheus metric collectors.
 *
 * Each factory function accepts a configuration lambda for the metric's builder and registers the metric
 * with the given [CollectorRegistry], which defaults to [CollectorRegistry.defaultRegistry].
 */
object PrometheusDsl {
  /**
   * Creates, configures, and registers a Prometheus [Counter].
   *
   * @param registry the registry to register the counter with.
   * @param block a lambda with [Counter.Builder] as receiver for configuring name, help, and labels.
   * @return the registered [Counter] instance.
   */
  @JvmOverloads
  fun counter(
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
    block: Counter.Builder.() -> Unit,
  ): Counter = Counter.build().apply(block).register(registry)

  /**
   * Creates, configures, and registers a Prometheus [Summary].
   *
   * @param registry the registry to register the summary with.
   * @param block a lambda with [Summary.Builder] as receiver for configuring name, help, quantiles, and labels.
   * @return the registered [Summary] instance.
   */
  @JvmOverloads
  fun summary(
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
    block: Summary.Builder.() -> Unit,
  ): Summary = Summary.build().apply(block).register(registry)

  /**
   * Creates, configures, and registers a Prometheus [Gauge].
   *
   * @param registry the registry to register the gauge with.
   * @param block a lambda with [Gauge.Builder] as receiver for configuring name, help, and labels.
   * @return the registered [Gauge] instance.
   */
  @JvmOverloads
  fun gauge(
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
    block: Gauge.Builder.() -> Unit,
  ): Gauge = Gauge.build().apply(block).register(registry)

  /**
   * Creates, configures, and registers a Prometheus [Histogram].
   *
   * @param registry the registry to register the histogram with.
   * @param block a lambda with [Histogram.Builder] as receiver for configuring name, help, buckets, and labels.
   * @return the registered [Histogram] instance.
   */
  @JvmOverloads
  fun histogram(
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
    block: Histogram.Builder.() -> Unit,
  ): Histogram = Histogram.build().apply(block).register(registry)
}
