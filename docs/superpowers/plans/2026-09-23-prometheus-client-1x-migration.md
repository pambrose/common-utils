# Prometheus Java Client 1.x Migration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `prometheus-utils` and `service-utils` from the legacy Prometheus Java client (`io.prometheus:simpleclient*` 0.16.0, the last release of the 0.x line, which gets no further releases) to the 1.x client (`io.prometheus:prometheus-metrics-*` 1.9.0), and release the result as **common-utils 5.0.0**.

**Why now:** 0.16.0 still works and has no known open CVE, but it is unmaintained, and every consumer (prometheus-proxy among them) inherits it transitively through `prometheus-utils`, which exposes it with `api(...)`. Every 0.x module in use has a 1.x replacement, checked on Maven Central on 2026-09-23 (see the mapping below).

**Architecture:** Replace the implementation of the existing wrappers (`PrometheusDsl`, `SamplerGaugeCollector`, `SystemMetrics`, `InstrumentedThreadFactory`, `MetricsService`, and the Dropwizard bridging in `AbstractGenericService`) with the 1.x API, keeping the wrapper names and shapes where the new API allows, so consumers change imports and types rather than call patterns. The public API still changes (simpleclient types appear in signatures, and `api(...)` re-exports them), so this is a **major** version and the ABI dumps change deliberately.

**Tech Stack:** Kotlin 2.4 (from the convention plugins), Gradle (Kotlin DSL), Kotest + MockK, kotlinter, detekt, KGP ABI validation (`checkKotlinAbi` / `make abi-update`), Jetty 12 (`ee11`), Dropwizard Metrics 4.2.40.

## Module mapping (verified against Maven Central, 1.9.0)

| 0.16.0 artifact | 1.x replacement | Key classes |
|---|---|---|
| `simpleclient` | `prometheus-metrics-core` | `io.prometheus.metrics.core.metrics.{Counter, Gauge, Histogram, Summary, GaugeWithCallback}`; registry is `io.prometheus.metrics.model.registry.PrometheusRegistry` (in `prometheus-metrics-model`, transitive) |
| `simpleclient_hotspot` | `prometheus-metrics-instrumentation-jvm` | `io.prometheus.metrics.instrumentation.jvm.{JvmMetrics, ProcessMetrics, JvmMemoryMetrics, JvmMemoryPoolAllocationMetrics, JvmGarbageCollectorMetrics, JvmThreadsMetrics, JvmClassLoadingMetrics, JvmRuntimeInfoMetric, ...}` |
| `simpleclient_dropwizard` | `prometheus-metrics-instrumentation-dropwizard` | `io.prometheus.metrics.instrumentation.dropwizard.DropwizardExports` — depends on `io.dropwizard.metrics:metrics-core` **4.2.40**, the version already in the catalog. (`...-dropwizard5` is for Dropwizard 5; not this one.) |
| `simpleclient_servlet_jakarta` | `prometheus-metrics-exporter-servlet-jakarta` | `io.prometheus.metrics.exporter.servlet.jakarta.PrometheusMetricsServlet` |
| — (optional, consumer side) | `prometheus-metrics-simpleclient-bridge` | `io.prometheus.metrics.simpleclient.bridge.SimpleclientCollector` — exposes 0.x collectors through a 1.x registry |

API facts the tasks rely on (checked in the 1.9.0 jars and the official migration guide):

- Builders are `X.builder()...register(registry)` (was `X.build()...register(registry)`); label values are `.labelValues(...)` (was `.labels(...)`).
- `PrometheusRegistry.defaultRegistry` replaces `CollectorRegistry.defaultRegistry`; it has `register`, `unregister`, `clear`, and `scrape` for both `Collector` and `MultiCollector`.
- `DropwizardExports.builder()...register(registry)` returns `void`, so code that must unregister later constructs `DropwizardExports(metricRegistry)` and calls `registry.register(it)` / `registry.unregister(it)`.
- `GaugeWithCallback.builder().name(...).help(...).labelNames(...).callback { cb -> cb.call(value, *labelValues) }.register(registry)` replaces a hand-written `Collector` that reports one sampled gauge.
- A counter named either `x` or `x_total` is exposed as `x_total`.
- JVM metric names changed to put units last, for example `jvm_memory_bytes_committed` becomes `jvm_memory_committed_bytes` and `jvm_info` becomes `jvm_runtime_info`. This is the change consumers' dashboards will see.

