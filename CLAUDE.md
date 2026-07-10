# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-module Kotlin/Java utility library (20+ modules) providing common functionality for various frameworks and use
cases. Published on Maven Central.

Three modules are Kotlin Multiplatform (KMP): **core-utils**, **json-utils**, and **ktor-client-utils**. They target
JVM, JS, wasmJs, and Native (iOS/macOS/tvOS/watchOS/Linux/Windows). Portable code lives in `src/commonMain`, JVM-bound
code in `src/jvmMain` (the JVM artifact keeps the full pre-KMP API). All other modules are plain Kotlin/JVM and depend
on core-utils' jvm variant via ordinary project dependencies.

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
- `./gradlew test` - Run all JVM-module tests
- `./gradlew :MODULE_NAME:test` - Run tests for a JVM module (e.g., `./gradlew :redis-utils:test`)
- `./gradlew :MODULE_NAME:test --tests "ClassName"` - Run a specific test class in a JVM module
- KMP modules (core-utils, json-utils, ktor-client-utils) have no `test` task; use `jvmTest`
  (e.g., `./gradlew :core-utils:jvmTest`, filter with `--tests "ClassName"`) or `allTests` for every
  host-runnable target. `commonTest` Kotest specs execute on every host-runnable target (JVM,
  Node.js for js/wasmJs, and macOS/iOS simulators); JVM-bound specs live in `jvmTest`.

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

The root `build.gradle.kts` applies a shared set of plugins to every subproject and defines several inline configuration functions, including:

- `configureKotlinJvm()` - JVM 17 target, experimental opt-ins (kotlin/jvm modules)
- `configureKotlinMultiplatform()` - full KMP target list, opt-ins, JUnit Platform for `jvmTest` (modules listed in `kmpModuleNames`)
- `configurePublishing(isKmp)` - Maven publication setup (vanniktech maven-publish, `KotlinJvm` or `KotlinMultiplatform` platform) and per-module Dokka configuration
- `configureVersions()` - pre-release filtering for the ben-manes `dependencyUpdates` task

The `kmpModuleNames` set in the root build script decides which modules build with `kotlin("multiplatform")`; everything else gets `kotlin("jvm")`.

Common behavior for testing and linting on the **JVM modules** is provided by [`pambrose-gradle-plugins`](https://github.com/pambrose/pambrose-gradle-plugins) convention plugins:

- `com.pambrose.testing` - JUnit Platform, `kotest-runner-junit5` and `kotlin-test` as default `testImplementation`, logback-classic on test runtime
- `com.pambrose.kotlinter` - Kotlinter lint/format tasks

The KMP modules apply the raw `org.jmailen.kotlinter` plugin instead (same reporters, configured inline in the root script) and declare their kotest/logback test dependencies explicitly in their own `build.gradle.kts` (versions pinned in the catalog to match the convention plugin).

Dependency-update reporting uses the `com.github.ben-manes.versions` plugin, configured by the inline `configureVersions()`: its `isNonStable` filter rejects a pre-release candidate only when the current version is stable, so dependencies intentionally tracked on a pre-release line still surface updates.

Detekt is applied directly in the root `build.gradle.kts` via `configureDetekt()`. The aggregate `detekt` task depends on every per-source-set detekt task by type (`detektMain`/`detektTest` on JVM modules; `detektJvmMain`, `detektMetadataCommonMain`, etc. on KMP modules), so analysis runs with full type resolution. Optional shared config lives at `config/detekt/detekt.yml` and a shared suppression baseline at `config/detekt/baseline.xml` (both auto-detected if present).

Version catalog in `gradle/libs.versions.toml` manages all dependency versions.

### Experimental Kotlin Features

These opt-ins are enabled globally:

- `kotlin.contracts.ExperimentalContracts`
- `kotlinx.coroutines.ExperimentalCoroutinesApi`
- `kotlin.time.ExperimentalTime`
- `kotlin.concurrent.atomics.ExperimentalAtomicApi`
- `kotlinx.serialization.ExperimentalSerializationApi`

### Key Technologies

- Kotlin 2.4.0 with JVM target 17 (KMP modules additionally target JS, wasmJs, and Native)
- Gradle 9.6.1 with Kotlin DSL
- Kotest + MockK for testing
- Kotlinter for linting

### Kotlin Multiplatform Notes

- `kotlin-js-store/` holds the Node/Yarn lockfiles for the JS and wasmJs toolchains; commit changes to it
  (run `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock` when JS dependencies change).
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS` repositories mode with ivy repositories for the Node.js,
  Yarn, and Binaryen distributions; the root build script unsets every toolchain env spec's `downloadBaseUrl`
  (root and per-subproject) so the Kotlin plugin never registers project-level repositories.
- The mixed common/JVM files (`StringExtensions`, `MiscExtensions`, `MiscFuncs` in core-utils) are split across
  `commonMain` and `jvmMain` using `@file:JvmName` + `@file:JvmMultifileClass` so the compiled JVM facade classes
  (and therefore the published JVM ABI) are unchanged.
- watchOS/tvOS simulator test tasks are disabled (host Xcode lacks those simulator runtimes); Apple coverage
  comes from macOS and iOS simulator test tasks.
- core-utils bundles no IANA time-zone database: `DateUtils` resolves only `TimeZone.currentSystemDefault()`
  and UTC, so named zones (which need the `@js-joda/timezone` npm package on JS/wasm) stay a consumer
  concern. Keep new common code zone-neutral to preserve this — a hardcoded named zone would force the tz
  database into every JS/wasmJs consumer.

### Testing Notes

- All tests are hermetic: no network, no external services. gRPC tests use the in-process transport and
  committed self-signed PEM fixtures (`grpc-utils/src/test/resources/tls/`); Exposed tests use in-memory H2;
  Redis tests mock Jedis with MockK; `blockingGet` tests run against a loopback JDK `HttpServer`.
- `RecaptchaService.httpClient` is `internal` (not private) as a test seam: module tests swap in a
  MockEngine-backed client to fake Google's siteverify endpoint, restoring the original in a `finally`.
- Demo `main()` functions in guava-utils concurrent classes are excluded from coverage via
  `koverExcludeClasses` in the root build script; don't write tests for them.

### Package Structure

All modules use: `com.pambrose.common.*`

### Version Management

- Project version: "3.0.0" (set in `gradle.properties`; override at publish time with `-PoverrideVersion=...`, used by Makefile snapshot/publish targets)
- Group: "com.pambrose.common-utils" (set in `gradle.properties`)
- All library versions in `gradle/libs.versions.toml`
