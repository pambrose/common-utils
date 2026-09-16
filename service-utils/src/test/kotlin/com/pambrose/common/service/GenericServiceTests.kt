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
import com.pambrose.common.metrics.SystemMetrics
import com.pambrose.common.servlet.VersionServlet
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.Enabled
import io.kotest.core.test.EnabledOrReasonIf
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.prometheus.client.CollectorRegistry
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.SortedMap
import java.util.concurrent.CountDownLatch
import javax.management.ObjectName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private fun canConnect(
  address: InetAddress,
  port: Int,
) = Socket().use { socket -> runCatching { socket.connect(InetSocketAddress(address, port), 1_000) }.isSuccess }

// An IPv4 address of an active non-loopback interface that a listener on every interface can be reached through, or
// null when there is none, such as on a machine with no network or behind a firewall that drops the connection.
private val reachableNonLoopbackAddress: InetAddress? by lazy {
  ServerSocket(0).use { probe ->
    Collections.list(NetworkInterface.getNetworkInterfaces())
      .filter { it.isUp && !it.isLoopback }
      .flatMap { Collections.list(it.inetAddresses) }
      .filterIsInstance<Inet4Address>()
      .firstOrNull { canConnect(it, probe.localPort) }
  }
}

// Skips a test that connects through reachableNonLoopbackAddress when there is none, so the report shows the skip
// rather than a pass that tested nothing.
private val needsNonLoopbackAddress: EnabledOrReasonIf = {
  if (reachableNonLoopbackAddress != null)
    Enabled.enabled
  else
    Enabled.disabled("no non-loopback IPv4 address accepts local connections")
}

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

// Admin paths spelled with the given prefix: "/ping" and "ping" must reach the same endpoint. Port 0 lets the OS
// choose a free port when the server starts, so parallel test JVMs cannot race for one. The enabled configs bind to
// LOOPBACK, which the tests send their requests to.
private fun enabledAdmin(
  port: Int = 0,
  prefix: String = "/",
) = disabledAdmin.copy(
  enabled = true,
  port = port,
  pingPath = "${prefix}ping",
  versionPath = "${prefix}version",
  healthCheckPath = "${prefix}healthcheck",
  threadDumpPath = "${prefix}threaddump",
  host = LOOPBACK,
)

private fun enabledMetrics(prefix: String = "") =
  disabledMetrics.copy(enabled = true, path = "${prefix}metrics", host = LOOPBACK)

// The port a started service's admin server listens on.
private val AbstractGenericService<*>.adminPort: Int
  get() =
    when (this) {
      is GenericService<*> -> servletService.boundPort
      is GenericKtorService<*> -> servletService.boundPort
      else -> error("${this::class.simpleName} has no admin server")
    }

// Requests every admin endpoint and the metrics endpoint of a running service.
private fun checkEndpoints(
  service: AbstractGenericService<*>,
  version: String,
  metricName: String,
) {
  httpGet(service.adminPort, "/ping").apply {
    statusCode() shouldBe 200
    body().trim() shouldBe "pong"
  }
  httpGet(service.adminPort, "/version").apply {
    statusCode() shouldBe 200
    body().trim() shouldBe version
  }
  httpGet(service.adminPort, "/healthcheck").apply {
    statusCode() shouldBe 200
    body() shouldContain "all_services_healthy"
  }
  httpGet(service.adminPort, "/threaddump").apply {
    statusCode() shouldBe 200
    body() shouldContain "state="
  }
  httpGet(service.metricsService.boundPort, "/metrics").apply {
    statusCode() shouldBe 200
    body() shouldContain metricName
  }
}

private class TestJettyService(
  admin: AdminConfig = disabledAdmin,
  metrics: MetricsConfig = disabledMetrics,
  zipkin: ZipkinConfig = disabledZipkin,
  extraServices: Pair<Service, Service>? = null,
  servletInit: ServletGroup.() -> Unit = {},
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
    initServletService(servletInit)
  }
}

private class TestKtorService(
  admin: AdminConfig = disabledAdmin,
  metrics: MetricsConfig = disabledMetrics,
  zipkin: ZipkinConfig = disabledZipkin,
  initKtor: Application.() -> Unit = {},
  servletInit: HttpServletGroup.() -> Unit = {},
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
    initKtorServletService(initKtor, servletInit)
  }
}

// The two admin server variants, so the lifecycle tests run against both.
private enum class Variant {
  Jetty,
  Ktor,
  ;

