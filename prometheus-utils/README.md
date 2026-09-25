# Prometheus Utils

Helpers for the Prometheus Java client 1.x (`io.prometheus:prometheus-metrics-*`): a builder DSL for the four
metric types, a sampling gauge collector, an instrumented `ThreadFactory`, and a one-call JVM metrics registration.

Before 5.0.0 this module was built on the 0.x client (`simpleclient`). See the
[5.0.0 migration notes](../RELEASE_NOTES.md#migrating-from-4x-prometheus-java-client-1x) for what changed.

## Features

### Metrics DSL

- **`PrometheusDsl`**: `counter`, `gauge`, `summary` and `histogram` builders that register the metric

### Collectors

- **`SamplerGaugeCollector`**: a gauge whose value is sampled by a lambda on each scrape
- **`InstrumentedThreadFactory`**: wraps a `ThreadFactory` and exports thread lifecycle counters

### JVM Metrics

- **`SystemMetrics.initialize(...)`**: registers the JVM and process metric sets you opt into

## Usage Examples

### Metrics DSL

Each builder takes the corresponding 1.x `Builder` (`io.prometheus.metrics.core.metrics.Counter.Builder` and so
on) as receiver, so you set `name`, `help`, `labelNames`, and so on exactly as with the Java client. The metric is
registered for you, with `PrometheusRegistry.defaultRegistry` unless you pass another registry, such as an isolated
`PrometheusRegistry()` in tests: `PrometheusDsl.counter(registry) { ... }`.

The 1.x builders have no `namespace()` or `subsystem()`, so put the full name in `name(...)`. A counter is exposed
as `<name>_total` whether or not its name already ends in `_total`. Registering a name the registry already holds
with the same label names throws `IllegalArgumentException`.

```kotlin
import com.pambrose.common.dsl.PrometheusDsl

val requestCounter =
  PrometheusDsl.counter {
    name("http_requests_total")
    help("Total HTTP requests")
    labelNames("method", "endpoint", "status")
  }

val activeConnections =
  PrometheusDsl.gauge {
    name("active_connections")
    help("Currently active connections")
  }

val requestDuration =
  PrometheusDsl.histogram {
    name("http_request_duration_seconds")
    help("HTTP request duration")
    classicUpperBounds(0.1, 0.5, 1.0, 2.5, 5.0)
  }

val responseSize =
  PrometheusDsl.summary {
    name("http_response_size_bytes")
    help("HTTP response size")
  }
```

Use them through the normal client API:

```kotlin
requestCounter.labelValues("GET", "/api/users", "200").inc()
activeConnections.inc()
activeConnections.dec()

requestDuration.startTimer().use { handleRequest() } // observes the duration when closed
```

### SamplerGaugeCollector

A gauge backed by a lambda, sampled on every scrape rather than set imperatively. It **registers itself
when constructed**, with the default registry unless you pass `registry`, so simply creating it is enough.
Registering does not run the lambda; the first scrape does.

```kotlin
import com.pambrose.common.metrics.SamplerGaugeCollector

SamplerGaugeCollector(
  name = "jvm_free_memory_bytes",
  help = "Free JVM memory in bytes",
) {
  Runtime.getRuntime().freeMemory().toDouble()
}

// With labels: labelNames and labelValues must be the same length. The labels are constants on the collector's single
// series; a registry rejects a second collector with the same name and label names, so a second "queue_depth"
// for another queue is rejected
SamplerGaugeCollector(
  name = "queue_depth",
  help = "Pending items in the queue",
  labelNames = listOf("queue"),
  labelValues = listOf("outbound"),
) {
  outboundQueue.size.toDouble()
}
```

Mismatched `labelNames` / `labelValues` sizes, an empty metric name, an invalid label name (`__x`), a repeated label
name (`a.b` and `a_b` count as the same), and a name the registry already holds with the same label names all throw
`IllegalArgumentException`, rather than producing an exposition Prometheus rejects. As in the 1.x client, any
non-empty UTF-8 string is a valid metric name (`queue-depth` included); the exposition formats escape it.

The collector is a 1.x `io.prometheus.metrics.model.registry.Collector`, so you remove it with
`registry.unregister(collector)`.

### InstrumentedThreadFactory

Wraps an existing `ThreadFactory` and exports counters for threads created and terminated, and a gauge for threads
running: `<name>_threads_created_total`, `<name>_threads_terminated_total` and `<name>_threads_running`. Pass
`registry` to register the metrics somewhere other than the default registry; two factories with the same
`name` need separate registries. If any of the three metric names is already taken, the constructor throws
`IllegalArgumentException` and leaves none of them registered. When the delegate rejects a thread by returning
`null`, `newThread` returns `null` too, and the thread is not counted.

```kotlin
import com.pambrose.common.concurrent.InstrumentedThreadFactory
import java.util.concurrent.Executors

val factory =
  InstrumentedThreadFactory(
    delegate = Executors.defaultThreadFactory(),
    name = "worker_pool",
    help = "Worker pool threads",
  )

val executor = Executors.newFixedThreadPool(4, factory)
```

### JVM Metrics

Every metric set is **off by default** — opt into the ones you want. Calling `initialize` again is safe: a set
registered by an earlier call is skipped, and one requested for the first time is registered. Each set is registered
all or nothing. A set whose metrics are already registered elsewhere, for example by the client's
`JvmMetrics.builder().register()`, is skipped with a warning. The reverse order fails: calling
`JvmMetrics.builder().register()` after `initialize` on the same registry throws part-way (client behaviour), so use
one or the other. Pass `registry` to use a registry other than the default one.

| Flag | Metric set | Example series |
|---|---|---|
| `enableStandardExports` | `ProcessMetrics` | `process_cpu_seconds_total`, `process_open_fds` |
| `enableMemoryPoolsExports` | `JvmMemoryMetrics`, `JvmMemoryPoolAllocationMetrics` | `jvm_memory_used_bytes`, `jvm_memory_pool_used_bytes`, `jvm_memory_pool_allocated_bytes_total` |
| `enableGarbageCollectorExports` | `JvmGarbageCollectorMetrics` | `jvm_gc_collection_seconds` |
| `enableThreadExports` | `JvmThreadsMetrics` | `jvm_threads_current`, `jvm_threads_state` |
| `enableClassLoadingExports` | `JvmClassLoadingMetrics` | `jvm_classes_currently_loaded` |
| `enableVersionInfoExports` | `JvmRuntimeInfoMetric` | `jvm_runtime_info` |
| `enableBufferPoolExports` | `JvmBufferPoolMetrics` | `jvm_buffer_pool_used_bytes` |
| `enableCompilationExports` | `JvmCompilationMetrics` | `jvm_compilation_time_seconds_total` |
| `enableNativeMemoryExports` | `JvmNativeMemoryMetrics` | only with `-XX:NativeMemoryTracking=summary` (or `detail`) |

```kotlin
import com.pambrose.common.metrics.SystemMetrics

SystemMetrics.initialize(
  enableStandardExports = true,
  enableMemoryPoolsExports = true,
  enableGarbageCollectorExports = true,
  enableThreadExports = true,
  enableClassLoadingExports = true,
  enableVersionInfoExports = true,
  enableBufferPoolExports = true,
  enableCompilationExports = true,
)
```

### Exposing Metrics

Serving the metrics endpoint is the Prometheus client's job, not this module's (service-utils' `MetricsService`
does it with an embedded Jetty server). `HTTPServer` comes from `io.prometheus:prometheus-metrics-exporter-httpserver`,
which this module does not include, so add it at the same version as `prometheus-metrics-core`:

```kotlin
import io.prometheus.metrics.exporter.httpserver.HTTPServer

val server = HTTPServer.builder().port(8080).buildAndStart()
```

## API Reference

### `PrometheusDsl`

Each takes `registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry` as its first parameter. The types
are the 1.x ones from `io.prometheus.metrics.core.metrics`.

- `counter(registry, block: Counter.Builder.() -> Unit): Counter`
- `gauge(registry, block: Gauge.Builder.() -> Unit): Gauge`
- `summary(registry, block: Summary.Builder.() -> Unit): Summary`
- `histogram(registry, block: Histogram.Builder.() -> Unit): Histogram`

### Collectors

- `class SamplerGaugeCollector(name: String, help: String, labelNames: List<String> = emptyList(), labelValues: List<String> = emptyList(), registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry, data: () -> Double) : Collector` — `collect()` returns a `GaugeSnapshot`
- `class InstrumentedThreadFactory(delegate: ThreadFactory, name: String, help: String, registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry) : ThreadFactory` — `newThread` returns `Thread?`

### `SystemMetrics`

- `initialize(enableStandardExports: Boolean = false, enableMemoryPoolsExports: Boolean = false, enableGarbageCollectorExports: Boolean = false, enableThreadExports: Boolean = false, enableClassLoadingExports: Boolean = false, enableVersionInfoExports: Boolean = false, enableBufferPoolExports: Boolean = false, enableCompilationExports: Boolean = false, enableNativeMemoryExports: Boolean = false, registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry)`

## Dependencies

This module depends on:

- Kotlin Standard Library
- kotlin-logging (internally)
- `io.prometheus:prometheus-metrics-core` (`api`), which brings `prometheus-metrics-model` (`PrometheusRegistry`)
- `io.prometheus:prometheus-metrics-instrumentation-jvm` (`api`)

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/prometheus-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/prometheus-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:prometheus-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>prometheus-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- Metric names should follow Prometheus conventions: `snake_case`, with a base-unit suffix such as
  `_seconds`, `_bytes` or `_total`
- Keep label cardinality low — never label with user ids, request ids, or timestamps
- `SamplerGaugeCollector` runs its lambda on the scrape thread, so keep it cheap and non-blocking

## License

Licensed under the Apache License, Version 2.0.
