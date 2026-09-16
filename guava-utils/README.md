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

The retrying overloads wait in attempts of `timeout` and call a `MonitorAction` between attempts; returning
`false` stops waiting. `BooleanMonitor.debug` / `info` / `warn` / `error` build actions that log a message and
keep waiting, which helps diagnose stuck waits:

```kotlin
monitor.waitUntilTrue(5.seconds, BooleanMonitor.info("Still waiting for the monitor"))

// Give up after a minute overall. The two-argument overload passes Duration.INFINITE for maxWait;
// Duration.ZERO checks the condition once, and a negative value means no limit.
monitor.waitUntilTrue(timeout = 5.seconds, maxWait = 1.minutes, block = null)
```

Each attempt's `timeout` must be at least 1 ms, and no attempt waits past `maxWait`.

A `GenericMonitor` subclass must change the state its `monitorSatisfied` reads inside the monitor, for example
with the protected `mutate { }` helper. Guava re-checks the condition only when a thread leaves the monitor or
starts waiting, so a change made outside it can leave waiting threads blocked.

### ConditionalBoolean and ConditionalValue

These are the suspending counterparts, backed by a `MutableStateFlow`. `set` does not suspend, so any code
can call it.

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
condition was met, `false` on timeout. The current value is checked first, so a condition that already holds
returns `true` even with a zero timeout.

Every `set` notifies waiters, even when the value equals the current one or is the same object changed in place.
Waiters see only the latest value, so a value replaced before a waiter observes it can be missed; wait for
conditions that stay true once reached.

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

`GenericIdleService` offers the same `startSync` / `stopSync` pair over Guava's `AbstractIdleService`. Both
take `timeout: Duration = 30.seconds` and throw `TimeoutException` when it elapses, or `IllegalStateException`
when the service fails.

### Service Listeners

`genericServiceListener` is an **extension function** on `Service` that builds a listener logging every state
transition. It only builds the listener, so add it to the service:

```kotlin
import com.google.common.util.concurrent.MoreExecutors
import com.pambrose.common.concurrent.genericServiceListener
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

service.addListener(service.genericServiceListener(logger), MoreExecutors.directExecutor())
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

Setting the same callback twice keeps the later one.

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

// Refuse content that expands beyond 10 MB, such as a gzip bomb
val bounded = compressed.unzip(maxBytes = 10_000_000)
```

`unzip` throws `IllegalArgumentException` when the content is larger than `maxBytes` or when `maxBytes` is
negative, and an `IOException` such as `ZipException` or `EOFException` when gzip data is corrupt. Empty input
returns an empty string, and input without the GZIP magic header is returned decoded as UTF-8. It is annotated
`@JvmOverloads`, so Java callers get both the no-arg and the `maxBytes` form.

### Platform Checks

```kotlin
import com.pambrose.common.util.isMac
import com.pambrose.common.util.isWindows
```

## API Reference

### Monitors

- `abstract class GenericMonitor` — `waitUntilTrue()`, `waitUntilTrue(waitTime: Duration): Boolean`,
  `waitUntilFalse()`, `waitUntilFalse(waitTime: Duration): Boolean`, `waitUntilTrueWithInterruption(...)`,
  `waitUntil(value: Boolean)`, `waitUntil(value: Boolean, waitTime: Duration): Boolean`, the retrying overloads
  `(timeout, block)` and `(timeout, maxWait, block)`, and the protected `mutate(block)`
- `class BooleanMonitor(initValue: Boolean) : GenericMonitor` — `get()`, `set(value: Boolean)`, and the companion
  `debug`/`info`/`warn`/`error` `MonitorAction` factories, each taking either a `String` or a message lambda

### Conditional Values

- `open class ConditionalValue<T>(initValue: T)` — `get(): T`, `set(value: T)`,
  `suspend waitUntil(timeoutDuration: Duration = INFINITE, predicate: (T) -> Boolean): Boolean`
- `class ConditionalBoolean(initValue: Boolean) : ConditionalValue<Boolean>` — `suspend waitUntilTrue(...)`,
  `suspend waitUntilFalse(...)`
- `abstract class GenericValueWaiter<T>(initValue: T)` — `checkCondition(value: T)` sets the value and resumes
  every waiter whose predicate now holds; the protected `suspend waitForCondition(predicate, timeoutDuration)` is
  what typed subclasses expose. `currValue` is `@Volatile protected` with a private setter, so only
  `checkCondition` changes it
- `class BooleanWaiter(initValue: Boolean) : GenericValueWaiter<Boolean>` — `setValue(value: Boolean)`,
  `suspend waitUntilTrue(timeoutDuration: Duration = INFINITE): Boolean`, `suspend waitUntilFalse(...)`

### Services

- `abstract class GenericExecutionThreadService : AbstractExecutionThreadService` — `startSync(timeout = 30.seconds)`,
  `stopSync(timeout = 30.seconds)`, both `@Throws(TimeoutException::class)`
- `abstract class GenericIdleService : AbstractIdleService` — the same `startSync` / `stopSync`
- `fun Service.genericServiceListener(logger: KLogger): Service.Listener` — pass the result to `addListener`
- `GuavaDsl.serviceManager(services: List<Service>, block: ServiceManager.() -> Unit): ServiceManager`
- `GuavaDsl.serviceListener(init: ServiceListenerHelper.() -> Unit)`
- `GuavaDsl.serviceManagerListener(init: ServiceManagerListenerHelper.() -> Unit)`
- `Any.toStringElements(block: MoreObjects.ToStringHelper.() -> Unit): String` — an extension declared in `GuavaDsl`

### Concurrency & Compression

- `val CountDownLatch.isFinished: Boolean`, `CountDownLatch.countDown(block: () -> Unit)`,
  `CountDownLatch.await(duration: Duration): Boolean`
- `fun <T> Semaphore.withLock(block: () -> T): T`
- `fun thread(latch: CountDownLatch, start: Boolean = true, isDaemon: Boolean = false, contextClassLoader: ClassLoader? = null, name: String? = null, priority: Int = -1, block: () -> Unit): Thread`
- `class VerboseCountDownLatch(count: Int) : CountDownLatch` — `await(timeout, msg)` logs `msg` after each timeout;
  `timeout` must be at least 1 ms
- `String.zip(): ByteArray`, `ByteArray.zip(): ByteArray`, `ByteArray.isZipped(): Boolean`,
  `ByteArray.unzip(maxBytes: Long = Long.MAX_VALUE): String`

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

| Need                                                | Use                                       |
|-----------------------------------------------------|-------------------------------------------|
| Blocking wait on a thread                           | `BooleanMonitor` / `GenericMonitor`       |
| Suspending wait, value observed as a flow           | `ConditionalBoolean` / `ConditionalValue` |
| Suspending wait, value set from non-suspending code | `BooleanWaiter` / `GenericValueWaiter`    |

## Thread Safety

- `BooleanMonitor` guards its value with Guava's `Monitor` and an atomic boolean
- `ConditionalValue` is backed by a `MutableStateFlow`, and every `set` notifies waiters
- The service base classes follow Guava's own service contract

## License

Licensed under the Apache License, Version 2.0.
