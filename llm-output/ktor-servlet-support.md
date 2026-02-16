# Ktor Servlet Support

## Overview

Added a servlet-to-Ktor route adapter in the `ktor-server-utils` module that wraps any
`jakarta.servlet.http.HttpServlet` as a Ktor route handler. This allows servlets (admin endpoints,
metrics, health checks) to be mounted inside a Ktor application on any engine (CIO, Netty, Jetty)
without requiring a standalone Jetty server.

## Public API

A single extension function on `Route`:

```kotlin
fun Route.servlet(
  path: String,
  servlet: HttpServlet
)
```

Usage:

```kotlin
routing {
  servlet("/metrics", MetricsServlet(registry))
  servlet("/version", VersionServlet())
}
```

## How It Works

When a request hits the route, the adapter:

1. Wraps the Ktor `ApplicationRequest` in a `KtorServletRequest` (implements `HttpServletRequest`)
2. Creates a `KtorServletResponse` that buffers output in memory (implements `HttpServletResponse`)
3. Calls `servlet.service(request, response)` on `Dispatchers.IO`
4. Transfers the buffered status code, headers, content type, and body bytes back to the Ktor response

## Package

`com.github.pambrose.common.servlet` (matches the existing jetty-utils servlet package).

## Files Added

| File                                                                   | Purpose                                                                                |
|------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| `ktor-server-utils/src/main/kotlin/.../servlet/KtorServletRequest.kt`  | `HttpServletRequest` adapter over Ktor `ApplicationRequest`                            |
| `ktor-server-utils/src/main/kotlin/.../servlet/KtorServletResponse.kt` | `HttpServletResponse` adapter with buffered `PrintWriter`/`ServletOutputStream` output |
| `ktor-server-utils/src/main/kotlin/.../servlet/ServletRoute.kt`        | `Route.servlet()` extension function                                                   |
| `ktor-server-utils/src/test/kotlin/.../servlet/ServletRouteTests.kt`   | 8 tests using Ktor `testApplication` and Kotest matchers                               |

## Files Modified

| File                                 | Change                                                                  |
|--------------------------------------|-------------------------------------------------------------------------|
| `gradle/libs.versions.toml`          | Added `jakarta-servlet-api` (5.0.0) and `ktor-server-test-host` entries |
| `ktor-server-utils/build.gradle.kts` | Added `compileOnly` and `testImplementation` dependencies               |

## Adapter Coverage

### KtorServletRequest

Implements the methods used by project servlets:

- `getMethod()`, `getRequestURI()`, `getQueryString()`
- `getParameter()`, `getParameterNames()`, `getParameterValues()`, `getParameterMap()`
- `getHeader()`, `getHeaders()`, `getHeaderNames()`
- `getScheme()`, `getServerName()`, `getServerPort()`, `getProtocol()`
- `getContentType()`, `getRemoteAddr()`, `getContextPath()`, `getServletPath()`

All other `HttpServletRequest` methods throw `UnsupportedOperationException`.

### KtorServletResponse

Implements:

- `setStatus()` / `getStatus()`
- `setHeader()` / `addHeader()` / `containsHeader()` / `getHeader()` / `getHeaders()` / `getHeaderNames()`
- `setContentType()` / `getContentType()` / `setCharacterEncoding()` / `getCharacterEncoding()`
- `getWriter()` — returns a `PrintWriter` over a `ByteArrayOutputStream`
- `getOutputStream()` — returns a `ServletOutputStream` over the same buffer

Enforces the servlet spec rule that only one of `getWriter()`/`getOutputStream()` may be called per response.

## Dependencies

- `compileOnly(libs.jakarta.servlet.api)` — Jakarta Servlet API 5.0.0 (matches Jetty 11; actual jar comes transitively
  from consumer modules)
- `testImplementation(libs.jakarta.servlet.api)` — so tests compile
- `testImplementation(libs.ktor.server.test.host)` — Ktor test engine
- `testImplementation(libs.kotest)` / `testImplementation(kotlin("test"))` — test framework

## Tests

All 8 tests pass via `./gradlew :ktor-server-utils:test`:

1. Basic GET returning text
2. Custom content type (application/json)
3. Query parameter passthrough
4. Request header passthrough
5. Custom status codes (404)
6. Response headers
7. OutputStream-based servlet (simulates MetricsServlet)
8. POST method dispatch
