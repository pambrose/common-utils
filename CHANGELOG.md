# Changelog

All notable changes to Common Utils are documented in this file.

## [2.7.1] - 2026-04-04

- Consolidate Dokka docs generation in root project with GitHub Actions workflow
- Fix missing POM description by deferring evaluation with provider
- Improve Maven publishing metadata with per-module POM descriptions
- Add Codacy badge and reorder README badges
- Reduce Gradle heap size
- Clean up code across multiple modules (unused imports, redundant suppressions, formatting)

## [2.7.0] - 2026-04-04

- Migrate artifact publishing from JitPack to Maven Central (group: `com.pambrose.common-utils`)
- Rename packages from `com.github.pambrose.common.*` to `com.pambrose.common.*`
- Add Vanniktech Maven Publish plugin for Maven Central publishing with POM metadata and signing
- Remove `common-utils-bom` module and all BOM dependencies in favor of explicit version refs
- Disable root project publishing to prevent stale artifacts
- Remove `jitpack.yml`
- Add ~78 new tests across 9 modules (ktor-client-utils, ktor-server-utils, prometheus-utils, guava-utils, service-utils, email-utils, jetty-utils, recaptcha-utils, script-utils-common)
- Add MockK and ktor-client-mock test dependencies
- Add GitHub Actions CI workflow for build and lint
- Update all documentation to reflect Maven Central coordinates
- Update copyright headers to 2026
- Upgrade Gradle wrapper to 9.4.1
- Update dependencies

## [2.6.2] - 2026-03-16

- Convert all tests from JUnit 5 to Kotest StringSpec (~50 test files across all modules)
- Upgrade Kotlin to 2.3.20
- Upgrade Gradle wrapper to 9.4.0
- Update Redis dependency to 7.4.0
- Refactor import statements for consistency
- Enable Gradle configuration caching
- Rename Makefile tasks (`trigger-build` to `trigger-jitpack`, `view-build` to `view-jitpack`)

## [2.6.1] - 2026-03-03

- Update Ktor dependency to 3.4.1

## [2.6.0] - 2026-02-28

- Upgrade Jetty to 11.0.26 and migrate servlet imports to Jakarta
- Add Ktor servlet support and migrate servlet handling from Jetty
- Update Jakarta Servlet API to 6.1.0
- Add string extension functions (`ensureLeadingSlash`, prefix/suffix helpers)
- Add `GenericKtorService` and refactor servlet handling
- Migrate Exposed from 0.61.0 to 1.1.1 (new v1 API, `JdbcTransaction`, `BlockingExecutable`)
- Extract `VERSION` variable in Makefile from `build.gradle.kts`
- Add agent-optimized `llms.txt` for project discoverability
- Update logback, logging, and redis dependencies

## [2.5.3] - 2026-02-09

- Downgrade Gradle wrapper to 9.2.0

## [2.5.2] - 2026-02-08

- Dependency updates

## [2.5.1] - 2026-02-08

- Dependency updates

## [2.5.0] - 2026-02-08

- Dependency updates

## [2.4.14] - 2026-02-05

- Downgrade Ktor to 3.3.3

## [2.4.13] - 2026-02-05

- Dependency updates

## [2.4.11] - 2026-02-05

- Dependency updates

## [2.4.10] - 2026-01-29

- Update Gradle to 9.3.0
- Bump Logback to 1.5.24
- Remove obsolete Codebeat badge
- Add OpenSpec documentation and commands for spec-driven development
- Update Ktor, Resend, and Serialization dependencies

## [2.4.9] - 2025-12-16

- Dependency version updates

## [2.4.8] - 2025-12-16

- Upgrade to Kotlin 2.3.0
- Dependency updates

## [2.4.7] - 2025-11-07

- Upgrade Gradle wrapper to 9.2.0
- Update library versions

## [2.4.6] - 2025-10-25

- Update to Kotlin 2.2.21
- Dependency updates

