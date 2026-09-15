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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.codahale.metrics.Counter
import com.codahale.metrics.health.HealthCheck
import com.pambrose.common.concurrent.GenericIdleService
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Collections
import java.util.SortedMap
import java.util.concurrent.CountDownLatch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private fun freePort() = ServerSocket(0).use { it.localPort }

private fun httpGet(
  port: Int,
  path: String,
): HttpResponse<String> =
  HttpClient.newHttpClient().send(
    HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).timeout(java.time.Duration.ofSeconds(10)).build(),
    HttpResponse.BodyHandlers.ofString(),
  )

// An IPv4 address of an active non-loopback interface, or null when the machine has none.
private fun nonLoopbackAddress(): InetAddress? =
  Collections.list(NetworkInterface.getNetworkInterfaces())
    .filter { it.isUp && !it.isLoopback }
    .flatMap { Collections.list(it.inetAddresses) }
    .firstOrNull { it is Inet4Address }

private fun canConnect(
  address: InetAddress,
  port: Int,
) = Socket().use { socket -> runCatching { socket.connect(InetSocketAddress(address, port), 1_000) }.isSuccess }

// Runs block with a function that snapshots the log events emitted by the service package in the meantime.
private inline fun <T> capturingServiceLogs(block: (logs: () -> List<ILoggingEvent>) -> T): T {
  val appender = ListAppender<ILoggingEvent>().apply { start() }
  val logger = LoggerFactory.getLogger(AbstractGenericService::class.java.packageName) as Logger
  logger.addAppender(appender)
  return try {
    block { synchronized(appender) { appender.list.toList() } }
  } finally {
    logger.detachAppender(appender)
  }
}