## Decisions (2026-09-25)

- **Release:** folds into the unreleased 5.0.0 already on `master` (`gradle.properties` is at 5.0.0 and `CHANGELOG.md`
  has a 5.0.0 entry). Extend that entry; do not add a new version.
- **`SamplerGaugeCollector`:** implements the 1.x `io.prometheus.metrics.model.registry.Collector` interface and
  registers itself, delegating `collect()` to an internal, unregistered `GaugeWithCallback`. It stays a type a
  consumer can pass to `registry.unregister(it)`. The hidden binary-compatibility constructor is dropped (major
  release).
- **`SystemMetrics`:** keeps the six flags and adds `enableBufferPoolExports`, `enableCompilationExports` and
  `enableNativeMemoryExports`, all defaulting to `false`. The memory-pools flag also registers
  `JvmMemoryPoolAllocationMetrics`.
- **`MetricsService`:** gains an optional `registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry`.
  `AbstractGenericService` keeps using the default registry for its Dropwizard exports; `MetricsConfig` is unchanged.
- Verified in the 1.9.0 sources: 1.9.0 is the newest 1.x; duplicate names throw `IllegalArgumentException`;
  `DropwizardExports(MetricRegistry)` is a public constructor; names ending in `_created` are accepted (collisions
  are checked at registration). 1.x builders have no `namespace()` / `subsystem()`, a consumer-visible break for the
  changelog.

## Global Constraints

- Target versions: every `prometheus-metrics-*` artifact at the same version (**1.9.0**, or the newest 1.x at implementation time). One catalog version entry drives them all.
- **Major version:** release as **5.0.0**. Public signatures change (see the tasks); run `make abi-update` on macOS and commit the regenerated `prometheus-utils/api/*.api` and `service-utils/api/*.api` dumps with the change.
- Keep wrapper **names and call shapes** where the 1.x API allows: `PrometheusDsl.counter { }`, `SamplerGaugeCollector(name, help, labelNames, labelValues, registry) { value }`, `SystemMetrics.initialize(enable...)`, `InstrumentedThreadFactory(delegate, name, help, registry)`, `MetricsService(port, path, host)`. Only the registry and builder types change.
- Keep existing behavior guarantees and their tests: `SamplerGaugeCollector`'s eager label-size validation and NaN-on-throwing-sampler; `SystemMetrics`' idempotent, per-registry registration and its skip-with-warning on a duplicate name; `InstrumentedThreadFactory`'s all-or-nothing registration and created/running/terminated ordering; `AbstractGenericService` exporting Dropwizard metrics only while running.
- Tests stay Kotest `StringSpec` with `init {}`, MockK where useful.
- Record the exposition before and after the change and review the diff; that diff drives the release notes' upgrade section.
- Keep both `CHANGELOG.md` and `RELEASE_NOTES.md`, each in its own style.
- Do NOT commit, push, or open a PR until instructed (repo and maintainer policy).

## File Structure

- **`gradle/libs.versions.toml`** (modify) — replace the `prometheus = "0.16.0"` entry and the `prometheus-core/-dropwizard/-hotspot/-servlet` libraries with `prometheus-metrics-*` 1.x entries; update the `prometheus-service` bundle.
- **`prometheus-utils/build.gradle.kts`** (modify) — `api(...)` the 1.x core and JVM instrumentation instead of `simpleclient` and `simpleclient_hotspot`.
- **`prometheus-utils/src/main/kotlin/com/pambrose/common/dsl/PrometheusDsl.kt`** (modify)
- **`prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt`** (modify)
- **`prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SystemMetrics.kt`** (modify)
- **`prometheus-utils/src/main/kotlin/com/pambrose/common/concurrent/InstrumentedThreadFactory.kt`** (modify)
- **`service-utils/src/main/kotlin/com/pambrose/common/service/MetricsService.kt`** (modify)
- **`service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt`** (modify)
- **Tests** (modify): `prometheus-utils/src/test/.../{PrometheusDslTests, SamplerGaugeCollectorTests, SystemMetricsTests, InstrumentedThreadFactoryTests}.kt`, `service-utils/src/test/.../GenericServiceTests.kt`.
- **`prometheus-utils/api/`, `service-utils/api/`** (regenerate) — ABI dumps.
- **`CHANGELOG.md`, `RELEASE_NOTES.md`, `README.md`, `llms.txt`** (modify) — 5.0.0 entry with a migration section.

