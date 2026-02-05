<!-- OPENSPEC:START -->

# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:

- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:

- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-module Kotlin/Java utility library (20+ modules) providing common functionality for various frameworks and use
cases. Published via JitPack.

## Common Development Commands

### Building

- `make build` - Build without tests
- `./gradlew build` - Full build with tests
- `make publishLocal` - Install to local Maven repo

### Testing

- `./gradlew test` - Run all tests
- `./gradlew :MODULE_NAME:test` - Run tests for specific module (e.g., `./gradlew :core-utils:test`)
- `./gradlew :MODULE_NAME:test --tests "ClassName"` - Run a specific test class
- `make reports` - Generate merged Kover coverage reports

### Code Quality

- `make lint` - Run Kotlinter linting
- `./gradlew formatKotlinMain formatKotlinTest` - Auto-format code

### Dependencies

- `make versioncheck` - Check for dependency updates
- `make tree` - Show dependency tree

## Architecture

### Module Categories

- **Core**: core-utils (fundamental utilities)
- **Framework**: dropwizard-utils, grpc-utils, ktor-client-utils, ktor-server-utils, jetty-utils
- **Data**: exposed-utils, json-utils, redis-utils
- **Scripting**: script-utils-common, script-utils-java, script-utils-kotlin, script-utils-python
- **Observability**: prometheus-utils, zipkin-utils
- **Services**: service-utils, guava-utils
- **Other**: email-utils, recaptcha-utils, common-utils-bom

### Build Configuration

The root `build.gradle.kts` defines configuration functions applied to all subprojects:

- `configureKotlin()` - JVM 17 target, experimental opt-ins
- `configurePublishing()` - Maven publication setup
- `configureTesting()` - JUnit Platform configuration
- `configureKotlinter()` - Lint settings

Version catalog in `gradle/libs.versions.toml` manages all dependency versions.

### Experimental Kotlin Features

These opt-ins are enabled globally:

- `kotlin.contracts.ExperimentalContracts`
- `kotlinx.coroutines.ExperimentalCoroutinesApi`
- `kotlin.time.ExperimentalTime`
- `kotlin.concurrent.atomics.ExperimentalAtomicApi`
- `kotlinx.serialization.ExperimentalSerializationApi`

### Key Technologies

- Kotlin 2.3.0 with JVM target 17
- Gradle 9.3.0 with Kotlin DSL
- JUnit 5 + Kotest for testing
- Kotlinter for linting

### Package Structure

All modules use: `com.github.pambrose.common.*`

### Version Management

- Project version: "2.4.14" (set in `allprojects` block of root build.gradle.kts)
- Group: "com.github.pambrose.common-utils"
- All library versions in `gradle/libs.versions.toml`
