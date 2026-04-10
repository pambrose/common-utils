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

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck.Result
import com.codahale.metrics.health.HealthCheckRegistry
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck
import com.codahale.metrics.jmx.JmxReporter
import com.pambrose.common.concurrent.GenericExecutionThreadService
import com.pambrose.common.concurrent.genericServiceListener
import com.pambrose.common.dsl.GuavaDsl.serviceManager
import com.pambrose.common.dsl.GuavaDsl.serviceManagerListener
import com.pambrose.common.dsl.MetricsDsl.healthCheck
import com.pambrose.common.metrics.SystemMetrics
import com.pambrose.common.servlet.VersionServlet
import com.pambrose.common.util.simpleClassName
import com.google.common.base.Joiner
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import io.dropwizard.metrics.servlets.HealthCheckServlet
import io.dropwizard.metrics.servlets.PingServlet
import io.dropwizard.metrics.servlets.ThreadDumpServlet
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import java.io.Closeable
import kotlin.time.TimeSource.Monotonic

/**
 * Abstract base class for services that use an embedded Jetty server for admin servlet hosting.
 *
 * Provides integrated support for:
 * - **Admin endpoints**: ping, version, health check, and thread dump servlets via [ServletService]
 * - **Prometheus metrics**: JVM and application metrics exported via [MetricsService]
 * - **Zipkin tracing**: distributed trace reporting via [ZipkinReporterService]
 * - **Health checks**: Dropwizard health check registry with thread deadlock and service state monitoring
 * - **Service lifecycle**: coordinated startup/shutdown of all sub-services via a Guava [ServiceManager]
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
  val configVals: T,
  private val adminConfig: AdminConfig,
  private val metricsConfig: MetricsConfig,
  private val zipkinConfig: ZipkinConfig,
  private val versionBlock: () -> String = { "No version" },
  val isTestMode: Boolean = false,
) : GenericExecutionThreadService(),
  Closeable {
  protected val startTime = Monotonic.markNow()
  protected val healthCheckRegistry = HealthCheckRegistry()
  protected val metricRegistry = MetricRegistry()
  protected val services = mutableListOf<Service>()

  /** Whether admin endpoints are enabled, based on [AdminConfig.enabled]. */
  val isAdminEnabled = adminConfig.enabled

  /** Whether Prometheus metrics collection is enabled, based on [MetricsConfig.enabled]. */
  val isMetricsEnabled = metricsConfig.enabled

  /** Whether Zipkin distributed tracing is enabled, based on [ZipkinConfig.enabled]. */
  val isZipkinEnabled = zipkinConfig.enabled

  private lateinit var serviceManager: ServiceManager
  private lateinit var servletGroup: ServletGroup

  /** The JMX reporter for Dropwizard metrics. Initialized when metrics are enabled. */
  lateinit var jmxReporter: JmxReporter

  /** The Jetty-based servlet service hosting admin endpoints. Initialized when admin is enabled. */
  lateinit var servletService: ServletService

  /** The Prometheus metrics service. Initialized when metrics are enabled. */
  lateinit var metricsService: MetricsService

  /** The Zipkin span reporter service. Initialized when Zipkin tracing is enabled. */
  lateinit var zipkinReporterService: ZipkinReporterService

  /** The elapsed time since the service was created. */
  val upTime get() = startTime.elapsedNow()

  /**
   * Initializes the servlet service, metrics, Zipkin tracing, and health checks.
   *
   * This method should be called during subclass initialization. It conditionally sets up admin
   * servlets, Prometheus metrics, JMX reporting, and Zipkin tracing based on their respective
   * configuration flags, then registers health checks and wires up the Guava [ServiceManager].
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

    if (isMetricsEnabled) {
      logger.info { "Enabling Dropwizard metrics" }
      CollectorRegistry.defaultRegistry.register(DropwizardExports(metricRegistry))

      logger.info { "Enabling JMX metrics" }
      metricsService = MetricsService(metricsConfig.port, metricsConfig.path) { addService(this) }
      SystemMetrics.initialize(
        enableStandardExports = metricsConfig.standardExportsEnabled,
        enableMemoryPoolsExports = metricsConfig.memoryPoolsExportsEnabled,
        enableGarbageCollectorExports = metricsConfig.garbageCollectorExportsEnabled,
        enableThreadExports = metricsConfig.threadExportsEnabled,
        enableClassLoadingExports = metricsConfig.classLoadingExportsEnabled,
        enableVersionInfoExports = metricsConfig.versionInfoExportsEnabled,
      )
      jmxReporter = JmxReporter.forRegistry(metricRegistry).build()
    } else {
      logger.info { "Metrics service disabled" }
    }

    if (isZipkinEnabled) {
      val url = "http://${zipkinConfig.hostname}:${zipkinConfig.port}/${zipkinConfig.path}"
      zipkinReporterService =
        ZipkinReporterService(url) { addService(this) }
    } else {
      logger.info { "Zipkin reporter service disabled" }
    }

    addListener(genericServiceListener(logger), directExecutor())

    addService(this)

    serviceManager =
      serviceManager(services) {
        val clazzname = this@GenericService.simpleClassName
        addListener(
          serviceManagerListener {
            healthy { logger.info { "All $clazzname services healthy" } }
            stopped { logger.info { "All $clazzname services stopped" } }
            failure { logger.info { "$clazzname service failed: $it" } }
          },
          directExecutor(),
        )
      }

    registerHealthChecks()
  }

  override fun startUp() {
    super.startUp()
    if (isZipkinEnabled)
      zipkinReporterService.startSync()

    if (isMetricsEnabled) {
      metricsService.startSync()
      jmxReporter.start()
    }

    if (::servletService.isInitialized)
      servletService.startSync()

    Runtime.getRuntime().addShutdownHook(shutDownHookAction(this))
  }

  override fun shutDown() {
    if (::servletService.isInitialized)
      servletService.stopSync()

    if (isMetricsEnabled) {
      metricsService.stopSync()
      jmxReporter.stop()
    }

    if (isZipkinEnabled)
      zipkinReporterService.stopSync()

    super.shutDown()
  }

  override fun close() {
    stopSync()
  }

  private fun addService(service: Service) {
    logger.info { "Adding service $service" }
    services += service
  }

  protected fun addServices(
    service: Service,
    vararg services: Service,
  ) {
    addService(service)
    services.forEach { addService(it) }
  }

  protected open fun registerHealthChecks() {
    healthCheckRegistry
      .apply {
        register("thread_deadlock", ThreadDeadlockHealthCheck())
        if (isMetricsEnabled)
          register("metrics_service", metricsService.healthCheck)
        register(
          "all_services_healthy",
          healthCheck {
            if (serviceManager.isHealthy) {
              Result.healthy()
            } else {
              val vals =
                serviceManager
                  .servicesByState()
                  .entries()
                  .filter { it.key !== Service.State.RUNNING }
                  .onEach { logger.warn { "Incorrect state - ${it.key}: ${it.value}" } }
                  .map { "${it.key}: ${it.value}" }
                  .toList()
              Result.unhealthy("Incorrect state: ${Joiner.on(", ").join(vals)}")
            }
          },
        )
      }
  }

  companion object {
    private val logger = KotlinLogging.logger {}

    /**
     * Creates a JVM shutdown hook [Thread] that gracefully stops the given [Service].
     *
     * @param service The Guava [Service] to stop when the JVM shuts down.
     * @return A [Thread] suitable for use with [Runtime.addShutdownHook].
     */
    fun shutDownHookAction(service: Service) =
      Thread {
        System.err.println("*** ${service.simpleClassName} shutting down ***")
        service.stopAsync()
        service.awaitTerminated()
        System.err.println("*** ${service.simpleClassName} shut down complete ***")
      }
  }
}