private fun noopService(name: String = "noop"): Service =
  object : AbstractIdleService() {
    override fun startUp() = Unit

    override fun shutDown() = Unit

    override fun serviceName() = name
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

// Admin paths spelled with the given prefix: "/ping" and "ping" must reach the same endpoint.
private fun enabledAdmin(
  port: Int,
  prefix: String = "/",
) = disabledAdmin.copy(
  enabled = true,
  port = port,
  pingPath = "${prefix}ping",
  versionPath = "${prefix}version",
  healthCheckPath = "${prefix}healthcheck",
  threadDumpPath = "${prefix}threaddump",
)

private fun enabledMetrics(
  port: Int,
  prefix: String = "",
) = disabledMetrics.copy(enabled = true, port = port, path = "${prefix}metrics")

// Requests every admin endpoint and the metrics endpoint of a running service.
private fun checkEndpoints(
  adminPort: Int,
  metricsPort: Int,
  version: String,
  metricName: String,
) {
  httpGet(adminPort, "/ping").apply {
    statusCode() shouldBe 200
    body().trim() shouldBe "pong"
  }
  httpGet(adminPort, "/version").apply {
    statusCode() shouldBe 200
    body().trim() shouldBe version
  }
  httpGet(adminPort, "/healthcheck").apply {
    statusCode() shouldBe 200
    body() shouldContain "all_services_healthy"
  }
  httpGet(adminPort, "/threaddump").apply {
    statusCode() shouldBe 200
    body() shouldContain "state="
  }
  httpGet(metricsPort, "/metrics").apply {
    statusCode() shouldBe 200
    body() shouldContain metricName
  }
}

private class TestJettyService(
  admin: AdminConfig = disabledAdmin,
  metrics: MetricsConfig = disabledMetrics,
  zipkin: ZipkinConfig = disabledZipkin,
  extraServices: Pair<Service, Service>? = null,
) : GenericService<String>(
    configVals = "jetty-config",
    adminConfig = admin,
    metricsConfig = metrics,
    zipkinConfig = zipkin,
    versionBlock = { "jetty-version" },
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

  fun runHealthChecks(): SortedMap<String, HealthCheck.Result> = healthCheckRegistry.runHealthChecks()

  fun counter(name: String): Counter = metricRegistry.counter(name)

  fun addExtraServices(
    first: Service,
    vararg rest: Service,
  ) = addServices(first, *rest)

  init {
    extraServices?.let { (first, second) -> addExtraServices(first, second) }
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
    versionBlock = { "ktor-version" },
  ) {
  private val stopRequested = CountDownLatch(1)

  override fun run() {
    stopRequested.await()
  }

  override fun triggerShutdown() {
    stopRequested.countDown()
  }

  val healthCheckNames get() = healthCheckRegistry.names.toList()

  fun counter(name: String): Counter = metricRegistry.counter(name)

  init {
    initKtorServletService()
  }
}

// A service whose admin server fails to stop, to show that shutDown still stops everything else.
private class FailingStopService(
  metrics: MetricsConfig,
) : AbstractGenericService<String>(
    configVals = "failing-stop",
    adminConfig = disabledAdmin,
    metricsConfig = metrics,
    zipkinConfig = disabledZipkin,
  ) {
  private val stopRequested = CountDownLatch(1)

  override val servletServiceOrNull: GenericIdleService =
    object : GenericIdleService() {
      override fun startUp() = Unit

      override fun shutDown() {
        error("admin server failed to stop")
      }
    }

  override fun run() {
    stopRequested.await()
  }

  override fun triggerShutdown() {
    stopRequested.countDown()
  }

  init {
    initMetricsAndHealthChecks()
  }
}

// A service that never calls its init method.
private class UninitializedService(
  metrics: MetricsConfig,
) : GenericService<String>(
    configVals = "uninitialized",
    adminConfig = disabledAdmin,
    metricsConfig = metrics,
    zipkinConfig = disabledZipkin,
  ) {
  override fun run() = Unit
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
      val expected = ["all_services_healthy", "thread_deadlock"]
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
        ["all_services_healthy", "metrics_service", "thread_deadlock"]

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

    // Both variants, with admin and metrics paths configured with and without a leading slash.
    ["/", ""].forEach { prefix ->
      val spelling = if (prefix.isEmpty()) "without" else "with"

      "Jetty service serves every admin endpoint and metrics for paths $spelling a leading slash" {
        val (adminPort, metricsPort) = freePort() to freePort()
        val metricName = "jetty_endpoints_${spelling}_slash"
        TestJettyService(admin = enabledAdmin(adminPort, prefix), metrics = enabledMetrics(metricsPort, prefix))
          .use { service ->
            service.counter(metricName).inc()
            service.startSync()
            checkEndpoints(adminPort, metricsPort, "jetty-version", metricName)
          }
      }

      "Ktor service serves every admin endpoint and metrics for paths $spelling a leading slash" {
        val (adminPort, metricsPort) = freePort() to freePort()
        val metricName = "ktor_endpoints_${spelling}_slash"
        TestKtorService(admin = enabledAdmin(adminPort, prefix), metrics = enabledMetrics(metricsPort, prefix))
          .use { service ->
            service.counter(metricName).inc()
            service.startSync()
            checkEndpoints(adminPort, metricsPort, "ktor-version", metricName)
          }
      }
    }

    "a failed startup stops the sub-services that had already started" {
      val metricsPort = freePort()
      ServerSocket(0).use { occupied ->
        val service =
          TestJettyService(
            admin = enabledAdmin(occupied.localPort),
            metrics = enabledMetrics(metricsPort),
            zipkin = disabledZipkin.copy(enabled = true),
          )
        shouldThrow<IllegalStateException> { service.startSync() }

        service.zipkinReporterService.state() shouldBe Service.State.TERMINATED
        service.metricsService.state() shouldBe Service.State.TERMINATED
        // Binding throws if the metrics server still holds its port.
        ServerSocket(metricsPort).close()
        service.registeredShutDownHook shouldBe null
      }
    }

    "shutDown stops the remaining sub-services even when one fails to stop" {
      val service = FailingStopService(enabledMetrics(freePort()))
      service.startSync()
      service.metricsService.isRunning shouldBe true

      shouldThrow<IllegalStateException> { service.close() }

      service.metricsService.state() shouldBe Service.State.TERMINATED
      service.registeredShutDownHook shouldBe null
    }

    "a sub-service failure is logged at error level" {
      val failures =
        capturingServiceLogs { logs ->
          ServerSocket(0).use { occupied ->
            shouldThrow<IllegalStateException> {
              TestJettyService(admin = enabledAdmin(occupied.localPort)).startSync()
            }
          }
          // ServiceManager listeners run on the failing service's thread, possibly after startSync() has returned.
          val deadline = TimeSource.Monotonic.markNow() + 5.seconds
          while (logs().none { "service failed" in it.formattedMessage } && deadline.hasNotPassedNow()) {
            delay(10.milliseconds)
          }
          logs().filter { "service failed" in it.formattedMessage }
        }
      failures.isEmpty() shouldBe false
      failures.map { it.level }.toSet() shouldBe setOf(Level.ERROR)
    }

    "the Dropwizard exporter is registered with Prometheus only while the service runs" {
      val service = TestJettyService(metrics = enabledMetrics(freePort()))
      service.counter("exporter_lifecycle").inc(3)
      CollectorRegistry.defaultRegistry.getSampleValue("exporter_lifecycle") shouldBe null

      service.startSync()
      CollectorRegistry.defaultRegistry.getSampleValue("exporter_lifecycle") shouldBe 3.0

      service.close()
      CollectorRegistry.defaultRegistry.getSampleValue("exporter_lifecycle") shouldBe null
    }

    "the Zipkin URL has a single slash before the path, whether or not the path has a leading slash" {
      ["api/v2/spans", "/api/v2/spans"].forEach { path ->
        TestJettyService(zipkin = disabledZipkin.copy(enabled = true, path = path))
          .zipkinReporterService.toString() shouldContain "url=http://localhost:9411/api/v2/spans"
      }
    }

    "starting a service whose init method was never called fails with a clear message" {
      val failure = shouldThrow<IllegalStateException> { UninitializedService(enabledMetrics(freePort())).startSync() }
      failure.cause?.message.orEmpty() shouldContain "initServletService"
    }

    "calling the init method a second time fails fast instead of orphaning the first servlet service" {
      val jetty = TestJettyService(admin = enabledAdmin(freePort()))
      val jettyServletService = jetty.servletService
      shouldThrow<IllegalStateException> { jetty.initServletService() }.message.orEmpty() shouldContain
        "already initialized"
      jetty.servletService shouldBe jettyServletService

      val ktor = TestKtorService(admin = enabledAdmin(freePort()))
      val ktorServletService = ktor.servletService
      shouldThrow<IllegalStateException> { ktor.initKtorServletService() }.message.orEmpty() shouldContain
        "already initialized"
      ktor.servletService shouldBe ktorServletService
    }

    "Ktor service admin health check endpoint reports the registered checks" {
      val port = freePort()
      val service = TestKtorService(admin = disabledAdmin.copy(enabled = true, port = port))
      service.startSync()
      try {
        val response = httpGet(port, "/healthcheck")

        response.statusCode() shouldBe 200
        response.body() shouldContain "thread_deadlock"
        response.body() shouldContain "all_services_healthy"
      } finally {
        service.close()
      }
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

    "services added before init are managed, while one added after init is not and triggers a warning" {
      val warnings =
        capturingServiceLogs { logs ->
          val service = TestJettyService(extraServices = noopService("early-one") to noopService("early-two"))
          service.addExtraServices(noopService("late-one"))

          // The extra services are never started, so the aggregate check names exactly the managed ones.
          val unhealthy = service.runHealthChecks()["all_services_healthy"]?.message.orEmpty()
          unhealthy shouldContain "early-one"
          unhealthy shouldContain "early-two"
          unhealthy shouldNotContain "late-one"
          // The health check also warns about each unstarted service, so keep only warnings about the late one.
          logs().filter { it.level == Level.WARN && "late-one" in it.formattedMessage }
        }
      warnings.size shouldBe 1
    }

    "the Zipkin reporter uses the configured service name" {
      TestJettyService(zipkin = disabledZipkin.copy(enabled = true, serviceName = "configured-name"))
        .zipkinReporterService.defaultServiceName shouldBe "configured-name"
    }

    "Jetty admin and metrics servers bind only to the configured host" {
      val (adminPort, metricsPort) = freePort() to freePort()
      TestJettyService(
        admin = enabledAdmin(adminPort).copy(host = "127.0.0.1"),
        metrics = enabledMetrics(metricsPort).copy(host = "127.0.0.1"),
      ).use { service ->
        service.startSync()
        httpGet(adminPort, "/ping").statusCode() shouldBe 200
        httpGet(metricsPort, "/metrics").statusCode() shouldBe 200
        nonLoopbackAddress()?.let { address ->
          canConnect(address, adminPort) shouldBe false
          canConnect(address, metricsPort) shouldBe false
        }
      }
    }

    "Ktor admin server binds only to the configured host" {
      val adminPort = freePort()
      TestKtorService(admin = enabledAdmin(adminPort).copy(host = "127.0.0.1")).use { service ->
        service.startSync()
        httpGet(adminPort, "/ping").statusCode() shouldBe 200
        nonLoopbackAddress()?.let { address -> canConnect(address, adminPort) shouldBe false }
      }
    }

    "admin and metrics servers still bind every interface when no host is configured" {
      val (adminPort, metricsPort) = freePort() to freePort()
      TestJettyService(admin = enabledAdmin(adminPort), metrics = enabledMetrics(metricsPort)).use { service ->
        service.startSync()
        nonLoopbackAddress()?.let { address ->
          canConnect(address, adminPort) shouldBe true
          canConnect(address, metricsPort) shouldBe true
        }
      }
    }

    "both variants expose a shutDownHookAction that builds an unstarted hook thread" {
      GenericService.shutDownHookAction(noopService()).isAlive shouldBe false
      GenericKtorService.shutDownHookAction(noopService()).isAlive shouldBe false
    }
  }
}
