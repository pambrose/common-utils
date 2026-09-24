# Ktor Server Utils

Utilities for Ktor servers: response and redirect helpers, a Heroku HTTPS-redirect plugin, and a bridge
for mounting Jakarta servlets inside Ktor routes.

## Features

### Response Utilities

- **`respondWith`**: respond with text produced by a (possibly suspending) lambda
- **`redirectTo`**: redirect to a URL produced by a lambda
- **`uriPrefix`**: the `scheme://host:port` prefix of a request connection point

Each helper exists on both `ApplicationCall` and `RoutingContext`, so it works with or without `call.`.

### Heroku Integration

- **`HerokuHttpsRedirect`**: redirects plain-HTTP requests to HTTPS based on the `x-forwarded-proto`
  header that Heroku's router sets

### Servlet Bridge

- **`Route.servlet(path, servlet)`**: mounts a Jakarta `HttpServlet` inside a Ktor route
- **`KtorServletRequest` / `KtorServletResponse`**: the adapters used by that bridge

## Usage Examples

### Response Utilities

`respondWith` defaults to `ContentType.Text.Html`. The block may be suspending.

```kotlin
import com.pambrose.common.response.redirectTo
import com.pambrose.common.response.respondWith
import io.ktor.http.ContentType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

routing {
  get("/page") {
    call.respondWith { "<h1>Hello</h1>" }
  }

  get("/plain") {
    call.respondWith(ContentType.Text.Plain) { "hello" }
  }

  get("/data") {
    // The block can suspend
    call.respondWith(ContentType.Application.Json) { fetchJsonFromDatabase() }
  }

  get("/old-path") {
    call.redirectTo { "/new-path" }                      // 302
  }

  get("/moved") {
    call.redirectTo(permanent = true) { "/new-home" }    // 301
  }
}
```

The `RoutingContext` overloads let you drop the `call.` prefix inside a route handler:

```kotlin
get("/page") {
  respondWith { "<h1>Hello</h1>" }
}
```

### Request URI Prefix

```kotlin
import com.pambrose.common.response.uriPrefix

get("/whoami") {
  // e.g. "https://example.com:443"
  call.respondWith { call.request.origin.uriPrefix }
}
```

### Heroku HTTPS Redirect

The plugin inspects the `x-forwarded-proto` header. When it is `http`, the request is redirected to HTTPS
unless an exclusion predicate matches. The redirect goes to the configured `host`, or to the request's own
host when `host` is not set, and keeps the path and query string exactly as the client sent them (order, case and
encoding), so signed URLs survive the redirect.

```kotlin
import com.pambrose.common.features.HerokuHttpsRedirect
import io.ktor.server.application.install

install(HerokuHttpsRedirect) {
  host = "myapp.herokuapp.com" // optional; defaults to the request's host
  sslPort = 443              // defaults to the HTTPS default port
  permanentRedirect = true   // the default: 301; set false for a 302

  // Exclusions match the request path, not the query string
  excludePrefix("/health")
  excludePrefix("/.well-known")
  excludeSuffix(".txt")
  exclude { call -> call.request.headers.contains("X-Skip-Redirect") }
}
```

Configuration properties are `host`, `sslPort`, `permanentRedirect` and `excludePredicates`; the three
`exclude*` helpers append to that predicate list.

### Mounting a Servlet

`Route.servlet` initializes the servlet once through `init(ServletConfig)`, the way a container does, then
translates each Ktor request into a `KtorServletRequest`/`KtorServletResponse` pair. Servlet processing runs
on `Dispatchers.IO`, and the servlet's status, headers and body are forwarded back through the Ktor pipeline.
The servlet's `destroy()` is called when the application stops.

- **Initialization:** the `ServletConfig` is named after the servlet's class and has no init parameters, so
  servlets that do their setup in `init(ServletConfig)` rather than the no-arg `init()` work. Its
  `ServletContext` supports attributes and logging and reports no init parameters, resources or dispatchers;
  container features such as dynamic registration and sessions throw `UnsupportedOperationException`.
- **Supported:** `sendError` and `sendRedirect`, so an unsupported HTTP method gets `405` from `HttpServlet`'s
  defaults. Also request attributes, and `getPathInfo()`, which is always `null`.