## [2.4.5] - 2025-09-10

- Update Jetty to 10.0.26
- Update Dropwizard, Kotest, Kotlin, logging, nettyTcNative, Redis, and Resend versions

## [2.4.4] - 2025-08-26

- Update dependency versions and adjust plugin application in build configuration

## [2.4.3] - 2025-08-20

- Upgrade Gradle to 9.0.0
- Update library dependencies to use BOMs
- Add `common-utils-bom` module with dependency constraints and publishing configuration

## [2.4.2] - 2025-08-18

- Update Kotlin to 2.2.10
- Add `email-utils` module with `EmailUtils` and `ResendService`
- Add data classes for email message handling (Bounce, Click, Data, Header, ResendWebhookMsg)
- Add `recaptcha-utils` module with configuration and verification service
- Add Kotlin serialization plugin to build configuration

## [2.4.1] - 2025-08-15

- Update dependencies
- Change `implementation` to `api` for Java and Python scripting libraries

## [2.4.0] - 2025-08-10

- Update library versions for compatibility and performance
- Remove `corex-utils` module
- Upgrade Kotest to 6.0.0.M14
- Upgrade JVM target to 17
- Update datetime library to 0.6.x

## [2.3.11] - 2025-06-25

- Convert to `build.gradle.kts` (Kotlin DSL)
- Convert to using `libs.versions.toml` version catalog
- Add `json-utils` module
- Refactor build scripts
- Dependency updates

## [2.3.10] - 2025-03-22

- Refactor atomic operations to use `kotlin.concurrent.atomics`
- Update library versions

## [2.3.9] - 2025-03-20

- Refactor dependencies to use `rootProject` libraries
- Update copyright
- Dependency updates

## [2.3.8] - 2024-12-19

- Update coroutines, JUnit, Ktor, and Logback jars

## [2.3.7] - 2024-12-11

- Consolidate Version classes

## [2.3.6] - 2024-12-10

- Add `Version2` with build time value

## [2.3.5] - 2024-12-10

- Update gRPC jars

## [2.3.4] - 2024-12-06

- Dependency updates

## [2.3.3] - 2024-12-02

- Dependency updates

## [2.3.2] - 2024-11-29

- Replace `PipelineCall` with `RoutingContext` in Ktor 3.0.1 code

## [2.3.1] - 2024-11-27

- Change minimum Java version from 17 to 11

## [2.3.0] - 2024-11-27

- Update to Ktor 3.0.1

## [2.2.0] - 2024-11-26

- Update Redis pool options

## [2.1.2] - 2024-11-10

- Add max wait seconds to Redis pool

## [2.1.1] - 2024-11-08

- Revert Redis jar

## [2.1.0] - 2024-10-18

- Update jars
- ktlint cleanup

## [2.0.0] - 2024-06-11

- Update klogger and Kotlin jars

## [1.51.0] - 2024-05-07

- Dependency updates

## [1.50.0] - 2024-03-22

- Dependency updates

## [1.44.2] - 2024-01-06

- Fix issue with evaluating Java script

## [1.44.1] - 2024-01-06

- Add verbose flag for executing Java scripts

## [1.44.0] - 2024-01-06

- Dependency updates

## [1.43.0] - 2023-12-11

- Upgrade Kotlin to 1.9.22
- Upgrade Dropwizard, Exposed, gRPC, and other dependencies
- Refactor gRPC DSL to enhance code readability (channel and server creation abstracted into utility functions)
- Refactor JavaScript utilities code structure (add `evalScriptInternal` synchronized function)
- Kotlinter cleanup

## [1.42.1] - 2023-11-01

- Fix regression in `ContentSource.kt`

## [1.42.0] - 2023-11-01

- Upgrade to Kotlin 1.9.20
- Update gRPC to 1.59.0, Dropwizard to 4.2.21, Exposed to 0.44.1
- Update Guava to 32.0.1-jre, Ktor to 2.3.5, Redis to 5.0.2
- Update to Kotlinter 4.0.0

