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
- **`JettyDsl.servletContextHandler { }`**: creates a `ServletContextHandler` with a status-only error handler and
  applies the optional block

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

### Error Pages

A context from `servletContextHandler` starts with an error handler whose pages show only the status code, its
reason phrase and the request URI, in HTML, plain text or JSON as the `Accept` header asks. A servlet that throws
gets a `500` page reading `Server Error`, and an unmapped path a `404` reading `Not Found`. The page carries no
exception class, message, cause or stack trace, no servlet name, and no "Powered by Jetty" footer. A message a
servlet passes to `sendError(code, message)` is replaced by the reason phrase too.

Without it, a servlet error would fall to the server's error handler, which prints the exception's `toString()`
and, unless the connector's `sendServerVersion` is off, a footer naming the Jetty version.

To show more, set your own handler in the block; setting it to `null` hands the context's errors back to the
server's error handler:

```kotlin
import org.eclipse.jetty.ee11.servlet.ErrorPageErrorHandler

val handler =
  JettyDsl.servletContextHandler {
    errorHandler = ErrorPageErrorHandler().apply { addErrorPage(404, "/not-found.html") }
  }
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

## Security Considerations

- The status-only error handler covers the requests a `servletContextHandler` context handles. A request no
  context matches (a path outside a non-root `contextPath`, for example) still gets the server's error page, with
  its "Powered by Jetty" footer
- `JettyDsl.server` keeps Jetty's default `HttpConfiguration`, which sends a `Server: Jetty(<version>)` header on
  every response and puts that footer on the server's error pages. For a server reachable by untrusted clients,
  turn it off in the block:

```kotlin
import org.eclipse.jetty.server.HttpConnectionFactory

val server =
  JettyDsl.server(8080, "127.0.0.1") {
    connectors.single().getConnectionFactory(HttpConnectionFactory::class.java).httpConfiguration
      .sendServerVersion = false
    this.handler = handler
  }
```

## License

Licensed under the Apache License, Version 2.0.
