# Guava Utils

Utilities built on Google Guava: monitor-based and coroutine-based waiting primitives, Guava `Service`
helpers, concurrency extensions, and gzip helpers.

## Features

### Monitor-based Waiting (blocking)

- **`GenericMonitor`**: abstract base wrapping Guava's `Monitor` with wait-until-satisfied operations
- **`BooleanMonitor`**: a `GenericMonitor` over a single boolean value

### Coroutine-based Waiting (suspending)

- **`ConditionalValue<T>` / `ConditionalBoolean`**: `StateFlow`-backed values you can suspend on
- **`GenericValueWaiter<T>` / `BooleanWaiter`**: continuation-based waiting with timeout support

### Guava Services

- **`GenericExecutionThreadService` / `GenericIdleService`**: base classes adding `startSync` / `stopSync`
- **`genericServiceListener`**: a logging `Service.Listener` extension
- **`GuavaDsl`**: builders for `ServiceManager`, `Service.Listener` and `ServiceManager.Listener`

### Concurrency Extensions

- **`CountDownLatch` extensions**: `isFinished`, `countDown { }`, `await(Duration)`
- **`Semaphore.withLock`**: run a block holding a permit
- **`thread(latch) { }`**: start a thread that counts a latch down when it finishes
- **`VerboseCountDownLatch`**: a `CountDownLatch` that logs while it waits

### Compression

- **`ZipExtensions`**: gzip a `String` or `ByteArray`, detect gzipped data, and unzip it

## Usage Examples

### BooleanMonitor

`BooleanMonitor` takes its initial value positionally and exposes `get()` / `set()`. Waiting comes from
`GenericMonitor`, and blocks the calling thread.

```kotlin
import com.pambrose.common.concurrent.BooleanMonitor
import kotlin.time.Duration.Companion.seconds

val monitor = BooleanMonitor(false)

monitor.get()        // false
monitor.set(true)    // sets the value and wakes waiting threads

// Block until the value is true
monitor.waitUntilTrue()

// Bounded wait: returns true if satisfied before the timeout
if (monitor.waitUntilTrue(5.seconds))
  println("Value became true within timeout")

// Wait for a specific value
monitor.waitUntil(false)
```

The `debug` / `info` / `warn` / `error` methods attach a log message that is emitted while the monitor
waits, which is useful for diagnosing stuck waits.

### ConditionalBoolean and ConditionalValue

These are the suspending counterparts, backed by a `MutableStateFlow`. `set` is a `suspend` function.

```kotlin
import com.pambrose.common.concurrent.ConditionalBoolean
import com.pambrose.common.concurrent.ConditionalValue
import kotlin.time.Duration.Companion.seconds

val ready = ConditionalBoolean(false)

launch {
  // Returns true if satisfied, false if the timeout expired
  if (ready.waitUntilTrue(10.seconds))
    println("Ready")
  else
    println("Timed out")
}

launch { ready.set(true) }

// The generic form waits on an arbitrary predicate
val status = ConditionalValue("starting")
launch { status.waitUntil(10.seconds) { it == "running" } }
launch { status.set("running") }

status.get()  // current value, without waiting
```

Both `waitUntilTrue` and `waitUntil` default to `Duration.INFINITE` and return `Boolean` — `true` when the
condition was met, `false` on timeout.

### BooleanWaiter

`BooleanWaiter` is the continuation-based variant; its `setValue` is **not** suspending, so it can be
called from ordinary code.

```kotlin
import com.pambrose.common.concurrent.BooleanWaiter
import kotlin.time.Duration.Companion.seconds

val waiter = BooleanWaiter(false)

launch { waiter.waitUntilTrue(5.seconds) }

waiter.setValue(true)
```

### Guava Services

There is no `GenericService` type — extend the Guava-shaped base class that matches your service:

```kotlin
import com.pambrose.common.concurrent.GenericExecutionThreadService
import kotlin.time.Duration.Companion.seconds

class MyService : GenericExecutionThreadService() {
  override fun run() {
    while (isRunning) {
      // do work
    }
  }
}

val service = MyService()

// startSync/stopSync wrap startAsync().awaitRunning() with a timeout
service.startSync(30.seconds)
service.stopSync(30.seconds)
```

`GenericIdleService` offers the same `startSync` / `stopSync` pair over Guava's `AbstractIdleService`.

### Service Listeners

`genericServiceListener` is an **extension function** on `Service` that registers a listener logging every
state transition:

```kotlin
import com.pambrose.common.concurrent.genericServiceListener
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

service.genericServiceListener(logger)
```

For custom callbacks, build a listener with the DSL:

