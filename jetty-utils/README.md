# Jetty Utils

Utilities for Eclipse Jetty web server, providing DSL functions for server configuration, servlet helpers, and common
web application patterns.

## Features

### Server DSL

- **Jetty Server DSL**: Fluent API for creating and configuring Jetty servers
- **Handler Configuration**: Easy setup of handlers, contexts, and servlets
- **SSL/TLS Support**: Simple TLS configuration for HTTPS

### Servlet Utilities

- **Lambda Servlet**: Create servlets using lambda functions
- **Version Servlet**: Built-in servlet for exposing application version information

### Common Patterns

- **Static Content**: Serve static files and resources
- **REST APIs**: Simple setup for REST endpoints

## Usage Examples

### Basic Server Setup

```kotlin
import com.github.pambrose.common.dsl.JettyDsl.jettyServer

val server = jettyServer {
  port = 8080

  // Add context handler
  contextHandler("/api") {
    addServlet<MyApiServlet>("/users/*")
  }

  // Add static content
  staticContent("/static", "web/static")
}

server.start()
server.join()
```

### Lambda Servlet

```kotlin
import com.github.pambrose.common.servlet.LambdaServlet

val echoServlet = LambdaServlet { request, response ->
  response.contentType = "text/plain"
  response.writer.use { writer ->
    writer.println("Echo: ${request.getParameter("message")}")
  }
}

// Add to server
server.addServlet(echoServlet, "/echo")
```

### Version Servlet

```kotlin
import com.github.pambrose.common.servlet.VersionServlet

@VersionAnnotation("1.0.0")
class MyApp

val versionServlet = VersionServlet(MyApp::class.java)

// Add to server - exposes version info at /version
server.addServlet(versionServlet, "/version")
```

### SSL/TLS Configuration

```kotlin
val httpsServer = jettyServer {
  port = 8443

  // Configure SSL
  sslConnector {
    keystorePath = "keystore.p12"
    keystorePassword = "password"
    keyManagerPassword = "password"
  }

  contextHandler("/secure") {
    addServlet<SecureServlet>("/data/*")
  }
}
```

### REST API Example

```kotlin
import com.github.pambrose.common.servlet.LambdaServlet
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

data class User(
  val id: Int,
  val name: String,
  val email: String
)

val usersServlet = LambdaServlet { request, response ->
  response.contentType = "application/json"

  when (request.method) {
    "GET" -> {
      val users = listOf(
        User(1, "John Doe", "john@example.com"),
        User(2, "Jane Smith", "jane@example.com")
      )
      response.writer.use { writer ->
        writer.write(Json.encodeToString(users))
      }
    }
    "POST" -> {
      // Handle POST request
      response.status = 201
      response.writer.use { writer ->
        writer.write("""{"message": "User created"}""")
      }
    }
    else -> {
      response.status = 405
      response.writer.use { writer ->
        writer.write("""{"error": "Method not allowed"}""")
      }
    }
  }
}
```

### Static Content and Resources

```kotlin
val server = jettyServer {
  port = 8080

  // Serve static files from filesystem
  staticContent("/static", "/var/www/static")

  // Serve resources from classpath
  resourceHandler("/resources", "web/resources")

  // Default handler for SPA
  defaultHandler { request, response ->
    response.contentType = "text/html"
    response.writer.use { writer ->
      writer.write(this::class.java.getResourceAsStream("/index.html")!!.readBytes().toString(Charsets.UTF_8))
    }
  }
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Eclipse Jetty Server
- Eclipse Jetty Servlet
- Core Utils (for version management)

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:jetty-utils:2.4.13")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>jetty-utils</artifactId>
  <version>2.4.13</version>
</dependency>
```

## Configuration Options

### Server Configuration

- `port`: HTTP port (default: 8080)
- `host`: Bind address (default: all interfaces)
- `maxThreads`: Maximum thread pool size
- `minThreads`: Minimum thread pool size
- `idleTimeout`: Connection idle timeout

### SSL Configuration

- `keystorePath`: Path to keystore file
- `keystorePassword`: Keystore password
- `keyManagerPassword`: Key manager password
- `trustStorePath`: Path to trust store (optional)
- `trustStorePassword`: Trust store password (optional)

## Security Considerations

⚠️ **Security Notes**:

- Always validate and sanitize user input in servlet handlers
- Use HTTPS in production environments
- Implement proper authentication and authorization
- Be cautious with file serving to prevent directory traversal attacks
- Set appropriate cache headers for static content

## Performance Notes

- Configure appropriate thread pool sizes based on expected load
- Use connection pooling for database connections
- Implement proper caching for static content
- Consider using NIO connectors for high-concurrency scenarios

## Error Handling

```kotlin
val errorHandlingServlet = LambdaServlet { request, response ->
  try {
    // Your logic here
    response.writer.use { writer ->
      writer.write("Success")
    }
  } catch (e: Exception) {
    response.status = 500
    response.writer.use { writer ->
      writer.write("""{"error": "${e.message}"}""")
    }
  }
}
```

## Best Practices

1. **Resource Management**: Always use `use` blocks for writers and streams
2. **Content Type**: Set appropriate content types for responses
3. **Status Codes**: Use proper HTTP status codes
4. **Error Handling**: Implement comprehensive error handling
5. **Security Headers**: Set security headers for production deployments
6. **Logging**: Implement request/response logging for debugging

## Common Patterns

### CORS Support

```kotlin
val corsServlet = LambdaServlet { request, response ->
  response.setHeader("Access-Control-Allow-Origin", "*")
  response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE")
  response.setHeader("Access-Control-Allow-Headers", "Content-Type")

  // Your API logic here
}
```

### Request Logging

```kotlin
val loggingServlet = LambdaServlet { request, response ->
  println("${request.method} ${request.requestURL} from ${request.remoteAddr}")

  // Your logic here
}
```

## License

Licensed under the Apache License, Version 2.0.
