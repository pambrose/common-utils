/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.service

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck
import com.codahale.metrics.jmx.JmxReporter
import com.codahale.metrics.servlets.HealthCheckServlet
import com.codahale.metrics.servlets.PingServlet
import com.codahale.metrics.servlets.ThreadDumpServlet
import com.github.pambrose.common.concurrent.GenericExecutionThreadService
import com.github.pambrose.common.concurrent.genericServiceListener
import com.github.pambrose.common.dsl.GuavaDsl.serviceManager
import com.github.pambrose.common.dsl.GuavaDsl.serviceManagerListener
import com.github.pambrose.common.dsl.MetricsDsl.healthCheck
import com.github.pambrose.common.metrics.SystemMetrics
import com.github.pambrose.common.servlet.VersionServlet
import com.github.pambrose.common.util.simpleClassName
import com.google.common.base.Joiner
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.dropwizard.DropwizardExports
import io.prometheus.common.AdminConfig
import io.prometheus.common.MetricsConfig
import io.prometheus.common.ZipkinConfig
import mu.KLogging
import java.io.Closeable
import kotlin.time.MonoClock

abstract class GenericService<T>
protected constructor(val configVals: T,
                      private val adminConfig: AdminConfig,
                      private val metricsConfig: MetricsConfig,
                      private val zipkinConfig: ZipkinConfig,
                      private val versionBlock: () -> String = { "No version" },
                      val isTestMode: Boolean = false) : GenericExecutionThreadService(), Closeable {

  protected val startTime = MonoClock.markNow()
  protected val healthCheckRegistry = HealthCheckRegistry()
  protected val metricRegistry = MetricRegistry()
  protected val services = mutableListOf<Service>()

  val isAdminEnabled = adminConfig.enabled
  val isMetricsEnabled = metricsConfig.enabled
  val isZipkinEnabled = zipkinConfig.enabled

  private lateinit var serviceManager: ServiceManager
  private lateinit var servletGroup: ServletGroup

  lateinit var jmxReporter: JmxReporter
  lateinit var adminService: AdminService
  lateinit var metricsService: MetricsService
  lateinit var zipkinReporterService: ZipkinReporterService

  init {
    if (isMetricsEnabled)
      CollectorRegistry.defaultRegistry.register(DropwizardExports(metricRegistry));
  }

  val upTime get() = startTime.elapsedNow()

  fun initService(adminServletInit: ServletGroup.() -> Unit = {}) {
    if (isAdminEnabled) {
      adminConfig.apply {
        servletGroup =
          ServletGroup(port)
            .apply {

              addServlet(pingPath, PingServlet())
              addServlet(versionPath, VersionServlet(versionBlock()))
              addServlet(healthCheckPath, HealthCheckServlet(healthCheckRegistry))
              addServlet(threadDumpPath, ThreadDumpServlet())

              adminServletInit(this)
            }
      }

      adminService =
        AdminService(servletGroup = servletGroup) {
          addService(this)
        }
    } else {
      logger.info { "Admin service disabled" }
    }

    if (isMetricsEnabled) {
      metricsService = MetricsService(metricsConfig.port, metricsConfig.path) { addService(this) }
      SystemMetrics.initialize(enableStandardExports = metricsConfig.standardExportsEnabled,
                               enableMemoryPoolsExports = metricsConfig.memoryPoolsExportsEnabled,
                               enableGarbageCollectorExports = metricsConfig.garbageCollectorExportsEnabled,
                               enableThreadExports = metricsConfig.threadExportsEnabled,
                               enableClassLoadingExports = metricsConfig.classLoadingExportsEnabled,
                               enableVersionInfoExports = metricsConfig.versionInfoExportsEnabled)
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

    addListener(genericServiceListener(this, logger), MoreExecutors.directExecutor())

    addService(this)

    serviceManager =
      serviceManager(services) {
        addListener(
          serviceManagerListener {
            healthy { logger.info { "All ${this@GenericService.simpleClassName} services healthy" } }
            stopped { logger.info { "All ${this@GenericService.simpleClassName} services stopped" } }
            failure { logger.info { "${this@GenericService.simpleClassName} service failed: $it" } }
          })
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

    if (isAdminEnabled)
      adminService.startSync()

    Runtime.getRuntime().addShutdownHook(shutDownHookAction(this))
  }

  override fun shutDown() {
    if (isAdminEnabled)
      adminService.stopSync()

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

  protected fun addServices(service: Service, vararg services: Service) {
    addService(service)
    services.forEach { addService(it) }
  }

  protected open fun registerHealthChecks() {
    healthCheckRegistry
      .apply {
        register("thread_deadlock", ThreadDeadlockHealthCheck())
        if (isMetricsEnabled)
          register("metrics_service", metricsService.healthCheck)
        register("all_services_healthy",
                 healthCheck {
                   if (serviceManager.isHealthy)
                     HealthCheck.Result.healthy()
                   else {
                     val vals =
                       serviceManager
                         .servicesByState()
                         .entries()
                         .filter { it.key !== Service.State.RUNNING }
                         .onEach { logger.warn { "Incorrect state - ${it.key}: ${it.value}" } }
                         .map { "${it.key}: ${it.value}" }
                         .toList()
                     HealthCheck.Result.unhealthy("Incorrect state: ${Joiner.on(", ").join(vals)}")
                   }
                 })
      }
  }

  companion object : KLogging() {
    fun shutDownHookAction(service: Service) =
      Thread {
        System.err.println("*** ${service.simpleClassName} shutting down ***")
        service.stopAsync()
        System.err.println("*** ${service.simpleClassName} shut down complete ***")
      }

  }
}