## Tasks

### Task 0: Baseline the exposition

- [ ] Write a small throwaway harness (a test or a `main` in scratch, not committed) that builds a registry the way a typical consumer does: `SystemMetrics.initialize` with every flag on, a `PrometheusDsl` counter, gauge, histogram, and summary with labels, a `SamplerGaugeCollector`, an `InstrumentedThreadFactory`, and `DropwizardExports` over a registry with a timer, meter, and gauge. Serve it through `MetricsService` and save the `/metrics` output as `build/exposition-0.16.txt`.
- [ ] Keep the harness; Task 8 reruns it on 1.x.

### Task 1: Dependencies

- [ ] In `libs.versions.toml`, replace `prometheus = "0.16.0"` with `prometheus = "1.9.0"` and the four `simpleclient*` library entries with `prometheus-metrics-core`, `prometheus-metrics-instrumentation-jvm`, `prometheus-metrics-instrumentation-dropwizard`, and `prometheus-metrics-exporter-servlet-jakarta`. Update the `prometheus-service` bundle.
- [ ] In `prometheus-utils/build.gradle.kts`, `api` the core and JVM instrumentation artifacts. `service-utils` keeps getting the servlet exporter and Dropwizard instrumentation through the bundle.
- [ ] Confirm with `./gradlew :prometheus-utils:dependencies --configuration runtimeClasspath` and `:service-utils:...` that no `io.prometheus:simpleclient*` artifact remains.
- [ ] Expect compile errors in every file below; the following tasks fix them.

### Task 2: `PrometheusDsl`

- [ ] Change each function's `registry` parameter to `PrometheusRegistry` (default `PrometheusRegistry.defaultRegistry`) and its `block` receiver to the 1.x builder (`Counter.Builder`, etc., from `io.prometheus.metrics.core.metrics`). Build with `X.builder().apply(block).register(registry)`.
- [ ] Update `PrometheusDslTests`: registration against a fresh `PrometheusRegistry`, labeled children via `labelValues`, and exposition through `registry.scrape()` rather than `CollectorRegistry` sample lookups. Keep one test per metric type.
- [ ] Note for the changelog: builder methods consumers call inside the block may differ in 1.x, for example `labels` becomes `labelNames` on builders and `labelValues` on metrics.

### Task 3: `SamplerGaugeCollector`

- [ ] Reimplement on `GaugeWithCallback`, keeping the class and constructor shape (`registry` becomes `PrometheusRegistry`). Keep the eager `labelNames.size == labelValues.size` `require`, and keep a throwing sampler reporting `NaN` with a WARN instead of failing the scrape. Wrap the callback body in the same `runCatching`.
- [ ] Decide whether the class stays a type consumers can hold or `unregister`. If it wraps a `GaugeWithCallback`, expose what `unregister` needs, and test register, sample, a throwing sampler, and unregister.
- [ ] Update `SamplerGaugeCollectorTests`, including the existing validation test.

### Task 4: `SystemMetrics`

- [ ] Map each flag to its 1.x metric set:
  - `enableStandardExports` → `ProcessMetrics`
  - `enableMemoryPoolsExports` → `JvmMemoryMetrics` (plus `JvmMemoryPoolAllocationMetrics` if the allocation series are wanted)
  - `enableGarbageCollectorExports` → `JvmGarbageCollectorMetrics`
  - `enableThreadExports` → `JvmThreadsMetrics`
  - `enableClassLoadingExports` → `JvmClassLoadingMetrics`
  - `enableVersionInfoExports` → `JvmRuntimeInfoMetric`
  Consider adding `JvmBufferPoolMetrics`, `JvmCompilationMetrics`, and `JvmNativeMemoryMetrics` behind new flags, defaulting off.
- [ ] Keep per-registry, idempotent registration (a `WeakHashMap<PrometheusRegistry, MutableSet<String>>`), and keep an already-taken name skipped with a warning. Confirm which exception 1.x throws on a duplicate name and match it, instead of assuming `IllegalArgumentException`.
- [ ] Update `SystemMetricsTests`: idempotence, per-registry tracking, the duplicate-name skip, and the expected metric names for each flag (these become the documented renames).

