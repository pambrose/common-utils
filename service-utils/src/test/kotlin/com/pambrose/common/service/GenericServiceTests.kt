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

package com.pambrose.common.service

import com.codahale.metrics.health.HealthCheck
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.net.ServerSocket
import java.util.SortedMap
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration

private fun freePort() = ServerSocket(0).use { it.localPort }

private fun noopService(): Service =
  object : AbstractIdleService() {
    override fun startUp() = Unit

    override fun shutDown() = Unit
  }

private val disabledAdmin =
  AdminConfig(
    enabled = false,
    port = 0,
    pingPath = "/ping",
    versionPath = "/version",
    healthCheckPath = "/healthcheck",
    threadDumpPath = "/threaddump",
  )

private val disabledMetrics =
  MetricsConfig(
    enabled = false,
    port = 0,
    path = "metrics",
    standardExportsEnabled = false,
    memoryPoolsExportsEnabled = false,
    garbageCollectorExportsEnabled = false,
    threadExportsEnabled = false,
    classLoadingExportsEnabled = false,
    versionInfoExportsEnabled = false,
  )

private val disabledZipkin =
  ZipkinConfig(
    enabled = false,
    hostname = "localhost",
    port = 9411,
    path = "api/v2/spans",
    serviceName = "test",
  )

private class TestJettyService(
  admin: AdminConfig = disabledAdmin,
  metrics: MetricsConfig = disabledMetrics,
  zipkin: ZipkinConfig = disabledZipkin,
) : GenericService<String>(
    configVals = "jetty-config",
    adminConfig = admin,
    metricsConfig = metrics,
    zipkinConfig = zipkin,
  ) {
  // run() blocks until the service is asked to stop, keeping it RUNNING between start and close.
  private val stopRequested = CountDownLatch(1)

  override fun run() {
    stopRequested.await()
  }

  override fun triggerShutdown() {
    stopRequested.countDown()
  }

  val healthCheckNames get() = healthCheckRegistry.names.toList()

  val serviceCount get() = services.size

  fun runHealthChecks(): SortedMap<String, HealthCheck.Result> = healthCheckRegistry.runHealthChecks()

  fun addExtraServices(
    first: Service,
    vararg rest: Service,
  ) = addServices(first, *rest)

  init {
    initServletService()
  }
}

private class TestKtorService(
  admin: AdminConfig = disabledAdmin,
  metrics: MetricsConfig = disabledMetrics,
  zipkin: ZipkinConfig = disabledZipkin,
) : GenericKtorService<String>(
    configVals = "ktor-config",
    adminConfig = admin,
    metricsConfig = metrics,
    zipkinConfig = zipkin,
  ) {
  private val stopRequested = CountDownLatch(1)

  override fun run() {
    stopRequested.await()
  }

  override fun triggerShutdown() {
    stopRequested.countDown()
  }

  val healthCheckNames get() = healthCheckRegistry.names.toList()

  init {
    initKtorServletService()
  }
}

class GenericServiceTests : StringSpec() {
  init {
    "both variants derive the same enablement flags from disabled config" {
      val jetty = TestJettyService()
      jetty.isAdminEnabled shouldBe false
      jetty.isMetricsEnabled shouldBe false
      jetty.isZipkinEnabled shouldBe false

      val ktor = TestKtorService()
      ktor.isAdminEnabled shouldBe false
      ktor.isMetricsEnabled shouldBe false
      ktor.isZipkinEnabled shouldBe false
    }

    "both variants register the same base health checks after init" {
      val expected = listOf("all_services_healthy", "thread_deadlock")
      TestJettyService().healthCheckNames shouldContainExactlyInAnyOrder expected
      TestKtorService().healthCheckNames shouldContainExactlyInAnyOrder expected
    }

    "both variants expose configVals and a non-negative upTime" {
      val jetty = TestJettyService()
      jetty.configVals shouldBe "jetty-config"
      (jetty.upTime >= Duration.ZERO) shouldBe true

      val ktor = TestKtorService()
      ktor.configVals shouldBe "ktor-config"
      (ktor.upTime >= Duration.ZERO) shouldBe true
    }

    "Jetty service with admin, metrics, and zipkin enabled starts healthy and stops" {
      val service =
        TestJettyService(
          admin = disabledAdmin.copy(enabled = true, port = freePort()),
          metrics = disabledMetrics.copy(enabled = true, port = freePort()),
          zipkin = disabledZipkin.copy(enabled = true),
        )
      service.isAdminEnabled shouldBe true
      service.isMetricsEnabled shouldBe true
      service.isZipkinEnabled shouldBe true

      // The metrics_service check is registered only when metrics are enabled.
      service.healthCheckNames shouldContainExactlyInAnyOrder
        listOf("all_services_healthy", "metrics_service", "thread_deadlock")

      // Before start, the sub-services are not yet running, so the aggregate check is unhealthy.
      service.runHealthChecks()["all_services_healthy"]?.isHealthy shouldBe false

      service.startSync()
      service.isRunning shouldBe true
      service.runHealthChecks().values.all { it.isHealthy } shouldBe true

      service.close()
      service.isRunning shouldBe false
    }

    "Ktor service with admin enabled starts and stops" {
      val service = TestKtorService(admin = disabledAdmin.copy(enabled = true, port = freePort()))
      service.isAdminEnabled shouldBe true

      service.startSync()
      service.isRunning shouldBe true

      service.close()
      service.isRunning shouldBe false
    }

    "shutdown hook is registered on start and removed on stop" {
      val service = TestJettyService(admin = disabledAdmin.copy(enabled = true, port = freePort()))
      service.startSync()
      val hook = service.registeredShutDownHook ?: error("expected a shutdown hook to be registered after start")

      service.close()

      service.registeredShutDownHook shouldBe null
      // Removing it again reports false, proving shutDown already de-registered it (no leak).
      Runtime.getRuntime().removeShutdownHook(hook) shouldBe false
    }

    "addServices appends each provided service to the managed list" {
      val service = TestJettyService()
      val before = service.serviceCount
      service.addExtraServices(noopService(), noopService())
      service.serviceCount shouldBe before + 2
    }

    "both variants expose a shutDownHookAction that builds an unstarted hook thread" {
      GenericService.shutDownHookAction(noopService()).isAlive shouldBe false
      GenericKtorService.shutDownHookAction(noopService()).isAlive shouldBe false
    }
  }
}
