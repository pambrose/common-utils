# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-module Kotlin/Java utility library (19 modules, plus the `common-utils-bom` platform) providing common functionality for various frameworks and use
cases. Published on Maven Central.

Three modules are Kotlin Multiplatform (KMP): **core-utils**, **json-utils**, and **ktor-client-utils**. They target
JVM, JS, wasmJs, and Native (iOS/macOS/tvOS/watchOS/Linux/Windows). Portable code lives in `src/commonMain`, JVM-bound
code in `src/jvmMain` (the JVM artifact keeps the full pre-KMP API). All other modules are plain Kotlin/JVM. Most depend
on core-utils' jvm variant via ordinary project dependencies; jetty-utils, dropwizard-utils, prometheus-utils,
exposed-utils and ktor-server-utils do not (they declare kotlin-logging directly where they log), and neither does
ktor-client-utils. Don't add core-utils to a module for one helper: every consumer inherits its whole `api` surface.

## Common Development Commands

Run `make help` for a self-documenting list of every target.

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

- `./gradlew formatKotlin` - Auto-format code. Use the aggregate task: KMP modules name their kotlinter
  tasks per source set (`formatKotlinCommonMain`, `formatKotlinJvmTest`, …), so
  `formatKotlinMain formatKotlinTest` silently skips core-utils, json-utils and ktor-client-utils.
- `make mutation` - PIT mutation testing (`./gradlew pitest`) for the modules in the root script's
  `mutationModuleNames` (currently exposed-utils and guava-utils). On demand only, not part of `check`; guava-utils
  alone takes a couple of minutes. Reports: `<module>/build/reports/pitest/index.html`.

### API compatibility

