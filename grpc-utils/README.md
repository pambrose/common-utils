# gRPC Utils

Utilities for gRPC applications: builder functions for channels and servers (Netty or in-process), TLS
context construction from certificate files, a `StreamObserver` DSL, and graceful shutdown extensions.

`GrpcDsl.channel` and `GrpcDsl.server` take their settings as **named arguments**. The trailing lambda is
applied to the underlying gRPC builder (`ManagedChannelBuilder` / `ServerBuilder`), so inside it you call
real gRPC builder methods such as `addService(...)` or `directExecutor()`.

## Features

### gRPC DSL

- **`GrpcDsl.channel`**: builds a `ManagedChannel` over Netty or the in-process transport
- **`GrpcDsl.server`**: builds a `Server` over Netty or the in-process transport
- **`GrpcDsl.streamObserver`**: builds a `StreamObserver<T>` from `onNext` / `onError` / `onCompleted` blocks
- **`GrpcDsl.attributes`**: builds a gRPC `Attributes` instance

### TLS Configuration

- **`TlsUtils`**: builds client and server `TlsContext`s from certificate files
- **Mutual authentication**: supplying a trust collection enables mutual auth
- **`TlsContext.PLAINTEXT_CONTEXT`**: the non-TLS context

### Server Management

- **`shutdownWithJvm`**: registers a JVM shutdown hook for graceful shutdown
- **`shutdownGracefully`**: shuts down, awaits termination, then forces shutdown

## Usage Examples

### Creating gRPC Servers

```kotlin
import com.pambrose.common.dsl.GrpcDsl
import com.pambrose.common.utils.TlsContext.Companion.PLAINTEXT_CONTEXT
import com.pambrose.common.utils.TlsUtils

// Plaintext server (tlsContext defaults to PLAINTEXT_CONTEXT)
val plaintextServer =
  GrpcDsl.server(port = 8080) {
    addService(MyServiceImpl())
  }

// TLS server
val tlsServer =
  GrpcDsl.server(
    port = 8443,
    tlsContext = TlsUtils.buildServerTlsContext(
      certChainFilePath = "server.crt",
      privateKeyFilePath = "server.key",
      trustCertCollectionFilePath = "ca.crt",  // supplying this enables mutual auth
    ),
  ) {
    addService(MyServiceImpl())
  }

// In-process server, ignoring port and TLS
val inProcessServer =
  GrpcDsl.server(inProcessServerName = "test-server") {
    addService(MyServiceImpl())
    directExecutor()
  }

plaintextServer.start()
```

### Creating gRPC Channels

`channel` requires `tlsContext` — pass `PLAINTEXT_CONTEXT` for a non-TLS channel.

```kotlin
import com.pambrose.common.dsl.GrpcDsl
import com.pambrose.common.utils.TlsContext.Companion.PLAINTEXT_CONTEXT
import com.pambrose.common.utils.TlsUtils

// Plaintext channel
val plaintextChannel =
  GrpcDsl.channel(
    hostName = "localhost",
    port = 8080,
    tlsContext = PLAINTEXT_CONTEXT,
  ) {
    // configure the ManagedChannelBuilder here
  }

// TLS channel with retry enabled
val tlsChannel =
  GrpcDsl.channel(
    hostName = "api.example.com",
    port = 443,
    enableRetry = true,
    maxRetryAttempts = 5,
    tlsContext = TlsUtils.buildClientTlsContext(
      certChainFilePath = "client.crt",
      privateKeyFilePath = "client.key",
      trustCertCollectionFilePath = "ca.crt",
    ),
  ) {
  }

// In-process channel, ignoring host, port and TLS
val inProcessChannel =
  GrpcDsl.channel(inProcessServerName = "test-server", tlsContext = PLAINTEXT_CONTEXT) {
    directExecutor()
  }
```

`overrideAuthority` overrides the authority used for TLS hostname verification, which is useful when the
certificate's name does not match the host you are dialing.

### StreamObserver DSL

Every block is optional; omitted callbacks do nothing.

```kotlin
import com.pambrose.common.dsl.GrpcDsl

val observer =
  GrpcDsl.streamObserver<String> {
    onNext { value -> println("Received: $value") }
    onError { error -> println("Failed: ${error.message}") }
    onCompleted { println("Done") }
  }
```

### TLS Contexts

