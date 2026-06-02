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

import com.pambrose.common.concurrent.GenericIdleService
import com.pambrose.common.servlet.VersionServlet
import com.google.common.util.concurrent.Service
import io.dropwizard.metrics.servlets.HealthCheckServlet
import io.dropwizard.metrics.servlets.PingServlet
import io.dropwizard.metrics.servlets.ThreadDumpServlet
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Abstract base class for services that use an embedded Jetty server for admin servlet hosting.
 *
 * Hosts the admin endpoints (ping, version, health check, and thread dump servlets) via [ServletService],
 * delegating metrics, Zipkin tracing, health checks, and lifecycle coordination to [AbstractGenericService].
 *
 * Subclasses should call [initServletService] during initialization to set up the service infrastructure.
 *
 * @param T The type of the configuration values object.
 * @param configVals The application-specific configuration values.
 * @param adminConfig Configuration for admin endpoints.
 * @param metricsConfig Configuration for Prometheus metrics.
 * @param zipkinConfig Configuration for Zipkin tracing.
 * @param versionBlock A lambda returning the application version string for the version endpoint.
 * @param isTestMode Whether the service is running in test mode.
 * @see GenericKtorService For a Ktor-based variant of this service.
 */
abstract class GenericService<T> protected constructor(
  configVals: T,
  private val adminConfig: AdminConfig,
  metricsConfig: MetricsConfig,
  zipkinConfig: ZipkinConfig,
  private val versionBlock: () -> String = { "No version" },
  isTestMode: Boolean = false,
) : AbstractGenericService<T>(configVals, adminConfig, metricsConfig, zipkinConfig, isTestMode) {
  private lateinit var servletGroup: ServletGroup

  /** The Jetty-based servlet service hosting admin endpoints. Initialized when admin is enabled. */
  lateinit var servletService: ServletService

  override val servletServiceOrNull: GenericIdleService?
    get() = if (::servletService.isInitialized) servletService else null

  /**
   * Initializes the servlet service, metrics, Zipkin tracing, and health checks.
   *
   * This method should be called during subclass initialization. It conditionally sets up admin
   * servlets via an embedded Jetty server, then delegates to [initMetricsAndHealthChecks] to wire up
   * Prometheus metrics, JMX reporting, Zipkin tracing, health checks, and the Guava `ServiceManager`.
   *
   * @param servletInit An optional block to register additional servlets in the [ServletGroup].
   */
  fun initServletService(servletInit: ServletGroup.() -> Unit = {}) {
    // See if admin servlets are enabled or something within the passed in lambda is enabled
    if (isAdminEnabled) {
      servletGroup =
        adminConfig.run {
          ServletGroup()
            .apply {
              if (isAdminEnabled) {
                addServlet(pingPath, PingServlet())
                addServlet(versionPath, VersionServlet(versionBlock()))
                addServlet(healthCheckPath, HealthCheckServlet(healthCheckRegistry))
                addServlet(threadDumpPath, ThreadDumpServlet())
              } else {
                logger.info { "Admin service disabled" }
              }

              servletInit(this)
            }
        }

      servletService =
        ServletService(port = adminConfig.port, servletGroup) {
          addService(this)
        }
    }

    initMetricsAndHealthChecks()
  }

  companion object {
    private val logger = KotlinLogging.logger {}

    /**
     * Creates a JVM shutdown hook [Thread] that gracefully stops the given [Service].
     *
     * @param service The Guava [Service] to stop when the JVM shuts down.
     * @return A [Thread] suitable for use with [Runtime.addShutdownHook].
     */
    fun shutDownHookAction(service: Service) = AbstractGenericService.shutDownHookAction(service)
  }
}
