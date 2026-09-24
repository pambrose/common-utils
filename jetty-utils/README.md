# Jetty Utils

A small set of helpers for embedded Jetty 12 (EE11): two DSL builders and two ready-made servlets.

This module is deliberately thin — `JettyDsl.server` and `JettyDsl.servletContextHandler` construct a real
Jetty `Server` / `ServletContextHandler` and hand it to your lambda as the receiver, so everything inside
the block is ordinary Jetty API.

## Features

### Server DSL

- **`JettyDsl.server(port) { }`**: creates a `Server` listening on a port on every interface and applies the optional
  configuration block
- **`JettyDsl.server(port, host) { }`**: the same, with its connector bound to `host` (`null` for every interface)
- **`JettyDsl.servletContextHandler { }`**: creates a `ServletContextHandler` and applies the optional block

### Servlets

- **`LambdaServlet`**: serves the result of a lambda as the GET response body
- **`VersionServlet`**: a `LambdaServlet` that serves a fixed version string as plain text

Both servlets serve **GET** (and **HEAD**, which runs the lambda too), send
`Cache-Control: must-revalidate,no-cache,no-store`, and encode the body as UTF-8 unless the content type names
another charset. The body is exactly the lambda's result, with no line separator appended.

## Usage Examples

### Building a Server

```kotlin
import com.pambrose.common.dsl.JettyDsl

val handler =
  JettyDsl.servletContextHandler {
    contextPath = "/api"
  }

val server =
  JettyDsl.server(8080) {
    this.handler = handler
  }

server.start()
server.join()
```

Pass port `0` to bind an ephemeral port. To listen on one address only, pass it as `host`; the module's own
tests bind the loopback address this way:

```kotlin
val server = JettyDsl.server(0, "127.0.0.1") { this.handler = handler }
```

### LambdaServlet

`LambdaServlet` takes a lambda producing the response body. The single-argument constructor defaults the
content type to `"text/plain"`.

```kotlin
import com.pambrose.common.dsl.JettyDsl
import com.pambrose.common.servlet.LambdaServlet
import org.eclipse.jetty.ee11.servlet.ServletHolder

val handler =
  JettyDsl.servletContextHandler {
    contextPath = "/"

    // Defaults to text/plain
    addServlet(ServletHolder(LambdaServlet { "OK" }), "/health")

    // Explicit content type
    addServlet(
      ServletHolder(LambdaServlet("application/json") { """{"status":"up"}""" }),
      "/status",
    )
  }
```

The lambda runs on every request, so it sees fresh state each time — useful for counters, uptime, and
health snapshots. It runs before the response is touched, so if it throws, the container sends an error
status rather than an empty `200`.

### VersionServlet

```kotlin
import com.pambrose.common.servlet.VersionServlet
import org.eclipse.jetty.ee11.servlet.ServletHolder

addServlet(ServletHolder(VersionServlet("3.2.3")), "/version")
```

`VersionServlet` captures the string at construction time. To report a value that can change, use
`LambdaServlet` instead.

### Combining with core-utils Version Metadata

core-utils' `Version` annotation pairs naturally with `VersionServlet`:

```kotlin
import com.pambrose.common.servlet.VersionServlet
import com.pambrose.common.util.Version
import com.pambrose.common.util.Version.Companion.versionDesc

@Version(version = "2.2.6", releaseDate = "2026-09-07", buildTime = 0L)
object MyApp

addServlet(ServletHolder(VersionServlet(MyApp::class.versionDesc())), "/version")
```

## API Reference

### `JettyDsl`

- `server(port: Int, block: Server.() -> Unit = {}): Server`
- `server(port: Int, host: String?, block: Server.() -> Unit = {}): Server`
- `servletContextHandler(block: ServletContextHandler.() -> Unit = {}): ServletContextHandler`

### Servlets

- `open class LambdaServlet(contentType: String, block: () -> String) : HttpServlet` — open for subclassing,
  but `doGet` is `final`; a subclass supplies its own lambda and content type
- `LambdaServlet(block: () -> String)` — content type `"text/plain"`
- `class VersionServlet(version: String) : LambdaServlet`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Jetty EE11 Servlet (`org.eclipse.jetty.ee11:jetty-ee11-servlet`)

Jetty 12 EE11 implements Jakarta Servlet 6.1, so servlets use the `jakarta.servlet` packages rather than
`javax.servlet`.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/jetty-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/jetty-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:jetty-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>jetty-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- `LambdaServlet` overrides only `doGet`, and that override is `final`, so `VersionServlet` and any other
  subclass keep its UTF-8 encoding and error handling. Other HTTP methods fall through to `HttpServlet`'s
  defaults: `HEAD` calls `doGet` (so the lambda runs, and a health probe using `HEAD` triggers its side
  effects) and drops the body, `OPTIONS` and `TRACE` answer `200`, and `POST`, `PUT`, `DELETE` and `PATCH`
  get `405`
- The no-cache headers make these endpoints safe to poll from load balancers and health checks
- Anything beyond these helpers — static content, filters, security — is plain Jetty configuration inside
  the DSL blocks

## License

Licensed under the Apache License, Version 2.0.
