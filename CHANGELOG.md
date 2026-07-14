# Changelog

All notable changes to Common Utils are documented in this file.

## [3.2.0] - 2026-07-14

### Build & tooling

- Silence the deprecated `io.grpc.Attributes.keys()` warning in the grpc-utils test suite with a scoped
  `@Suppress("DEPRECATION")`. gRPC exposes no public replacement for enumerating attribute keys
  (`keysForTest()` is package-private), so the test's exact-key-set assertion is preserved rather than
  dropped. Test-only — no API or behavior change.

### Dependency bumps

- `kotlin` 2.4.0 → 2.4.10
- Bump project version to 3.2.0

## [3.1.0] - 2026-07-10

### New features

- New `DateUtils` object (core-utils, `DateUtils.kt`) collecting multiplatform date/time helpers built on
  `kotlinx-datetime`: ISO parsing (`parseToLocalDate`/`parseToLocalTime`/`parseToLocalDateTime`),
  `instantNow`/`localDateNow`/`localDateTimeNow`, US-style formatters (`toMMDDYYYY`, `toMMDDYY`, `toMMDD`,
  `toDashedYYYYMMDD`, `toFullDateString`, `toLogString`, `toMMDDYYYYHHMM`, `toCreated`, `toISO8601`,
  `toUTCDateTime`), `abbrevDayOfWeek`, and duration/age helpers (`age`, `toAdjustedString`). Every member is
  KDoc-documented.
- `DateUtils` is time-zone-neutral: `localDateNow`/`localDateTimeNow` default to
  `TimeZone.currentSystemDefault()` instead of a hardcoded region, and no named zone is resolved internally,
  so core-utils pulls no IANA time-zone database into JS/wasmJs consumers. Callers needing a fixed zone pass
  one explicitly (which on JS/wasmJs requires the `@js-joda/timezone` npm package on the consumer side).
- **Moved (source-incompatible)**: `toFullDateString` and `abbrevDayOfWeek`, previously top-level functions in
  `com.pambrose.common.util`, are now members of the `DateUtils` object; update call sites to
  `import com.pambrose.common.util.DateUtils.toFullDateString` (and `.abbrevDayOfWeek`).
- `DateUtils.toFullDateString(timeZone)` overload appends the DST-aware UTC offset for the given zone
  (e.g. `"Mon 04/10/26 14:30:00 -04:00"`, `Z` for a zero offset). kotlinx-datetime exposes the numeric
  offset rather than a letter abbreviation such as `EST`/`EDT`, which is ambiguous across regions; the
  no-argument `toFullDateString()` is unchanged. Resolving a named zone on JS/wasmJs still requires the
  consumer to add `@js-joda/timezone`; fixed-offset and UTC zones need no database.

### Bug fixes

- `DateUtils.toFullDateString` no longer appends a hardcoded `"PST"` suffix, which was incorrect during
  Pacific Daylight Time.
- `DateUtils.toLogString` left-pads the millisecond field, so `5 ms` renders as `.005` (previously `.500`).
- `DateUtils.toMMDDYYYYHHMM` zero-pads the hour, so 9 AM renders as `09:05` (previously `9:05`).

### Build & tooling

- KMP module `Test` tasks configure `testLogging` (PASSED/SKIPPED/FAILED events with full exception format).

### Dependency bumps

- `logback` 1.5.32 → 1.5.38
- `grpc` 1.82.1 → 1.82.2
- Bump project version to 3.1.0

## [3.0.0] - 2026-07-09

### Kotlin Multiplatform conversion

- `core-utils`, `json-utils`, and `ktor-client-utils` are now Kotlin Multiplatform modules targeting JVM, JS,
  wasmJs, and Native (iOS/macOS/tvOS/watchOS/Linux/Windows). Portable code moved to `commonMain`; JVM-bound
  declarations moved verbatim to `jvmMain`, so the published JVM API is unchanged. The mixed files
  (`StringExtensions`, `MiscExtensions`, `MiscFuncs`) are split across source sets with `@file:JvmName` +
  `@file:JvmMultifileClass`, keeping the compiled JVM facade classes binary-identical.
