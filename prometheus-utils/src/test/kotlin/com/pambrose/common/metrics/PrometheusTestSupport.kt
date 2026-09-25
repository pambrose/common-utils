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

package com.pambrose.common.metrics

import io.prometheus.metrics.expositionformats.ExpositionFormats
import io.prometheus.metrics.model.registry.PrometheusRegistry

// Test helpers that read a registry the way a scrape does: through the exposition writers the servlet exporter
// uses by default (ExpositionFormats.init()), so the series names asserted on are the ones Prometheus sees.

/** The registry in the Prometheus text format (what a scrape without an OpenMetrics `Accept` header gets). */
internal fun PrometheusRegistry.prometheusText(): String =
  ExpositionFormats.init().prometheusTextFormatWriter.toDebugString(scrape())

/** The registry in the OpenMetrics 1.0 text format. */
internal fun PrometheusRegistry.openMetricsText(): String =
  ExpositionFormats.init().openMetricsTextFormatWriter.toDebugString(scrape())

/** The base names of the metric families the registry holds, as registered (for a counter, without `_total`). */
internal fun PrometheusRegistry.metricNames(): Set<String> = scrape().map { it.metadata.prometheusName }.toSet()

/** The series names in [text], the part of each sample line before its labels or value. */
internal fun seriesNames(text: String): Set<String> =
  text
    .lines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .map { it.substringBefore('{').substringBefore(' ') }
    .toSet()

/**
 * The value of the Prometheus-text sample [series] with exactly [labels], or `null` when the registry exposes no such
 * sample.
 */
internal fun PrometheusRegistry.sampleValue(
  series: String,
  vararg labels: Pair<String, String>,
): Double? {
  val prefix =
    if (labels.isEmpty())
      "$series "
    else
      labels.sortedBy { it.first }.joinToString(",", "$series{", "} ") { (k, v) -> "$k=\"$v\"" }
  return prometheusText()
    .lines()
    .firstOrNull { it.startsWith(prefix) }
    ?.removePrefix(prefix)
    ?.substringBefore(' ')
    ?.toDouble()
}
