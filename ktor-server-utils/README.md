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

The plugin inspects the `x-forwarded-proto` header. When it is `http`, the request is redirected to the
configured HTTPS host unless an exclusion predicate matches.

```kotlin
import com.pambrose.common.features.HerokuHttpsRedirect
import io.ktor.server.application.install

install(HerokuHttpsRedirect) {
  host = "myapp.herokuapp.com"
  sslPort = 443              // defaults to the HTTPS default port
  permanentRedirect = true   // 301 instead of 302

  // Exclusions — use these rather than raw path lists
  excludePrefix("/health")
  excludeSuffix(".well-known")
  exclude { call -> call.request.headers.contains("X-Skip-Redirect") }
}
```

Configuration properties are `host`, `sslPort`, `permanentRedirect` and `excludePredicates`; the three
`exclude*` helpers append to that predicate list.

### Mounting a Servlet

`Route.servlet` initializes the servlet once, then translates each Ktor request into a
`KtorServletRequest`/`KtorServletResponse` pair. Servlet processing runs on `Dispatchers.IO`, and the
servlet's status, headers and body are forwarded back through the Ktor pipeline.

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

- `class HerokuHttpsRedirect` — properties `host`, `redirectPort`, `permanent`, `excludePredicates`
- `HerokuHttpsRedirect.Configuration` — `host`, `sslPort`, `permanentRedirect`, `excludePredicates`,
  `excludePrefix(pathPrefix)`, `excludeSuffix(pathSuffix)`, `exclude(predicate)`
- `typealias CallPredicate = (ApplicationCall) -> Boolean`

### Servlet Bridge

- `fun Route.servlet(path: String, servlet: HttpServlet)`
- `class KtorServletRequest`, `class KtorServletResponse`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Ktor Server Core
- Kotlin Reflect
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
