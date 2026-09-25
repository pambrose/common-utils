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
import io.prometheus.metrics.core.metrics.GaugeWithCallback
import io.prometheus.metrics.model.registry.Collector
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import io.prometheus.metrics.model.snapshots.Labels
import io.prometheus.metrics.model.snapshots.MetricFamilyDescriptor

/**
 * A Prometheus [Collector] that exposes a gauge metric whose value is obtained by invoking
 * a sampling function on each collection cycle.
 *
 * The collector registers itself with [registry] upon construction and can later be removed with
 * `registry.unregister(collector)`. Collection is delegated to an internal, unregistered [GaugeWithCallback].
 * Registration reads only the metric's descriptor (name, type, help and label names), never its value, so the
 * sampler first runs on the first scrape, not during construction.
 *
 * Each collector exposes exactly one series, with [labelNames] and [labelValues] as constant labels. The registry
 * rejects a second collector with the same [name] and the same label names, so one metric cannot get a series per
 * label value this way. (It does accept the same [name] with other label names, provided the help text matches.)
 *
 * @param name the metric name. As in the Prometheus Java client 1.x, any non-empty UTF-8 string is accepted; the
 *   exposition formats escape characters outside the legacy `[a-zA-Z_:][a-zA-Z0-9_:]*` set.
 * @param help the help/description text for the metric.
 * @param labelNames the label names for the metric. Defaults to empty.
 * @param labelValues the label values corresponding to [labelNames]. Defaults to empty.
 * @param registry the registry to register with. Defaults to [PrometheusRegistry.defaultRegistry].
 * @param data a lambda that returns the current gauge value as a [Double]. If it throws, the failure is logged at
 *   WARN and the gauge reports `NaN` for that scrape instead of failing the whole scrape.
 * @throws IllegalArgumentException if [labelNames] and [labelValues] differ in size, if [name] is empty or a label
 *   name is not a valid Prometheus label name (for example one starting with `__`), if a label name is repeated, or
 *   if [registry] already holds a metric whose exposed names collide with this one.
 */
class SamplerGaugeCollector(
  private val name: String,
  help: String,
  labelNames: List<String> = emptyList(),
  labelValues: List<String> = emptyList(),
  registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
  private val data: () -> Double,
) : Collector {
  // Explicit overloads for Java rather than @JvmOverloads, which would also generate a public (name, help,
  // labelNames, data) constructor that defaulted labelValues to empty and so threw for any non-empty labelNames.

  /** Creates an unlabelled gauge registered with [PrometheusRegistry.defaultRegistry]. */
  constructor(name: String, help: String, data: () -> Double) :
    this(name, help, emptyList(), emptyList(), PrometheusRegistry.defaultRegistry, data)

  /** Creates a gauge with the given labels, registered with [PrometheusRegistry.defaultRegistry]. */
  constructor(name: String, help: String, labelNames: List<String>, labelValues: List<String>, data: () -> Double) :
    this(name, help, labelNames, labelValues, PrometheusRegistry.defaultRegistry, data)

  private val gauge: GaugeWithCallback

  init {
    // The builder and Labels.of validate the metric and label names (Labels.of also rejects "a.b" with "a_b", which
    // expose the same name) before anything is registered. The sizes are checked here only for a clearer message.
    require(labelNames.size == labelValues.size) {
      "labelNames (${labelNames.size}) and labelValues (${labelValues.size}) must have the same size"
    }
    gauge =
      GaugeWithCallback
        .builder()
        .name(name)
        .help(help)
        .constLabels(Labels.of(labelNames, labelValues))
        .callback { callback -> callback.call(sample()) }
        .build()
    registry.register(this)
  }

  private fun sample(): Double =
    // The callback runs on every Prometheus scrape; a throwing sampler must not abort the whole scrape
    // (taking down every other metric), so guard it and report NaN instead.
    runCatching { data() }.getOrElse { e ->
      logger.warn(e) { "Sampler for metric \"$name\" threw; reporting NaN" }
      Double.NaN
    }

  // Registration reads this descriptor to check for name collisions. Without it the registry would skip that check,
  // so it is what lets registration succeed or fail without running the sampler.
  override fun getMetricFamilyDescriptor(): MetricFamilyDescriptor? = gauge.metricFamilyDescriptor

  override fun collect(): GaugeSnapshot = gauge.collect()

  private companion object {
    private val logger = KotlinLogging.logger {}
  }
}
