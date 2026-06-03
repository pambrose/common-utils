# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-module Kotlin/Java utility library (20+ modules) providing common functionality for various frameworks and use
cases. Published on Maven Central.

## Common Development Commands

Run `make help` for a self-documenting list of every target.

### Building

- `make build` - Build without tests
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

- `make lint` - Run Kotlinter and Detekt
- `./gradlew formatKotlinMain formatKotlinTest` - Auto-format code
- `make detekt` - Run Detekt static analysis across all modules (HTML/XML reports under `build/reports/detekt/`)
- `make detekt-baseline` - Generate/update `config/detekt/baseline.xml` to suppress current findings
- `make coverage` - Generate both aggregated Kover HTML and XML coverage reports
- `make coverage-html` - Generate aggregated Kover HTML coverage report (output under `build/reports/kover/html/`)
- `make coverage-xml` - Generate aggregated Kover XML coverage report (e.g. for CI / Codacy)
- `make coverage-log` - Print Kover coverage summary to the build log
- `make coverage-verify` - Run Kover coverage verification rules
- `make coverage-open` - Generate the HTML report and open it in the default browser
- `make coverage-packages` - Print a per-package coverage table derived from the XML report
- `make coverage-clean` - Clean Kover outputs and previous test results

### Dependencies

- `make versions` - Check for dependency updates
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

Detekt is applied directly in the root `build.gradle.kts` via `configureDetekt()`. The aggregate `detekt` task depends on the per-source-set `detektMain` and `detektTest` tasks, so analysis runs with full type resolution. Optional shared config lives at `config/detekt/detekt.yml` and a shared suppression baseline at `config/detekt/baseline.xml` (both auto-detected if present).

Version catalog in `gradle/libs.versions.toml` manages all dependency versions.

### Experimental Kotlin Features

These opt-ins are enabled globally:

- `kotlin.contracts.ExperimentalContracts`
- `kotlinx.coroutines.ExperimentalCoroutinesApi`
- `kotlin.time.ExperimentalTime`
- `kotlin.concurrent.atomics.ExperimentalAtomicApi`
- `kotlinx.serialization.ExperimentalSerializationApi`

### Key Technologies

- Kotlin 2.4.0 with JVM target 17
- Gradle 9.5.1 with Kotlin DSL
- Kotest + MockK for testing
- Kotlinter for linting

### Package Structure

All modules use: `com.pambrose.common.*`

### Version Management

- Project version: "2.9.0" (set in `gradle.properties`; override at publish time with `-PoverrideVersion=...`, used by Makefile snapshot/publish targets)
- Group: "com.pambrose.common-utils" (set in `gradle.properties`)
- All library versions in `gradle/libs.versions.toml`
