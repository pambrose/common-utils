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
import io.prometheus.client.CollectorRegistry
import java.util.concurrent.ThreadFactory

/**
 * A [ThreadFactory] decorator that instruments thread lifecycle with Prometheus metrics.
 *
 * Tracks the number of threads created (counter), currently running (gauge), and terminated (counter).
 *
 * @param delegate the underlying [ThreadFactory] to delegate thread creation to.
 * @param name the base name for the Prometheus metrics (suffixed with `_threads_created`, `_threads_running`, `_threads_terminated`).
 * @param help the base help text for the Prometheus metrics.
 * @param registry the registry to register the metrics with. Defaults to [CollectorRegistry.defaultRegistry];
 *   factories with the same [name] need separate registries.
 */
class InstrumentedThreadFactory
  @JvmOverloads
  constructor(
    private val delegate: ThreadFactory,
    name: String,
    help: String,
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
  ) : ThreadFactory {
    private val created =
      PrometheusDsl.counter(registry) {
        name("${name}_threads_created")
        help("$help threads created")
      }
    private val running =
      PrometheusDsl.gauge(registry) {
        name("${name}_threads_running")
        help("$help threads running")
      }
    private val terminated =
      PrometheusDsl.counter(registry) {
        name("${name}_threads_terminated")
        help("$help threads terminated")
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
