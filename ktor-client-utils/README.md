# Ktor Client Utils

Utilities for Ktor HTTP client, providing DSL functions and extensions for HTTP operations, including both suspending
and blocking variants.

## Features

### HTTP Client DSL

- **Client Configuration**: DSL for creating and configuring Ktor HTTP clients
- **Request Builders**: Simplified request building with common patterns
- **Response Handling**: Utilities for processing HTTP responses

### Client Operations

- **Suspending Functions**: Coroutine-friendly HTTP operations
- **Blocking Operations**: Traditional blocking HTTP calls for compatibility
- **Resource Management**: Automatic client lifecycle management

## Usage Examples

### Basic HTTP Requests

```kotlin
import com.github.pambrose.common.dsl.KtorDsl.httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*

// Create a configured client
val client = httpClient {
  timeout {
    requestTimeoutMillis = 30000
    connectTimeoutMillis = 10000
  }

  defaultRequest {
    header("User-Agent", "MyApp/1.0")
  }
}

// Make a GET request
val response: HttpResponse = client.get("https://api.example.com/users")
val content = response.bodyAsText()
println(content)

// Clean up
client.close()
```

### Using with Resource Management

```kotlin
import com.github.pambrose.common.dsl.KtorDsl.httpClient

// Automatically closes client
httpClient().use { client ->
  val response = client.get("https://api.example.com/data")
  println("Status: ${response.status}")
  println("Content: ${response.bodyAsText()}")
}
```

### Blocking Operations

```kotlin
import com.github.pambrose.common.dsl.KtorDsl.blockingGet

// Blocking GET request (for non-coroutine contexts)
val content = blockingGet("https://api.example.com/data")
println(content)
```

### JSON API Client

```kotlin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

@Serializable
data class User(
  val id: Int,
  val name: String,
  val email: String
)

val jsonClient = httpClient {
  install(ContentNegotiation) {
    json()
  }
}

// GET JSON data
val users: List<User> = jsonClient.get("https://api.example.com/users").body()

// POST JSON data
val newUser = User(0, "John Doe", "john@example.com")
val response = jsonClient.post("https://api.example.com/users") {
  contentType(ContentType.Application.Json)
  setBody(newUser)
}
```

### File Download

```kotlin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import java.io.File

suspend fun downloadFile(
  url: String,
  outputFile: File
) {
  httpClient().use { client ->
    val response: HttpResponse = client.get(url)
    val channel: ByteReadChannel = response.bodyAsChannel()

    outputFile.writeBytes(channel.toByteArray())
  }
}

// Usage
downloadFile("https://example.com/file.pdf", File("downloaded.pdf"))
```

### Authentication

```kotlin
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*

val authenticatedClient = httpClient {
  install(Auth) {
    bearer {
      loadTokens {
        BearerTokens("your-access-token", "your-refresh-token")
      }
    }
  }
}

// Requests will automatically include authentication
val response = authenticatedClient.get("https://api.example.com/protected")
```

### Error Handling

```kotlin
import io.ktor.client.plugins.*
import io.ktor.client.statement.*

try {
  val response = client.get("https://api.example.com/data")

  when (response.status.value) {
    in 200..299 -> {
      val content = response.bodyAsText()
      println("Success: $content")
    }
    404 -> {
      println("Resource not found")
    }
    in 500..599 -> {
      println("Server error: ${response.status}")
    }
    else -> {
      println("Unexpected status: ${response.status}")
    }
  }
} catch (e: HttpRequestTimeoutException) {
  println("Request timed out")
} catch (e: ClientRequestException) {
  println("Client error: ${e.message}")
} catch (e: ServerResponseException) {
  println("Server error: ${e.message}")
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Ktor Client Core
- Ktor Client CIO (default engine)
- Kotlinx Coroutines

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:ktor-client-utils:2.5.4")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>ktor-client-utils</artifactId>
  <version>2.5.4</version>
</dependency>
```

## Configuration Options

### Timeout Configuration

```kotlin
val client = httpClient {
  timeout {
    requestTimeoutMillis = 30000   // Total request timeout
    connectTimeoutMillis = 10000   // Connection timeout
    socketTimeoutMillis = 15000    // Socket read timeout
  }
}
```

### Retry Configuration

```kotlin
val client = httpClient {
  install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 3)
    exponentialDelay()
  }
}
```

### Logging

```kotlin
val client = httpClient {
  install(Logging) {
    level = LogLevel.INFO
  }
}
```

## Performance Notes

- **Connection Pooling**: Ktor automatically manages connection pooling
- **Blocking Operations**: `blockingGet()` uses `runBlocking` which blocks threads - use sparingly
- **Client Reuse**: Reuse HTTP clients when possible to benefit from connection pooling
- **Resource Management**: Always close clients when done or use `use` blocks

## Thread Safety

- HTTP clients are thread-safe and can be used from multiple coroutines
- Client configuration should be done once during initialization
- Sharing clients across multiple operations is recommended

## Error Handling Best Practices

1. **Timeout Handling**: Set appropriate timeouts for your use case
2. **Retry Logic**: Implement retry logic for transient failures
3. **Status Code Handling**: Check HTTP status codes and handle errors appropriately
4. **Network Errors**: Handle network connectivity issues gracefully
5. **Resource Cleanup**: Ensure clients are properly closed

## Common Patterns

### API Client Wrapper

```kotlin
class ApiClient(
  private val baseUrl: String,
  private val apiKey: String
) {
  private val client = httpClient {
    defaultRequest {
      url(baseUrl)
      header("Authorization", "Bearer $apiKey")
      header("Content-Type", "application/json")
    }
  }

  suspend fun getUser(id: Int): User? {
    return try {
      client.get("/users/$id").body<User>()
    } catch (e: ClientRequestException) {
      if (e.response.status.value == 404) null else throw e
    }
  }

  fun close() = client.close()
}
```

### Batch Requests

```kotlin
suspend fun fetchMultipleUrls(urls: List<String>): List<String> {
  return httpClient().use { client ->
    urls.map { url ->
      async {
        client.get(url).bodyAsText()
      }
    }.awaitAll()
  }
}
```

## Security Considerations

- **HTTPS**: Always use HTTPS for sensitive data
- **Certificate Validation**: Ensure proper certificate validation in production
- **API Keys**: Never hardcode API keys - use environment variables or secure storage
- **Input Validation**: Validate all user inputs before making requests

## License

Licensed under the Apache License, Version 2.0.
