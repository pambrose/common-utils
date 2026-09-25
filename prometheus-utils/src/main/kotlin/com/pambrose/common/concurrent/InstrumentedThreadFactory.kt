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

package com.pambrose.common.concurrent

import com.pambrose.common.dsl.PrometheusDsl
import com.pambrose.common.metrics.registerAllOrNothing
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Gauge
import io.prometheus.metrics.model.registry.PrometheusRegistry
import java.util.concurrent.ThreadFactory

/**
 * A [ThreadFactory] decorator that instruments thread lifecycle with Prometheus metrics.
 *
 * Tracks the number of threads created (counter), currently running (gauge), and terminated (counter). The exposed
 * series are `<name>_threads_created_total`, `<name>_threads_running` and `<name>_threads_terminated_total`. In the
 * Prometheus text format the counter families are also named with `_total`; in OpenMetrics they are named
 * `<name>_threads_created` and `<name>_threads_terminated`. The counters' `_created` timestamp series are written only
 * when the exporter enables created timestamps (`io.prometheus.exporter.include_created_timestamps`), which is off by
 * default.
 *
 * @param delegate the underlying [ThreadFactory] to delegate thread creation to.
 * @param name the base name for the Prometheus metrics (see above for the series names).
 * @param help the base help text for the Prometheus metrics.
 * @param registry the registry to register the metrics with. Defaults to [PrometheusRegistry.defaultRegistry];
 *   factories with the same [name] need separate registries.
 * @throws IllegalArgumentException if one of the metric names is already registered in [registry]. None of the
 *   three metrics is left registered in that case.
 */
class InstrumentedThreadFactory
  @JvmOverloads
  constructor(
    private val delegate: ThreadFactory,
    name: String,
    help: String,
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
  ) : ThreadFactory {
    private val created: Counter
    private val running: Gauge
    private val terminated: Counter

    init {
      // Register all three metrics or none: when a later name is already taken, the metrics registered before it
      // are removed again, so a failed construction leaves nothing behind in the registry.
      registry.registerAllOrNothing { tracking ->
        created =
          PrometheusDsl.counter(tracking) {
            name("${name}_threads_created")
            help("$help threads created")
          }
        running =
          PrometheusDsl.gauge(tracking) {
            name("${name}_threads_running")
            help("$help threads running")
          }
        terminated =
          PrometheusDsl.counter(tracking) {
            name("${name}_threads_terminated")
            help("$help threads terminated")
          }
      }
    }

    /**
     * Creates a thread through the delegate, counting it as created.
     *
     * @return the new thread, or `null` when the delegate rejects the request, which is then not counted.
     */
    override fun newThread(runnable: Runnable): Thread? =
      delegate.newThread(InstrumentedRunnable(runnable))?.also { created.inc() }

    private inner class InstrumentedRunnable(
      private val runnable: Runnable,
    ) : Runnable {
      override fun run() {
        running.inc()
        try {
          runnable.run()
        } finally {
          // Increment terminated before decrementing running, so a concurrent scrape never sees a finished
          // thread as neither running nor terminated.
          terminated.inc()
          running.dec()
        }
      }
    }
  }
