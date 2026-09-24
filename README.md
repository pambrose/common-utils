# Common Utils - Kotlin & Java Utility Library Collection

[![GitHub release (latest by date)](https://img.shields.io/github/v/release/pambrose/common-utils)](https://github.com/pambrose/common-utils/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/core-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/core-utils)
[![Tests](https://github.com/pambrose/common-utils/actions/workflows/test.yml/badge.svg)](https://github.com/pambrose/common-utils/actions/workflows/test.yml)
[![Kotlin version](https://img.shields.io/badge/kotlin-2.4.20-red?logo=kotlin)](http://kotlinlang.org)
[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081)](https://pinterest.github.io/ktlint/)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/5bb4750894844031a55375227acfff6f)](https://app.codacy.com/gh/pambrose/common-utils/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)
[![codecov](https://codecov.io/gh/pambrose/common-utils/branch/master/graph/badge.svg)](https://codecov.io/gh/pambrose/common-utils)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A collection of utility libraries for Kotlin and Java development.

## Overview

This repository contains a modular collection of utility libraries designed to simplify and enhance Kotlin and Java
development. Each module focuses on a specific domain or framework, providing extension functions, DSLs, and utility
classes that reduce boilerplate code and improve developer productivity.

Three modules — **core-utils**, **json-utils**, and **ktor-client-utils** — are built with
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) and can be used from JVM, JS, wasmJs, and
Native (iOS/macOS/tvOS/watchOS/Linux/Windows) projects. Their portable APIs live in `commonMain`; JVM-specific
functions (e.g. hashing, URL codecs, classpath resources) remain available to JVM consumers unchanged via `jvmMain`.
The remaining modules are JVM-only, as they integrate JVM-specific frameworks.

## Module Structure

### Core Utilities

#### [**core-utils**](core-utils/README.md) _(multiplatform)_

Fundamental utility functions and extensions for common programming tasks.

- String, number, and collection utilities
- I/O operations with security enhancements
- Atomic operations and thread-safe delegates
- Date/time formatting and duration helpers (`DateUtils`), zone-neutral by default
- Reflection utilities

### Framework Integration

#### [**dropwizard-utils**](dropwizard-utils/README.md)

Utilities for Dropwizard Metrics integration.

- Health check DSL for building Dropwizard `HealthCheck`s from a lambda
- Ready-made backlog-size and map-size health checks

#### [**exposed-utils**](exposed-utils/README.md)

Enhancements for JetBrains Exposed SQL framework.

- Custom SQL expressions
- UPSERT statement support
- Database operation utilities

#### [**grpc-utils**](grpc-utils/README.md)

gRPC server and client utilities.

- Server configuration DSL
- TLS/SSL utilities for secure communication, using OpenSSL (BoringSSL) on Linux, macOS and Windows
- Server extension functions

#### [**guava-utils**](guava-utils/README.md)

Google Guava integration and extensions.

- Concurrent programming utilities
- Service lifecycle management
- Thread-safe monitoring and waiting mechanisms
- Gzip compression helpers

#### [**jetty-utils**](jetty-utils/README.md)

Jetty 12 (EE11) web server integration utilities.

- Server configuration DSL
- Lambda-based servlet implementations (Jakarta Servlet 6.1)
- Version endpoint utilities

#### [**ktor-client-utils**](ktor-client-utils/README.md) _(multiplatform)_

Ktor HTTP client enhancements.

- Client configuration DSL
- Request/response utilities
- No bundled engine: add one (CIO, Darwin, Curl, WinHttp) on the JVM and Native; js and wasmJs use Ktor's own
  Js engine

#### [**ktor-server-utils**](ktor-server-utils/README.md)

Ktor server-side utilities.

- Heroku HTTPS redirect plugin
- Response and redirect helpers
- Bridge for mounting Jakarta servlets in Ktor routes

### Data & Serialization

#### [**json-utils**](json-utils/README.md) _(multiplatform)_

JSON processing utilities with Kotlinx.serialization.

- JsonElement extension functions for easy data access
- Nested path navigation via slash-separated paths (`getByPath("a/b/c")`)
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

- DSL for building a Brave `Tracing` that reports to Zipkin

### Persistence & Caching

#### [**redis-utils**](redis-utils/README.md)

Redis client utilities and extensions.

- Connection management
- Common Redis operation patterns
- Jedis client enhancements

### Scripting Support

#### [**script-utils-common**](script-utils-common/README.md)

Common base classes and interfaces for scripting engines.

- Abstract engine implementations
- Script pool management
- Expression evaluator frameworks

#### [**script-utils-java**](script-utils-java/README.md)

Java scripting engine integration (compiles and evaluates Java source at runtime via
[java-scriptengine](https://github.com/eobermuhlner/java-scriptengine)).

- Java source execution utilities
- Configurable isolation levels
- Script pooling for performance

#### [**script-utils-kotlin**](script-utils-kotlin/README.md)

Kotlin script engine integration.

- Kotlin script execution
- Expression evaluation
- Script compilation and caching

#### [**script-utils-python**](script-utils-python/README.md)

Python (Jython) script engine integration.

- Python script execution via Jython
- Expression evaluation capabilities

### Communication & Security

#### [**email-utils**](email-utils/README.md)

Email sending utilities using Resend.

- Email composition and sending via Resend API
- Resend webhook message handling
- HTML email bodies built with kotlinx-html, with an embedded stylesheet

#### [**recaptcha-utils**](recaptcha-utils/README.md)

Google reCAPTCHA verification utilities.

- reCAPTCHA configuration and service
- Server-side verification via Ktor HTTP client

### Service Infrastructure

#### [**service-utils**](service-utils/README.md)

Service lifecycle and configuration management.

- Generic service base classes
- Admin interface configuration
- Metrics service integration
- Admin and metrics servers that can bind to a single interface
- Servlet service management
- Zipkin reporting service

## Installation

This library is available on [Maven Central](https://central.sonatype.com/artifact/com.pambrose.common-utils/core-utils).

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Include specific modules as needed
  implementation("com.pambrose.common-utils:core-utils:4.1.0")
  implementation("com.pambrose.common-utils:json-utils:4.1.0")
  implementation("com.pambrose.common-utils:ktor-server-utils:4.1.0")
    // ... other modules
}
```

From 5.0.0, the `common-utils-bom` platform aligns every module on one version, so the modules themselves can be
declared without one:

```kotlin
dependencies {
  implementation(platform("com.pambrose.common-utils:common-utils-bom:5.0.0"))
  implementation("com.pambrose.common-utils:core-utils")
  implementation("com.pambrose.common-utils:service-utils")
}
```

### Maven

For the multiplatform modules (**core-utils**, **json-utils**, **ktor-client-utils**), Maven consumers must
depend on the `-jvm` artifact (e.g. `core-utils-jvm`); Gradle consumers resolve the correct variant from the
root coordinate automatically. The JVM-only modules keep their plain artifact ids.

```xml
<dependencies>
    <dependency>
        <groupId>com.pambrose.common-utils</groupId>
        <artifactId>core-utils-jvm</artifactId>
      <version>4.1.0</version>
    </dependency>
    <!-- Add other modules as needed -->
</dependencies>
```

From 5.0.0, import the BOM in `<dependencyManagement>` instead to align every module (it lists the `-jvm`
artifacts too):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.pambrose.common-utils</groupId>
            <artifactId>common-utils-bom</artifactId>
            <version>5.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Technology Stack

- **Languages**: Kotlin 2.4.20, Java
- **Build System**: Gradle 9.7.1 with Kotlin DSL
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
# Show every available make target with descriptions
make help

# Build all modules
./gradlew build

# Build without tests
make build

# Run tests (JVM modules)
./gradlew test

# Run tests for a specific JVM module
./gradlew :redis-utils:test

# The multiplatform modules (core-utils, json-utils, ktor-client-utils) have no
# `test` task; use jvmTest for the JVM target or allTests for every host target
./gradlew :core-utils:jvmTest
./gradlew :core-utils:allTests

# Lint check (Kotlinter + Detekt)
make lint

# Detekt static analysis only
make detekt

# Aggregated Kover coverage reports (HTML + XML)
make coverage

# Line/branch coverage tables, weakest first
make coverage-packages
make coverage-modules

# PIT mutation testing (on demand; see mutationModuleNames in build.gradle.kts)
make mutation

# Rewrite the committed public-API dumps after an intended API change
make abi-update
```

### Code Quality

This project maintains high code quality standards:

- **Linting**: Kotlinter (ktlint) and Detekt (config in `config/detekt/`)
- **Testing**: Comprehensive test coverage with Kotest
- **Coverage**: Kotlinx Kover with aggregated HTML/XML reports, project-wide and per-package floors enforced by
  `check`, and Codecov upload from CI
- **Mutation testing**: PIT with the Kotest plugin, run on demand for selected modules
- **API compatibility**: Kotlin ABI dumps committed under each module's `api/` directory and checked by `check`
- **Platforms**: CI runs the multiplatform tests on Linux, macOS (macOS and iOS simulator) and Windows (mingwX64)
- **Security**: Regular dependency updates and security reviews
- **Documentation**: Comprehensive module documentation

### Adding New Modules

1. Create module directory with `build.gradle.kts`
2. Add module to `settings.gradle.kts`, and to `kmpModuleNames` in the root `build.gradle.kts` if it is
   multiplatform
3. Create module-specific `README.md`, and list the module in this README and in `llms.txt`
4. Follow existing package structure: `com.pambrose.common.*`
5. Add comprehensive tests using Kotest
6. Generate its ABI dump with `make abi-update` and commit `<module>/api/`; without it `checkKotlinAbi` fails
7. Add a component for it to `component_management` in `codecov.yml`

`common-utils-bom` picks up every module in `settings.gradle.kts` by itself.

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
