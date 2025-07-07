# Corex Utils

Coroutine extensions and utilities for Kotlin coroutines, providing convenient functions for common coroutine
operations.

## Features

### Duration-Based Delay

- **Duration Delay**: Convenient delay function using Kotlin's `Duration` type instead of milliseconds

## Usage Examples

### Duration Delay

```kotlin
import com.github.pambrose.common.coroutine.delayFor
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

suspend fun example() {
  // Delay for 5 seconds
  delayFor(5.seconds)

  // Delay for 500 milliseconds
  delayFor(500.milliseconds)

  // Delay for 2.5 seconds
  delayFor(2.5.seconds)
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Kotlinx Coroutines Core

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:corex-utils:2.4.0")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>corex-utils</artifactId>
  <version>2.4.0</version>
</dependency>
```

## Notes

- The `delayFor(Duration)` function converts the duration to milliseconds, which may result in sub-millisecond precision
  loss
- This function provides a more type-safe alternative to the millisecond-based delay function
- The function is named `delayFor()` to avoid confusion with the standard kotlinx.coroutines.delay function

## Thread Safety

- All coroutine utilities are thread-safe and work correctly in concurrent environments
- The delay function delegates to kotlinx.coroutines.delay which is thread-safe

## License

Licensed under the Apache License, Version 2.0.