  fun newService(
    admin: AdminConfig = disabledAdmin,
    metrics: MetricsConfig = disabledMetrics,
    zipkin: ZipkinConfig = disabledZipkin,
  ): AbstractGenericService<String> =
    when (this) {
      Jetty -> TestJettyService(admin, metrics, zipkin)
      Ktor -> TestKtorService(admin, metrics, zipkin)
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
          admin = enabledAdmin(),
          metrics = enabledMetrics(),
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
      // No requests are sent, so the default of every interface is safe here.
      val service = TestKtorService(admin = enabledAdmin().copy(host = null))
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
        val metricName = "jetty_endpoints_${spelling}_slash"
        TestJettyService(admin = enabledAdmin(prefix = prefix), metrics = enabledMetrics(prefix)).use { service ->
          service.counter(metricName).inc()
          service.startSync()
          checkEndpoints(service, "jetty-version", metricName)
        }
      }

      "Ktor service serves every admin endpoint and metrics for paths $spelling a leading slash" {
        val metricName = "ktor_endpoints_${spelling}_slash"
        TestKtorService(admin = enabledAdmin(prefix = prefix), metrics = enabledMetrics(prefix)).use { service ->
          service.counter(metricName).inc()
          service.startSync()
          checkEndpoints(service, "ktor-version", metricName)
        }
      }
    }

    Variant.entries.forEach { variant ->
      "a failed $variant startup stops the sub-services that had already started" {
        occupiedLoopbackPort().use { occupied ->
          val service =
            variant.newService(
              admin = enabledAdmin(occupied.localPort),
              metrics = enabledMetrics(),
              zipkin = disabledZipkin.copy(enabled = true),
            )
          shouldThrow<IllegalStateException> { service.startSync() }

          service.state() shouldBe Service.State.FAILED
          service.zipkinReporterService.state() shouldBe Service.State.TERMINATED
          service.metricsService.state() shouldBe Service.State.TERMINATED
          shouldBeReleased(service.metricsService.boundPort)
          service.registeredShutDownHook shouldBe null
        }
      }

      "the $variant health check reports a sub-service that stopped while the service runs" {
        variant.newService(admin = enabledAdmin(), metrics = enabledMetrics()).use { service ->
          service.startSync()
          httpGet(service.adminPort, "/healthcheck").statusCode() shouldBe 200

          service.metricsService.stopSync()

          // The ServiceManager learns of the stop from a listener that can still be running when stopSync()
          // returns, so all_services_healthy may briefly report the service as STOPPING.
          eventually(5.seconds) {
            httpGet(service.adminPort, "/healthcheck").apply {
              statusCode() shouldBe 500
              // all_services_healthy names only the stopped service, leaving out the ones still running.
              body() shouldContain "Incorrect state: TERMINATED: ${service.metricsService}"
              body() shouldNotContain "RUNNING:"
              // metrics_service notices it too.
              body() shouldContain "Jetty server not running"
            }
          }
        }
      }

      "closing a never-started $variant service, or closing one twice, succeeds" {
        variant.newService(admin = enabledAdmin()).apply {
          close()
          state() shouldBe Service.State.TERMINATED
          close()
          state() shouldBe Service.State.TERMINATED
        }

        variant.newService(admin = enabledAdmin()).apply {
          startSync()
          close()
          close()
          state() shouldBe Service.State.TERMINATED
          registeredShutDownHook shouldBe null
        }
      }

      "closing a $variant service releases its admin and metrics ports" {
        val service = variant.newService(admin = enabledAdmin(), metrics = enabledMetrics())
        service.startSync()
        val ports = [service.adminPort, service.metricsService.boundPort]

        service.close()

        ports.forEach { shouldBeReleased(it) }
      }
    }

    "shutDown stops the remaining sub-services even when one fails to stop" {
      val service = FailingStopService(enabledMetrics())
      service.startSync()
      service.metricsService.isRunning shouldBe true

      shouldThrow<IllegalStateException> { service.close() }

      service.metricsService.state() shouldBe Service.State.TERMINATED
      service.registeredShutDownHook shouldBe null
    }

    "a sub-service failure is logged at error level" {
      val failures =
        capturingServiceLogs { logs ->
          occupiedLoopbackPort().use { occupied ->
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
      val service = TestJettyService(metrics = enabledMetrics())
      service.counter("exporter_lifecycle").inc(3)
      CollectorRegistry.defaultRegistry.getSampleValue("exporter_lifecycle") shouldBe null

      service.startSync()
      CollectorRegistry.defaultRegistry.getSampleValue("exporter_lifecycle") shouldBe 3.0

      service.close()
      CollectorRegistry.defaultRegistry.getSampleValue("exporter_lifecycle") shouldBe null
    }

    "the JMX reporter exposes the service's metrics only while it runs" {
      val mbeanServer = ManagementFactory.getPlatformMBeanServer()
      val counterName = ObjectName("metrics:name=jmx_reporter_lifecycle,type=counters")
      val service = TestJettyService(metrics = enabledMetrics())
      service.counter("jmx_reporter_lifecycle").inc(2)
      mbeanServer.isRegistered(counterName) shouldBe false

      service.use {
        service.startSync()
        mbeanServer.getAttribute(counterName, "Count") shouldBe 2L
      }

      mbeanServer.isRegistered(counterName) shouldBe false
    }

    "each metrics export flag reaches its own SystemMetrics.initialize parameter" {
      // In SystemMetrics.initialize parameter order, each with a single flag set.
      val oneFlagEach =
        [
          disabledMetrics.copy(standardExportsEnabled = true),
          disabledMetrics.copy(memoryPoolsExportsEnabled = true),
          disabledMetrics.copy(garbageCollectorExportsEnabled = true),
          disabledMetrics.copy(threadExportsEnabled = true),
          disabledMetrics.copy(classLoadingExportsEnabled = true),
          disabledMetrics.copy(versionInfoExportsEnabled = true),
        ]
      val flagsPassed: MutableList<List<Any?>> = []
      mockkObject(SystemMetrics)
      try {
        every { SystemMetrics.initialize(any(), any(), any(), any(), any(), any(), any()) } answers {
          flagsPassed += args.take(oneFlagEach.size)
        }

        oneFlagEach.forEach { TestJettyService(metrics = it.copy(enabled = true)) }
      } finally {
        unmockkObject(SystemMetrics)
      }

      // Compared call by call: a flag passed to the wrong parameter moves that call's single true value.
      flagsPassed shouldBe oneFlagEach.indices.map { index -> List(oneFlagEach.size) { it == index } }
    }

    "the Zipkin URL has a single slash before the path, whether or not the path has a leading slash" {
      ["api/v2/spans", "/api/v2/spans"].forEach { path ->
        TestJettyService(zipkin = disabledZipkin.copy(enabled = true, path = path))
          .zipkinReporterService.toString() shouldContain "url=http://localhost:9411/api/v2/spans"
      }
    }

    "starting a service whose init method was never called fails with a clear message" {
      val failure = shouldThrow<IllegalStateException> { UninitializedService(enabledMetrics()).startSync() }
      failure.cause?.message.orEmpty() shouldContain "initServletService"
    }

    "calling the init method a second time fails fast instead of orphaning the first servlet service" {
      val jetty = TestJettyService(admin = enabledAdmin())
      val jettyServletService = jetty.servletService
      shouldThrow<IllegalStateException> { jetty.initServletService() }.message.orEmpty() shouldContain
        "already initialized"
      jetty.servletService shouldBe jettyServletService

      val ktor = TestKtorService(admin = enabledAdmin())
      val ktorServletService = ktor.servletService
      shouldThrow<IllegalStateException> { ktor.initKtorServletService() }.message.orEmpty() shouldContain
        "already initialized"
      ktor.servletService shouldBe ktorServletService
    }

    "Ktor service admin health check endpoint reports the registered checks" {
      val service = TestKtorService(admin = enabledAdmin())
      service.startSync()
      service.use { service ->
        val response = httpGet(service.adminPort, "/healthcheck")

        response.statusCode() shouldBe 200
        response.body() shouldContain "thread_deadlock"
        response.body() shouldContain "all_services_healthy"
      }
    }

    "an admin-disabled Ktor service still serves metrics and stops cleanly" {
      val service = TestKtorService(metrics = enabledMetrics())
      service.use {
        service.startSync()
        shouldThrow<UninitializedPropertyAccessException> { service.servletService }
        httpGet(service.metricsService.boundPort, "/metrics").statusCode() shouldBe 200
      }

      service.state() shouldBe Service.State.TERMINATED
      service.metricsService.state() shouldBe Service.State.TERMINATED
    }

    "servletInit adds a servlet to the Jetty admin server" {
      TestJettyService(
        admin = enabledAdmin(),
        servletInit = { addServlet("added", VersionServlet("added-servlet")) },
      ).use { service ->
        service.startSync()
        httpGet(service.adminPort, "/added").body().trim() shouldBe "added-servlet"
        httpGet(service.adminPort, "/ping").body().trim() shouldBe "pong"
      }
    }

    "servletInit and initKtor add endpoints to the Ktor admin server" {
      TestKtorService(
        admin = enabledAdmin(),
        initKtor = { routing { get("/added-route") { call.respondText("added-route") } } },
        servletInit = { addServlet("/added", VersionServlet("added-servlet")) },
      ).use { service ->
        service.startSync()
        httpGet(service.adminPort, "/added-route").body() shouldBe "added-route"
        httpGet(service.adminPort, "/added").body().trim() shouldBe "added-servlet"
        httpGet(service.adminPort, "/ping").body().trim() shouldBe "pong"
      }
    }

    "shutdown hook is registered on start and removed on stop" {
      val service = TestJettyService(admin = enabledAdmin())
      service.startSync()
      val hook = service.registeredShutDownHook ?: error("expected a shutdown hook to be registered after start")

      service.close()

      service.registeredShutDownHook shouldBe null
      // Removing it again reports false, proving shutDown already de-registered it (no leak).
      Runtime.getRuntime().removeShutdownHook(hook) shouldBe false
    }

    "running the shutdown hook stops the service, releases its ports, and clears the hook" {
      val service = TestJettyService(admin = enabledAdmin(), metrics = enabledMetrics())
      service.startSync()
      val hook = service.registeredShutDownHook ?: error("expected a shutdown hook to be registered after start")

      hook.run()

      service.state() shouldBe Service.State.TERMINATED
      service.registeredShutDownHook shouldBe null
      Runtime.getRuntime().removeShutdownHook(hook) shouldBe false
      shouldBeReleased(service.adminPort)
      shouldBeReleased(service.metricsService.boundPort)
    }

    "close() succeeds when the JVM refuses to remove the shutdown hook" {
      val service = TestJettyService()
      service.startSync()
      val hook = service.registeredShutDownHook ?: error("expected a shutdown hook to be registered after start")

      // removeShutdownHook throws this once the JVM has begun shutting down.
      val runtime = spyk(Runtime.getRuntime())
      every { runtime.removeShutdownHook(any()) } throws IllegalStateException("Shutdown in progress")
      mockkStatic(Runtime::class)
      try {
        every { Runtime.getRuntime() } returns runtime
        shouldNotThrowAny { service.close() }
        verify { runtime.removeShutdownHook(hook) }
      } finally {
        unmockkStatic(Runtime::class)
      }

      service.state() shouldBe Service.State.TERMINATED
      service.registeredShutDownHook shouldBe null
      // The refused removal left the hook registered, so remove it for real before the JVM exits.
      Runtime.getRuntime().removeShutdownHook(hook) shouldBe true
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

    "Jetty admin and metrics servers bind only to the configured host"
      .config(enabledOrReasonIf = needsNonLoopbackAddress) {
        TestJettyService(admin = enabledAdmin(), metrics = enabledMetrics()).use { service ->
          service.startSync()
          val (adminPort, metricsPort) = service.adminPort to service.metricsService.boundPort
          httpGet(adminPort, "/ping").statusCode() shouldBe 200
          httpGet(metricsPort, "/metrics").statusCode() shouldBe 200

          val address = checkNotNull(reachableNonLoopbackAddress)
          canConnect(address, adminPort) shouldBe false
          canConnect(address, metricsPort) shouldBe false
        }
      }

    "Ktor admin server binds only to the configured host".config(enabledOrReasonIf = needsNonLoopbackAddress) {
      TestKtorService(admin = enabledAdmin()).use { service ->
        service.startSync()
        httpGet(service.adminPort, "/ping").statusCode() shouldBe 200
        canConnect(checkNotNull(reachableNonLoopbackAddress), service.adminPort) shouldBe false
      }
    }

    "admin and metrics servers still bind every interface when no host is configured"
      .config(enabledOrReasonIf = needsNonLoopbackAddress) {
        TestJettyService(
          admin = enabledAdmin().copy(host = null),
          metrics = enabledMetrics().copy(host = null),
        ).use { service ->
          service.startSync()
          val address = checkNotNull(reachableNonLoopbackAddress)
          canConnect(address, service.adminPort) shouldBe true
          canConnect(address, service.metricsService.boundPort) shouldBe true
        }
      }

    "both variants expose a shutDownHookAction that builds an unstarted hook thread" {
      GenericService.shutDownHookAction(noopService()).isAlive shouldBe false
      GenericKtorService.shutDownHookAction(noopService()).isAlive shouldBe false
    }
  }
}
