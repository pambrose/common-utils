# Dropwizard Utils

Utilities for Dropwizard Metrics, providing DSL functions for creating health checks and common health check patterns.

## Features

### Health Check DSL

- **Health Check Builder**: DSL for creating Dropwizard health checks with clean syntax
- **Common Health Checks**: Pre-built health checks for common scenarios (backlog size, map size)

## Usage Examples

### Health Check DSL

```kotlin
import com.github.pambrose.common.dsl.MetricsDsl.healthCheck
import com.github.pambrose.common.util.MetricsUtils.newBacklogHealthCheck
import com.github.pambrose.common.util.MetricsUtils.newMapHealthCheck

// Create a custom health check
val customHealthCheck = healthCheck("Database Connection") {
  try {
    // Check database connectivity
    database.isConnected()
    healthy("Database is connected")
  } catch (e: Exception) {
    unhealthy("Database connection failed: ${e.message}")
  }
}

// Create a backlog size health check
val backlogHealthCheck = newBacklogHealthCheck(
  name = "Queue Backlog",
  queue = myQueue,
  maxSize = 100
)

// Create a map size health check
val mapHealthCheck = newMapHealthCheck(
  name = "Cache Size",
  map = myCache,
  maxSize = 1000
)
```

### Registering Health Checks

```kotlin
import com.codahale.metrics.health.HealthCheckRegistry

val healthCheckRegistry = HealthCheckRegistry()

// Register health checks
healthCheckRegistry.register("database", customHealthCheck)
healthCheckRegistry.register("queue-backlog", backlogHealthCheck)
healthCheckRegistry.register("cache-size", mapHealthCheck)

// Check health status
val results = healthCheckRegistry.runHealthChecks()
results.forEach { (name, result) ->
  println("$name: ${if (result.isHealthy) "HEALTHY" else "UNHEALTHY"}")
  if (!result.isHealthy) {
    println("  Error: ${result.message}")
  }
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Dropwizard Metrics Core
- Dropwizard Metrics Health Checks

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:dropwizard-utils:2.5.4-SNAPSHOT")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>dropwizard-utils</artifactId>
  <version>2.5.4-SNAPSHOT</version>
</dependency>
```

## API Reference

### MetricsDsl

- `healthCheck(name: String, block: () -> HealthCheck.Result)`: Creates a health check with the given name and logic

### MetricsUtils

- `newBacklogHealthCheck(name: String, queue: Collection<*>, maxSize: Int)`: Creates a health check for queue backlog
  size
- `newMapHealthCheck(name: String, map: Map<*, *>, maxSize: Int)`: Creates a health check for map size

## Best Practices

1. **Health Check Names**: Use descriptive names that clearly identify what is being checked
2. **Error Messages**: Provide clear, actionable error messages in unhealthy results
3. **Timeout Handling**: Keep health check logic fast to avoid blocking the health check endpoint
4. **Resource Cleanup**: Ensure health checks don't hold resources or connections open

## Thread Safety

- All health check utilities are thread-safe
- The DSL functions create immutable health check instances
- Collection size checks are performed atomically

## License

Licensed under the Apache License, Version 2.0.
