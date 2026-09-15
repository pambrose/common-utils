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

import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.client.Collector
import io.prometheus.client.CollectorRegistry

/**
 * A Prometheus [Collector] that exposes a gauge metric whose value is obtained by invoking
 * a sampling function on each collection cycle.
 *
 * The collector registers itself with [registry] upon construction. It describes its metric without sampling,
 * so the sampler first runs on the first scrape, not during construction.
 *
 * @param name the metric name.
 * @param help the help/description text for the metric.
 * @param labelNames the label names for the metric. Defaults to empty.
 * @param labelValues the label values corresponding to [labelNames]. Defaults to empty.
 * @param registry the registry to register with. Defaults to [CollectorRegistry.defaultRegistry].
 * @param data a lambda that returns the current gauge value as a [Double].
 * @throws IllegalArgumentException if [labelNames] and [labelValues] differ in size.
 */
class SamplerGaugeCollector
  @JvmOverloads
  constructor(
    private val name: String,
    private val help: String,
    private val labelNames: List<String> = emptyList(),
    private val labelValues: List<String> = emptyList(),
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
    private val data: () -> Double,
  ) : Collector(),
    Collector.Describable {
    init {
      // Validate eagerly: otherwise MetricFamilySamples.Sample throws on every collect() (i.e. every
      // Prometheus scrape), far from the construction site, taking down the whole scrape.
      require(labelNames.size == labelValues.size) {
        "labelNames (${labelNames.size}) and labelValues (${labelValues.size}) must have the same size"
      }
      register<Collector>(registry)
    }

    // Registration reads the metric names from describe(); without it, the registry calls collect(),
    // running the sampler on the constructing thread before the state it reads may be ready.
    override fun describe(): List<MetricFamilySamples> = [MetricFamilySamples(name, Type.GAUGE, help, [])]

    override fun collect(): List<MetricFamilySamples> {
      // collect() runs on every Prometheus scrape; a throwing sampler must not abort the whole scrape
      // (taking down every other metric), so guard it and report NaN instead.
      val value =
        runCatching { data() }.getOrElse { e ->
          logger.warn(e) { "Sampler for metric \"$name\" threw; reporting NaN" }
          Double.NaN
        }
      val sample = MetricFamilySamples.Sample(name, labelNames, labelValues, value)
      return [MetricFamilySamples(name, Type.GAUGE, help, [sample])]
    }

    companion object {
      private val logger = KotlinLogging.logger {}
    }
  }
