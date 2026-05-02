# Release Notes

Release history for `common-utils`. Entries are sorted from newest to oldest.
Release details are sourced from [GitHub Releases](https://github.com/pambrose/common-utils/releases).

---

## v2.8.2 — 2026-05-02

### Highlights

- **Coverage with Kover + Codecov**: Added Kotlinx Kover with aggregated HTML and XML reports across all modules, a `codecov.yml`, and a CI upload to Codecov. Patch coverage is gated at 70% on PRs.
- **Improved test coverage**: Lifted line coverage from ~52% to ~62% across the repository.
- **Gradle wrapper 9.5.0 + GPG validation**: Bumped the wrapper to 9.5.0, added GPG environment validation in the publish targets, deduplicated Dokka configuration, and bumped project version to 2.8.2.

### Build improvements

- Moved `group` and `version` from `build.gradle.kts` to `gradle.properties`; preserved `-PoverrideVersion` for the Makefile snapshot/publish targets and updated the Makefile to read `VERSION` from `gradle.properties`.
- Switched property reads (`overrideVersion`, `signingInMemoryKey`) to `providers.gradleProperty(...)` for modern Gradle idiom.
- Hoisted Kover excludes to a single shared list reused by the root aggregator and the per-project filter.
- Derived the POM SCM and homepage URLs from a shared `scmHost` constant; wrapped `pom.name` in a provider for consistency with `description`.
- Enabled `org.gradle.parallel=true`; passed `--no-parallel` to `versioncheck` (the `manes` plugin is not parallel-safe).

### Bug fixes

- Replaced deprecated `DefaultJedisClientConfig.ssl(Boolean)` with `sslOptions(SslOptions.defaults())` (Jedis 7.4.2+).
- Fixed functional bugs in Redis, Python script handling, Banner, SystemMetrics, and Zipkin.

### Dependency bumps

- `kover` 0.9.1 → 0.9.8
- `grpc` 1.80.0 → 1.81.0
- `netty-tcnative` 2.0.76.Final → 2.0.77.Final

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.8.1...2.8.2

---

## v2.8.1 — 2026-04-24

### Highlights

- **Kotlin 2.3.21**: Bumped Kotlin to 2.3.21.
- **Gradle build cleanup**: Moved shared configuration into the `allprojects` block, scoped Dokka per-subproject, simplified repository declarations, and adopted dependency bundles in `gradle/libs.versions.toml`.
- **API scopes**: Promoted inter-module dependencies to the `api` configuration so transitive resolution is correct for consumers.

### Housekeeping

- Dropped the stale Kover target.
- Refreshed dependency versions in `gradle/libs.versions.toml`.

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.8.0...2.8.1

---

## v2.8.0 — 2026-04-22

### Highlights

- **Jetty 12 (EE11)**: Upgraded `jetty-utils` to Jetty 12 with the EE11 servlet API. Imports moved from `org.eclipse.jetty.servlet.*` to `org.eclipse.jetty.ee11.servlet.*`.
- **Convention plugins**: Migrated to [`com.pambrose` Gradle convention plugins](https://github.com/pambrose/pambrose-gradle-plugins) (`pambrose.kotlinter`, `pambrose.testing`, `pambrose.stable-versions`). Bumped to `pambrose-gradle-plugins:1.0.14`, which provides `kotest-runner-junit5` and `kotlin-test` as default `testImplementation` dependencies, removing per-module duplication across all 19 modules.
- **Consolidated build**: Subproject plugin application moved to the root `build.gradle.kts`. Dokka aggregation lives at the root, and `configureKotlin()` / `configurePublishing()` apply JVM 17 toolchain, experimental opt-ins, and Maven publication metadata uniformly.
- **API vs implementation**: Promoted several dependencies to the `api` configuration where their types appear in public signatures, ensuring consumers get correct transitive resolution.

### Breaking changes

- `jetty-utils` consumers must update imports from `org.eclipse.jetty.servlet.*` to `org.eclipse.jetty.ee11.servlet.*` and align to Jetty 12 APIs.

### Housekeeping

- Added `RELEASE_NOTES.md` index and `.superset/` ignore.
- Removed unused version catalog entries and cleaned up per-module build scripts.

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.7.1...2.8.0

---

## v2.7.1 — 2026-04-17

### Changes

- Improve Maven publishing metadata (fix missing POM description)
- Consolidate Dokka docs in root project with GitHub Pages deployment for KDocs
- Add comprehensive KDoc documentation across all modules
- Add Claude Code GitHub Workflow integration
- Add Codacy badge, Tests badge, and reorder README badges
- Reduce Gradle heap size

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.7.0...2.7.1

---

## v2.7.0 — 2026-04-04

### Maven Central Migration

This release migrates artifact publishing from JitPack to Maven Central.

#### Breaking Changes

- **Group ID changed**: `com.github.pambrose.common-utils` → `com.pambrose.common-utils`
- **Package renamed**: `com.github.pambrose.common.*` → `com.pambrose.common.*`
- **JitPack no longer supported** — use Maven Central instead

#### Migration

Update your dependencies from:

```kotlin
implementation("com.github.pambrose.common-utils:core-utils:2.6.4")
```

to:

```kotlin
implementation("com.pambrose.common-utils:core-utils:2.7.0")
```

No custom repository declaration is needed — Maven Central is the default in Gradle and Maven.

#### Changes

- Migrate artifact publishing from JitPack to Maven Central (group: `com.pambrose.common-utils`)
- Add Vanniktech Maven Publish plugin with POM metadata and GPG signing
- Rename packages from `com.github.pambrose` to `com.pambrose` across all modules
- Disable root project publishing to prevent stale artifacts
- Remove `jitpack.yml` and `common-utils-bom` module
- Remove all BOM dependencies in favor of explicit version refs
- Add ~78 new tests across 9 modules (ktor-client-utils, ktor-server-utils, prometheus-utils, guava-utils, service-utils, email-utils, jetty-utils, recaptcha-utils, script-utils-common)
- Add MockK and ktor-client-mock test dependencies
- Add GitHub Actions CI workflow for build and lint
- Update all documentation to reflect Maven Central coordinates
- Update copyright headers to 2026
- Update dependencies

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.6.4...2.7.0

---

## v2.6.4 — 2026-04-01

- Release 2.6.4: dependency updates, CHANGELOG, and README badges (#85)

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.6.3...2.6.4

---

## v2.6.3 — 2026-03-19

- Extract JitPack URLs into Makefile variables (`JITPACK_BUILD_LOG`, `JITPACK_API_URL`)
- Update dependencies: gRPC 1.80.0, Resend 4.13.0
- Update `CODE_REVIEW.md` dependency versions and move to `docs/`
- Bump version references across all module READMEs and docs

---

## v2.6.2 — 2026-03-16

- Convert all tests from JUnit 5 to Kotest StringSpec across all modules
- Upgrade Gradle wrapper to 9.4.0
- Update Kotlin to 2.3.20
- Update Redis dependency to 7.4.0
- Dependency updates and codebase improvements

---

## v2.6.1 — 2026-03-04

- 2.6.1 (#82)

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.6.0...2.6.1

---

## v2.6.0 — 2026-03-01

- 2.6.0 (#81)

**Full Changelog**: https://github.com/pambrose/common-utils/compare/2.5.3...2.6.0

---

## v2.5.3 — 2026-02-09

- More jitpack.io issues

---

## v2.5.2 — 2026-02-08

- More jitpack.io issues

---

## v2.5.1 — 2026-02-08

- Fix jitpack.io problem

---

## v2.5.0 — 2026-02-08

- Fix byte array encoding in hashing functions to use UTF-8 charset
- Fix `IndexOutOfBoundsException` in Redis `userInfo` parsing and add tests
- Fix logger message handling to ensure proper function invocation and add tests for null database scenarios
- Fix `Atomic` class to use internal value for thread-safe operations and add tests for lambda-based logging methods
- Fix `resetContext` to clear `valueMap` and `typeMap` to prevent state leakage between pool users and add tests for verification
- Fix shutdown hook to wait for service termination and add verification tests
- Fix `Atomic` class to enforce read-only value access and add verification tests for MD5/SHA-256 hashing
- Fix `AtomicDelegates` to throw an exception if property is already set during `compareAndSet` operation
- Fix `AtomicDelegatesTests` to throw `IllegalStateException` for invalid value assignments and update `criticalSection` to return block result
- Fix `linesBetween` function to handle invalid start and end indices and improve URL masking logic
- Fix `JsonElement.isEmpty` function to correctly handle `JsonObject`, `JsonArray`, and `JsonPrimitive` types
- Fix `BugFixVerificationTests` to validate behavior of `isEmpty`, `criticalSection`, and `linesBetween` functions
- Fix `toMap` function to handle nested arrays and mixed types in `JsonElement`
- Fix `AbstractExprEvaluator.eval` to safely handle non-Boolean results with meaningful error messages
- Fix race conditions in `BooleanWaiter` and `LameBooleanWaiter`, and improve `isZipped` handling for byte arrays
- Refactor `LameBooleanWaiter` to use `coroutineScope` for structured concurrency and improve cancellation handling
- Fix `isZipped` function to ensure proper size check before accessing byte array elements
- Fix `minEvictableIdleDuration` in `RedisUtils` to correctly evict connections idle for 1 minute
- Fix `build_time` key in JSON output to remove unnecessary colon
- Fix `Duration` formatting to handle negative values correctly
- Fix `Short` multiplication operator to correctly handle type conversion
- Fix duration formatting for negative values and update timeout handling to use `Duration.INFINITE`
- Add bug summary documentation for identified issues and fixes
- Add example script for Kotlin expression evaluation
- Bump project version to 2.4.15 and update dependency versions in documentation
- Update Kotlin and logging versions in `libs.versions.toml`
- Add `@Synchronized` annotation to `import` method in `JavaScript.kt`
- Refactor `Atomic` class to use internal mutex instance for locking
- Add `waitForPortAvailable` function to check port availability with retries
- Update ktlint import ordering rule and bump ktor and logback versions
- Refactor `RedisUtils` to replace `JedisPool` with `RedisClient` and update related methods
- Refactor `BugFixVerificationTests` to replace `JedisPool` with `RedisClient` in test cases
- Refactor `IOExtensionsTests` and `JavaScript` to use `Int` instead of `Integer` for type handling
- Refactor `JavaScript.kt` to update type handling for `Int` and `Integer`
- Refactor `ResendService` to use `runCatching` for error handling in `sendEmail`
- Refactor `BugFixVerificationTests` to add a comment in the `startUp` method for clarity

---

## v2.4.14 — 2026-02-05

- Downgrade ktor to 3.3.3

---

## 2.4.13 — 2026-02-05

- Fix maven bom issue

---

## v2.4.11 — 2026-02-05

- Update grpc, nettyTcNative, and redis versions in `libs.versions.toml`; add `refresh` target in Makefile
- Add `ExceptionUtils` with cancellable `runCatching` and rethrow functionality

---

## v2.4.10 — 2026-01-29

- Update library versions and increase project version to 2.4.10
- Update Gradle to 9.3.0, bump Logback to 1.5.24, and remove obsolete Codebeat badge
- Add OpenSpec documentation and commands for spec-driven development
- Update library versions for Ktor, Resend, and Serialization in `libs.versions.toml`

---

## v2.4.9 — 2025-12-17

- Fix the problem with jitpack.io artifacts

---

## v2.4.8 — 2025-12-16

- Update jars and upgrade to Kotlin 2.3.0

---

## v2.4.7 — 2025-11-07

- Update jars

---

## v2.4.6 — 2025-10-25

- Update to Kotlin 2.2.21
- Update jars

---

## v2.4.5 — 2025-09-10

- Update jetty version to 10.0.26 in `libs.versions.toml`
- Update dropwizard, kotest, kotlin, logging, nettyTcNative, redis, and resend versions in `libs.versions.toml`

---

## v2.4.4 — 2025-08-27

- Update dependency versions in `libs.versions.toml` and adjust plugin application in `build.gradle.kts`

---

## v2.4.3 — 2025-08-21

- Upgrade Gradle to 9.0.0 and update library dependencies to use BOMs
- Add `common-utils-bom` module with dependency constraints and publishing configuration

---

## v2.4.2 — 2025-08-18

- Update dependencies to version 2.4.2 and add `EmailUtils` and `ResendService`
- Refactor `Email` class and improve formatting in various files
- Add data classes for email message handling: `Bounce`, `Click`, `Data`, `Header`, and `ResendWebhookMsg`
- Add reCAPTCHA utility module with configuration and verification service
- Refactor reCAPTCHA service logging and update build configuration for Kotlin serialization

---

## v2.4.1 — 2025-08-16

- Update dependencies
- Change implementation to api for java and python scripting libraries in `build.gradle.kts`

---

## v2.4.0 — 2025-08-11

- Update library versions for compatibility and performance improvements
- Remove `corex-utils` module and update `README.md` to reflect changes
- Update library versions for kotest, logging, and redis
- Update datetime library version for compatibility with 0.6.x
- Fix formatting of datetime library version in `libs.versions.toml`
- Update kotest version to 6.0.0.M14 and upgrade JVM target to 17
- Upgrade Java version to 17 in configuration files

---

## v2.3.11 — 2025-06-25

- Update jars
- Convert to `build.gradle.kts`
- Convert to using `libs.versions.toml`
- Fix 2.2.0 issue
- Refactor `build.gradle.kts` scripts
- Add `json-utils` module

---

## v2.3.10 — 2025-03-23

- Refactor atomic operations to use `kotlin.concurrent.atomics` and update library versions

---

## v2.3.9 — 2025-03-21

- Update jars
- Update copyright
- Refactor dependencies to use `rootProject` libraries

---

## v2.3.8 — 2024-12-20

- Update jars

---

## v2.3.7 — 2024-12-11

- Clean up `Version` class

---

## v2.3.6 — 2024-12-11

- Add `Version2` with build time value

---

## v2.3.5 — 2024-12-10

- Update gRPC jars

---

## 2.3.4 — 2024-12-06

- Update exposed jar

---

## v2.3.3 — 2024-12-03

- Update jars

---

## v2.3.2 — 2024-11-30

- Replace `PipelineCall` with `RoutingContext` in ktor code

---

## v2.3.1 — 2024-11-28

- Change minimum Java version from 17 to 11

---

## v2.3.0 — 2024-11-28

- Update to ktor 3.0.1

---

## v2.2.0 — 2024-11-27

- Update to Kotlin 2.1.0

---

## v2.1.3 — 2024-11-27

- Add options to Redis pool

---

## v2.1.2 — 2024-11-11

- Add max wait secs to Redis pool

---

## v2.1.1 — 2024-11-09

- Revert Redis jar

---

## v2.1.0 — 2024-10-18

- Update jars
- ktlint cleanup

---

## v2.0.0 — 2024-06-11

- Update klogger and kotlin jars

---

## v1.51.0 — 2024-05-08

- Update jars

---

## v1.50.0 — 2024-03-23

- Update jars

---

## v1.44.2 — 2024-01-07

- Fix issue with evaluating Java script

---

## v1.44.1 — 2024-01-07

- Add a verbose flag for evaluating Java scripts

---

## v1.44.0 — 2024-01-07

- Upgrade to Kotlin 1.9.22

---

## v1.43.0 — 2023-12-12

- Create 1.43.0 branch
- kotlinter cleanup
- Update project dependencies (Gradle, Dropwizard, Exposed, gRPC, and others)
- Refactor gRPC DSL to enhance code readability (channel/server creation abstracted into private utility functions)
- Refactor JavaScript utilities code structure (introduces private synchronized `evalScriptInternal` to avoid repetition, widens scope of `clazz`)

---

## v1.42.1 — 2023-11-02

- Fix regression in `ContentSource.kt`

---

## v1.42.0 — 2023-11-01

Update jars:

- kotlin 1.9.20
- grpc 1.59.0
- dropwizard 4.2.21
- exposed 0.44.1
- guava 32.0.1-jre
- ktor 2.3.5
- redis 5.0.2

Update to kotlinter 4.0.0.

---

## v1.41.0 — 2023-08-23

- Update kotlin, exposed, grpc, and logback jars

---

## v1.40.0 — 2023-08-02

- Update exposed, coroutines, ktor, redis, and grpc jars

---

## v1.39.9 — 2023-07-12

- Fix release issue

---

## v1.39.0 — 2023-07-07

- Update to Kotlin 1.9.0

---

## v1.38.0 — 2023-05-16

- Update jars

---

## v1.37.0 — 2023-05-07

- Update coroutines to 1.7.0

---

## v1.36.0 — 2023-05-04

- Update to Kotlin 1.8.21

---

## v1.35.0 — 2023-04-10

- Update jars

---

## v1.34.1 — 2023-02-02

- Fix logger issue in `Banner.kt`

---

## v1.34.0 — 2023-02-02

- Update jars

---

## v1.33.0 — 2023-01-01

- Upgrade to Kotlin 1.8.0

---

## v1.32.0 — 2022-12-15

- Update gRPC, Jetty, and ktor jars

---

## v1.31.0 — 2022-11-20

- Update jars

---

## v1.30.0 — 2022-10-12

- Update jars

---

## v1.29.0 — 2022-10-02

- Update to Kotlin 1.7.20
- Update jars

---

## 1.28.0 — 2022-07-10

- Update to Kotlin 1.7.10
- Update jars

---

## v1.27.0 — 2022-06-11

- Upgrade to Kotlin 1.7.0

---

## v1.26.0 — 2022-05-31

- Remove redundant `String` lowercase and uppercase functions
- Add `PipelineCall` typealias
- Upgrade jars

---

## v1.25.0 — 2022-04-29

- Code cleanup
- Update Kotlin to 1.6.21
- Update jars

---

## v1.24.0 — 2022-04-12

- Upgrade to Ktor 2.0

---

## v1.23.0 — 2022-04-01

- Update jars
- Add `String.toLower()` and `String.toUpper()`
- Update to Kotlin 1.6.20

---

## v1.22.0 — 2022-02-22

- Upgrade jars

---

## v1.21.0 — 2022-01-13

- Upgrade to ktor 2.0
- Update jars

---

## v1.20.0 — 2021-12-14

- Update to Kotlin 1.6.10
- Update jars

---

## v1.19.0 — 2021-11-16

- Upgrade to Kotlin 1.6.0
- Upgrade jars

---

## v1.18.0 — 2021-08-27

- Upgrade jars

---

## v1.17.0 — 2021-08-25

- Update jars

---

## v1.16.0 — 2021-07-16

- Update jars

---

## v1.15.0 — 2021-06-27

- Fix problem with missing sources

---

## v1.14.0 — 2021-06-24

- Upgrade jars

---

## v1.13.0 — 2021-06-21

- Upgrade jars
- Fix jitpack.io install issue with Gradle 7.0

---

## v1.12.0 — 2021-06-02

- Upgrade jars

---

## v1.11.0 — 2021-05-26

- Upgrade to Kotlin 1.5.10
- kotlinter cleanup

---

## v1.10.0 — 2021-05-21

- Update jars

---

## v1.9.0 — 2021-05-01

- Lambda cleanup
- Upgrade to Kotlin 1.5.0
- Code cleanups
- Update copyright

---

## v1.8.1 — 2021-04-24

- Update jars

---

## v1.7.0 — 2021-02-03

- Add `String.maxLength()`
- Update `HttpClient` calls to include `expectSuccess`
- Add gRPC `enableRetry` support
- Update to Kotlin 1.4.30

---

## v1.6.0 — 2020-12-15

- Upgrade Kotlin to 1.4.21
- Fix problem with kotlin bindings requiring a tmp name
- Add `ExprEvaluators` and Pools
- Upgrade jars

---

## v1.5.0 — 2020-12-05

- Exclude default user from Redis `auth()` call
- Add `ContentSource.quotedSource`
- Make `GLOBAL` bindings nullable
- Cast Kotlin engine to `KotlinJsr223JvmLocalScriptEngine`
- Upgrade jars

---

## v1.4.0 — 2020-10-08

- Add config options to ktor http client
- Convert `withHttp{}` to return value
- Upgrade to Kotlin 1.4.10
- Add non-nullable version of Redis pool requests
- Make stacktrace printing optional in Redis pools
- Add `exposed-utils`

---

## v1.3.0 — 2020-08-28

- Add script pools
- Add properties to Redis pools
- Default salt value on digests
- Update to Kotlin 1.4.0
- Update to ktor 1.4.0
- Add isolation to `JavaScriptEngine`
- Make scripts resettable
- Add support for multiple URLs with Redis pools
- Add timeout support to ktor client

---

## v1.2.0 — 2020-08-14

- Add Kotlin 1.4.0-rc support

---

## v1.1.20 — 2020-08-13

- Add `isNotNull()` and `isNull()`
- Add `String.isNotQuoted()`
- Add `String.isNotSingleQuoted()` and `String.isNotDoubleQuoted()`
- Add `String.isNotBracketed()`
- Add `String.isInt()` and `String.isDouble()`
- Add `OwnerType` to `GitHubRepo`
- Add Histogram support to `prometheus-utils`

---

## v1.1.19 — 2020-08-13

- Fix jar creation

---

## v1.1.18 — 2020-07-01

- Update jars

---

## v1.1.17 — 2020-05-31

- Add `GitHubRepo`
- Add `HerokuHttpsRedirect.kt`
- Add SHA256 encoding
- Add `isNotValidEmail()`
- Add `RedisUtils.kt`

---

## v1.1.16 — 2020-05-04

- Add support for Java and Python script processing

---

## v1.1.15 — 2020-03-07

- Upgrade ktor
- Upgrade jars

---

## v1.1.14 — 2019-12-19

- Update grpc jar

---

## v1.1.13 — 2019-12-15

- Fix unzip dropping newlines

---

## v1.1.12 — 2019-12-15

- Add `script-utils`
- Add delegate `atomicInteger()`
- Add `String.zip()` and `ByteArray.unzip()` extensions
- Add gRPC Server extensions

---

## v1.1.11 — 2019-12-05

- Add hostname to log msg

---

## v1.1.10 — 2019-12-04

- Add `times` extension
- Add gRPC TLS support

---

## v1.1.9 — 2019-12-01

- Update jars
- Clean up `build.gradle` files

---

## v1.1.8 — 2019-11-22

- Convert `genericServiceListener()` to `Service.genericServiceListener()`

---

## v1.1.7 — 2019-11-20

- Add ability for admin servlet customization

---

## v1.1.6 — 2019-11-18

- Add `MetricRegistry` to `GenericService`
- Rename `KtorUtils` to `KtorExtensions`

---

## v1.1.5 — 2019-11-17

_No release notes recorded._

---

## v1.1.4 — 2019-11-16

_No release notes recorded._

---

## v1.1.3 — 2019-11-15

- Update kotlin jar

---

## v1.1.2 — 2019-11-15

- Add jetty, prometheus, ktor, grpc, zipkin, and dropwizard utils

---

## v1.1.1 — 2019-11-14

_No release notes recorded._

---

## v1.1.0 — 2019-11-14

_No release notes recorded._

---

## v1.0.6 — 2019-11-13

_No release notes recorded._

---

## v1.0.5 — 2019-11-11

_No release notes recorded._

---

## v1.0.4 — 2019-11-10

- Add thread with latch option

---

## v1.0.3 — 2019-11-07

- Upgrade guava jar to 28.1

---

## v1.0.2 — 2019-11-04

- Replace guava with kotlin objects
- Add `HostInfo` class for returning host information
- Add duration tests

---

## v1.0.1 — 2019-10-26

- Clean up extensions
- Add host extensions

---

## 1.0.0 — 2019-10-22

Initial release.
