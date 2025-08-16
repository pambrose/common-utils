# Prometheus Utils

Utilities for Prometheus metrics collection, providing DSL functions for creating metrics, system monitoring, and thread
instrumentation.

## Features

### Metrics DSL

- **Prometheus DSL**: Fluent API for creating Prometheus metrics (counters, gauges, histograms)
- **Metric Registration**: Simplified metric registration and management
- **Collector Utilities**: Custom collectors for specialized metrics

### System Metrics

- **SystemMetrics**: Automatic collection of JVM and system metrics
- **JVM Monitoring**: Memory, GC, thread, and class loading metrics
- **Custom Collectors**: Extensible metric collection framework

### Thread Instrumentation

- **InstrumentedThreadFactory**: Thread factory with Prometheus instrumentation
- **Thread Monitoring**: Track thread creation, execution, and lifecycle

### Specialized Collectors

- **SamplerGaugeCollector**: Custom gauge collector with sampling capabilities

## Usage Examples

### Basic Metrics Creation

```kotlin
import com.github.pambrose.common.dsl.PrometheusDsl.*
import io.prometheus.client.CollectorRegistry

// Create metrics using DSL
val requestCounter = counter {
    name = "http_requests_total"
    help = "Total number of HTTP requests"
    labelNames = arrayOf("method", "status")
}

val responseTime = histogram {
    name = "http_request_duration_seconds"
    help = "HTTP request duration in seconds"
    labelNames = arrayOf("method")
    buckets = arrayOf(0.1, 0.5, 1.0, 2.0, 5.0)
}

val activeConnections = gauge {
    name = "active_connections"
    help = "Number of active connections"
}

// Register metrics
val registry = CollectorRegistry.defaultRegistry
requestCounter.register(registry)
responseTime.register(registry)
activeConnections.register(registry)
```

### Recording Metrics

```kotlin
// Increment counter
requestCounter.labels("GET", "200").inc()
requestCounter.labels("POST", "404").inc()

// Record histogram observation
responseTime.labels("GET").observe(0.75)

// Set gauge value
activeConnections.set(42.0)
```

### System Metrics Collection

```kotlin
import com.github.pambrose.common.metrics.SystemMetrics

// Enable automatic system metrics collection
SystemMetrics.enableJvmMetrics()

// Or enable specific metric groups
SystemMetrics.enableMemoryMetrics()
SystemMetrics.enableGarbageCollectorMetrics()
SystemMetrics.enableThreadMetrics()
SystemMetrics.enableClassLoadingMetrics()
```

### Instrumented Thread Factory

```kotlin
import com.github.pambrose.common.concurrent.InstrumentedThreadFactory
import java.util.concurrent.Executors

// Create thread factory with metrics
val threadFactory = InstrumentedThreadFactory("worker-pool")

// Use with executor service
val executor = Executors.newFixedThreadPool(10, threadFactory)

// Submit tasks - thread metrics will be automatically collected
executor.submit {
    // Your task here
    performWork()
}
```

### Custom Sampler Gauge

```kotlin
import com.github.pambrose.common.metrics.SamplerGaugeCollector

// Create a gauge that samples a value periodically
val memoryUsageGauge = SamplerGaugeCollector.Builder()
    .name("memory_usage_bytes")
    .help("Current memory usage in bytes")
    .supplier { Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() }
    .build()

memoryUsageGauge.register()
```

### Web Application Example

```kotlin
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports

fun main() {
    // Enable default JVM metrics
    DefaultExports.initialize()

    // Enable custom system metrics
    SystemMetrics.enableJvmMetrics()

    // Create application metrics
    val requestCounter = counter {
        name = "app_requests_total"
        help = "Total application requests"
        labelNames = arrayOf("endpoint")
    }

    val requestDuration = histogram {
        name = "app_request_duration_seconds"
        help = "Application request duration"
        labelNames = arrayOf("endpoint")
    }

    // Start Prometheus HTTP server
    val server = HTTPServer(9090)
    println("Prometheus metrics available at http://localhost:9090/metrics")

    // Simulate application activity
    while (true) {
        val timer = requestDuration.labels("/api/users").startTimer()
        try {
            // Simulate request processing
            Thread.sleep((Math.random() * 1000).toLong())
            requestCounter.labels("/api/users").inc()
        } finally {
            timer.observeDuration()
        }
    }
}
```

