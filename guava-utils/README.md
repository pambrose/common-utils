# Guava Utils

Utilities for Google Guava libraries, providing concurrent programming utilities, service abstractions, and DSL
functions for common Guava patterns.

## Features

### Concurrent Utilities

- **BooleanMonitor**: Thread-safe boolean monitoring with Guava's Monitor
- **GenericMonitor**: Generic monitoring utilities
- **ConditionalValue**: Coroutine-friendly conditional value waiting
- **GenericValueWaiter**: Generic value waiting with timeout support

### Service Abstractions

- **GenericService**: Base class for Guava services with lifecycle management
- **GenericServiceListener**: Service state change listeners
- **GenericExecutionThreadService**: Abstract execution thread service
- **GenericIdleService**: Abstract idle service implementation

### Concurrent Extensions

- **ConcurrentExtensions**: Extensions for concurrent collections and operations
- **VerboseCountDownLatch**: Enhanced CountDownLatch with verbose logging

### Archive Utilities

- **ZipExtensions**: Utilities for working with ZIP archives

### DSL Functions

- **GuavaDsl**: DSL functions for creating Guava services and utilities

## Usage Examples

### BooleanMonitor

```kotlin
import com.github.pambrose.common.concurrent.BooleanMonitor

val monitor = BooleanMonitor(initialValue = false)

// Set value and notify waiters
monitor.setValueAndNotify(true)

// Wait for value to become true
monitor.waitForValue(true)

// Wait with timeout
if (monitor.waitForValue(true, 5.seconds)) {
  println("Value became true within timeout")
}
```

### ConditionalValue

```kotlin
import com.github.pambrose.common.concurrent.ConditionalValue

val condition = ConditionalValue<String>()

// Wait for value in coroutine
launch {
  val value = condition.waitForValue(10.seconds)
  if (value != null) {
    println("Received: $value")
  } else {
    println("Timeout waiting for value")
  }
}

// Set value from another coroutine
condition.setValue("Hello, World!")
```

### Generic Service

```kotlin
import com.github.pambrose.common.concurrent.GenericService

class MyService : GenericService() {
  override fun doStart() {
    // Service startup logic
    println("Service starting...")
    notifyStarted()
  }

  override fun doStop() {
    // Service shutdown logic
    println("Service stopping...")
    notifyStopped()
  }
}

val service = MyService()
service.startAsync().awaitRunning()
```

### Service Listener

```kotlin
import com.github.pambrose.common.concurrent.GenericServiceListener

val listener = GenericServiceListener(
  onStarting = { println("Service starting") },
  onRunning = { println("Service running") },
  onStopping = { println("Service stopping") },
  onTerminated = { println("Service terminated") },
  onFailed = { state, throwable ->
    println("Service failed in state $state: ${throwable.message}")
  }
)

service.addListener(listener, MoreExecutors.directExecutor())
```

### Zip Utilities

```kotlin
import com.github.pambrose.common.util.isZipped

val data = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // ZIP magic bytes

if (data.isZipped()) {
  println("Data is a ZIP archive")
}
```

### Guava DSL

```kotlin
import com.github.pambrose.common.dsl.GuavaDsl.stopwatch

val elapsed = stopwatch {
  // Some operation to time
  Thread.sleep(1000)
}

println("Operation took ${elapsed.toMillis()}ms")
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Google Guava
- Kotlinx Coroutines
- Kotlinx Coroutines Guava

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:guava-utils:2..4.12")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>guava-utils</artifactId>
  <version>2..4.12</version>
</dependency>
```

## Thread Safety

- **BooleanMonitor**: Thread-safe using Guava's Monitor
- **ConditionalValue**: Thread-safe using coroutine synchronization primitives
- **GenericService**: Thread-safe following Guava's service contract
- **ConcurrentExtensions**: Thread-safe for concurrent collections

## Performance Notes

- Monitor-based utilities have low overhead for synchronization
- ConditionalValue uses StateFlow which may have higher overhead for simple boolean conditions
- Service abstractions follow Guava's efficient service lifecycle patterns

## Best Practices

1. **Service Lifecycle**: Always use `startAsync()` and `stopAsync()` for service management
2. **Monitoring**: Use appropriate timeout values for waiting operations
3. **Error Handling**: Implement proper error handling in service implementations
4. **Resource Management**: Ensure proper cleanup in service shutdown methods

## Error Handling

- Service failures are propagated through the service listener mechanism
- Timeout operations return null or false to indicate timeout
- Monitor operations may throw InterruptedException

## Common Patterns

### Service with Health Checks

```kotlin
class HealthyService : GenericService() {
  private val healthCheck = object : HealthCheck() {
    override fun check(): Result {
      return if (isRunning) {
        Result.healthy("Service is running")
      } else {
        Result.unhealthy("Service is not running")
      }
    }
  }

  fun getHealthCheck() = healthCheck
}
```

### Coordinated Service Shutdown

```kotlin
val services = listOf(service1, service2, service3)

// Stop all services
services.forEach { it.stopAsync() }

// Wait for all to stop
services.forEach { it.awaitTerminated() }
```

## License

Licensed under the Apache License, Version 2.0.
