# Ktor Server Utils

Utilities for Ktor HTTP server, providing plugins, response utilities, and common server-side patterns including
Heroku-specific features.

## Features

### Server Plugins

- **HerokuHttpsRedirect**: Automatic HTTPS redirect for Heroku deployments
- **Plugin Configuration**: Easy installation and configuration of custom plugins

### Response Utilities

- **Response Helpers**: Utilities for common response patterns
- **Content Types**: Helper functions for setting appropriate content types
- **Status Codes**: Utilities for handling HTTP status codes

### Heroku Integration

- **HTTPS Redirect**: Handle `x-forwarded-proto` header for Heroku SSL termination
- **Environment Configuration**: Utilities for Heroku-specific configuration

## Usage Examples

### Heroku HTTPS Redirect Plugin

```kotlin
import com.github.pambrose.common.features.HerokuHttpsRedirect
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
  embeddedServer(Netty, port = 8080) {
    // Install Heroku HTTPS redirect plugin
    install(HerokuHttpsRedirect) {
      // Exclude specific paths from redirect
      excludePaths = setOf("/health", "/metrics")

      // Exclude paths matching patterns
      excludePathPatterns = setOf("/api/v1/public/.*".toRegex())
    }

    // Your application routes
    routing {
      get("/") {
        call.respondText("Hello, HTTPS World!")
      }

      get("/health") {
        call.respondText("OK") // This won't be redirected
      }
    }
  }.start(wait = true)
}
```

### Response Utilities

```kotlin
import com.github.pambrose.common.response.ResponseUtils
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
  routing {
    get("/api/users") {
      val users = getUsersFromDatabase()

      // Use response utilities
      call.respondJson(users)
    }

    get("/download/{filename}") {
      val filename = call.parameters["filename"]!!
      val file = getFileFromStorage(filename)

      if (file.exists()) {
        call.respondFile(file)
      } else {
        call.respondNotFound("File not found")
      }
    }

    post("/api/users") {
      try {
        val user = call.receive<User>()
        val savedUser = saveUser(user)
        call.respondCreated(savedUser)
      } catch (e: ValidationException) {
        call.respondBadRequest(e.message)
      }
    }
  }
}
```

### Custom Plugin Example

```kotlin
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*

val RequestLoggingPlugin = createApplicationPlugin(
  name = "RequestLogging"
) {
  on(CallLogging) { call ->
    val method = call.request.httpMethod.value
    val uri = call.request.uri
    val userAgent = call.request.headers["User-Agent"]

    println("$method $uri - User-Agent: $userAgent")
  }
}

// Install the plugin
fun Application.configurePlugins() {
  install(RequestLoggingPlugin)
}
```

### Environment-Aware Configuration

```kotlin
import io.ktor.server.application.*

fun Application.configureForHeroku() {
  val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString()?.toInt()
    ?: System.getenv("PORT")?.toInt()
    ?: 8080

  // Configure based on environment
  if (isHerokuEnvironment()) {
    install(HerokuHttpsRedirect)

    // Enable compression for Heroku
    install(Compression) {
      gzip {
        priority = 1.0
      }
      deflate {
        priority = 10.0
        minimumSize(1024)
      }
    }
  }
}

fun isHerokuEnvironment(): Boolean {
  return System.getenv("DYNO") != null
}
```

### API Response Patterns

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
  val success: Boolean,
  val data: T? = null,
  val error: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

fun Application.configureApiRouting() {
  routing {
    route("/api/v1") {
      get("/users/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()

        if (id == null) {
          call.respond(
            HttpStatusCode.BadRequest,
            ApiResponse<Nothing>(
              success = false,
              error = "Invalid user ID"
            )
          )
          return@get
        }

        val user = getUserById(id)
        if (user != null) {
          call.respond(
            ApiResponse(
              success = true,
              data = user
            )
          )
        } else {
          call.respond(
            HttpStatusCode.NotFound,
            ApiResponse<Nothing>(
              success = false,
              error = "User not found"
            )
          )
        }
      }
    }
  }
}
```

### CORS Configuration

```kotlin
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
  install(CORS) {
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Patch)
    allowHeader(HttpHeaders.Authorization)
    allowHeader(HttpHeaders.ContentType)

    // Configure based on environment
    if (isDevelopment()) {
      anyHost() // Allow all hosts in development
    } else {
      allowHost("myapp.com", schemes = listOf("https"))
      allowHost("www.myapp.com", schemes = listOf("https"))
    }
  }
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Ktor Server Core
- Ktor Server Host Common
- Kotlinx Serialization JSON

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:ktor-server-utils:2.5.3")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>ktor-server-utils</artifactId>
  <version>2.5.3</version>
</dependency>
```

## Plugin Configuration

### HerokuHttpsRedirect Options

- `excludePaths`: Set of exact paths to exclude from redirect
- `excludePathPatterns`: Set of regex patterns for paths to exclude
- `httpsPort`: HTTPS port to redirect to (default: 443)
- `permanentRedirect`: Use 301 instead of 302 redirect (default: false)

## Security Considerations

⚠️ **Security Notes**:

- The `HerokuHttpsRedirect` plugin trusts the `x-forwarded-proto` header, which can be spoofed
- Always validate that you're running behind a trusted proxy (like Heroku's load balancer)
- Consider implementing additional security headers for production deployments
- Validate all user inputs in route handlers

## Performance Notes

- HTTPS redirects add a small overhead to HTTP requests
- Exclude health check and monitoring endpoints from redirects to reduce overhead
- Use appropriate caching headers for static content
- Consider using compression for API responses

## Best Practices

1. **Environment Detection**: Use environment variables to detect deployment context
2. **Graceful Degradation**: Handle missing environment variables gracefully
3. **Security Headers**: Implement security headers like HSTS in production
4. **Health Checks**: Exclude health check endpoints from redirects
5. **Monitoring**: Exclude monitoring endpoints from redirects and logging

## Common Patterns

### Health Check Endpoint

```kotlin
routing {
  get("/health") {
    call.respondText("OK", ContentType.Text.Plain)
  }

  get("/health/detailed") {
    val health = checkApplicationHealth()
    call.respond(health)
  }
}
```

### Metrics Endpoint

```kotlin
routing {
  get("/metrics") {
    val metrics = collectApplicationMetrics()
    call.respondText(metrics, ContentType.Text.Plain)
  }
}
```

### Error Handling

```kotlin
install(StatusPages) {
  exception<Throwable> { call, cause ->
    call.respond(
      HttpStatusCode.InternalServerError,
      ApiResponse<Nothing>(
        success = false,
        error = "Internal server error"
      )
    )

    // Log the error
    logger.error("Unhandled exception", cause)
  }
}
```

## Heroku Deployment

When deploying to Heroku, the plugin automatically detects the Heroku environment and configures HTTPS redirects
appropriately. Make sure to:

1. Configure your Heroku app to use SSL
2. Set up proper domain routing
3. Exclude health check endpoints used by Heroku
4. Use environment variables for configuration

## License

Licensed under the Apache License, Version 2.0.