## [1.41.0] - 2023-08-23

- Update Kotlin, Exposed, gRPC, and Logback jars

## [1.40.0] - 2023-08-02

- Update Exposed, coroutines, Ktor, Redis, and gRPC jars

## [1.39.9] - 2023-07-12

- Fix release issue

## [1.39.0] - 2023-07-06

- Update to Kotlin 1.9.0

## [1.38.0] - 2023-05-16

- Update gRPC to 1.55.1 and coroutines to 1.7.1

## [1.37.0] - 2023-05-07

- Update coroutines to 1.7.0

## [1.36.0] - 2023-05-03

- Update to Kotlin 1.8.21

## [1.35.0] - 2023-04-09

- Dependency updates

## [1.34.1] - 2023-02-01

- Fix logger issue in `Banner.kt`

## [1.34.0] - 2023-02-01

- Dependency updates

## [1.33.0] - 2022-12-31

- Update to Kotlin 1.8.0

## [1.32.0] - 2022-12-14

- Update deprecated call

## [1.31.0] - 2022-11-19

- Dependency updates

## [1.30.0] - 2022-10-11

- Dependency updates

## [1.29.0] - 2022-10-02

- Dependency updates

## [1.28.0] - 2022-07-10

- Update to Kotlin 1.7.10
- Dependency updates

## [1.27.0] - 2022-06-11

- Upgrade to Kotlin 1.7.0

## [1.26.0] - 2022-05-31

- Remove redundant String `lowercase` and `uppercase` functions
- Add `PipelineCall` typealias
- Dependency updates

## [1.25.0] - 2022-04-29

- Update Kotlin to 1.6.21
- Code cleanup
- Dependency updates

## [1.24.0] - 2022-04-11

- Upgrade to Ktor 2.0

## [1.23.0] - 2022-04-01

- Add `String.toLower()` and `String.toUpper()`
- Update to Kotlin 1.6.20
- Dependency updates

## [1.22.0] - 2022-02-22

- Dependency updates

## [1.21.0] - 2022-01-13

- Upgrade to Ktor 2.0
- Dependency updates

## [1.20.0] - 2021-12-14

- Update to Kotlin 1.6.10
- Dependency updates

## [1.19.0] - 2021-11-16

- Update to Kotlin 1.6.0
- Dependency updates

## [1.18.0] - 2021-08-27

- Dependency updates

## [1.17.0] - 2021-08-25

- Dependency updates

## [1.16.0] - 2021-07-16

- Dependency updates

## [1.15.0] - 2021-06-26

- Fix missing sources

## [1.14.0] - 2021-06-24

- Dependency updates

## [1.13.0] - 2021-06-20

- Clean up `build.gradle`

## [1.12.0] - 2021-06-02

- Dependency updates

## [1.11.0] - 2021-05-25

- Upgrade to Kotlin 1.5.10
- Add Kotlinter

## [1.10.0] - 2021-05-20

- Release branch created

## [1.9.0] - 2021-05-01

- Upgrade to Kotlin 1.5.0
- Lambda cleanup
- Code cleanups
- Update copyright

## [1.8.1] - 2021-04-24

- Clean up `Banner.kt`
- Clean up `build.gradle`
- Dependency updates

## [1.7.0] - 2021-02-03

- Add `String.maxLength()`
- Update `HttpClient` calls to include `expectSuccess`
- Add gRPC `enableRetry` support
- Update to Kotlin 1.4.30

## [1.6.0] - 2020-12-15

- Upgrade Kotlin to 1.4.21
- Fix problem with Kotlin bindings requiring a tmp name
- Add `ExprEvaluators` and Pools
- Dependency updates

## [1.5.0] - 2020-12-04

- Exclude default user from Redis `auth()` call
- Add `ContentSource.quotedSource`
- Make `GLOBAL` bindings nullable
- Cast Kotlin engine to `KotlinJsr223JvmLocalScriptEngine`
- Dependency updates