- **HEAD, TRACE and `getLastModified`:** HEAD runs `doGet` (Servlet 6.1's default) and is answered with the
  headers and `Content-Length` GET would send, but no body. TRACE and servlets that override `getLastModified`
  (`If-Modified-Since`, `Last-Modified`) work through `HttpServlet`'s defaults.
- **Spec-defined helpers:** on the response, `set`/`addDateHeader`, `set`/`addIntHeader`, `flushBuffer` (commits
  the response), `resetBuffer`, `reset`, and `null` arguments to `setHeader`, `addHeader`, `setContentType` and
  `setCharacterEncoding`. `setContentLength` is accepted and ignored, like a `Content-Length` header. On the
  request, `getCookies`, `getDateHeader`, `getIntHeader`, `getRequestURL`, `isSecure`, `getCharacterEncoding`,
  `getContentLength`, `getDispatcherType` (`REQUEST`) and `getServletContext`.
- **Request bodies are not available:** `getInputStream`, `getReader` and `getParts` throw
  `UnsupportedOperationException`, and `getParameter` sees only the query string, not form bodies.
- **Content type and character encoding:** a charset set through `setContentType` or `setCharacterEncoding` is
  used for the body and included in the `Content-Type`, as a servlet container reports it. It can't change after
  `getWriter()`. A `Content-Type` header set with `setHeader` or `addHeader` is the content type, as in a
  container: it sets the charset, `getHeader` returns `getContentType()`, and `getHeaderNames()` does not list it.
  A servlet that sets no content type, or one that does not parse, is answered as `application/octet-stream`; the
  malformed value is logged as a warning.
- **Headers:** every value of a multi-valued header is forwarded. The servlet's `Content-Length`,
  `Transfer-Encoding` and `Upgrade` headers are dropped: Ktor sets `Content-Length` from the body it sends and
  rejects the other two.
- **Parameters:** parameter names are case-insensitive, which is Ktor's behavior, unlike a servlet container.

```kotlin
import com.pambrose.common.servlet.servlet
import io.ktor.server.routing.routing

routing {
  servlet("/metrics", MetricsServlet())
}
```

The Jakarta Servlet API is a `compileOnlyApi` dependency, so add it yourself when you use this bridge:

```kotlin
dependencies {
  implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
}
```

## API Reference

### Response Utilities

- `suspend fun ApplicationCall.respondWith(contentType: ContentType = ContentType.Text.Html, block: suspend () -> String)`
- `suspend fun ApplicationCall.redirectTo(permanent: Boolean = false, block: suspend () -> String)`
- `suspend fun RoutingContext.respondWith(contentType: ContentType = ContentType.Text.Html, block: suspend () -> String)`
- `suspend fun RoutingContext.redirectTo(permanent: Boolean = false, block: suspend () -> String)`
- `val RequestConnectionPoint.uriPrefix: String`

### Heroku

- `class HerokuHttpsRedirect` — properties `host` (nullable), `redirectPort`, `permanent`, `excludePredicates`
- `HerokuHttpsRedirect.Configuration` — `host` (default `null`), `sslPort` (default 443),
  `permanentRedirect` (default `true`), `excludePredicates`, `excludePrefix(pathPrefix)`,
  `excludeSuffix(pathSuffix)`, `exclude(predicate)`
- `typealias CallPredicate = (ApplicationCall) -> Boolean`

### Servlet Bridge

- `fun Route.servlet(path: String, servlet: HttpServlet)`
- `class KtorServletRequest`, `class KtorServletResponse`

## Dependencies

This module depends on:

- Kotlin Standard Library
- kotlin-logging (`implementation`)
- Ktor Server Core
- Jakarta Servlet API (`compileOnlyApi` — supply it yourself if you use `Route.servlet`)

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/ktor-server-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/ktor-server-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:ktor-server-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>ktor-server-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- `HerokuHttpsRedirect` relies on `x-forwarded-proto`; behind a proxy that does not set it, no redirect
  occurs
- Exclude health and metrics endpoints from the redirect so probes are not bounced
- No server engine is pulled in — add the Ktor engine you want (CIO, Netty, …) yourself

## License

Licensed under the Apache License, Version 2.0.
