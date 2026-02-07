# Project Context

## Purpose

Multi-module Kotlin/Java utility library providing common functionality for various frameworks and use cases. The
library is designed to reduce boilerplate and provide reusable components across projects. Published via JitPack for
easy consumption.

## Tech Stack

- **Language**: Kotlin 2.3.0 (JVM target 17)
- **Build**: Gradle 9.3.0 with Kotlin DSL
- **Testing**: JUnit 5 + Kotest 6.0.4
- **Linting**: Kotlinter 5.3.0
- **Distribution**: JitPack (Maven repository)

### Key Framework Integrations

- Ktor (client & server) 3.3.3
- gRPC 1.78.0
- Dropwizard Metrics 4.2.37
- Jetty 10.0.26
- Exposed ORM 0.61.0
- Prometheus 0.16.0
- Zipkin/Brave tracing 6.3.0
- Redis (Jedis) 7.2.0
- Kotlinx Coroutines 1.10.2
- Kotlinx Serialization 1.9.0

## Project Conventions

### Code Style

- Kotlinter enforces code style (ktlint-based)
- Run `./gradlew formatKotlinMain formatKotlinTest` to auto-format
- Run `make lint` to check for violations
- Package structure: `com.github.pambrose.common.*`

### Architecture Patterns

- **Multi-module design**: 20+ independent modules organized by category
  - Core: `core-utils` (fundamental utilities)
  - Framework: `dropwizard-utils`, `grpc-utils`, `ktor-client-utils`, `ktor-server-utils`, `jetty-utils`
  - Data: `exposed-utils`, `json-utils`, `redis-utils`
  - Scripting: `script-utils-common`, `script-utils-java`, `script-utils-kotlin`, `script-utils-python`
  - Observability: `prometheus-utils`, `zipkin-utils`
  - Services: `service-utils`, `guava-utils`
  - Other: `email-utils`, `recaptcha-utils`, `common-utils-bom`
- Root `build.gradle.kts` defines shared configuration functions applied to subprojects
- Version catalog in `gradle/libs.versions.toml` manages all dependency versions
- BOM module (`common-utils-bom`) for coordinated versioning

### Experimental Kotlin Features

These opt-ins are enabled globally:

- `kotlin.contracts.ExperimentalContracts`
- `kotlinx.coroutines.ExperimentalCoroutinesApi`
- `kotlin.time.ExperimentalTime`
- `kotlin.concurrent.atomics.ExperimentalAtomicApi`
- `kotlinx.serialization.ExperimentalSerializationApi`

### Testing Strategy

- JUnit Platform with Kotest runner
- Run all tests: `./gradlew test`
- Run module tests: `./gradlew :MODULE_NAME:test`
- Run specific test: `./gradlew :MODULE_NAME:test --tests "ClassName"`
- Coverage reports via Kover: `make reports`
- Test logging includes passed, skipped, failed events with full exception format

### Git Workflow

- Main branch: `master`
- Version managed in root `build.gradle.kts` (`allprojects` block)
- Current version: 2.4.15
- Group ID: `com.github.pambrose.common-utils`

## Domain Context

This is a utility library, not an application. Each module provides helper functions, extension functions, and wrapper
classes that simplify working with their respective frameworks. Consumers add individual modules as dependencies rather
than the entire library.

## Important Constraints

- JVM 17 minimum required
- Each module should remain focused and independent
- Avoid circular dependencies between modules
- Changes to `core-utils` may affect many downstream modules

## External Dependencies

- **JitPack**: Primary distribution mechanism (https://jitpack.io)
- **Maven Central**: Source for all library dependencies
- **Google Maven**: Additional Android/Google library dependencies
