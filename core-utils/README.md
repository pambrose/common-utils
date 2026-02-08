# Core Utils

Core utilities module providing fundamental Kotlin and Java utility functions, extensions, and helpers for common
programming tasks.

## Features

### Atomic Operations

- **Atomic Operations with Coroutines**: Thread-safe atomic operations with mutex support
- **Atomic Property Delegates**: Single-assignment and atomic property delegates
- **Critical Section Utilities**: Utilities for managing critical sections with atomic flags

### Time & Duration

- **Duration Extensions**: Convert between `TimeUnit` and Kotlin `Duration`
- **Time Formatting**: Format durations with customizable output

### Collections & Arrays

- **Array Utilities**: String representations and printing utilities for all array types
- **List Utilities**: String conversion and utility functions for lists

### String Processing

- **String Extensions**: Comprehensive string manipulation utilities including:
  - Encoding/decoding (Base64, URL, HTML)
  - Validation (email, phone numbers)
  - Formatting (bracketing, masking, capitalization)
  - Hashing (MD5, SHA-256, bcrypt)
  - Random string generation
- **Banner Utilities**: Display ASCII art banners from resources

### Content Sources

- **Content Loading**: Load content from various sources (files, URLs, resources)
- **HTTP Content**: Fetch content from HTTP/HTTPS URLs
- **Resource Management**: Handle classpath resources and file system content

### Serialization & I/O

- **Serialization Extensions**: Convert objects to/from byte arrays
- **I/O Utilities**: Stream and byte array manipulation

### Reflection & Metadata

- **Reflection Extensions**: Type checking and reflection utilities
- **Version Management**: Annotation-based version tracking with build metadata

### Numeric Extensions

- **Number Extensions**: Mathematical operations and formatting for numbers
- **Random Utilities**: Secure random number generation

## Usage Examples

### Atomic Operations

```kotlin
import com.github.pambrose.common.concurrent.Atomic

val atomicValue = Atomic(0)
atomicValue.setWithLock { currentValue ->
  currentValue + 1
}
```

### String Extensions

```kotlin
import com.github.pambrose.common.util.*

// Email validation
val email = "user@example.com"
if (email.isValidEmail()) {
  println("Valid email")
}

// String hashing
val password = "mypassword"
val hashed = password.hashWithBcrypt()

// URL masking
val url = "https://user:pass@example.com/path"
val masked = url.maskUrlCredentials() // "https://***:***@example.com/path"
```

### Duration Utilities

```kotlin
import com.github.pambrose.common.time.*
import kotlin.time.Duration.Companion.seconds

val duration = 90.seconds
println(duration.format()) // "1 min 30 secs"
```

### Content Sources

```kotlin
import com.github.pambrose.common.util.*

// Load from various sources
val fileContent = FileContentSource("config.txt").readContent()
val urlContent = UrlContentSource("https://api.example.com/data").readContent()
val resourceContent = ClasspathContentSource("banner.txt").readContent()
```

### Version Management

```kotlin
import com.github.pambrose.common.util.*

@VersionAnnotation("1.0.0")
class MyApp

val version = Version.versionOf<MyApp>()
println(version.json()) // {"version": "1.0.0", "build_time": "..."}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Kotlinx Coroutines
- Kotlinx Serialization (for object serialization)

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:core-utils:2.5.1")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>core-utils</artifactId>
  <version>2.5.1</version>
</dependency>
```

## Security Notes

⚠️ **Important Security Considerations:**

- The `toObject()` deserialization functions should be used with trusted data only
- Email validation is basic - use dedicated libraries for production email validation
- Random string generation uses `SecureRandom` for cryptographically secure randomness
- Password hashing uses bcrypt with secure salt generation

## Thread Safety

- **Atomic utilities**: Thread-safe using kotlinx.coroutines.sync.Mutex
- **String extensions**: Thread-safe (immutable operations)
- **Content sources**: Thread-safe for read operations
- **SingleAssignVar**: ⚠️ Not thread-safe - use with caution in concurrent environments

## Performance Notes

- Array utilities have significant code duplication and should be refactored
- Reflection operations are expensive and should be cached when used frequently
- String operations create intermediate objects - consider StringBuilder for intensive operations

## License

Licensed under the Apache License, Version 2.0.