- Every module has KGP's built-in ABI validation enabled (`abiValidation()`, experimental). `check` runs
  `checkKotlinAbi` against the dumps committed under `<module>/api/` (`<module>.api` for the JVM, plus
  `<module>.klib.api` for the KMP modules' other targets).
- After an intended public API change, run `make abi-update` (`./gradlew updateKotlinAbi`) and commit the
  changed dumps with the change. Do it on macOS: a host that cannot compile some native target copies that
  target's declarations from the committed dump instead of regenerating them.
- The DSL is the 2.4 one (`abiValidation()` is a function; 2.2's `enabled` property is gone). Because the
  compiler version comes from the convention plugins (see below), a convention-plugin bump can change it.

### Publishing

- `make publish-snapshot` - Publish `-SNAPSHOT` to Maven Central (requires `GPG_SIGNING_KEY_ID` env var and
  `gradle-signing-password` keychain entry)
- `make publish-maven-central` - Publish and release to Maven Central (same prerequisites)

## Architecture

### Build Configuration

The root `build.gradle.kts` applies a shared set of plugins to every subproject and defines several inline configuration functions, including:

- `configureKotlinJvm()` - JVM 17 target, experimental opt-ins (kotlin/jvm modules)
- `configureKotlinMultiplatform()` - full KMP target list, opt-ins, JUnit Platform for `jvmTest` (modules listed in `kmpModuleNames`)
- `configurePublishing(isKmp)` - Maven publication setup: vanniktech maven-publish with the `KotlinJvm` or `KotlinMultiplatform` platform, POM metadata, and `signAllPublications()` applied **only when a `signingInMemoryKey` is present**. Signing unconditionally breaks `make publish-local`, which publishes to the local Maven repo with no signatory configured; a `doFirst` guard on every `*MavenCentral*` task fails the build with an explanatory message when the key is missing, so an unsigned Central upload still cannot happen
- `configureDokka()` - per-module Dokka HTML configuration (homepage link and footer), shared with the root `dokka` block
- `configureBom()` - turns `common-utils-bom` into a `java-platform` with a constraint on every other module (and
  on the KMP modules' `-jvm` artifacts), published through the same POM setup; it skips all the Kotlin, lint,
  coverage and docs configuration
- `configureVersions()` - pre-release filtering for the ben-manes `dependencyUpdates` task
- `configurePitest()` - applies `info.solidsoft.pitest` and the Kotest PIT plugin to the modules in
  `mutationModuleNames`. `targetTests` is widened to `com.pambrose.*` because some specs live outside
  `com.pambrose.common`

The `kmpModuleNames` set in the root build script decides which modules build with `kotlin("multiplatform")`; everything else gets `kotlin("jvm")`.

Common behavior for testing and linting on the **JVM modules** is provided by [`pambrose-gradle-plugins`](https://github.com/pambrose/pambrose-gradle-plugins) convention plugins:

- `com.pambrose.testing` - JUnit Platform, `kotest-runner-junit5` and `kotlin-test` as default `testImplementation`, logback-classic on test runtime
- `com.pambrose.kotlinter` - Kotlinter lint/format tasks

The KMP modules apply the raw `org.jmailen.kotlinter` plugin instead (same reporters, configured inline in the root script) and declare their kotest/logback test dependencies explicitly in their own `build.gradle.kts` (versions pinned in the catalog to match the convention plugin).

Dependency-update reporting uses the `io.github.ben-manes.versions` plugin (the id it publishes under as of 0.57.0; earlier releases used `com.github.ben-manes.versions`), configured by the inline `configureVersions()`: its `isNonStable` filter rejects a pre-release candidate only when the current version is stable, so dependencies intentionally tracked on a pre-release line still surface updates.

The report lists `org.junit.platform:junit-platform-launcher` as "contributed by a plugin into the
'testRuntimeOnly' configuration". No build script declares it: the pitest plugin, applied to the
`mutationModuleNames` modules, adds it from a `withDependencies` hook at the JUnit Platform version already on the
test classpath, which Kotest's runner sets (1.13.4 with Kotest 6.2.5). It follows Kotest, so don't pin it
separately; its 1.14.x/6.x candidates become relevant once Kotest moves.

Detekt is applied directly in the root `build.gradle.kts` via `configureDetekt()`. The aggregate `detekt` task depends on every per-source-set detekt task by type (`detektMain`/`detektTest` on JVM modules; `detektJvmMain`, `detektMetadataCommonMain`, etc. on KMP modules), so analysis runs with full type resolution. Optional shared config lives at `config/detekt/detekt.yml` and a shared suppression baseline at `config/detekt/baseline.xml` (both auto-detected if present).

Version catalog in `gradle/libs.versions.toml` manages all dependency versions.

The catalog's `kotlin` version governs the `kotlin-scripting-*` artifacts, `kotlin-reflect` and the Kotlin plugin
ids. It was held at 2.4.10 until `KotlinScript` stopped binding each variable as its own engine binding: the JSR-223
engine turns every binding into a script property typed by the value's runtime class, and 2.4.20 cannot declare one
for a generic Java class such as `ArrayList`. Variables now live in a single non-generic `ScriptVariables` holder,
so `:script-utils-kotlin:test` passes on 2.4.20. Re-run that suite on every Kotlin bump all the same.

It does **not** govern the compiler: `pambrose-gradle-plugins`
pulls a `kotlin-gradle-plugin` of its own, which wins on the buildscript classpath, so the project can be
compiled by a newer Kotlin than the catalog names. Run `./gradlew buildEnvironment` to see the version
actually in use rather than assuming the catalog value — and be aware that a convention-plugin bump can
therefore change the compiler, and the resolved JS toolchain npm versions, without touching the catalog. Keep the
catalog's `kotlin` in step with that compiler: core-utils exports `kotlin-reflect` as `api`, so a lag would publish a
POM pairing the compiler's stdlib with an older reflect.

Dependabot (`.github/dependabot.yml`) opens weekly version-update PRs for the Gradle ecosystem (catalog
libraries and plugins, plus the Gradle wrapper) and for GitHub Actions. Kotlin updates get their own group, since
they move the scripting artifacts, `kotlin-reflect` and the plugin ids together. One `ignore` rule:
`io.netty:netty-tcnative-boringssl-static` gets no automatic PR at all, because it must match the version grpc-java
pins for the grpc release in use (bump it by hand alongside grpc, and note the rule suppresses security-update PRs
too, though alerts still fire). The `kotlinx-datetime` `-0.6.x-compat` suffix needs no ignore rule —
Dependabot only proposes candidates carrying the same suffix. Nothing in this repo needs the compat line
(exposed-utils uses `exposed-jodatime`, and core-utils' API uses `kotlin.time.Instant`), but core-utils exports
kotlinx-datetime as `api`, so consumers inherit it: keep the compat artifacts so a consumer that also uses a
library linked against the deprecated `kotlinx.datetime.Instant` (such as `exposed-kotlin-datetime`) keeps
working. Dependabot updates the wrapper files but not the catalog's `gradle-wrapper` entry that
`make upgrade-wrapper` reads, so that entry goes stale after a Dependabot wrapper bump.

`gradle-wrapper.properties` carries a `distributionSha256Sum`, so the wrapper verifies every distribution it
downloads; `make upgrade-wrapper` fetches the new release's published checksum and passes it to both wrapper runs,
and a wrapper bump from any other route must update it too. `gradlew` and `gradlew.bat` are text in
`.gitattributes` (not `binary`), so changes to them show up in diffs and PR reviews. The workflows pin every
action to a commit SHA with the release in a trailing comment, and every job sets `timeout-minutes`.

`netty-tcnative-boringssl-static`'s main jar holds no native code: its POM pulls the per-platform jars in as
classifier dependencies on itself, which Gradle drops. `grpc-utils/build.gradle.kts` therefore lists the classifier
jars explicitly, and `OpenSslTests` fails if OpenSSL does not load. When a tcnative bump changes the classifiers its
POM lists, update that list to match.

### Experimental Kotlin Features

These opt-ins are enabled globally:

- `kotlin.contracts.ExperimentalContracts`
- `kotlinx.coroutines.ExperimentalCoroutinesApi`
- `kotlin.time.ExperimentalTime`
- `kotlin.concurrent.atomics.ExperimentalAtomicApi`
- `kotlinx.serialization.ExperimentalSerializationApi`

Additionally, the experimental `-Xcollection-literals` compiler flag is enabled on every compilation
(JVM and KMP, main and test) so `[...]` collection-literal syntax can be used in place of `listOf(...)` /
`mutableListOf(...)`. It does not reach the Gradle build scripts (`*.gradle.kts`), which must keep using
`listOf(...)`. The flag is experimental in Kotlin 2.4 and may need revisiting on a future Kotlin
upgrade (if the syntax changes or the feature stabilizes and the flag can be dropped).

The `-Xreturn-value-checker=check` flag is applied to **production compilations only** — `compileKotlin` on
the JVM modules, and each target's `main` compilation on the KMP ones — so discarding a non-Unit result is
reported as a warning. Test sources are excluded deliberately: Kotest's assertion DSL returns its receiver
and tests discard it, which would yield nothing but false positives. When a result really is meant to be
ignored, consume it with `val _ = ...` rather than turning the flag off.

Atomics come from `kotlin.concurrent.atomics` (`AtomicInt`, `AtomicLong`, `AtomicBoolean`,
`AtomicReference`) everywhere — `src` and `test`, JVM-only modules included. Use the
`load()`/`store()`/`compareAndSet()`/`incrementAndFetch()` idiom rather than the Java
`get()`/`set()`/`incrementAndGet()` names, and note that `AtomicReference` has no no-arg constructor
(pass an explicit `null`). Do not reintroduce `java.util.concurrent.atomic` imports; on the JVM the Kotlin
types are typealiases to the Java ones, so there is nothing to gain from the Java API.

### Kotlin Multiplatform Notes

- `kotlin-js-store/` holds the Node/Yarn lockfiles for the JS and wasmJs toolchains; commit changes to it
  (run `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock` when JS dependencies change).
- Vulnerable transitive npm packages are pinned through the `yarnResolutions` map in the root build script,
  which feeds `YarnRootExtension.resolution(...)`. Gotchas when changing it:
  - The map is **not** a tracked input to `rootPackageJson`, so that task stays `UP-TO-DATE` and reuses a
    stale `build/js/package.json`. The upgrade tasks then re-resolve against the *old* resolutions and report
    `BUILD SUCCESSFUL` while changing nothing. Always `rm -f build/js/package.json build/wasm/package.json`
    first, then run the two upgrade tasks, then confirm the new pin actually landed — check the `resolutions`
    block of the regenerated `build/js/package.json` and diff `kotlin-js-store/`. An empty lockfile diff
    means the pin did not take, not that nothing needed fixing.
  - A resolution only rewrites the lockfile when it changes the resolved version. yarn v1 will not re-resolve
    an entry that still satisfies its range, so bumping a package already inside its requested range (the
    usual shape of a CVE fix) requires the explicit pin — `kotlinUpgradeYarnLock` alone will not do it.
  - The map is shared by both toolchains, so a pin for a package only one of them uses still adds a bare
    pin-only entry (plus its transitives) to the other's lockfile. `diff`, `serialize-javascript`,
    `brace-expansion`, and `js-yaml` are all pin-only entries in `kotlin-js-store/wasm/yarn.lock` — their
    presence there does not mean the wasm toolchain actually pulls them in.
- Verify JS/wasm toolchain changes with `./gradlew jsNodeTest --rerun wasmJsNodeTest --rerun`; without
  `--rerun` these tasks report `UP-TO-DATE` and verify nothing.
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS` repositories mode with ivy repositories for the Node.js,
  Yarn, and Binaryen distributions; the root build script unsets every toolchain env spec's `downloadBaseUrl`
  (root and per-subproject) so the Kotlin plugin never registers project-level repositories.
- The mixed common/JVM files (`StringExtensions`, `MiscExtensions`, `MiscFuncs` in core-utils) are split across
  `commonMain` and `jvmMain` using `@file:JvmName` + `@file:JvmMultifileClass` so the compiled JVM facade classes
  (and therefore the published JVM ABI) are unchanged.
- watchOS/tvOS simulator test tasks are disabled (host Xcode lacks those simulator runtimes); Apple coverage
  comes from macOS and iOS simulator test tasks.
- Put specs for `commonMain` code in `commonTest`, so they run on every platform, not only in `jvmTest`. The
  platforms really do differ in places (JS prints `1.0` as `1`, parses numbers with unary `+`, and its
  `substring` clamps bad indices instead of throwing). core-utils' `commonTest` has a `testPlatform` value
  (`expect` in `TestPlatform.kt`, `actual` in `jvmTest`, `jsTest`, `wasmJsTest` and `nativeTest`) for pinning
  such results per platform. When a spec moves, the JVM-only remainder keeps the `…JvmTests` name, because a
  class name cannot appear in both `commonTest` and `jvmTest`.
- CI (`.github/workflows/test.yml`) runs the native tests on three hosts. The `test` job (ubuntu) runs
  `linuxX64Test` through `build`. `native-apple` (macos-latest) runs `macosArm64Test iosSimulatorArm64Test`.
  `native-windows` runs `mingwX64Test`. `iosX64Test` is skipped on arm64 hosts, and `linuxArm64` has no test
  task at all.
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
- ktor-client-utils declares no client engine, so its client-creating `commonTest` specs rely on one supplied
  by the test source sets: CIO is a `jvmTest` and `nativeTest` dependency, and js/wasmJs fall back to the Js
  engine bundled in ktor-client-core. Kotlin/Native has no such fallback, so dropping CIO from `nativeTest`
  makes those specs fail there with "Failed to find HTTP client engine implementation".
- service-utils tests configure admin and metrics servers with port 0 and read the port the OS chose from
  the `internal` `boundPort` test seam on `MetricsService`, `ServletService` and `KtorServletService`.
  Don't pick a free port by opening and closing a `ServerSocket(0)`: another test JVM
  (`org.gradle.parallel=true`) can take it before the server binds. `boundPort` is set when the server
  starts and keeps its value after it stops, so a test can check the port was released.
- Any server those tests send requests to binds to `127.0.0.1` (`LOOPBACK` in `ServiceTestSupport.kt`),
  never to every interface. On macOS, `SO_REUSEADDR` lets another app (seen with a local desktop app) bind
  `127.0.0.1` to the same port as a wildcard listener. That more specific listener then takes the test's
  loopback connections, which fail with `HTTP/1.1 header parser received no bytes`. For the same reason, a
  socket that holds a port or checks that one is free (`occupiedLoopbackPort()`, `shouldBeReleased()`) must
  bind the same address as the server.
- `script-utils-kotlin` runs the Kotlin compiler **in-process** (the JSR-223 engine compiles every
  snippet), so its test task sets `maxHeapSize = "2g"` in the module's own `build.gradle.kts`; nothing
  else sets a test heap, so every other module uses Gradle's 512m default. Leave that setting in place.
  When that worker runs short of heap the failure does not look like an OOM — it surfaces as
  `IllegalStateException: Could not read file: ...kotlin-stdlib-<ver>.jar!/...class` wrapped in a
  `FileAnalysisException`, with the `OutOfMemoryError` several `Caused by` levels down. Note also that
  `maxHeapSize` is not part of Gradle's build-cache key, so a cached result can be served straight
  across a heap change; use `--rerun-tasks` when verifying anything heap-related.

### Coverage

Kover verification rules live in the root `build.gradle.kts`:

- **Project-wide rule:** 90% line and 80% branch across the aggregated report.
- **Per-package rule:** 90% line in every package.

They are floors, not targets, and this is the one place the current figures are kept (the root build script and
`codecov.yml` point here). As of 4.1.0 the project sits at 99.8% line and 97.2% branch, and the weakest package
by line coverage is `script` at 98.5%.

- **Why a per-package rule:** without it, any module smaller than about 200 lines (all but core-utils,
  service-utils, ktor-server-utils and guava-utils) could lose all its coverage without failing the aggregate.
- **Why no per-package branch floor:** `response` has only four branches, two of them compiler-generated and
  unreachable, so it sits at 50%.
- Raise any floor only after lifting the weakest packages, or the next unrelated PR goes red.
- Packages span modules (`com.pambrose.common.dsl` lives in seven), so the per-package rule is not a
  per-module guarantee.

`koverVerify` runs as part of `check`, so `./gradlew build` and CI enforce the floors. `make build` passes
`-x koverVerify` on purpose: that target is documented as building without tests, and `koverVerify` depends on
the instrumented test tasks.

Tables, both sorted weakest branch coverage first:

- `make coverage-packages`: line and branch coverage per package.
- `make coverage-modules`: the same per module. It maps each report source file back to its module.

`docs/TEST_COVERAGE_REVIEW_2026-09-16.md` tracked the known test gaps (`TC-001`…`TC-083`); all 83 were fixed
before the 4.1.0 release (#187–#199). If a later review adds items, update its tracker as they are fixed, as with
the code review doc.

Codecov (`codecov.yml`):

- It gets the same Kover XML report.
- Its patch target is 90%.
- It defines one component per module, so its `component_management` list must be kept in step with
  `settings.gradle.kts` when a module is added or removed.

### Package Structure

All modules use: `com.pambrose.common.*`

### Version Management

- Project version and group live in `gradle.properties`; override the version at publish time with
  `-PoverrideVersion=...` (used by the Makefile snapshot/publish targets).
- Versioning policy (the practice since 4.0.0): a minor release stays binary compatible, but may carry
  source-breaking changes, such as a return type becoming nullable or a narrower exception type, each listed
  under "Changed" in the CHANGELOG. A signature is not removed or changed in the ABI dump without a major
  release: keep the old one as a `@Deprecated(level = DeprecationLevel.HIDDEN)` overload, as
  `GrpcDsl.server` and `SamplerGaugeCollector` do. A declaration that could never be called successfully
  (for example one that always threw) may be dropped in a minor release, with a CHANGELOG note.
