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

package com.pambrose.common.service

import com.codahale.metrics.health.HealthCheck
import com.pambrose.common.concurrent.GenericIdleService
import com.pambrose.common.concurrent.genericServiceListener
import com.pambrose.common.dsl.GuavaDsl.toStringElements
import com.pambrose.common.dsl.MetricsDsl.healthCheck
import com.pambrose.common.util.ensureLeadingSlash
import com.google.common.util.concurrent.MoreExecutors
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.metrics.exporter.servlet.jakarta.PrometheusMetricsServlet
import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.eclipse.jetty.ee11.servlet.ServletHolder

/**
 * A Guava [GenericIdleService] that runs an embedded Jetty server to expose a Prometheus [PrometheusMetricsServlet].
 *
 * The service starts a Jetty HTTP server on the specified port and serves the Prometheus metrics
 * endpoint at the given path. It also exposes a [healthCheck] property for integration with
 * Dropwizard health check registries.
 *
 * The servlet negotiates the exposition format through the request's `Accept` header: a plain scrape (no `Accept`,
 * or one that does not ask for OpenMetrics) gets the Prometheus text format (`Content-Type` starting
 * `text/plain; version=0.0.4`), and a scrape sending `Accept: application/openmetrics-text; version=1.0.0` gets the
 * OpenMetrics text format instead.
 *
 * @param port The HTTP port for the metrics endpoint.
 * @param path The URL path for the Prometheus metrics servlet, with or without a leading slash.
 * @param host The interface to bind to, or `null` to bind every interface.
 * @param registry The [PrometheusRegistry] to serve. Defaults to [PrometheusRegistry.defaultRegistry].
 * @param initBlock An optional initialization block invoked after the service listener is registered.
 */
class MetricsService(
  private val port: Int,
  private val path: String,
  host: String? = null,
  registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
  initBlock: (MetricsService.() -> Unit) = {},
) : GenericIdleService() {
  private val server =
    jettyServer(host, port) {
      addServlet(ServletHolder(PrometheusMetricsServlet(registry)), path.ensureLeadingSlash())
    }

  /** A Dropwizard [HealthCheck] that reports healthy when the embedded Jetty server is running. */
  val healthCheck =
    healthCheck {
      if (server.isRunning)
        HealthCheck.Result.healthy()
      else
        HealthCheck.Result.unhealthy("Jetty server not running")
    }

  /**
   * Visible for testing: the port the server listens on, which the OS chooses when [port] is 0. It is updated when
   * the service starts and keeps that value after it stops.
   */
  @Volatile
  internal var boundPort = port
    private set

  init {
    addListener(genericServiceListener(logger), MoreExecutors.directExecutor())
    initBlock(this)
  }

  @Deprecated("Binary compatibility with the constructor that predates host", level = DeprecationLevel.HIDDEN)
  constructor(
    port: Int,
    path: String,
    initBlock: MetricsService.() -> Unit,
  ) : this(port, path, null, PrometheusRegistry.defaultRegistry, initBlock)

  override fun startUp() {
    server.start()
    boundPort = server.localPort
  }

  override fun shutDown() = server.stop()

  override fun toString() =
    toStringElements {
      add("port", port)
      add("path", path.ensureLeadingSlash())
    }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
