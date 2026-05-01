# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-module Kotlin/Java utility library (20+ modules) providing common functionality for various frameworks and use
cases. Published on Maven Central.

## Common Development Commands

### Building

- `make build` (alias: `make compile`) - Build without tests
- `./gradlew build` - Full build with tests
- `make clean` - Run `gradle clean`
- `make stop` - Stop the Gradle daemon
- `make publish-local` - Install to local Maven repo
- `make publish-local-snapshot` - Install a `-SNAPSHOT` build to local Maven repo
- `make kdocs` - Generate Dokka HTML documentation
- `make upgrade-wrapper` - Re-run the Gradle wrapper task at the pinned version

### Testing

- `make tests` - Run `./gradlew --rerun-tasks check` (lint + tests)
- `./gradlew test` - Run all tests
- `./gradlew :MODULE_NAME:test` - Run tests for specific module (e.g., `./gradlew :core-utils:test`)
- `./gradlew :MODULE_NAME:test --tests "ClassName"` - Run a specific test class

### Code Quality

- `make lint` - Run Kotlinter linting
- `./gradlew formatKotlinMain formatKotlinTest` - Auto-format code
- `make coverage` - Generate aggregated Kover HTML coverage report (output under `build/reports/kover/html/`)
- `make coverage-xml` - Generate aggregated Kover XML coverage report (e.g. for CI / Codacy)

### Dependencies

- `make versioncheck` - Check for dependency updates (default `make` target)
- `make refresh` - Refresh dependencies and re-run `dependencyUpdates`
- `make tree` - Show dependency tree (quiet)
- `make depends` - Show dependency tree (verbose)

### Publishing

- `make publish-snapshot` - Publish `-SNAPSHOT` to Maven Central (requires `GPG_SIGNING_KEY_ID` env var and
  `gradle-signing-password` keychain entry)
- `make publish-maven-central` - Publish and release to Maven Central (same prerequisites)

## Architecture

### Module Categories

- **Core**: core-utils (fundamental utilities)
- **Framework**: dropwizard-utils, grpc-utils, ktor-client-utils, ktor-server-utils, jetty-utils
- **Data**: exposed-utils, json-utils, redis-utils
- **Scripting**: script-utils-common, script-utils-java, script-utils-kotlin, script-utils-python
- **Observability**: prometheus-utils, zipkin-utils
- **Services**: service-utils, guava-utils
- **Other**: email-utils, recaptcha-utils

### Build Configuration

The root `build.gradle.kts` applies a shared set of plugins to every subproject and defines two inline configuration functions:

- `configureKotlin()` - JVM 17 target, experimental opt-ins
- `configurePublishing()` - Maven publication setup (vanniktech maven-publish) and per-module Dokka configuration

Common behavior for testing, linting, and dependency-update reporting is provided by [`pambrose-gradle-plugins`](https://github.com/pambrose/pambrose-gradle-plugins) convention plugins applied to every subproject:

- `com.pambrose.testing` - JUnit Platform, `kotest-runner-junit5` and `kotlin-test` as default `testImplementation`, logback-classic on test runtime
- `com.pambrose.kotlinter` - Kotlinter lint/format tasks
- `com.pambrose.stable-versions` - stable-only filtering for `dependencyUpdates`

Version catalog in `gradle/libs.versions.toml` manages all dependency versions.

### Experimental Kotlin Features

These opt-ins are enabled globally:

- `kotlin.contracts.ExperimentalContracts`
- `kotlinx.coroutines.ExperimentalCoroutinesApi`
- `kotlin.time.ExperimentalTime`
- `kotlin.concurrent.atomics.ExperimentalAtomicApi`
- `kotlinx.serialization.ExperimentalSerializationApi`

### Key Technologies

- Kotlin 2.3.21 with JVM target 17
- Gradle 9.4.1 with Kotlin DSL
- Kotest + MockK for testing
- Kotlinter for linting

### Package Structure

All modules use: `com.pambrose.common.*`

### Version Management

- Project version: "2.8.2" (set in `allprojects` block of root build.gradle.kts)
- Group: "com.pambrose.common-utils"
- All library versions in `gradle/libs.versions.toml`
