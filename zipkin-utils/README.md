# Zipkin Utils

A Kotlin DSL for building Brave `Tracing` instances, the entry point for recording spans and sending them to
Zipkin.

## Features

- **`ZipkinDsl.tracing { }`**: builds a `Tracing` from a `Tracing.Builder` configuration block

## Usage Examples

### Building a Tracing Instance

The block receives Brave's `Tracing.Builder` as its receiver, so everything inside it is ordinary Brave API.

```kotlin
import brave.sampler.Sampler
import com.pambrose.common.dsl.ZipkinDsl

val tracing =
  ZipkinDsl.tracing {
    localServiceName("orders")
    sampler(Sampler.ALWAYS_SAMPLE)
  }
```

### Reporting Spans to Zipkin

`ZipkinSpanHandler`, from `zipkin-reporter-brave`, which this module includes, sends finished spans through a
reporter. An HTTP sender such as `OkHttpSender` comes from `io.zipkin.reporter2:zipkin-sender-okhttp3`, which this
module does not include.

```kotlin
import com.pambrose.common.dsl.ZipkinDsl
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.okhttp3.OkHttpSender

val sender = OkHttpSender.create("http://localhost:9411/api/v2/spans")
val reporter = AsyncReporter.create(sender)

val tracing =
  ZipkinDsl.tracing {
    localServiceName("orders")
    addSpanHandler(ZipkinSpanHandler.create(reporter))
  }
```

service-utils' `ZipkinReporterService` sets this up for a Guava service, and sends queued spans when it stops.

### Tracing Work

```kotlin
val tracer = tracing.tracer()
val span = tracer.nextSpan().name("process-order").start()
try {
  tracer.withSpanInScope(span).use { processOrder() }
} finally {
  span.finish()
}
```

`Tracing` is `Closeable`. Close it when you are done; closing it also clears `Tracing.current()`.

```kotlin
tracing.close()
```

## API Reference

### `ZipkinDsl`

- `tracing(block: Tracing.Builder.() -> Unit): Tracing`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Brave (`io.zipkin.brave:brave`)
- Zipkin (`io.zipkin.zipkin2:zipkin`)
- Zipkin Reporter for Brave (`io.zipkin.reporter2:zipkin-reporter-brave`)

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/zipkin-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/zipkin-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:zipkin-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>zipkin-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## License

Licensed under the Apache License, Version 2.0.