```kotlin
import com.google.common.util.concurrent.MoreExecutors
import com.pambrose.common.dsl.GuavaDsl.serviceListener
import com.pambrose.common.dsl.GuavaDsl.serviceManager
import com.pambrose.common.dsl.GuavaDsl.serviceManagerListener

val listener =
  serviceListener {
    starting { println("starting") }
    running { println("running") }
    stopping { from -> println("stopping from $from") }
    terminated { from -> println("terminated from $from") }
    failed { from, throwable -> println("failed from $from: ${throwable.message}") }
  }

service.addListener(listener, MoreExecutors.directExecutor())

// ServiceManager and its listener have matching builders
val manager =
  serviceManager(listOf(service1, service2)) {
    addListener(
      serviceManagerListener {
        healthy { println("all services healthy") }
        stopped { println("all services stopped") }
        failure { svc -> println("service failed: $svc") }
      },
      MoreExecutors.directExecutor(),
    )
  }

manager.startAsync().awaitHealthy()
```

### Concurrency Extensions

```kotlin
import com.pambrose.common.concurrent.await
import com.pambrose.common.concurrent.countDown
import com.pambrose.common.concurrent.isFinished
import com.pambrose.common.concurrent.thread
import com.pambrose.common.concurrent.withLock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import kotlin.time.Duration.Companion.seconds

val latch = CountDownLatch(2)

// Counts the latch down even if the block throws
latch.countDown { doSomeWork() }

// Starts a thread that counts the latch down when the block finishes
thread(latch, name = "worker") { doMoreWork() }

latch.await(30.seconds)   // Boolean
latch.isFinished          // count == 0L

val semaphore = Semaphore(4)
val result = semaphore.withLock { computeSomething() }
```

### Compression

These are gzip helpers, not ZIP archive readers.

```kotlin
import com.pambrose.common.util.isZipped
import com.pambrose.common.util.unzip
import com.pambrose.common.util.zip

val compressed = "some long string".zip()   // ByteArray
compressed.isZipped()                        // true
val original = compressed.unzip()            // String
```

### Platform Checks

```kotlin
import com.pambrose.common.util.isMac
import com.pambrose.common.util.isWindows
```

## API Reference

### Monitors

- `abstract class GenericMonitor` — `waitUntilTrue()`, `waitUntilTrue(waitTime: Duration): Boolean`,
  `waitUntilFalse()`, `waitUntilFalse(waitTime: Duration): Boolean`, `waitUntilTrueWithInterruption(...)`,
  `waitUntil(value: Boolean)`, plus `debug`/`info`/`warn`/`error` message actions
- `class BooleanMonitor(initValue: Boolean) : GenericMonitor` — `get()`, `set(value: Boolean)`

### Conditional Values

- `open class ConditionalValue<T>(initValue: T)` — `get(): T`, `suspend set(value: T)`,
  `suspend waitUntil(timeoutDuration: Duration = INFINITE, predicate: (T) -> Boolean): Boolean`
- `class ConditionalBoolean(initValue: Boolean) : ConditionalValue<Boolean>` — `suspend waitUntilTrue(...)`,
  `suspend waitUntilFalse(...)`
- `abstract class GenericValueWaiter<T>(initValue: T)` / `class BooleanWaiter(initValue: Boolean)` —
  `setValue(value)`, `suspend waitUntilTrue(...)`, `suspend waitUntilFalse(...)`

### Services

- `abstract class GenericExecutionThreadService : AbstractExecutionThreadService` — `startSync(timeout)`, `stopSync(timeout)`
- `abstract class GenericIdleService : AbstractIdleService` — `startSync(maxWait)`, `stopSync(maxWait)`
- `fun Service.genericServiceListener(logger: KLogger)`
- `GuavaDsl.serviceManager(services: List<Service>, block: ServiceManager.() -> Unit): ServiceManager`
- `GuavaDsl.serviceListener(init: ServiceListenerHelper.() -> Unit)`
- `GuavaDsl.serviceManagerListener(init: ServiceManagerListenerHelper.() -> Unit)`
- `GuavaDsl.toStringElements(block: MoreObjects.ToStringHelper.() -> Unit)`

### Concurrency & Compression

- `val CountDownLatch.isFinished: Boolean`, `CountDownLatch.countDown(block: () -> Unit)`,
  `CountDownLatch.await(duration: Duration): Boolean`
- `fun <T> Semaphore.withLock(block: () -> T): T`
- `fun thread(latch: CountDownLatch, start: Boolean = true, isDaemon: Boolean = false, contextClassLoader: ClassLoader? = null, name: String? = null, priority: Int = -1, block: () -> Unit): Thread`
- `class VerboseCountDownLatch(count: Int) : CountDownLatch`
- `String.zip(): ByteArray`, `ByteArray.zip(): ByteArray`, `ByteArray.isZipped(): Boolean`, `ByteArray.unzip(): String`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Google Guava

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/guava-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/guava-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:guava-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>guava-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Choosing a Waiting Primitive

| Need | Use |
|------|-----|
| Blocking wait on a thread | `BooleanMonitor` / `GenericMonitor` |
| Suspending wait, value observed as a flow | `ConditionalBoolean` / `ConditionalValue` |
| Suspending wait, value set from non-suspending code | `BooleanWaiter` / `GenericValueWaiter` |

## Thread Safety

- `BooleanMonitor` guards its value with Guava's `Monitor` and an atomic boolean
- `ConditionalValue` is backed by a `MutableStateFlow`
- The service base classes follow Guava's own service contract

## License

Licensed under the Apache License, Version 2.0.