## [1.4.0] - 2020-10-08

- Add config options to Ktor HTTP client
- Convert `withHttp{}` to return value
- Upgrade to Kotlin 1.4.10
- Add non-nullable version of Redis pool requests
- Make stacktrace printing optional in Redis pools
- Add `exposed-utils` module

## [1.3.0] - 2020-08-28

- Add script pools
- Add properties to Redis pools
- Default salt value on digests
- Update to Kotlin 1.4.0
- Update to Ktor 1.4.0
- Add isolation to `JavaScriptEngine`
- Make scripts resettable
- Add support for multiple URLs with Redis pools
- Add timeout support to Ktor client

## [1.2.0] - 2020-08-14

- Upgrade to Kotlin 1.4.0-rc

## [1.1.20] - 2020-08-12

- Add `isNotNull()` and `isNull()`
- Add `String.isNotQuoted()`
- Add `String.isNotSingleQuoted()` and `String.isNotDoubleQuoted()`
- Add `String.isNotBracketed()`
- Add `String.isInt()` and `String.isDouble()`
- Add `OwnerType` to `GitHubRepo`
- Add Histogram support to `prometheus-utils`

## [1.1.19] - 2020-07-02

- Fix jar creation

## [1.1.18] - 2020-07-01

- Dependency updates

## [1.1.17] - 2020-05-31

- Add `GitHubRepo`
- Add `HerokuHttpsRedirect.kt`
- Add SHA256 encoding
- Add `isNotValidEmail()`
- Add `RedisUtils.kt`

## [1.1.16] - 2020-05-04

- Add support for Java and Python script processing

## [1.1.15] - 2020-03-07

- Upgrade Ktor
- Dependency updates

## [1.1.14] - 2019-12-19

- Update gRPC jar

## [1.1.13] - 2019-12-15

- Fix `unzip` dropping newlines

## [1.1.12] - 2019-12-15

- Add `script-utils` module
- Add delegate `atomicInteger()`
- Add `String.zip()` and `ByteArray.unzip()` extensions
- Add gRPC Server extensions

## [1.1.11] - 2019-12-04

- Add hostname to log message

## [1.1.10] - 2019-12-03

- Add times extension
- Add gRPC TLS support

## [1.1.9] - 2019-11-30

- Dependency updates
- Cleanup `build.gradle` files

## [1.1.8] - 2019-11-22

- Convert `genericServiceListener()` to `Service.genericServiceListener()`

## [1.1.7] - 2019-11-20

- Add ability for admin servlet customization

## [1.1.6] - 2019-11-17

- Add `MetricRegistry` to `GenericService`
- Rename `KtorUtils` to `KtorExtensions`

## [1.1.5] - 2019-11-17

- Add service classes

## [1.1.4] - 2019-11-15

- Minor updates

## [1.1.3] - 2019-11-15

- Update Kotlin jar

## [1.1.2] - 2019-11-14

- Version update

## [1.1.1] - 2019-11-14

- Minor updates

## [1.1.0] - 2019-11-13

- Break up jars by dependencies (multi-module restructure)

## [1.0.6] - 2019-11-13

- Convert `Int.length` into a property
- Cleanup lambda invocations

## [1.0.5] - 2019-11-11

- Add `Int.length` and `Long.length`

## [1.0.4] - 2019-11-10

- Minor updates

## [1.0.3] - 2019-11-06

- Update Guava jar to 28.1

## [1.0.2] - 2019-11-03

- Replace Guava with Kotlin objects
- Add `HostInfo` class for returning host information
- Add duration tests

## [1.0.1] - 2019-10-26

- Clean up extensions
- Add host extensions

## [1.0.0] - 2019-10-21

- Initial release
- Add `UndocumentedPublicClass` suppress directives
- Fix sleep overflow problem
- Add `@JvmStatic`
- Add `CountDownLatch.countDown {}`
