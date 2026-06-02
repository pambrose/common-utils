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
import com.pambrose.common.util.ensureLeadingSlash
import com.google.common.util.concurrent.Service
import io.dropwizard.metrics.servlets.HealthCheckServlet
import io.dropwizard.metrics.servlets.PingServlet
import io.dropwizard.metrics.servlets.ThreadDumpServlet
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application

/**
 * Abstract base class for services that use an embedded Ktor CIO server for admin servlet hosting.
 *
 * Hosts the admin endpoints (ping, version, health check, and thread dump servlets) via
 * [KtorServletService], delegating metrics, Zipkin tracing, health checks, and lifecycle coordination
 * to [AbstractGenericService].
 *
 * Subclasses should call [initKtorServletService] during initialization to set up the service infrastructure.
 *
 * @param T The type of the configuration values object.
 * @param configVals The application-specific configuration values.
 * @param adminConfig Configuration for admin endpoints.
 * @param metricsConfig Configuration for Prometheus metrics.
 * @param zipkinConfig Configuration for Zipkin tracing.
 * @param versionBlock A lambda returning the application version string for the version endpoint.
 * @param isTestMode Whether the service is running in test mode.
 * @see GenericService For a Jetty-based variant of this service.
 */
abstract class GenericKtorService<T> protected constructor(
  configVals: T,
  private val adminConfig: AdminConfig,
  metricsConfig: MetricsConfig,
  zipkinConfig: ZipkinConfig,
  private val versionBlock: () -> String = { "No version" },
  isTestMode: Boolean = false,
) : AbstractGenericService<T>(configVals, adminConfig, metricsConfig, zipkinConfig, isTestMode) {
  private lateinit var servletGroup: HttpServletGroup

  /** The Ktor-based servlet service hosting admin endpoints. Initialized when admin is enabled. */
  lateinit var servletService: KtorServletService

  override val servletServiceOrNull: GenericIdleService?
    get() = if (::servletService.isInitialized) servletService else null

  /**
   * Initializes the Ktor servlet service, metrics, Zipkin tracing, and health checks.
   *
   * This method should be called during subclass initialization. It conditionally sets up admin
   * servlets via an embedded Ktor server, then delegates to [initMetricsAndHealthChecks] to wire up
   * Prometheus metrics, JMX reporting, Zipkin tracing, health checks, and the Guava `ServiceManager`.
   *
   * @param initKtor An optional Ktor [Application] configuration block for custom Ktor setup.
   * @param servletInit An optional block to register additional servlets in the [HttpServletGroup].
   */
  fun initKtorServletService(
    initKtor: Application.() -> Unit = {},
    servletInit: HttpServletGroup.() -> Unit = {},
  ) {
    if (isAdminEnabled) {
      servletGroup =
        adminConfig.run {
          HttpServletGroup().apply {
            addServlets(
              pingPath.ensureLeadingSlash() to PingServlet(),
              versionPath.ensureLeadingSlash() to VersionServlet(versionBlock()),
              healthCheckPath.ensureLeadingSlash() to HealthCheckServlet(healthCheckRegistry),
              threadDumpPath.ensureLeadingSlash() to ThreadDumpServlet(),
            )
            servletInit(this)
          }
        }

      servletService = KtorServletService(adminConfig.port, servletGroup, initKtor) { addService(this) }
    } else {
      logger.info { "Admin service disabled" }
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