### Task 5: `InstrumentedThreadFactory`

- [ ] Switch to 1.x `Counter`/`Gauge` through `PrometheusDsl`, and `registry` to `PrometheusRegistry`. Keep all-or-nothing registration (`registry.unregister` the ones already registered on failure) and the created/running/terminated ordering.
- [ ] The counters `${name}_threads_created` and `_terminated` are exposed with `_total` in 1.x. Check the Task 0 baseline to see whether 0.16.0 already exposed them that way, and record any difference.
- [ ] Update `InstrumentedThreadFactoryTests`.

### Task 6: `MetricsService`

- [ ] Replace `io.prometheus.client.servlet.jakarta.exporter.MetricsServlet` with `io.prometheus.metrics.exporter.servlet.jakarta.PrometheusMetricsServlet` in the Jetty `ServletHolder`. Consider an optional `registry: PrometheusRegistry` parameter, defaulting to `defaultRegistry`, so a consumer can serve a non-default registry.
- [ ] Check the response's `Content-Type` and body format against the baseline. Prometheus scrapes negotiate the format through `Accept`, and the 1.x servlet supports OpenMetrics and text. Confirm a plain `curl` still gets the text format.
- [ ] Update the service tests that fetch `/metrics`.

### Task 7: `AbstractGenericService` Dropwizard bridging

- [ ] Replace `io.prometheus.client.dropwizard.DropwizardExports` with the 1.x class. Keep register-on-start and unregister-on-stop by constructing `DropwizardExports(metricRegistry)` and calling `PrometheusRegistry.defaultRegistry.register(it)` / `.unregister(it)`. The builder's `register()` returns nothing to unregister.
- [ ] Update `GenericServiceTests` that assert on Dropwizard metrics being exported only while running.
- [ ] Compare Dropwizard metric names and label shapes against the baseline, since 1.x's Dropwizard mapping may name timers and meters differently.

### Task 8: Exposition diff, ABI, and verification

- [ ] Rerun the Task 0 harness on 1.x and save `build/exposition-1.x.txt`. Diff it against the baseline and classify every difference as a renamed series, a removed series (for example `_created`), a new series, a changed type or help text, or changed labels. This list is the core of the release notes' migration section.
- [ ] `make abi-update` on macOS; review the `prometheus-utils` and `service-utils` `.api` diffs. They should contain only the intended type changes.
- [ ] `make tests` (`./gradlew --rerun-tasks check`: lint, detekt, tests, ABI check).

### Task 9: Release notes and docs (5.0.0)

- [ ] `CHANGELOG.md` and `RELEASE_NOTES.md`: a 5.0.0 entry that leads with the breaking change and a **Migrating from 4.x** section covering:
  - the type changes (`CollectorRegistry` → `PrometheusRegistry`, builder receivers)
  - the metric renames from Task 8
  - the servlet class
  - an optional incremental path: a consumer with its own 0.x instrumentation can add `prometheus-metrics-simpleclient-bridge` and register `SimpleclientCollector` to keep exposing it through the 1.x registry while it migrates
- [ ] `README.md` and `llms.txt`: update the `prometheus-utils` and `service-utils` descriptions and any code samples.

### Task 10: Consumer follow-up (prometheus-proxy, separate repo and PR)

Not part of this repo's change; record it so the release is usable:

- [ ] Bump `common-utils` to 5.0.0 in prometheus-proxy's `libs.versions.toml`, and replace its direct `io.prometheus:simpleclient` dependency.
- [ ] Port its three direct `Histogram` uses (`ProxyMetrics`, `AgentMetrics`, `Agent`'s timer) and its `PrometheusDsl` / `SamplerGaugeCollector` call sites. Replace test uses of `CollectorRegistry.defaultRegistry.clear()` with `PrometheusRegistry.defaultRegistry.clear()`.
- [ ] Diff its `/metrics` output before and after. Update `docs/metrics-and-grafana.md`, the website's monitoring page, and the `grafana/` dashboards for any renamed series, especially the JVM ones. Add a "Before you upgrade" entry to its release notes.
