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

import io.prometheus.client.Collector

/**
 * A Prometheus [Collector] that exposes a gauge metric whose value is obtained by invoking
 * a sampling function on each collection cycle.
 *
 * The collector automatically registers itself with the default collector registry upon construction.
 *
 * @param name the metric name.
 * @param help the help/description text for the metric.
 * @param labelNames the label names for the metric. Defaults to empty.
 * @param labelValues the label values corresponding to [labelNames]. Defaults to empty.
 * @param data a lambda that returns the current gauge value as a [Double].
 * @throws IllegalArgumentException if [labelNames] and [labelValues] differ in size.
 */
class SamplerGaugeCollector(
  private val name: String,
  private val help: String,
  private val labelNames: List<String> = emptyList(),
  private val labelValues: List<String> = emptyList(),
  private val data: () -> Double,
) : Collector() {
  init {
    // Validate eagerly: otherwise MetricFamilySamples.Sample throws on every collect() (i.e. every
    // Prometheus scrape), far from the construction site, taking down the whole scrape.
    require(labelNames.size == labelValues.size) {
      "labelNames (${labelNames.size}) and labelValues (${labelValues.size}) must have the same size"
    }
    register<Collector>()
  }

  override fun collect(): List<MetricFamilySamples> {
    val sample = MetricFamilySamples.Sample(name, labelNames, labelValues, data())
    return listOf(MetricFamilySamples(name, Type.GAUGE, help, listOf(sample)))
  }
}
