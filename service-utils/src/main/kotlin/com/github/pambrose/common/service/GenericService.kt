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

package com.github.pambrose.common.service

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.health.HealthCheck
import com.codahale.metrics.health.HealthCheckRegistry
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck
import com.codahale.metrics.jmx.JmxReporter
import com.github.pambrose.common.concurrent.GenericExecutionThreadService
import com.github.pambrose.common.concurrent.genericServiceListener
import com.github.pambrose.common.dsl.GuavaDsl.serviceManager
import com.github.pambrose.common.dsl.GuavaDsl.serviceManagerListener
import com.github.pambrose.common.dsl.MetricsDsl.healthCheck
import com.github.pambrose.common.metrics.SystemMetrics
import com.github.pambrose.common.util.simpleClassName
import com.google.common.base.Joiner
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager
import io.prometheus.common.AdminConfig
import io.prometheus.common.MetricsConfig
import io.prometheus.common.ZipkinConfig
import mu.KLogging
import java.io.Closeable
import kotlin.properties.Delegates.notNull

abstract class GenericService<T>
protected constructor(val genericConfigVals: T,
                      adminConfig: AdminConfig,
                      metricsConfig: MetricsConfig,
                      zipkinConfig: ZipkinConfig,
                      versionBlock: () -> String,
                      val isTestMode: Boolean) : GenericExecutionThreadService(), Closeable {

  protected val healthCheckRegistry = HealthCheckRegistry()
  protected val metricRegistry = MetricRegistry()

  private val services = mutableListOf<Service>()

  private lateinit var serviceManager: ServiceManager

  val isAdminEnabled = adminConfig.enabled
  val isMetricsEnabled = metricsConfig.enabled
  val isZipkinEnabled = zipkinConfig.enabled

  private var jmxReporter: JmxReporter by notNull()
  var adminService: AdminService by notNull()
  var metricsService: MetricsService by notNull()
  var zipkinReporterService: ZipkinReporterService by notNull()

  init {
    if (isAdminEnabled) {
      adminService =
        AdminService(healthCheckRegistry = healthCheckRegistry,
                     port = adminConfig.port,
                     pingPath = adminConfig.pingPath,
                     versionPath = adminConfig.versionPath,
                     healthCheckPath = adminConfig.healthCheckPath,
                     threadDumpPath = adminConfig.threadDumpPath,
                     versionBlock = versionBlock) {
          addService(this)
        }
    } else {
      logger.info { "Admin service disabled" }
    }

    if (isMetricsEnabled) {
      metricsService = MetricsService(metricRegistry, metricsConfig.port, metricsConfig.path) { addService(this) }
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
  }

  fun initService() {
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

    Runtime.getRuntime().addShutdownHook(shutDownHookAction(
      this))
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