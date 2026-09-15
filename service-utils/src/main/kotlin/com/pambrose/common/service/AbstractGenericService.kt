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
import com.pambrose.common.concurrent.GenericIdleService
import com.pambrose.common.concurrent.genericServiceListener
import com.pambrose.common.dsl.GuavaDsl.serviceManager
import com.pambrose.common.dsl.GuavaDsl.serviceManagerListener
import com.pambrose.common.dsl.MetricsDsl.healthCheck
import com.pambrose.common.metrics.SystemMetrics
import com.pambrose.common.util.simpleClassName
import com.google.common.base.Joiner
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import java.io.Closeable
import kotlin.time.TimeSource.Monotonic

/**
 * Shared base class for [GenericService] and [GenericKtorService].
 *
 * Holds the parts that are identical between the Jetty- and Ktor-hosted variants:
 * - **Prometheus metrics**: JVM and application metrics exported via [MetricsService]
 * - **Zipkin tracing**: distributed trace reporting via [ZipkinReporterService]
 * - **Health checks**: Dropwizard health check registry with thread deadlock and service state monitoring
 * - **Service lifecycle**: coordinated startup/shutdown of all sub-services via a Guava [ServiceManager]
 *
 * Subclasses supply only the embedded server used to host the admin servlets — exposing it through
 * [servletServiceOrNull] — and call [initMetricsAndHealthChecks] from their own init method once that
 * servlet service has been constructed. The init method must run exactly once, before the service starts.
 *
 * @param T The type of the configuration values object.
 * @param configVals The application-specific configuration values.
 * @param adminConfig Configuration for admin endpoints.
 * @param metricsConfig Configuration for Prometheus metrics.
 * @param zipkinConfig Configuration for Zipkin tracing.
 * @param isTestMode Whether the service is running in test mode.
 */
