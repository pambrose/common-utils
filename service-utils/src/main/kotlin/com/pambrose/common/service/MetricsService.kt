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
import com.pambrose.common.dsl.JettyDsl.server
import com.pambrose.common.dsl.JettyDsl.servletContextHandler
import com.pambrose.common.dsl.MetricsDsl.healthCheck
import com.google.common.util.concurrent.MoreExecutors
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.client.servlet.jakarta.exporter.MetricsServlet
import org.eclipse.jetty.ee11.servlet.ServletHolder

/**
 * A Guava [GenericIdleService] that runs an embedded Jetty server to expose a Prometheus [MetricsServlet].
 *
 * The service starts a Jetty HTTP server on the specified port and serves the Prometheus metrics
 * endpoint at the given path. It also exposes a [healthCheck] property for integration with
 * Dropwizard health check registries.
 *
 * @param port The HTTP port for the metrics endpoint.
 * @param path The URL path for the Prometheus metrics servlet (without a leading slash).
 * @param initBlock An optional initialization block invoked after the service listener is registered.
 */
class MetricsService(
  private val port: Int,
  private val path: String,
  initBlock: (MetricsService.() -> Unit) = {},
) : GenericIdleService() {
  private val server =
    server(port) {
      handler =
        servletContextHandler {
          contextPath = "/"
          addServlet(ServletHolder(MetricsServlet()), "/$path")
        }
    }

  /** A Dropwizard [HealthCheck] that reports healthy when the embedded Jetty server is running. */
  val healthCheck =
    healthCheck {
      if (server.isRunning)
        HealthCheck.Result.healthy()
      else
        HealthCheck.Result.unhealthy("Jetty server not running")
    }

  init {
    addListener(genericServiceListener(logger), MoreExecutors.directExecutor())
    initBlock(this)
  }

  override fun startUp() = server.start()

  override fun shutDown() = server.stop()

  override fun toString() =
    toStringElements {
      add("port", port)
      add("path", "/$path")
    }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
