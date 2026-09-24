# Ktor Client Utils

A small DSL around Ktor's `HttpClient` that handles client creation and lifecycle, plus a blocking GET
helper on the JVM.

**Multiplatform** — the DSL targets JVM, JS, wasmJs and Native. `blockingGet` is JVM-only.

This module deliberately contains only these helpers; everything else is ordinary Ktor client API.

## Features

- **`newHttpClient`**: creates an `HttpClient` with `HttpTimeout` installed
- **`withHttpClient`**: runs a block with the client as **receiver**, closing it if it created one
- **`httpClient`**: same, but passes the client as a **parameter**
- **`HttpClient.get`**: GET that hands the `HttpResponse` to a block
- **`blockingGet`** (JVM): the same GET, run with `runBlocking`

## Usage Examples

### Creating a Client

```kotlin
import com.pambrose.common.dsl.KtorDsl

// expectSuccess = false by default, so non-2xx responses do not throw
val client = KtorDsl.newHttpClient()

// Throw on non-2xx instead
val strictClient = KtorDsl.newHttpClient(expectSuccess = true)
```

`HttpTimeout` is installed for you; configure it per request with the `setUp` block or on the client.

### withHttpClient and httpClient

Both take an optional existing client. When it is `null`, a new client is created **and closed** after the
block; when you pass one in, it is reused and left open for you to manage.

```kotlin
import com.pambrose.common.dsl.KtorDsl.httpClient
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

// Receiver form — `this` is the HttpClient
val body =
  withHttpClient {
    get("https://example.com/api").bodyAsText()
  }

// Parameter form — useful when the receiver would be ambiguous
val body2 =
  httpClient { client ->
    client.get("https://example.com/api").bodyAsText()
  }

// Reuse a long-lived client; it is NOT closed for you
val existing = KtorDsl.newHttpClient()
val body3 = withHttpClient(httpClient = existing) { get("https://example.com").bodyAsText() }
existing.close()
```

`expectSuccess` applies only when the helper creates the client; it is ignored when you supply one.

### The `get` Helper

`get` is declared as a **member extension** of `KtorDsl`. Bring `KtorDsl` into scope with `with(KtorDsl) { }`, or
import the member directly with `import com.pambrose.common.dsl.KtorDsl.get`. With the import, a call ending in a
trailing lambda can be ambiguous with Ktor's own `io.ktor.client.request.get` if both are imported:

```kotlin
import com.pambrose.common.dsl.KtorDsl
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

val result =
  withHttpClient {
    with(KtorDsl) {
      this@withHttpClient.get("https://example.com/api") { response ->
        if (response.status == HttpStatusCode.OK) response.bodyAsText() else "failed"
      }
    }
  }
```

Add request configuration through `setUp`:

```kotlin
with(KtorDsl) {
  client.get(
    url = "https://example.com/api",
    setUp = {
      headers.append("Authorization", "Bearer $token")
    },
  ) { response ->
    response.bodyAsText()
  }
}
```

For most code, Ktor's own `client.get(url)` is simpler; this helper exists for the response-block style.

### blockingGet (JVM only)

`blockingGet` is an extension on `KtorDsl` that wraps the whole exchange in `runBlocking`, creating and
closing a client unless you pass one. Use it only from non-suspending JVM code — never inside a coroutine.

```kotlin
import com.pambrose.common.dsl.KtorDsl
import com.pambrose.common.dsl.blockingGet
import io.ktor.client.statement.bodyAsText

val body =
  KtorDsl.blockingGet("https://example.com/api") { response ->
    response.bodyAsText()
  }
```

Pass `httpClient` to reuse an existing client, which is left open. Pass `expectSuccess = true` to make a newly
created client throw on non-2xx responses:

```kotlin
val client = KtorDsl.newHttpClient()
val status = KtorDsl.blockingGet("https://example.com/api", httpClient = client) { it.status }

val strictBody = KtorDsl.blockingGet("https://example.com/api", expectSuccess = true) { it.bodyAsText() }
```

## API Reference

### `KtorDsl` (commonMain)

- `newHttpClient(expectSuccess: Boolean = false): HttpClient`
- `suspend fun <T> withHttpClient(httpClient: HttpClient? = null, expectSuccess: Boolean = false, block: suspend HttpClient.() -> T): T`
- `suspend fun <T> httpClient(httpClient: HttpClient? = null, expectSuccess: Boolean = false, block: suspend (HttpClient) -> T): T`
- `suspend fun <T> HttpClient.get(url: String, setUp: HttpRequestBuilder.() -> Unit = {}, block: suspend (HttpResponse) -> T): T` — member extension; use `with(KtorDsl)` or
  `import com.pambrose.common.dsl.KtorDsl.get`

### jvmMain

- `fun <T> KtorDsl.blockingGet(url: String, httpClient: HttpClient? = null, expectSuccess: Boolean = false, setUp: HttpRequestBuilder.() -> Unit = {}, block: suspend (HttpResponse) -> T): T`
  — pass `setUp` by name (`setUp = { … }`); before 4.1.0 it was the second parameter

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Ktor Client Core

No engine is included, and `newHttpClient` (like `withHttpClient`/`httpClient` without a client) uses whichever
engine Ktor finds among your dependencies:

| Platform      | Engine                                                                 |
|---------------|------------------------------------------------------------------------|
| JVM           | Add one, e.g. `ktor-client-cio`                                        |
| JS, wasmJs    | None needed: `ktor-client-core` falls back to its bundled Js engine    |
| Apple         | Add one, e.g. `ktor-client-darwin`                                     |
| Linux/Windows | Add one, e.g. `ktor-client-curl` (or `ktor-client-winhttp` on Windows) |

Without an engine, creating a client fails. The cause of the first failure is Ktor's `IllegalStateException`
("Failed to find HTTP client engine implementation"), wrapped in the platform's initialization error
(`ExceptionInInitializerError` on the JVM); later attempts fail without that cause. Passing your own
`HttpClient(engine)` avoids the lookup entirely.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/ktor-client-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/ktor-client-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:ktor-client-utils:LATEST_VERSION")
}
```

### Maven

Maven consumers must depend on the `-jvm` artifact, since this is a multiplatform module:

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>ktor-client-utils-jvm</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- Creating an `HttpClient` is expensive — prefer one long-lived client over per-request creation, and pass
  it to `withHttpClient`/`httpClient` rather than letting them create one each time
- `expectSuccess` defaults to `false`, so check `response.status` yourself unless you opt in
- `blockingGet` blocks the calling thread; in suspending code call `withHttpClient` directly

## License

Licensed under the Apache License, Version 2.0.
