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
 * Each collector exposes exactly one series, and a registry accepts only one collector per metric name. Labels are
 * therefore constants on that one series: a second collector for the same [name] with other [labelValues] is
 * rejected by the registry, so one metric cannot have a series per label value this way.
 *
 * @param name the metric name.
 * @param help the help/description text for the metric.
 * @param labelNames the label names for the metric. Defaults to empty.
 * @param labelValues the label values corresponding to [labelNames]. Defaults to empty.
 * @param registry the registry to register with. Defaults to [CollectorRegistry.defaultRegistry].
 * @param data a lambda that returns the current gauge value as a [Double].
 * @throws IllegalArgumentException if [labelNames] and [labelValues] differ in size, if [name] or a label name is not
 *   a valid Prometheus name, if a label name is repeated, or if [name] is already registered with [registry].
 */
class SamplerGaugeCollector(
  private val name: String,
  private val help: String,
  private val labelNames: List<String> = emptyList(),
  private val labelValues: List<String> = emptyList(),
  registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
  private val data: () -> Double,
) : Collector(),
  Collector.Describable {
  // Explicit overloads for Java rather than @JvmOverloads, which also generated a public (name, help, labelNames,
  // data) constructor that defaulted labelValues to empty and so threw for any non-empty labelNames.

  /** Creates an unlabelled gauge registered with [CollectorRegistry.defaultRegistry]. */
  constructor(name: String, help: String, data: () -> Double) :
    this(name, help, emptyList(), emptyList(), CollectorRegistry.defaultRegistry, data)

  /** Creates a gauge with the given labels, registered with [CollectorRegistry.defaultRegistry]. */
  constructor(name: String, help: String, labelNames: List<String>, labelValues: List<String>, data: () -> Double) :
    this(name, help, labelNames, labelValues, CollectorRegistry.defaultRegistry, data)

  // The constructor @JvmOverloads generated, kept for binary compatibility only: hidden and synthetic, so neither
  // Kotlin nor Java source can call it. It works only with an empty labelNames, as it always did.
  @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
  constructor(name: String, help: String, labelNames: List<String>, data: () -> Double) :
    this(name, help, labelNames, emptyList(), CollectorRegistry.defaultRegistry, data)

  init {
    // Validate eagerly. Neither the registry nor MetricFamilySamples.Sample checks names or sizes, and the text
    // format writes them verbatim, so an invalid one would only surface as a malformed exposition that makes
    // Prometheus reject the whole scrape.
    require(labelNames.size == labelValues.size) {
      "labelNames (${labelNames.size}) and labelValues (${labelValues.size}) must have the same size"
    }
    checkMetricName(name)
    labelNames.forEach(::checkMetricLabelName)
    require(labelNames.toSet().size == labelNames.size) { "labelNames must be distinct but were $labelNames" }
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

  private companion object {
    private val logger = KotlinLogging.logger {}
  }
}
