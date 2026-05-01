# Common Utils - Kotlin & Java Utility Library Collection

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/pambrose/common-utils)](https://github.com/pambrose/common-utils/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/core-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/core-utils)
[![Tests](https://github.com/pambrose/common-utils/actions/workflows/test.yml/badge.svg)](https://github.com/pambrose/common-utils/actions/workflows/test.yml)
[![Kotlin version](https://img.shields.io/badge/kotlin-2.3.21-red?logo=kotlin)](http://kotlinlang.org)
[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081)](https://pinterest.github.io/ktlint/)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5bb4750894844031a55375227acfff6f)](https://app.codacy.com/gh/pambrose/common-utils/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![codecov](https://codecov.io/gh/pambrose/common-utils/branch/master/graph/badge.svg)](https://codecov.io/gh/pambrose/common-utils)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A collection of utility libraries for Kotlin and Java development.

## Overview

This repository contains a modular collection of utility libraries designed to simplify and enhance Kotlin and Java
development. Each module focuses on a specific domain or framework, providing extension functions, DSLs, and utility
classes that reduce boilerplate code and improve developer productivity.

## Module Structure

### Core Utilities

#### [**core-utils**](core-utils/README.md)

Fundamental utility functions and extensions for common programming tasks.

- String, number, and collection utilities
- I/O operations with security enhancements
- Atomic operations and thread-safe delegates
- Time duration helpers
- Reflection utilities

### Framework Integration

#### [**dropwizard-utils**](dropwizard-utils/README.md)

Utilities for Dropwizard Metrics integration.

- Metrics DSL for cleaner configuration
- Health check utilities
- JMX integration helpers

#### [**exposed-utils**](exposed-utils/README.md)

Enhancements for JetBrains Exposed SQL framework.

- Custom SQL expressions
- UPSERT statement support
- Database operation utilities

#### [**grpc-utils**](grpc-utils/README.md)

gRPC server and client utilities.

- Server configuration DSL
- TLS/SSL utilities for secure communication
- Server extension functions

#### [**guava-utils**](guava-utils/README.md)

Google Guava integration and extensions.

- Concurrent programming utilities
- Service lifecycle management
- Thread-safe monitoring and waiting mechanisms
- Archive (ZIP) processing utilities

#### [**jetty-utils**](jetty-utils/README.md)

Jetty 12 (EE11) web server integration utilities.

- Server configuration DSL
- Lambda-based servlet implementations (Jakarta Servlet 6.1)
- Version endpoint utilities

#### [**ktor-client-utils**](ktor-client-utils/README.md)

Ktor HTTP client enhancements.

- Client configuration DSL
- Request/response utilities

#### [**ktor-server-utils**](ktor-server-utils/README.md)

Ktor server-side utilities.

- Heroku HTTPS redirect feature
- Response handling utilities
- Server configuration helpers

### Data & Serialization

#### [**json-utils**](json-utils/README.md)

JSON processing utilities with Kotlinx.serialization.

- JsonElement extension functions for easy data access
- Nested path navigation with dot notation
- Multiple JSON format configurations (pretty, raw, lenient, strict)
- Type-safe value extraction with null safety

### Observability & Monitoring

#### [**prometheus-utils**](prometheus-utils/README.md)

Prometheus metrics integration.

- Metrics DSL for clean metric definitions
- System metrics collection
- Instrumented thread factories
- Custom gauge collectors

#### [**zipkin-utils**](zipkin-utils/README.md)

Zipkin distributed tracing utilities.

- Tracing configuration DSL
- Span management utilities

### Persistence & Caching

#### [**redis-utils**](redis-utils/README.md)

Redis client utilities and extensions.

- Connection management
- Common Redis operation patterns
- Jedis client enhancements

### Scripting Support

#### **script-utils-common**

Common base classes and interfaces for scripting engines.

- Abstract engine implementations
- Script pool management
- Expression evaluator frameworks

#### **script-utils-java**

JavaScript engine integration.

- JavaScript execution utilities
- Script pooling for performance

#### [**script-utils-kotlin**](script-utils-kotlin/README.md)

Kotlin script engine integration.

- Kotlin script execution
- Expression evaluation
- Script compilation and caching

#### **script-utils-python**

Python (Jython) script engine integration.

- Python script execution via Jython
- Expression evaluation capabilities

### Communication & Security

#### **email-utils**

Email sending utilities using Resend.

- Email composition and sending via Resend API
- Resend webhook message handling
- HTML email support via Ktor HTML builder

#### **recaptcha-utils**

Google reCAPTCHA verification utilities.

- reCAPTCHA configuration and service
- Server-side verification via Ktor HTTP client

### Service Infrastructure

#### **service-utils**

Service lifecycle and configuration management.

- Generic service base classes
- Admin interface configuration
- Metrics service integration
- Servlet service management
- Zipkin reporting service

## Installation

This library is available on [Maven Central](https://central.sonatype.com/artifact/com.pambrose.common-utils/core-utils).

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Include specific modules as needed
  implementation("com.pambrose.common-utils:core-utils:2.8.2")
  implementation("com.pambrose.common-utils:json-utils:2.8.2")
  implementation("com.pambrose.common-utils:ktor-server-utils:2.8.2")
    // ... other modules
}
```

### Maven

```xml
<dependencies>
    <dependency>
        <groupId>com.pambrose.common-utils</groupId>
        <artifactId>core-utils</artifactId>
      <version>2.8.2</version>
    </dependency>
    <!-- Add other modules as needed -->
</dependencies>
```

## Technology Stack

- **Languages**: Kotlin 2.3.21, Java
- **Build System**: Gradle 9.4.1 with Kotlin DSL
- **Testing**: Kotest, MockK
- **Serialization**: Kotlinx.serialization
- **Concurrency**: Kotlin Coroutines, Guava
- **Web Frameworks**: Ktor, Jetty 12 (EE11)
- **Metrics**: Dropwizard Metrics, Prometheus
- **Databases**: JetBrains Exposed
- **Caching**: Redis (Jedis)
- **Tracing**: Zipkin, Brave

## Development

### Building the Project

```bash
# Build all modules
./gradlew build

# Build without tests
make build

# Run tests
./gradlew test

# Run tests for a specific module
./gradlew :core-utils:test

# Lint check
make lint
```

### Code Quality

This project maintains high code quality standards:

- **Linting**: Kotlinter (ktlint)
- **Testing**: Comprehensive test coverage with Kotest
- **Security**: Regular dependency updates and security reviews
- **Documentation**: Comprehensive module documentation

### Adding New Modules

1. Create module directory with `build.gradle.kts`
2. Add module to `settings.gradle.kts`
3. Create module-specific `README.md`
4. Follow existing package structure: `com.pambrose.common.*`
5. Add comprehensive tests using Kotest

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Follow the existing code style and conventions
4. Add tests for new functionality
5. Update documentation as needed
6. Submit a pull request

## License

Licensed under the Apache License, Version 2.0. See [License.txt](License.txt) for details.

## Support

For questions, issues, or contributions, please use the GitHub issue tracker.