abstract class AbstractGenericService<T> protected constructor(
  val configVals: T,
  adminConfig: AdminConfig,
  private val metricsConfig: MetricsConfig,
  private val zipkinConfig: ZipkinConfig,
  val isTestMode: Boolean = false,
) : GenericExecutionThreadService(),
  Closeable {
  protected val startTime = Monotonic.markNow()
  protected val healthCheckRegistry = HealthCheckRegistry()
  protected val metricRegistry = MetricRegistry()

  /**
   * The services added through [addService] and [addServices].
   *
   * The Guava [ServiceManager] built by the init method takes a copy of this list, so only the services added
   * before then reach it, its failure listener, and the `all_services_healthy` health check. This class never
   * starts or stops the services added here; that stays with the caller.
   */
  protected val services: MutableList<Service> = []

  /** Whether admin endpoints are enabled, based on [AdminConfig.enabled]. */
  val isAdminEnabled = adminConfig.enabled

  /** Whether Prometheus metrics collection is enabled, based on [MetricsConfig.enabled]. */
  val isMetricsEnabled = metricsConfig.enabled

  /** Whether Zipkin distributed tracing is enabled, based on [ZipkinConfig.enabled]. */
  val isZipkinEnabled = zipkinConfig.enabled

  private lateinit var serviceManager: ServiceManager

  // Registered with Prometheus only while the service runs, so a stopped service stops exporting and a second
  // instance in the same JVM does not add duplicate metric families.
  private var dropwizardExports: DropwizardExports? = null

  @Volatile
  private var shutDownHook: Thread? = null

  /** Visible for testing: the JVM shutdown hook currently registered for this service, or `null`. */
  internal val registeredShutDownHook: Thread? get() = shutDownHook

  /** The JMX reporter for Dropwizard metrics. Initialized when metrics are enabled. */
  lateinit var jmxReporter: JmxReporter

  /** The Prometheus metrics service. Initialized when metrics are enabled. */
  lateinit var metricsService: MetricsService

  /** The Zipkin span reporter service. Initialized when Zipkin tracing is enabled. */
  lateinit var zipkinReporterService: ZipkinReporterService

  /** The elapsed time since the service was created. */
  val upTime get() = startTime.elapsedNow()

  /**
   * The admin servlet-hosting service, or `null` when admin is disabled or not yet initialized.
   *
   * Implemented by each subclass to expose its variant-specific servlet service (Jetty or Ktor) so the
   * shared lifecycle can start and stop it without depending on the concrete type.
   */
  protected abstract val servletServiceOrNull: GenericIdleService?

  /**
   * Wires up Prometheus metrics, Zipkin tracing, the Guava [ServiceManager], and health checks.
   *
   * Subclasses call this from their own init method after constructing the admin servlet service, so
   * that the servlet service is registered ahead of the metrics and Zipkin services.
   */
  protected fun initMetricsAndHealthChecks() {
    if (isMetricsEnabled) {
      logger.info { "Enabling Dropwizard metrics" }

      logger.info { "Enabling JMX metrics" }
      metricsService = MetricsService(metricsConfig.port, metricsConfig.path, metricsConfig.host) { addService(this) }
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
      val url = "http://${zipkinConfig.hostname}:${zipkinConfig.port}/${zipkinConfig.path.removePrefix("/")}"
      zipkinReporterService =
        ZipkinReporterService(url, zipkinConfig.serviceName) { addService(this) }
    } else {
      logger.info { "Zipkin reporter service disabled" }
    }

    addListener(genericServiceListener(logger), directExecutor())

    addService(this)

    serviceManager =
      serviceManager(services) {
        val clazzname = this@AbstractGenericService.simpleClassName
        addListener(
          serviceManagerListener {
            healthy { logger.info { "All $clazzname services healthy" } }
            stopped { logger.info { "All $clazzname services stopped" } }
            failure { logger.error(it.failureCause()) { "$clazzname service failed: $it" } }
          },
          directExecutor(),
        )
      }

    registerHealthChecks()
  }

  // Called first by each init method: a second run would replace the admin servlet service, orphaning the first,
  // and then fail part-way through when registering the health checks again.
  internal fun checkNotInitialized() =
    check(!::serviceManager.isInitialized) {
    "$simpleClassName is already initialized"
  }

  override fun startUp() {
    check(::serviceManager.isInitialized) {
      "Call initServletService() or initKtorServletService() before starting $simpleClassName"
    }
    super.startUp()

    // Guava does not call shutDown() when startUp() throws, so on a failure stop whatever already started, most
    // recent first, instead of leaving its ports and threads behind.
    val stopActions = ArrayDeque<() -> Unit>()
    try {
      if (isZipkinEnabled) {
        zipkinReporterService.startSync()
        stopActions.addFirst { zipkinReporterService.stopSync() }
      }

      if (isMetricsEnabled) {
        registerDropwizardExports()
        stopActions.addFirst(::unregisterDropwizardExports)
        metricsService.startSync()
        stopActions.addFirst { metricsService.stopSync() }
        jmxReporter.start()
        stopActions.addFirst { jmxReporter.stop() }
      }

      servletServiceOrNull?.also { servletService ->
        servletService.startSync()
        stopActions.addFirst { servletService.stopSync() }
      }
    } catch (e: Throwable) {
      stopActions.runEach().forEach(e::addSuppressed)
      throw e
    }

    shutDownHook = shutDownHookAction(this).also { Runtime.getRuntime().addShutdownHook(it) }
  }

  override fun shutDown() {
    // Every step runs even when an earlier one fails, so one sub-service failing to stop cannot leave the others
    // running. The first failure is rethrown with the rest attached as suppressed exceptions.
    val failures =
      listOf<() -> Unit>(
        { servletServiceOrNull?.stopSync() },
        { if (isMetricsEnabled) metricsService.stopSync() },
        { if (isMetricsEnabled) jmxReporter.stop() },
        ::unregisterDropwizardExports,
        { if (isZipkinEnabled) zipkinReporterService.stopSync() },
        ::removeShutDownHook,
      ).runEach()

    super.shutDown()

    failures.firstOrNull()?.let { first ->
      failures.drop(1).forEach(first::addSuppressed)
      throw first
    }
  }

  override fun close() {
    stopSync()
  }

  /**
   * Adds [service] to [services].
   *
   * Call it before the init method ([GenericService.initServletService] or
   * [GenericKtorService.initKtorServletService]). The [ServiceManager] is built there from a copy of [services], so a
   * service added afterwards is not managed, and a warning is logged. This class never starts or stops added
   * services.
   */
  protected fun addService(service: Service) {
    if (::serviceManager.isInitialized)
      logger.warn {
        "$service was added after $simpleClassName was initialized, so its ServiceManager and the " +
          "all_services_healthy check do not include it"
      }
    else
      logger.info { "Adding service $service" }
    services += service
  }

  /** Adds each of the given services with [addService], which must be called before the init method. */
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

  private fun registerDropwizardExports() {
    dropwizardExports = DropwizardExports(metricRegistry).also { CollectorRegistry.defaultRegistry.register(it) }
  }

  private fun unregisterDropwizardExports() {
    dropwizardExports?.let { CollectorRegistry.defaultRegistry.unregister(it) }
    dropwizardExports = null
  }

  private fun removeShutDownHook() {
    shutDownHook?.let { hook ->
      // removeShutdownHook throws IllegalStateException if the JVM is already shutting down (e.g. when
      // shutDown was triggered by the hook itself) and SecurityException under a SecurityManager; in both
      // cases the hook simply remains registered until JVM exit, so swallow rather than fail the shutdown.
      val _ = runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
      shutDownHook = null
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

// Runs every step, including those after one that throws, and returns what they threw.
private fun Iterable<() -> Unit>.runEach(): List<Throwable> = mapNotNull { step -> runCatching(step).exceptionOrNull() }
