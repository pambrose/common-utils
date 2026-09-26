# Service Utils

Service lifecycle and management utilities: Guava-based service base classes that bundle admin endpoints,
Prometheus metrics, JMX reporting, Dropwizard health checks, and Zipkin tracing behind one `startSync`/`stopSync` pair.

This is the most dependency-heavy module in the library. Because its public API exposes types from the other
modules, most of them are `api` dependencies — subclassing a service puts core-utils, ktor-server-utils,
guava-utils, jetty-utils, dropwizard-utils, zipkin-utils, Dropwizard's `metrics-jmx` and the Prometheus Java client's
`prometheus-metrics-model` on your compile classpath. See [Dependencies](#dependencies).

## Features

### Service Base Classes

- **`AbstractGenericService<T>`**: the shared base, extending guava-utils' `GenericExecutionThreadService` and
  implementing `Closeable`; owns the health check registry, metric registry, sub-service list, and lifecycle
- **`GenericService<T>`**: hosts the admin endpoints on an embedded Jetty server
- **`GenericKtorService<T>`**: hosts the admin endpoints on an embedded Ktor CIO server

### Admin Endpoints

- **Ping, version, health check, and thread dump servlets**: registered from `AdminConfig` paths, which may be
  given with or without a leading slash. A blank path turns that endpoint off
- **`ServletGroup` / `HttpServletGroup`**: mutable path-to-servlet maps for the Jetty and Ktor variants

### Servlet Hosting Services

- **`ServletService`**: a `GenericIdleService` running an embedded Jetty server for a `ServletGroup`
- **`KtorServletService`**: a `GenericIdleService` running an embedded Ktor CIO server, mounting each servlet as a route

### Metrics and Tracing

- **`MetricsService`**: a `GenericIdleService` serving a Prometheus 1.x `PrometheusRegistry` from an embedded Jetty
  server through the client's `PrometheusMetricsServlet`, with a Dropwizard `healthCheck` property
- **`ZipkinReporterService`**: a `GenericIdleService` managing an `AsyncReporter` and `OkHttpSender`, with a
  `newTracing` factory for Brave `Tracing` instances

### Configuration

- **`AdminConfig`**, **`MetricsConfig`**, **`ZipkinConfig`**: data classes for the admin, Prometheus, and Zipkin
  settings, each with an optional `host` to restrict the bind address. `MetricsConfig`'s nine `*ExportsEnabled` flags
  select the metric sets passed to prometheus-utils' `SystemMetrics.initialize`

## Usage Examples

### A Jetty-hosted Service

A subclass supplies the configuration objects, overrides Guava's `run()` and `triggerShutdown()`, and calls
`initServletService()` from its `init` block.

```kotlin
import com.pambrose.common.service.AdminConfig
import com.pambrose.common.service.GenericService
import com.pambrose.common.service.MetricsConfig
import com.pambrose.common.service.ZipkinConfig
import java.util.concurrent.CountDownLatch

class MyService(configVals: MyConfig) :
  GenericService<MyConfig>(
    configVals = configVals,
    adminConfig = AdminConfig(
      enabled = true,
      port = 8080,
      pingPath = "/ping",
      versionPath = "/version",
      healthCheckPath = "/healthcheck",
      threadDumpPath = "/threaddump",
      host = "127.0.0.1",          // null (the default) binds every interface
    ),
    metricsConfig = MetricsConfig(
      enabled = true,
      port = 9090,
      path = "metrics",             // a leading slash is optional
      standardExportsEnabled = true,
      memoryPoolsExportsEnabled = true,
      garbageCollectorExportsEnabled = true,
      threadExportsEnabled = true,
      classLoadingExportsEnabled = true,
      versionInfoExportsEnabled = true,
      bufferPoolExportsEnabled = true,    // these three default to false
      compilationExportsEnabled = true,
      nativeMemoryExportsEnabled = true,  // needs -XX:NativeMemoryTracking=summary (or detail)
    ),
    zipkinConfig = ZipkinConfig(
      enabled = true,
      hostname = "localhost",
      port = 9411,
      path = "api/v2/spans",
      serviceName = "my-service",
    ),
    versionBlock = { "1.2.3" },
  ) {
  private val stopRequested = CountDownLatch(1)

  override fun run() {
    stopRequested.await()
  }

  override fun triggerShutdown() {
    stopRequested.countDown()
  }

  init {
    // Add any extra Guava services first, then initialize exactly once
    initServletService()
  }
}

MyService(configVals).use { service ->
  service.startSync()   // from GenericExecutionThreadService, default timeout 30 seconds
  // ...
}                       // close() calls stopSync()
```

Registering extra admin servlets is done through the `servletInit` block:

```kotlin
init {
  initServletService {
    addServlet("/custom", MyCustomServlet())
  }
}
```

### A Ktor-hosted Service

`GenericKtorService` takes the same configuration. Its init method accepts both a Ktor `Application` block and a
servlet block:

```kotlin
import com.pambrose.common.service.GenericKtorService
import com.pambrose.common.service.HttpServletGroup
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression // add io.ktor:ktor-server-compression yourself

class MyKtorService(configVals: MyConfig) :
  GenericKtorService<MyConfig>(
    configVals = configVals,
    adminConfig = adminConfig,
    metricsConfig = metricsConfig,
    zipkinConfig = zipkinConfig,
    versionBlock = { "1.2.3" },
  ) {
  override fun run() { /* ... */ }

  init {
    initKtorServletService(
      initKtor = { install(Compression) },
      servletInit = { addServlets("/custom" to MyCustomServlet()) },
    )
  }
}
```

service-utils uses Ktor's compression and call-logging plugins only as runtime `implementation` dependencies, so
they are not on your compile classpath. To install `Compression` as above, depend on
`io.ktor:ktor-server-compression` yourself.

### Health Checks and Metrics

`healthCheckRegistry` and `metricRegistry` are `protected`, so expose whatever a subclass needs:

```kotlin
class MyService(/* ... */) : GenericService<MyConfig>(/* ... */) {
  val requestCounter = metricRegistry.counter("my_service_requests")

  override fun registerHealthChecks() {
    super.registerHealthChecks()   // keep thread_deadlock, all_services_healthy, metrics_service
    healthCheckRegistry.register("database", myDatabaseHealthCheck)
  }
}
```

Out of the box the registry holds `thread_deadlock` and `all_services_healthy`, plus `metrics_service` when
metrics are enabled. The aggregate check reports unhealthy while any managed service is not `RUNNING`, and names
the offending services in its message.

### Adding Managed Sub-services

```kotlin
init {
  addService(myBackgroundService)
  addServices(firstService, secondService)
  initServletService()
}
```

Added services are handed to the Guava `ServiceManager` and the `all_services_healthy` check. This module never
starts or stops them — that stays with the caller.

### Zipkin Tracing

```kotlin
import com.pambrose.common.service.ZipkinReporterService

val reporter = ZipkinReporterService("http://localhost:9411/api/v2/spans", "my-service")
reporter.startSync()

val tracing = reporter.newTracing()              // uses defaultServiceName
val scoped = reporter.newTracing("sub-system")   // or an explicit name

tracing.tracer().nextSpan().name("work").start().finish()

reporter.stopSync()   // flushes queued spans, then closes the reporter and sender
```

When a `GenericService` or `GenericKtorService` has Zipkin enabled, it builds this service itself and exposes it
as `zipkinReporterService`, with the URL assembled as
`http://{hostname}:{port}/{path}` (a leading slash on `path` does not produce a double slash).

### Standalone Metrics Service

`MetricsService` can be used on its own, outside a `GenericService`:

```kotlin
import com.pambrose.common.service.MetricsService
import io.prometheus.metrics.model.registry.PrometheusRegistry

val metrics = MetricsService(port = 9090, path = "metrics", host = "127.0.0.1")
metrics.startSync()
metrics.healthCheck.execute().isHealthy   // true while the Jetty server runs
metrics.stopSync()

// Serve a registry other than PrometheusRegistry.defaultRegistry
val registry = PrometheusRegistry()
val isolated = MetricsService(port = 9091, path = "metrics", registry = registry)
```

A plain scrape gets the Prometheus text format (`Content-Type: text/plain; version=0.0.4`); a scrape sending
`Accept: application/openmetrics-text; version=1.0.0` gets OpenMetrics.

## API Reference

### `AbstractGenericService<T>`

- `abstract class AbstractGenericService<T> protected constructor(configVals: T, adminConfig: AdminConfig, metricsConfig: MetricsConfig, zipkinConfig: ZipkinConfig, isTestMode: Boolean = false) : GenericExecutionThreadService(), Closeable`
- `val configVals: T`, `val isTestMode: Boolean`, `val upTime: Duration`
- `val isAdminEnabled: Boolean`, `val isMetricsEnabled: Boolean`, `val isZipkinEnabled: Boolean`
- `lateinit var jmxReporter: JmxReporter`, `lateinit var metricsService: MetricsService`,
  `lateinit var zipkinReporterService: ZipkinReporterService` — initialized only when the matching feature is enabled
- `protected val healthCheckRegistry: HealthCheckRegistry`, `protected val metricRegistry: MetricRegistry`,
  `protected val services: MutableList<Service>`, `protected val startTime: TimeMark`
- `protected abstract val servletServiceOrNull: GenericIdleService?`
- `protected fun initMetricsAndHealthChecks()`, `protected fun addService(service: Service)`,
  `protected fun addServices(service: Service, vararg services: Service)`,
  `protected open fun registerHealthChecks()`
- `override fun startUp()`, `override fun shutDown()`, `override fun close()` — `close()` calls `stopSync()`
- `companion object { fun shutDownHookAction(service: Service): Thread }` — an unstarted thread that calls
  `stopAsync()` then `awaitTerminated()`

### `GenericService<T>` and `GenericKtorService<T>`

- `abstract class GenericService<T> protected constructor(configVals: T, adminConfig: AdminConfig, metricsConfig: MetricsConfig, zipkinConfig: ZipkinConfig, versionBlock: () -> String = { "No version" }, isTestMode: Boolean = false)`
- `GenericService.servletService: ServletService`, `fun initServletService(servletInit: ServletGroup.() -> Unit = {})`
- `abstract class GenericKtorService<T>` — the same constructor shape
- `GenericKtorService.servletService: KtorServletService`,
  `fun initKtorServletService(initKtor: Application.() -> Unit = {}, servletInit: HttpServletGroup.() -> Unit = {})`
- Both expose `companion object { fun shutDownHookAction(service: Service): Thread }`, delegating to
  `AbstractGenericService.shutDownHookAction`

### Servlet Groups and Hosting Services

- `class ServletGroup` — `addServlet(path: String, servlet: jakarta.servlet.Servlet)`
- `class HttpServletGroup` — `addServlet(path: String, servlet: HttpServlet)`,
  `addServlets(vararg servlets: Pair<String, HttpServlet>)`
- Both silently ignore empty or blank paths, add a missing leading slash, and let a later servlet at the same
  path replace the earlier one — so `"ping"` and `"/ping"` name one endpoint
- `class ServletService(port: Int, servletGroup: ServletGroup, host: String? = null, initBlock: ServletService.() -> Unit = {}) : GenericIdleService`
- `class KtorServletService(port: Int, servletGroup: HttpServletGroup, initKtor: Application.() -> Unit = {}, host: String? = null, initBlock: KtorServletService.() -> Unit = {}) : GenericIdleService`

### Metrics and Tracing Services

- `class MetricsService(port: Int, path: String, host: String? = null, registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry, initBlock: MetricsService.() -> Unit = {}) : GenericIdleService` —
  `val healthCheck: HealthCheck`
- `class ZipkinReporterService(url: String, defaultServiceName: String = "unknown", initBlock: ZipkinReporterService.() -> Unit = {}) : GenericIdleService` —
  `val defaultServiceName: String`, `fun newTracing(serviceName: String = defaultServiceName): Tracing`

### Configuration

- `data class AdminConfig(enabled: Boolean, port: Int, pingPath: String, versionPath: String, healthCheckPath: String, threadDumpPath: String, host: String? = null)`
- `data class MetricsConfig(enabled: Boolean, port: Int, path: String, standardExportsEnabled: Boolean, memoryPoolsExportsEnabled: Boolean, garbageCollectorExportsEnabled: Boolean, threadExportsEnabled: Boolean, classLoadingExportsEnabled: Boolean, versionInfoExportsEnabled: Boolean, host: String? = null, bufferPoolExportsEnabled: Boolean = false, compilationExportsEnabled: Boolean = false, nativeMemoryExportsEnabled: Boolean = false)`
- `data class ZipkinConfig(enabled: Boolean, hostname: String, port: Int, path: String, serviceName: String)`

## Dependencies

Types from these modules appear in the public API — as supertypes, public and protected members, and
constructor and function parameters — so they are `api` dependencies and land on a consumer's compile classpath:

- core-utils
- ktor-server-utils
- guava-utils — `GenericExecutionThreadService` and `GenericIdleService` supertypes
- jetty-utils
- dropwizard-utils — `HealthCheckRegistry`, `MetricRegistry`, `HealthCheck`
- zipkin-utils — Brave `Tracing`
- Dropwizard `metrics-jmx` — the `JmxReporter` property
- Prometheus `prometheus-metrics-model` 1.x — `PrometheusRegistry`, a `MetricsService` constructor parameter

Also used, but as `implementation` details that consumers do not inherit:

- prometheus-utils, and the Prometheus 1.x Jakarta servlet exporter (`prometheus-metrics-exporter-servlet-jakarta`)
  and Dropwizard bridge (`prometheus-metrics-instrumentation-dropwizard`)
- Dropwizard Jakarta metrics servlets
- Ktor server CIO, call logging, and compression
- Zipkin OkHttp sender

Jakarta `HttpServlet` and `Servlet` types appear in the servlet-group API, and Ktor's `Application` in
`initKtorServletService`.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/service-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/service-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:service-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>service-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Initialization

A service must call its init method — `initServletService()` for `GenericService`,
`initKtorServletService()` for `GenericKtorService` — exactly once, before it starts:

- **Never called**: `startUp()` throws `IllegalStateException` with the message
  `Call initServletService() or initKtorServletService() before starting <class name>`.
- **Called twice**: the second call throws `IllegalStateException` ending in `is already initialized`, and fails
  fast rather than replacing the admin servlet service and orphaning the first one.

Call `addService` / `addServices` **before** the init method. The Guava `ServiceManager` is built there from a
copy of the service list, so a service added afterwards is not managed, is absent from the
`all_services_healthy` check, and logs a warning.

## Lifecycle Notes

- **Startup rollback**: Guava does not call `shutDown()` when `startUp()` throws, so a failure stops whatever
  already started, most recent first, and attaches those failures to the original exception as suppressed
  exceptions. Ports and threads are not left behind.
- **Shutdown completeness**: every shutdown step runs even when an earlier one fails, so one sub-service failing
  to stop cannot leave the others running. The first failure is rethrown with the rest suppressed.
- **Prometheus exporter registration**: the Dropwizard-to-Prometheus bridge (`DropwizardExports`) is registered with
  `PrometheusRegistry.defaultRegistry` in `startUp()` and unregistered on shutdown. Repeated start/stop cycles
  therefore do not accumulate collectors, and a stopped instance leaves nothing registered, so starting another one
  afterwards adds no duplicate families. Two instances running at once whose Dropwizard registries share metric
  names now collide at scrape time: `DropwizardExports.describe()` returns no family descriptors in both 0.16 and
  1.9.0, so both instances register. 0.16 tolerated this — a scrape served duplicate families, and Prometheus's
  parser drops the duplicate samples but keeps the scrape. 1.9.0 does not: `TextFormatUtil.mergeDuplicates` throws
  `DuplicateLabelsException` from the `MetricSnapshot` constructor at write time, which `PrometheusScrapeHandler`
  turns into an HTTP 500 — every scrape of the default registry fails and all metrics are lost while both instances
  run. The same happens when a Dropwizard metric's exposed name equals a native 1.x metric of the same type, e.g. a
  Dropwizard `Counter` named `x` alongside a `PrometheusDsl` counter also named `x`. A Dropwizard `Counter` is
  exposed as a Prometheus counter named `<name>_total`; timers and histograms become summaries, meters `_total`
  counters and gauges gauges.
- **Shutdown hook**: registered at the end of a successful `startUp()` and removed during `shutDown()`. Removal
  failures — an `IllegalStateException` when the JVM is already shutting down, for instance — are swallowed
  rather than failing the shutdown.

## Zipkin Delivery on Shutdown

`ZipkinReporterService` builds its `AsyncReporter` with a **500 ms** message timeout, while leaving the
library's **1 second** close timeout in place. The flusher thread holds a batch for up to the message timeout
before sending it, and `close()` waits only the close timeout for that thread. Keeping the message timeout the
shorter of the two lets a span finished just before a graceful stop go out before `close()` gives up, without
making shutdown any slower.

`shutDown()` flushes queued spans before closing, since `close()` drops whatever is still queued. A flush that
throws does not prevent the reporter and the sender from closing, and the sender is closed even when closing the
reporter fails.

## Bind Addresses and Paths

- A `null` `host` (the default on `AdminConfig`, `MetricsConfig`, and the services) binds every interface; set it
  to `"127.0.0.1"` to accept only local connections.
- Admin and metrics paths may be written with or without a leading slash — `"ping"` and `"/ping"` reach the same
  endpoint.
- A blank admin path disables that endpoint on both variants; it is never served at `/`.
- The Jetty admin and metrics servers send no `Server` header and no version on error pages, and their error
  pages carry no stack traces.

## License

Licensed under the Apache License, Version 2.0.
