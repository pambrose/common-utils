# Dropwizard Utils

Utilities for Dropwizard Metrics, providing a small DSL for creating health checks plus two ready-made
health check factories.

## Features

### Health Check DSL

- **`MetricsDsl.healthCheck`**: build a Dropwizard `HealthCheck` from a lambda instead of subclassing
- **`MetricsUtils`**: ready-made checks for two common "is this collection too big?" patterns

## Usage Examples

### Health Check DSL

`healthCheck` takes a single lambda with `HealthCheck` as its receiver, and returns a `HealthCheck` whose
`check()` runs that lambda. It does not take a name — names are assigned when you register the check.

```kotlin
import com.codahale.metrics.health.HealthCheck
import com.pambrose.common.dsl.MetricsDsl.healthCheck

val databaseHealthCheck =
  healthCheck {
    if (database.isConnected)
      HealthCheck.Result.healthy("Database is connected")
    else
      HealthCheck.Result.unhealthy("Database connection failed")
  }
```

The lambda may throw; `check()` is declared `@Throws(Exception::class)`, and Dropwizard converts a thrown
exception into an unhealthy result.

### Ready-made Health Checks

Both factories report **healthy while the observed size is strictly below `size`**, and unhealthy at or
above it, with a `"Large size: N (threshold: T)"` message. Both read the current size on every `check()`.

```kotlin
import com.pambrose.common.util.MetricsUtils.newBacklogHealthCheck
import com.pambrose.common.util.MetricsUtils.newMapHealthCheck

// Unhealthy once the cache reaches 1000 entries
val cacheHealthCheck = newMapHealthCheck(map = cache, size = 1000)

// Unhealthy once the backlog reaches 100
val liveBacklogCheck = newBacklogHealthCheck(backlogSize = { queue.size }, size = 100)
```

The older `newBacklogHealthCheck(backlogSize: Int, size)` is deprecated: it captures the size once, so the
check reports the same result forever.

The factories are `@JvmStatic`, so Java calls them as `MetricsUtils.newMapHealthCheck(cache, 1000)` and
`MetricsDsl.healthCheck(...)`, without `INSTANCE`.

### Registering Health Checks

Registration and execution use Dropwizard's own `HealthCheckRegistry`; this module only builds the checks.

```kotlin
import com.codahale.metrics.health.HealthCheckRegistry

val healthCheckRegistry = HealthCheckRegistry()

healthCheckRegistry.register("database", databaseHealthCheck)
healthCheckRegistry.register("queue-backlog", liveBacklogCheck)
healthCheckRegistry.register("cache-size", cacheHealthCheck)

healthCheckRegistry.runHealthChecks().forEach { (name, result) ->
  println("$name: ${if (result.isHealthy) "HEALTHY" else "UNHEALTHY"}")
  if (!result.isHealthy)
    println("  Error: ${result.message}")
}
```

## API Reference

### `MetricsDsl`

- `healthCheck(block: HealthCheck.() -> HealthCheck.Result): HealthCheck` — creates a `HealthCheck` whose
  `check()` is the given lambda; `@JvmStatic`

### `MetricsUtils`

All are `@JvmStatic`.

- `newBacklogHealthCheck(backlogSize: () -> Int, size: Int): HealthCheck` — healthy while `backlogSize() < size`,
  re-read on every check
- `newBacklogHealthCheck(backlogSize: Int, size: Int): HealthCheck` — deprecated; `backlogSize` is captured by value
- `newMapHealthCheck(map: Map<*, *>, size: Int): HealthCheck` — healthy while `map.size < size`, re-read on
  every check

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Dropwizard Metrics Core
- Dropwizard Metrics Health Checks

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/dropwizard-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/dropwizard-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:dropwizard-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>dropwizard-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Best Practices

1. **Health Check Names**: names live at the registry, so pick descriptive ones when calling `register`
2. **Error Messages**: include the offending value in unhealthy results, as both factories do
3. **Keep Checks Fast**: health check logic runs on the health check endpoint's thread
4. **Read State Inside the Lambda**: capture the source of truth, not a snapshot of it

## License

Licensed under the Apache License, Version 2.0.