```kotlin
import com.pambrose.common.utils.TlsUtils

// Client context; every path is optional. With no trust path, the JVM's default trust store is used,
// which is what a server holding a public-CA certificate needs.
val clientContext =
  TlsUtils.buildClientTlsContext(
    certChainFilePath = "client.crt",
    privateKeyFilePath = "client.key",
    trustCertCollectionFilePath = "ca.crt",
  )

// Server context; cert chain and private key are required
val serverContext =
  TlsUtils.buildServerTlsContext(
    certChainFilePath = "server.crt",
    privateKeyFilePath = "server.key",
  )

// "plaintext", "TLS with mutual auth", or "TLS (no mutual auth)"
println(serverContext.desc())
```

`buildClientTlsContext` and `buildServerTlsContext` return a `TlsContext` ready to hand to `channel` or
`server`. The `clientTlsContextBuilder` and `serverTlsContext` variants return a `TlsContextBuilder` whose
`builder` you can customize before calling `build()` yourself. Both carry gRPC's ALPN configuration, which
`NettyServerBuilder.sslContext` rejects a context without, so a hand-built context works as well as the
ready-made one.

Supplying `trustCertCollectionFilePath` pins the servers you trust to that CA; supplying a client
`certChainFilePath` and `privateKeyFilePath` together enables mutual auth. On the server side, a
`trustCertCollectionFilePath` requires a client certificate.

### Graceful Shutdown

```kotlin
import com.pambrose.common.utils.shutdownGracefully
import com.pambrose.common.utils.shutdownWithJvm
import kotlin.time.Duration.Companion.seconds

// Register a JVM shutdown hook
server.shutdownWithJvm(maxWaitTime = 30.seconds)

// Or shut down explicitly
server.shutdownGracefully(maxWaitTime = 10.seconds)
```

`shutdownGracefully` calls `shutdown()`, waits up to the timeout via `awaitTermination`, and then calls
`shutdownNow()` in a `finally` block so the server always stops — including when `shutdown()` itself throws,
as it does on an already-terminated server. It throws `InterruptedException` if the waiting thread is
interrupted.

`shutdownWithJvm` runs the same sequence from a JVM shutdown hook, swallowing any failure since nothing can
observe it at that point. It rejects a timeout under a millisecond when the hook is registered, rather than
failing at JVM exit where it would skip the shutdown entirely.

## API Reference

### `GrpcDsl`

- `channel(hostName: String = "", port: Int = -1, enableRetry: Boolean = false, maxRetryAttempts: Int = 5, tlsContext: TlsContext = PLAINTEXT_CONTEXT, overrideAuthority: String = "", inProcessServerName: String = "", block: ManagedChannelBuilder<*>.() -> Unit): ManagedChannel`
- `server(port: Int = -1, tlsContext: TlsContext = PLAINTEXT_CONTEXT, inProcessServerName: String = "", block: ServerBuilder<*>.() -> Unit): Server`
- `attributes(block: Attributes.Builder.() -> Unit): Attributes`
- `streamObserver(init: StreamObserverHelper<T>.() -> Unit): StreamObserver<T>` — each callback may be
  registered at most once; a second registration throws `IllegalStateException`

### `TlsUtils`

- `buildClientTlsContext(certChainFilePath: String = "", privateKeyFilePath: String = "", trustCertCollectionFilePath: String = ""): TlsContext`
- `buildServerTlsContext(certChainFilePath: String, privateKeyFilePath: String, trustCertCollectionFilePath: String = ""): TlsContext`

### `TlsContext`

- `data class TlsContext(sslContext: SslContext?, mutualAuth: Boolean)`
- `desc(): String`
- `TlsContext.PLAINTEXT_CONTEXT`

### Server Extensions

- `Server.shutdownWithJvm(maxWaitTime: Duration)`
- `Server.shutdownGracefully(maxWaitTime: Duration)`
- `Server.shutdownGracefully(timeout: Long, unit: TimeUnit)`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- gRPC Netty, In-Process, Protobuf and Services
- Netty tcnative (BoringSSL) for TLS, with its native libraries for Linux (x86_64, aarch_64), macOS (x86_64,
  aarch_64) and Windows (x86_64) as runtime dependencies. Netty uses OpenSSL on those platforms and falls back to
  the JDK TLS provider elsewhere. Exclude `io.netty:netty-tcnative-boringssl-static` to always use the JDK provider.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/grpc-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/grpc-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:grpc-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>grpc-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Testing

The in-process transport makes gRPC tests hermetic — no ports are bound. Pass the same
`inProcessServerName` to `server` and `channel`, and use `directExecutor()` to keep calls on the test
thread.

## License

Licensed under the Apache License, Version 2.0.
