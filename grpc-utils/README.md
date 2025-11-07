# gRPC Utils

Utilities for gRPC applications, providing DSL functions for creating gRPC channels and servers, TLS configuration, and
server lifecycle management.

## Features

### gRPC DSL

- **Channel Builder**: DSL for creating gRPC channels with various configurations
- **Server Builder**: DSL for creating gRPC servers with TLS and plaintext support
- **StreamObserver Helper**: Utilities for working with gRPC streaming responses

### TLS Configuration

- **TLS Context Builder**: Comprehensive TLS configuration for client and server
- **Certificate Management**: Support for client certificates, server certificates, and mutual authentication
- **SSL Context Creation**: Utilities for creating SSL contexts from certificate files

### Server Management

- **Graceful Shutdown**: Extensions for graceful server shutdown with timeout
- **Server Lifecycle**: Utilities for managing server startup and shutdown

## Usage Examples

### Creating gRPC Channels

```kotlin
import com.github.pambrose.common.dsl.GrpcDsl.grpcChannel

// Create a plaintext channel
val plaintextChannel = grpcChannel {
  hostName = "localhost"
  port = 8080
}

// Create a TLS channel
val tlsChannel = grpcChannel {
  hostName = "api.example.com"
  port = 443
  tlsContext = tlsContext {
    certChainFile = "client.crt"
    privateKeyFile = "client.key"
    trustCertCollectionFile = "ca.crt"
  }
}

// Create an in-process channel
val inProcessChannel = grpcChannel {
  inProcessServerName = "test-server"
}
```

### Creating gRPC Servers

```kotlin
import com.github.pambrose.common.dsl.GrpcDsl.grpcServer

// Create a plaintext server
val plaintextServer = grpcServer {
  port = 8080
  addService(MyServiceImpl())
}

// Create a TLS server
val tlsServer = grpcServer {
  port = 8443
  tlsContext = tlsContext {
    certChainFile = "server.crt"
    privateKeyFile = "server.key"
    trustCertCollectionFile = "ca.crt"
    clientAuth = true  // Enable mutual authentication
  }
  addService(MyServiceImpl())
}
```

### TLS Configuration

```kotlin
import com.github.pambrose.common.utils.TlsUtils.tlsContext

// Client TLS context
val clientTlsContext = tlsContext {
  certChainFile = "client.crt"
  privateKeyFile = "client.key"
  trustCertCollectionFile = "ca.crt"
}

// Server TLS context with mutual authentication
val serverTlsContext = tlsContext {
  certChainFile = "server.crt"
  privateKeyFile = "server.key"
  trustCertCollectionFile = "ca.crt"
  clientAuth = true
}
```

### Server Lifecycle Management

```kotlin
import com.github.pambrose.common.utils.shutdownGracefully
import kotlin.time.Duration.Companion.seconds

// Start server
val server = grpcServer {
  port = 8080
  addService(MyServiceImpl())
}.start()

// Graceful shutdown with timeout
server.shutdownGracefully(30.seconds)
```

### StreamObserver Helper

```kotlin
import com.github.pambrose.common.dsl.GrpcDsl.StreamObserverHelper

val helper = StreamObserverHelper<MyResponse>()

// Set up response observer
helper.responseObserver = object : StreamObserver<MyResponse> {
  override fun onNext(value: MyResponse) {
    println("Received: $value")
  }

  override fun onError(t: Throwable) {
    println("Error: ${t.message}")
  }

  override fun onCompleted() {
    println("Stream completed")
  }
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- gRPC Core
- gRPC Netty
- gRPC Protobuf
- gRPC Services
- gRPC Stub
- Netty SSL/TLS

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:grpc-utils:2.4.8")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>grpc-utils</artifactId>
  <version>2.4.8</version>
</dependency>
```

## TLS Security

### Certificate Files

- **Server Certificate**: `server.crt` - Server's public certificate
- **Server Private Key**: `server.key` - Server's private key
- **Client Certificate**: `client.crt` - Client's public certificate (for mutual auth)
- **Client Private Key**: `client.key` - Client's private key (for mutual auth)
- **CA Certificate**: `ca.crt` - Certificate Authority's public certificate

### Best Practices

1. **Key Management**: Store private keys securely and never commit them to version control
2. **Certificate Validation**: Always validate certificate chains and expiration dates
3. **Mutual Authentication**: Use client certificates for enhanced security
4. **Cipher Suites**: Use strong cipher suites and disable weak ones

## Security Considerations

⚠️ **Security Notes**:

- Certificate paths are not validated against path traversal attacks
- No automatic certificate expiration checking
- Private keys should be stored securely and encrypted when possible
- Consider using certificate management systems for production deployments

## Performance Notes

- Connection pooling is handled by the underlying gRPC implementation
- TLS handshake adds latency to connection establishment
- Keep-alive settings can be configured for long-lived connections

## Thread Safety

- All DSL builders are thread-safe
- gRPC channels and servers are thread-safe
- TLS contexts are immutable and thread-safe

## Error Handling

- TLS configuration errors are thrown during context creation
- Server startup errors are propagated immediately
- Channel connection errors are handled asynchronously

## License

Licensed under the Apache License, Version 2.0.