### Custom Metric Collector

```kotlin
import io.prometheus.client.Collector
import io.prometheus.client.GaugeMetricFamily

class DatabaseConnectionPoolCollector : Collector() {
    override fun collect(): List<MetricFamilySamples> {
        val connections = GaugeMetricFamily(
            "db_connection_pool_size",
            "Database connection pool metrics",
            listOf("state")
        )

        val pool = getConnectionPool()
        connections.addMetric(listOf("active"), pool.activeConnections.toDouble())
        connections.addMetric(listOf("idle"), pool.idleConnections.toDouble())
        connections.addMetric(listOf("total"), pool.totalConnections.toDouble())

        return listOf(connections)
    }
}

// Register custom collector
DatabaseConnectionPoolCollector().register()
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Prometheus Java Client
- Prometheus Hotspot (for JVM metrics)
- Prometheus Servlet (for HTTP exposure)

## Installation

### Gradle

```kotlin
dependencies {
    implementation("com.github.pambrose.common-utils:prometheus-utils:2.4.1")
}
```

### Maven

```xml
<dependency>
    <groupId>com.github.pambrose.common-utils</groupId>
    <artifactId>prometheus-utils</artifactId>
    <version>2.4.1</version>
</dependency>
```

## Metric Types

### Counter

- Monotonically increasing values
- Use for: request counts, error counts, processed items
- Methods: `inc()`, `inc(amount)`

### Gauge

- Values that can go up or down
- Use for: memory usage, active connections, queue size
- Methods: `set(value)`, `inc()`, `dec()`, `inc(amount)`, `dec(amount)`

### Histogram

- Observations of events (usually request durations or response sizes)
- Automatically provides `_count`, `_sum`, and `_bucket` metrics
- Use for: request durations, response sizes
- Methods: `observe(value)`, `startTimer()`

### Summary

- Similar to histogram but provides quantiles
- Use for: request durations with percentiles
- Methods: `observe(value)`, `startTimer()`

## Best Practices

1. **Metric Naming**: Use descriptive names following Prometheus conventions
2. **Label Management**: Keep label cardinality low to avoid performance issues
3. **Registration**: Register metrics once during application startup
4. **Cleanup**: Properly clean up metrics when removing labels
5. **Documentation**: Always provide helpful descriptions for metrics

## Performance Considerations

- Metrics collection has minimal overhead but avoid excessive label cardinality
- System metrics are collected periodically - configure appropriate intervals
- Thread instrumentation adds small overhead to thread creation
- Use sampling for high-frequency events when appropriate

## Common Patterns

### Request Tracking

```kotlin
val requestCounter = counter {
    name = "http_requests_total"
    labelNames = arrayOf("method", "status", "endpoint")
}

val requestDuration = histogram {
    name = "http_request_duration_seconds"
    labelNames = arrayOf("method", "endpoint")
}

fun handleRequest(method: String, endpoint: String) {
    val timer = requestDuration.labels(method, endpoint).startTimer()
    try {
        // Process request
        val status = processRequest()
        requestCounter.labels(method, status.toString(), endpoint).inc()
    } finally {
        timer.observeDuration()
    }
}
```

### Error Tracking

```kotlin
val errorCounter = counter {
    name = "application_errors_total"
    labelNames = arrayOf("type", "component")
}

fun trackError(error: Exception, component: String) {
    errorCounter.labels(error.javaClass.simpleName, component).inc()
}
```

## Integration with Monitoring Systems

### Grafana Dashboard

```json
{
  "targets": [
    {
      "expr": "rate(http_requests_total[5m])",
      "legendFormat": "{{method}} {{status}}"
    }
  ]
}
```

### Alert Rules

```yaml
groups:
  - name: application.rules
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: High error rate detected
```

## License

Licensed under the Apache License, Version 2.0.
