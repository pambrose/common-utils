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

`AsyncZipkinSpanHandler`, from `zipkin-reporter-brave`, which this module includes, queues finished spans and sends
them to Zipkin in the background as JSON V2. An HTTP sender such as `OkHttpSender` comes from
`io.zipkin.reporter2:zipkin-sender-okhttp3`, which this module does not include.

```kotlin
import com.pambrose.common.dsl.ZipkinDsl
import zipkin2.reporter.BytesMessageSender
import zipkin2.reporter.brave.AsyncZipkinSpanHandler
import zipkin2.reporter.okhttp3.OkHttpSender

// Typed as BytesMessageSender so create() resolves to the current overload. OkHttpSender also extends Sender,
// which would otherwise select the create(Sender) overload deprecated since Zipkin Reporter 3.2.
val sender: BytesMessageSender = OkHttpSender.create("http://localhost:9411/api/v2/spans")
val handler = AsyncZipkinSpanHandler.create(sender)

val tracing =
  ZipkinDsl.tracing {
    localServiceName("orders")
    addSpanHandler(handler)
  }
```

`AsyncZipkinSpanHandler` encodes spans on its own thread. `ZipkinSpanHandler.create(reporter)` with an
`AsyncReporter` also works, and is the way to send another format such as JSON V1, but it converts each span on
the thread that finishes it.

service-utils' `ZipkinReporterService` manages a sender, reporter and span handler like these for a Guava service,
and sends queued spans when it stops.

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

Closing `Tracing` does not close its span handlers or their sender, so close those too when reporting to Zipkin.
The handler runs a flusher thread and the sender an HTTP client, and `handler.close()` drops any spans still
queued, so flush it first:

```kotlin
tracing.close()
handler.flush()
handler.close()
sender.close()
```

## API Reference

### `ZipkinDsl`

- `tracing(block: Tracing.Builder.() -> Unit): Tracing`

## Dependencies

This module depends on:

- Kotlin Standard Library
- Brave (`io.zipkin.brave:brave`), Zipkin (`io.zipkin.zipkin2:zipkin`) and Zipkin Reporter for Brave
  (`io.zipkin.reporter2:zipkin-reporter-brave`), all `api` dependencies
- core-utils, as an `implementation` dependency since 5.4.0: no core-utils type is in this module's API, so it is
  no longer on a consumer's compile classpath. Depend on core-utils yourself to call it. It is kept on the runtime
  classpath for code compiled against earlier releases, and 6.0.0 removes it.

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
