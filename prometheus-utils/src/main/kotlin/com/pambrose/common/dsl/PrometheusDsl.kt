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

import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary

/**
 * Provides a Kotlin DSL for building and registering Prometheus metric collectors.
 *
 * Each factory function accepts a configuration lambda for the metric's builder and
 * automatically registers the metric with the default Prometheus [io.prometheus.client.CollectorRegistry].
 */
object PrometheusDsl {
  /**
   * Creates, configures, and registers a Prometheus [Counter].
   *
   * @param block a lambda with [Counter.Builder] as receiver for configuring name, help, and labels.
   * @return the registered [Counter] instance.
   */
  fun counter(block: Counter.Builder.() -> Unit): Counter =
    Counter.build().run {
      block(this)
      register()
    }

  /**
   * Creates, configures, and registers a Prometheus [Summary].
   *
   * @param block a lambda with [Summary.Builder] as receiver for configuring name, help, quantiles, and labels.
   * @return the registered [Summary] instance.
   */
  fun summary(block: Summary.Builder.() -> Unit): Summary =
    Summary.build().run {
      block(this)
      register()
    }

  /**
   * Creates, configures, and registers a Prometheus [Gauge].
   *
   * @param block a lambda with [Gauge.Builder] as receiver for configuring name, help, and labels.
   * @return the registered [Gauge] instance.
   */
  fun gauge(block: Gauge.Builder.() -> Unit): Gauge =
    Gauge.build().run {
      block(this)
      register()
    }

  /**
   * Creates, configures, and registers a Prometheus [Histogram].
   *
   * @param block a lambda with [Histogram.Builder] as receiver for configuring name, help, buckets, and labels.
   * @return the registered [Histogram] instance.
   */
  fun histogram(block: Histogram.Builder.() -> Unit): Histogram =
    Histogram.build().run {
      block(this)
      register()
    }
}
