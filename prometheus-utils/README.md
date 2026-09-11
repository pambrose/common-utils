# Prometheus Utils

Helpers for the Prometheus Java client (simpleclient): a builder DSL for the four metric types, a
sampling gauge collector, an instrumented `ThreadFactory`, and a one-call JVM metrics registration.

## Features

### Metrics DSL

- **`PrometheusDsl`**: `counter`, `gauge`, `summary` and `histogram` builders that register the metric

### Collectors

- **`SamplerGaugeCollector`**: a gauge whose value is sampled by a lambda on each scrape
- **`InstrumentedThreadFactory`**: wraps a `ThreadFactory` and exports thread lifecycle counters

### JVM Metrics

- **`SystemMetrics.initialize(...)`**: registers the hotspot JVM exporters you opt into

## Usage Examples

### Metrics DSL

Each builder takes the corresponding Prometheus `Builder` as receiver, so you set `name`, `help`,
`labelNames`, and so on exactly as with the Java client. The metric is registered for you.

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
    buckets(0.1, 0.5, 1.0, 2.5, 5.0)
  }

val responseSize =
  PrometheusDsl.summary {
    name("http_response_size_bytes")
    help("HTTP response size")
  }
```

Use them through the normal client API:

```kotlin
requestCounter.labels("GET", "/api/users", "200").inc()
activeConnections.inc()
activeConnections.dec()

val timer = requestDuration.startTimer()
try {
  handleRequest()
} finally {
  timer.observeDuration()
}
```

### SamplerGaugeCollector

A gauge backed by a lambda, sampled on every scrape rather than set imperatively. It **registers itself
with the default registry when constructed**, so simply creating it is enough.

```kotlin
import com.pambrose.common.metrics.SamplerGaugeCollector

SamplerGaugeCollector(
  name = "jvm_free_memory_bytes",
  help = "Free JVM memory in bytes",
) {
  Runtime.getRuntime().freeMemory().toDouble()
}

// With labels: labelNames and labelValues must be the same length
SamplerGaugeCollector(
  name = "queue_depth",
  help = "Pending items in the queue",
  labelNames = listOf("queue"),
  labelValues = listOf("outbound"),
) {
  outboundQueue.size.toDouble()
}
```

Mismatched `labelNames` / `labelValues` sizes throw `IllegalArgumentException`.

### InstrumentedThreadFactory

Wraps an existing `ThreadFactory` and exports counters for threads created, running and terminated.

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

Every exporter is **off by default** — opt into the ones you want. Repeat calls after the first are
ignored.

```kotlin
import com.pambrose.common.metrics.SystemMetrics

SystemMetrics.initialize(
  enableStandardExports = true,
  enableMemoryPoolsExports = true,
  enableGarbageCollectorExports = true,
  enableThreadExports = true,
  enableClassLoadingExports = true,
  enableVersionInfoExports = true,
)
```

### Exposing Metrics

Serving the metrics endpoint is the Prometheus client's job, not this module's:

```kotlin
import io.prometheus.client.exporter.HTTPServer

val server = HTTPServer(8080)
```

## API Reference

### `PrometheusDsl`

- `counter(block: Counter.Builder.() -> Unit): Counter`
- `gauge(block: Gauge.Builder.() -> Unit): Gauge`
- `summary(block: Summary.Builder.() -> Unit): Summary`
- `histogram(block: Histogram.Builder.() -> Unit): Histogram`

### Collectors

- `class SamplerGaugeCollector(name: String, help: String, labelNames: List<String> = emptyList(), labelValues: List<String> = emptyList(), data: () -> Double) : Collector`
- `class InstrumentedThreadFactory(delegate: ThreadFactory, name: String, help: String) : ThreadFactory`

### `SystemMetrics`

- `initialize(enableStandardExports: Boolean = false, enableMemoryPoolsExports: Boolean = false, enableGarbageCollectorExports: Boolean = false, enableThreadExports: Boolean = false, enableClassLoadingExports: Boolean = false, enableVersionInfoExports: Boolean = false)`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Prometheus simpleclient
- Prometheus simpleclient_hotspot

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