- The remaining 16 framework modules stay Kotlin/JVM and consume `core-utils`' jvm variant transparently.
- **Consumer note (Maven only)**: non-Gradle consumers of the three KMP modules must depend on the `-jvm`
  artifact (e.g. `core-utils-jvm`); Gradle consumers resolve the correct variant from the root coordinate
  automatically.
- **Known deviation**: `KtorDsl.blockingGet(...)` is now a JVM-only extension function on `KtorDsl`
  (`runBlocking` does not exist in common code). Qualified call sites compile unchanged, but the member-import
  form becomes `import com.pambrose.common.dsl.blockingGet`, and the compiled symbol moved from `KtorDsl` to
  `KtorDslJvmKt` (binary-incompatible for this one function).
- `kotlin-logging` dependency switched from the `-jvm` artifact to the multiplatform root artifact
  (JVM consumers resolve the same jar as before).
- Tests: portable Kotest specs moved to `commonTest` and now execute on JVM (JUnit Platform), Node.js,
  wasmJs, and native simulators via the Kotest Gradle plugin (`io.kotest` + KSP); JVM-bound specs stay in
  `jvmTest`. watchOS/tvOS simulator test tasks are disabled (no simulator runtimes installed by default).
- The native target set excludes the Intel-based Apple targets (`macosX64`, `tvosX64`, `watchosX64`), which
  Kotlin 2.4 deprecates for removal (https://kotl.in/native-targets-tiers); Apple platforms are covered by the
  Arm64 device/simulator targets.
- Build: `kmpModuleNames` switch in the root `build.gradle.kts` selects KMP vs JVM configuration;
  settings-level ivy repositories serve the Node.js/Yarn/Binaryen toolchain downloads under
  `FAIL_ON_PROJECT_REPOS` (each toolchain env spec's `downloadBaseUrl` is unset so the Kotlin plugin never
  registers project-level repositories); `kotlin-js-store/` lockfiles are now tracked; Gradle heap raised
  to 8g for Kotlin/Native link tasks.

### Testing

- Aggregate JVM instruction coverage raised from 83.5% to 98.7%; the remaining misses are almost entirely
  unreachable defensive branches. Coverage of the core-utils and ktor-client-utils JVM extensions
  (`blockingGet` against a loopback HTTP server, salted hashes with known-answer vectors) closes the
  codecov patch findings from the multiplatform conversion.
- `RecaptchaService`'s verification `HttpClient` is now an internal, swappable property, giving tests a seam
  to fake Google's siteverify endpoint with a MockEngine-backed client; new tests cover the verification
  success/failure response branches, the outgoing form parameters, and `RecaptchaResponse`'s serializer
  write path. Production behavior and the public API are unchanged.
- New hermetic specs across the JVM modules: in-process gRPC round-trip and TLS-context construction from
  committed self-signed PEM fixtures (grpc-utils), MockK-based servlet adapter and Jedis tests
  (ktor-server-utils, redis-utils), H2 in-memory Exposed tests including the custom `upsert`
  (exposed-utils), latch-handshake concurrency tests (guava-utils), webhook serialization branches
  (email-utils), and Jython pool lifecycle (script-utils-python).

### Build & tooling

- Aggregate `detekt`/`detektBaseline` tasks are wired with `tasks.named()` instead of a name-matching live
  spec; the kotlinter lint/format generated-source excludes collapsed onto the shared
  `ConfigurableKtLintTask` supertype with the excluded path derived from `layout.buildDirectory`
  (separator-safe); watchOS/tvOS simulator test disabling selects targets by `konanTarget.family` instead of
  task-name prefixes; duplicated publishing jar arguments and the `-Xreturn-value-checker` flag hoisted to
  shared values.

### Dependency bumps

- `jetty` 12.1.10 → 12.1.11
- `kotest` 6.2.1 → 6.2.2
- Security: Yarn resolution overrides force patched versions of vulnerable transitive npm packages in the
  JS/wasmJs test toolchains — `ws` 8.20.1 → 8.21.0 (memory-exhaustion DoS), `serialize-javascript`
  6.0.2 → 7.0.5 (RCE + CPU DoS), `diff` 7.0.0 → 8.0.3 (parsePatch DoS) — clearing all five Dependabot
  alerts on the `kotlin-js-store/` lockfiles. Dev-time test infrastructure only; nothing ships in
  published artifacts.
- Bump project version to 3.0.0 (major: the two binary/coordinate deviations above for the KMP modules)

## [2.9.3] - 2026-07-03

### New features

- `with(a, b) { ... }` (core-utils, `ScopeFunctions.kt`): a two-receiver variant of the standard `with` that runs a `context(A, B) () -> R` block with both `a` and `b` available as context parameters. The two type parameters keep each receiver's distinct type, so each can satisfy a separate `context(...)` parameter (a `vararg` would collapse them to their common supertype). For more receivers, add further fixed-arity overloads.
- `readProperties(vararg fileNames)` / `readProperties(List<String>)` (core-utils, `PropertyFunctions.kt`): load `key=value` lines from the given files into JVM system properties via `System.setProperty`, skipping `#`-comment and non-`key=value` lines and failing fast when a file is missing.

### Build & tooling

- Enable Kotlin's unused-return-value checker (`-Xreturn-value-checker=check`) on the production `compileKotlin` task only; test source sets are skipped so Kotest's assertion DSL (which returns its receiver) does not emit false positives.
- Replace the `com.pambrose.stable-versions` convention plugin with a direct `com.github.ben-manes.versions` plugin plus an inline `configureVersions()` helper. Its `isNonStable` filter now uses a delimiter-anchored regex that recognizes dot-separated qualifiers (Netty's `.Beta1`, Spring/Hibernate `.RC1`/`.CR1`) while leaving classifier versions such as guava's `-jre` stable, and it rejects a pre-release candidate only when the current version is stable — so dependencies intentionally tracked on a pre-release line (e.g. detekt alphas) still surface updates. The `DependencyUpdatesTask` is now configured lazily via `configureEach`.

### Dependency bumps

- Gradle wrapper 9.5.1 → 9.6.1
- `detekt` 2.0.0-alpha.4 → 2.0.0-alpha.5
- `gradlePlugins` (pambrose convention plugins) 1.0.14 → 1.1.0
- `mavenPublish` 0.36.0 → 0.37.0
- `grpc` 1.82.0 → 1.82.1
- `ktor` 3.5.0 → 3.5.1
- `exposed` 1.3.0 → 1.3.1
- `redis` 7.5.2 → 7.5.3
- Bump project version to 2.9.3

## [2.9.2] - 2026-06-14

### New features

- `ApplicationCall.respondWith`/`redirectTo` and their `RoutingContext` overloads (ktor-server-utils) now accept a `suspend () -> String` block, so the body/redirect-target lambda can call suspending functions. Existing non-suspending lambdas remain valid, since `() -> String` is a subtype of `suspend () -> String`.

### Dependency bumps

- `detekt` 2.0.0-alpha.3 → 2.0.0-alpha.4

## [2.9.1] - 2026-06-11

### Breaking changes

- `Table.upsert` (exposed-utils) is now a thin overload of Exposed's native `upsert` and takes only a unique `Index` as the conflict target (`upsert(conflictIndex = myUniqueIndex) { ... }`). The hand-rolled `UpsertStatement` class and the `conflictColumn` parameter have been removed; pass a single-column unique index instead of a column. The lambda receiver is now Exposed's `org.jetbrains.exposed.v1.core.statements.UpsertStatement<Long>`.

### Refactoring & internals

- Replace the custom `UpsertStatement` and its `prepareSQL` override with a forwarding call to Exposed's native `upsert`, keeping named index definitions as the single source of truth for conflict columns while letting Exposed generate the `ON CONFLICT` SQL

### Dependency bumps

- `grpc` 1.81.0 → 1.82.0
- `netty-tcnative-boringssl-static` pinned to 2.0.75.Final to match gRPC 1.82.0 (catalog alias `netty-ssl` renamed to `netty-tcnative`)

## [2.9.0] - 2026-06-03

### Breaking changes

- `JavaScript.eval`/`evalScript` now return `Any?` instead of `Any`, so a null script result is returned honestly (fixes a `NullPointerException` on `eval("null")`); binary-compatible, source-breaking only for Kotlin callers that bound the result to a non-null type, and aligns `JavaScript` with `KotlinScript`/`PythonScript`
- `KtorServletRequest.getContentType()` now returns `String?` and yields `null` when no `Content-Type` header is present, matching the `HttpServletRequest` contract (previously returned `"*/*"`)
- Remove the always-false `isJava6` public val from `guava-utils`

### Bug fixes

- `ContentSource.GitLabFile` uses `repo.domainName` instead of a hardcoded `gitlab.com`, so self-hosted GitLab instances resolve correctly
- `ListUtils.listPrint` quotes String elements per element instead of casting the whole list, fixing a `ClassCastException` on mixed-type lists
- `InstrumentedThreadFactory` increments terminated before decrementing running, so a concurrent scrape never sees `running + terminated < created`
- `AbstractExprEvaluator.compute()` returns `Any?` and propagates null instead of throwing on a null expression result
- `String.singleToDoubleQuoted()` now escapes inner double quotes per its KDoc (`te"st` → `"te\"st"`)
- `AbstractGenericService` removes its JVM shutdown hook on `shutDown()`, fixing a hook leak that left one hook registered per service instance for the life of the JVM
- `UpsertStatement` emits `DO NOTHING` when every inserted column is part of the conflict key, instead of generating a dangling `DO UPDATE SET` that PostgreSQL rejects
- `SamplerGaugeCollector` validates `labelNames`/`labelValues` sizes at construction (fail-fast) instead of throwing on every Prometheus scrape; `collect()` now guards the user-supplied sampler with `runCatching` and reports `Double.NaN` instead of aborting the whole scrape
- `GenericValueWaiter`/`BooleanWaiter` support concurrent waiters via per-call `(predicate, continuation)` pairs under a lock, fixing waiters clobbering each other; also fixes a liveness bug where a satisfied wait could stall for the full timeout, and isolates a throwing predicate so it no longer starves the other waiters
- `ZipExtensions.unzip()` decodes non-gzipped bytes explicitly as UTF-8 (was the JVM default charset, which corrupted non-ASCII content on a non-UTF-8 JVM)
- `String.obfuscate()` requires `freq > 0` instead of throwing `ArithmeticException` (divide by zero)
- `MiscJavaFuncs.random(int)`/`random(long)` use the unbiased `nextInt`/`nextLong(bound)`, removing modulo bias and rejecting `bound <= 0`
- `MiscJavaFuncs.sleepMillis` restores the thread interrupt flag when `Thread.sleep` is interrupted
- `KtorServletResponse` backs its header map with a case-insensitive `TreeMap` per RFC 9110 §5.1 / the `HttpServletResponse` contract

### New features

- Add a `ByteArray.zip()` GZIP overload mirroring `String.zip()`, avoiding a redundant UTF-8 re-encode for callers that already hold bytes
- Broaden `isValidEmail()` to accept plus-addressing (`user+tag@example.com`), TLDs longer than four characters (`.travel`, `.email`), and single-character domain labels (`user@x.io`), while still rejecting clearly invalid input

### Refactoring & internals

- Extract `AbstractGenericService<T>` to de-duplicate the ~95% identical `GenericService` and `GenericKtorService` base classes; public API unchanged
- Centralize JVM-termination detection in a new `ScriptGuards` (script-utils-common), broadened to cover `System.exit`, `kotlin.system.exitProcess`, and `Runtime.exit`/`halt` across the Kotlin and Java engines; documented everywhere as best-effort guards against accidental termination, **not** a security sandbox
- Remove `LameBooleanWaiter` (a redundant, lower-quality duplicate of `BooleanWaiter`)
- Dead-code sweep: remove unreachable branches, dead fallbacks, a shipped `Example.kt` demo, and stale suppressions across eight modules
- Idiomatic-Kotlin and small performance cleanups (single-pass JSON path walks, a cached default `Json`, `joinToString` hex building, `toXOrNull()` numeric checks, etc.), each behavior-equivalence verified
- Refactor four convoluted-logic targets (`Duration.format`, `BooleanMonitor` init, `JavaScript.javaEquiv`, and `KtorServletRequest` case-insensitive parameter access) with characterization tests

### Documentation

- KDoc accuracy fixes across dropwizard-utils, recaptcha-utils, exposed-utils, json-utils, redis-utils, and script-utils-common so the public docs match actual behavior

### Tests

- Add `grpc-utils` tests: `TlsUtilsTests` (11 cases) and `ServerExtensionsTests` (5 cases) covering trust/cert/key validation, `TlsContext.desc()`, `PLAINTEXT_CONTEXT`, graceful-shutdown ordering, and the `require(timeout > 0)` precondition; add `mockk` to `grpc-utils` test dependencies
- Add tests for open coverage gaps across six modules (script-utils-kotlin, recaptcha-utils, jetty-utils, email-utils, and others)
- Drop redundant `runBlocking { }` wrappers from suspend Kotest test bodies in script-utils-kotlin, script-utils-java, and redis-utils

### Static analysis & coverage

- Wire **Detekt** into every subproject via `configureDetekt()` in the root `build.gradle.kts`, with HTML/checkstyle reports per module and auto-detection of optional `config/detekt/detekt.yml` and `config/detekt/baseline.xml`; `make lint` now also runs Detekt
- Add a `config/detekt/detekt.yml` tailored for a multi-module utility library (disables threshold-style rules, excludes test code from `EmptyFunctionBlock` / `VariableNaming`); activate `LongMethod`, suppress `IgnoredReturnValue`, and resolve the type-resolution findings surfaced by the upgrade across eight modules
- Add `make detekt` and `make detekt-baseline` targets
- Add Kover coverage subcommands: `coverage-html`, `coverage-log`, `coverage-verify`, `coverage-open`, `coverage-packages`, `coverage-clean`; `make coverage` now generates both HTML and XML
- Move the `coverage-packages` inline Python to `scripts/coverage-packages.py` with a friendlier missing-report error

### Build & tooling

- Tidy Gradle config: organize `gradle/libs.versions.toml` into functional categories, alphabetize the scripting module includes, and dedupe the Kover excludes comment
- Centralize the Gradle wrapper version and JVM target (`17`) under a `# Toolchain` section in `gradle/libs.versions.toml`, consumed from `build.gradle.kts` and the `Makefile`'s `upgrade-wrapper` target
- Consolidate duplicated string literals in `build.gradle.kts` (`common-utils`, `"17"`, `config/detekt`) into named vals
- Add `make help` for a self-documenting target list, parsed from `## description` comments
- Add fail-fast guards in the `Makefile` when `VERSION` or `GRADLE_VERSION` cannot be parsed, instead of silently passing empty strings to publish/wrapper commands
- Pass `ORG_GRADLE_PROJECT_signingInMemoryKeyId` in `GPG_ENV` so the in-memory signing plugin has all three required properties
- Drop the unused `compile` alias from the `Makefile`

### Dependencies

- Add **Detekt** `2.0.0-alpha.3` (plugin id `dev.detekt`), with the aggregate `detekt` task depending on the per-source-set `detektMain`/`detektTest` tasks so analysis runs with full type resolution
- Upgrade Gradle wrapper 9.5.0 → 9.5.1
- Bump `kotlin` 2.3.21 → 2.4.0
- Bump `kotlinx-coroutines` 1.10.2 → 1.11.0
- Bump `ktor` 3.4.3 → 3.5.0
- Bump `exposed` 1.2.0 → 1.3.0
- Bump `jetty` 12.1.8 → 12.1.10
- Bump `redis` 7.5.0 → 7.5.2
- Bump `logging` 8.0.1 → 8.0.4
- Bump `dropwizard` 4.2.38 → 4.2.39
- Add `h2` 2.3.232 as a test dependency
- Bump project version to 2.9.0

## [2.8.2] - 2026-05-02

- Add Kotlinx Kover coverage with aggregated HTML/XML reports across all modules and Codecov upload from CI
- Add `codecov.yml`; gate patch coverage at 70% and silence no-change PR comments
- Improve repository line coverage from ~52% to ~62%
- Upgrade Gradle wrapper to 9.5.0; add GPG environment validation in the publish targets; deduplicate Dokka configuration
- Move `group` and `version` from `build.gradle.kts` to `gradle.properties`; preserve `-PoverrideVersion` for snapshot/publish targets; update `Makefile` to read `VERSION` from `gradle.properties`
- Switch `overrideVersion` and `signingInMemoryKey` reads to `providers.gradleProperty(...)`
- Hoist Kover excludes to a shared list reused by the root aggregator and per-project filter
- Derive POM SCM and homepage URLs from a shared `scmHost` constant; wrap `pom.name` in a provider for consistency with `description`
- Enable `org.gradle.parallel=true`; pass `--no-parallel` to `versioncheck` (manes plugin is not parallel-safe)
- Replace deprecated `DefaultJedisClientConfig.ssl(Boolean)` with `sslOptions(SslOptions.defaults())` (Jedis 7.4.2+)
- Fix functional bugs in Redis, Python script handling, Banner, SystemMetrics, and Zipkin
- Add `CountDownLatch.await(Duration)` extension in `guava-utils`
- Bump `kover` 0.9.1 → 0.9.8, `grpc` 1.80.0 → 1.81.0, `netty-tcnative` 2.0.76.Final → 2.0.77.Final
- Bump project version to 2.8.2

## [2.8.1] - 2026-04-24

- Bump Kotlin to 2.3.21
- Tighten root `build.gradle.kts`: move shared configuration into `allprojects`, scope Dokka per-subproject, simplify repository declarations
- Promote inter-module dependencies to the `api` configuration so consumers get correct transitive resolution
- Drop the stale Kover target
- Refresh dependency versions in `gradle/libs.versions.toml` and adopt dependency bundles
- Bump project version to 2.8.1

## [2.8.0] - 2026-04-22

- Upgrade Jetty to 12 (EE11); move servlet imports from `org.eclipse.jetty.servlet.*` to `org.eclipse.jetty.ee11.servlet.*` (breaking)
- Migrate to `com.pambrose` Gradle convention plugins (`pambrose.kotlinter`, `pambrose.testing`, `pambrose.stable-versions`) at version 1.0.14
- Rely on `pambrose.testing` defaults for `kotest-runner-junit5` and `kotlin-test`; remove per-module test dependency duplication across all 19 modules
- Consolidate subproject plugin application in root `build.gradle.kts`
- Consolidate Dokka aggregation at the root project
- Promote several dependencies to the `api` configuration where their types leak into public signatures
- Clean up per-module build scripts and remove unused version catalog entries
- Add `RELEASE_NOTES.md` release history index
- Add `.superset/` to `.gitignore`
- Bump project version to 2.8.0

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
