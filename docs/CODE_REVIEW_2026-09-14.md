# Code Review — common-utils

| | |
|---|---|
| **Review date** | 2026-09-14 |
| **Project version** | 3.2.3 |
| **Commit reviewed** | `bb4cea6` (master) |
| **Scope** | All 20 modules (main + test sources, module build files), root build, CI workflows, project docs |
| **Method** | Seven parallel reviewers, one per module group plus build/CI/docs. Each finding was traced in the code and tests. Findings that depend on outside behavior (third-party libraries, wire formats, platform APIs) were checked against the dependency sources in `~/.gradle/caches`, vendor docs, or `kotlinc` probes. The high and medium findings were spot-checked again before being written up. |
| **Supersedes** | `docs/CODE_REVIEW.md` (2026-03-01, v2.6.3) and root `code-review.md` (May 2026), whose items were largely fixed in #118–#136 (see [CR-139](#cr-139)) |

## How to track progress

- Every action item has a stable ID (`CR-001` … `CR-139`). **Never renumber.** If an item is dropped, close it as won't-fix instead of deleting it.
- **Status lives only in the [Tracker](#tracker) checklist.** It is the single source of truth.
  - `- [ ]` open
  - `- [x]` done: append `— fixed in #NNN` (PR) or a commit SHA
  - `- [x]` won't fix: append `— won't fix: <reason>`
  - `- [ ]` in progress: append `— in progress (#NNN)` and leave the box unchecked
- The [Details](#details) section is reference material: problem, evidence and suggested fix. It carries no status, so it never goes stale.
- "Carried over" means the item was also raised in the May 2026 review and is still present.
- Quick progress counts:

  ```bash
  grep -c '^- \[x\] \*\*CR-' docs/CODE_REVIEW_2026-09-14.md   # closed
  grep -c '^- \[ \] \*\*CR-' docs/CODE_REVIEW_2026-09-14.md   # open
  grep    '^- \[ \] \*\*CR-.*`HIGH`' docs/CODE_REVIEW_2026-09-14.md   # open high-severity
  ```

## Summary

The library is in good shape. The May 2026 fixes largely held: the `GenericService`/`GenericKtorService` duplication is gone, the shutdown-hook leak is fixed, `upsert` now delegates to Exposed, and the eval results are correctly nullable. Tests are hermetic and follow repo conventions. There are no critical findings.

**Severity counts:** 3 high · 39 medium · 97 low (139 items).

The three high-severity items:

1. **[CR-001](#cr-001)** `toObjectSecure` turns its class allow-list off by default. The README still presents it as safe for untrusted data.
2. **[CR-038](#cr-038)** The Ktor admin `/healthcheck` endpoint throws an NPE on every request. `Route.servlet` never calls `init(ServletConfig)`. Two reviewers found this independently.
3. **[CR-124](#cr-124)** service-utils declares sibling modules as `implementation`, but its public API exposes their types. Consumers can't compile subclasses of `GenericService`/`GenericKtorService` without adding those modules themselves. Confirmed in the published 3.2.3 POM.

Recurring themes in the medium items:

- **Silent wrong results at edge values:** `Long.length` near 10^18, `toISO8601` at round minutes, two-digit years, JSON `null` read as `"null"`, SQL NULL in `ResultRow.get`.
- **Documented failure paths that never trigger:** the Redis `withRedis` null path, `waitUntil(Duration.ZERO)`.
- **Script engine pooling:** instances lost on cancellation, state leaking between borrowers, unbounded REPL history.
- **Doc drift:** README snippets that don't compile, and llms.txt describing modules wrongly.

## Suggested PR batches

These follow the per-area batching used for #118–#136. The order within the list is roughly by impact.

| Batch | Items |
|---|---|
| 1. Publishing scopes | CR-124, CR-125, CR-127 |
| 2. Servlet bridge + service endpoints | CR-038, CR-039, CR-041, CR-042, CR-045, CR-046, CR-047, CR-048 |
| 3. core-utils security + correctness | CR-001 – CR-011 |
| 4. script-utils pooling/binding | CR-081 – CR-098 |
| 5. guava-utils concurrency | CR-064 – CR-078 |
| 6. json-utils accessors | CR-026 – CR-034 |
| 7. redis / exposed / grpc / email / recaptcha | CR-099 – CR-123 |
| 8. prometheus / dropwizard / jetty / zipkin | CR-056 – CR-063, CR-079, CR-080 |
| 9. CI hardening | CR-132 – CR-135 |
| 10. Docs + remaining low items | everything else |

---

## Tracker

### core-utils
- [x] **CR-001** `HIGH` · security — `toObjectSecure` disables its allow-list by default; no `ObjectInputFilter` ([details](#cr-001)) — fixed in #174
- [x] **CR-002** `MEDIUM` · bug — Blocklist prefix `java.lang.Runtime` rejects every `RuntimeException` subclass ([details](#cr-002)) — fixed in #175
- [x] **CR-003** `MEDIUM` · bug — `maskUrlCredentials` fabricates credentials/host when `@` is in path or query ([details](#cr-003)) — fixed in #175
- [x] **CR-004** `MEDIUM` · bug — Glob→regex (`toPattern`/`asRegex`) escapes only `.` ([details](#cr-004)) — fixed in #175
- [x] **CR-005** `MEDIUM` · bug — `Long.length` off by one for values near 10^16–10^18 ([details](#cr-005)) — fixed in #175
- [x] **CR-006** `MEDIUM` · bug — `toISO8601` drops seconds on round minutes ([details](#cr-006)) — fixed in #175
- [x] **CR-007** `MEDIUM` · bug — Two-digit years computed as `year - 2000`; `versionDesc` fallback prints `12/31/-31` ([details](#cr-007)) — fixed in #175
- [x] **CR-008** `MEDIUM` · bug — `singleSetReference` compares by reference; non-null `initValue` makes it unsettable ([details](#cr-008)) — fixed in #175
- [x] **CR-009** `MEDIUM` · bug — `typeParameterCount` counts the superclass's type args (breaks script-utils `add()`) ([details](#cr-009)) — fixed in #175
- [x] **CR-010** `MEDIUM` · bug — `ContentRoot.file(path)` ignores the root ([details](#cr-010)) — fixed in #175
- [x] **CR-011** `MEDIUM` · bug — `GitLabFile` targets the HTML `/-/blob/` page instead of `/-/raw/` ([details](#cr-011)) — fixed in #175
- [x] **CR-012** `LOW` · docs — core-utils README snippets don't compile or misdescribe behavior ([details](#cr-012)) — fixed in #176
- [x] **CR-013** `LOW` · robustness — `UrlSource.content`: no timeouts, refetch on every access, deprecated `URL(String)` ([details](#cr-013)) — fixed in #176
- [x] **CR-014** `LOW` · bug — `join`/`toPath`: multi-char separators and empty elements ([details](#cr-014)) — fixed in #176
- [x] **CR-015** `LOW` · bug — `GitHubRepo.rawSourcePrefix` replace-all and custom-domain inconsistency ([details](#cr-015)) — fixed in #176
- [x] **CR-016** `LOW` · security/docs — Unkeyed SHA-256 checksum billed as tamper detection; magic `32` ([details](#cr-016)) — fixed in #176
- [x] **CR-017** `LOW` · API — `toByteArraySecure` duplicates `toByteArray`; `ReplaceWith` hints don't compile ([details](#cr-017)) — fixed in #176
- [x] **CR-018** `LOW` · bug — `getBanner`/`ReadResources` use the library's classloader ([details](#cr-018)) — fixed in #176
- [x] **CR-019** `LOW` · bug — `readProperties` isn't `.properties`-compatible and applies files partially ([details](#cr-019)) — fixed in #176
- [x] **CR-020** `LOW` · API — `waitForPortAvailable` gives up silently; `repeatWithSleep` sleeps after last iteration ([details](#cr-020)) — fixed in #176
- [x] **CR-021** `LOW` · cleanup — `hostInfo` double lookup, `!!`, commented-out code (carried over) ([details](#cr-021)) — fixed in #176
- [x] **CR-022** `LOW` · docs — `captureStdout` swaps global `System.out`, default charset (carried over) ([details](#cr-022)) — fixed in #176
- [x] **CR-023** `LOW` · docs — `toCsv` performs no escaping, undocumented (carried over) ([details](#cr-023)) — fixed in #176
- [x] **CR-024** `LOW` · API — `atomicInteger`/`atomicLong` default to -1; dead commented-out delegate (carried over) ([details](#cr-024)) — fixed in #176
- [x] **CR-025** `LOW` · tests — Boundary tests missing, weak assertions, 10M-iteration sweep in `commonTest` ([details](#cr-025)) — fixed in #176

### json-utils
- [x] **CR-026** `MEDIUM` · API — `s.toJsonString()` and `s.toJsonString(prettyPrint = true)` do different things ([details](#cr-026)) — fixed in #177
- [x] **CR-027** `MEDIUM` · bug — Non-`OrNull` accessors turn JSON `null` into `"null"` / `false` ([details](#cr-027)) — fixed in #177
- [x] **CR-028** `MEDIUM` · bug/docs — `*OrNull` accessors throw on type mismatch, contrary to README ([details](#cr-028)) — fixed in #177
- [x] **CR-029** `LOW` · bug — `booleanValue` treats any non-`"true"` as `false` (carried over) ([details](#cr-029)) — fixed in #177
- [x] **CR-030** `LOW` · API — `isNumber` true for quoted numeric strings (carried over) ([details](#cr-030)) — fixed in #177
- [x] **CR-031** `LOW` · API — Internal `JsonElementUtils` logger holder is public (carried over) ([details](#cr-031)) — fixed in #177
- [x] **CR-032** `LOW` · API — Path navigation: inconsistent empty segments; dotted keys unreachable (carried over) ([details](#cr-032)) — fixed in #177
- [x] **CR-033** `LOW` · docs — README examples missing `get` import; `size` on an array throws ([details](#cr-033)) — fixed in #177
- [x] **CR-034** `LOW` · docs — `isEmpty()` KDoc says "blank"; `JsonNull` counts as non-empty ([details](#cr-034)) — fixed in #177
- [x] **CR-035** `LOW` · bug — `deepCopy()` string round-trip throws on NaN/Infinity ([details](#cr-035)) — fixed in #177
- [x] **CR-036** `LOW` · tests — json-utils tests that cannot fail or are unfinished ([details](#cr-036)) — fixed in #177

### ktor-client-utils
- [x] **CR-037** `LOW` · API/tests — `blockingGet` lacks `httpClient`/`expectSuccess`; tests don't verify `setUp`/closing (carried over) ([details](#cr-037)) — fixed in #177

### ktor-server-utils
- [x] **CR-038** `HIGH` · bug — `Route.servlet` calls no-arg `init()`; Ktor `/healthcheck` NPEs on every request ([details](#cr-038)) — fixed in #174
- [x] **CR-039** `MEDIUM` · bug — `sendError` throws, so unsupported methods return 500 instead of 405/501 ([details](#cr-039)) — fixed in #178
- [x] **CR-040** `MEDIUM` · bug — `HerokuHttpsRedirect` defaults `host` to `localhost` ([details](#cr-040)) — fixed in #178
- [x] **CR-041** `LOW` · lifecycle — Servlet `destroy()` never called (carried over) ([details](#cr-041)) — fixed in #178
- [x] **CR-042** `LOW` · bug — Servlet character encoding ignored by writer and Content-Type (carried over) ([details](#cr-042)) — fixed in #178
- [x] **CR-043** `LOW` · docs — Parameter lookups case-insensitive; comment contradicts `getParameterMap` ([details](#cr-043)) — fixed in #178
- [x] **CR-044** `LOW` · bug/tests — `excludeSuffix`/`excludePrefix` match query string; redirect tests assert status only ([details](#cr-044)) — fixed in #178

### service-utils
- [x] **CR-045** `MEDIUM` · bug — Jetty admin/metrics paths with a leading slash become `//ping` → 404 (carried over) ([details](#cr-045)) — fixed in #179
- [x] **CR-046** `MEDIUM` · leak — Failed `startUp()`/`shutDown()` leaves already-started sub-services running ([details](#cr-046)) — fixed in #179
- [x] **CR-047** `MEDIUM` · tests — No test issues an HTTP request to any admin/metrics endpoint ([details](#cr-047)) — fixed in #179
- [x] **CR-048** `LOW` · API — Services added after init are silently unmanaged; test asserts the wrong thing ([details](#cr-048)) — fixed in #179
- [x] **CR-049** `LOW` · bug — `ZipkinReporterService.shutDown` drops queued spans (no `flush()`) ([details](#cr-049)) — fixed in #179
- [x] **CR-050** `LOW` · leak — `DropwizardExports` registered globally, never unregistered ([details](#cr-050)) — fixed in #179
- [x] **CR-051** `LOW` · robustness — Failure logged at info; Zipkin URL `//`; no init guard (carried over) ([details](#cr-051)) — fixed in #179
- [x] **CR-052** `LOW` · API — `ZipkinConfig.serviceName` never used (carried over) ([details](#cr-052)) — fixed in #179
- [x] **CR-053** `LOW` · security — Admin and metrics servers bind all interfaces, no host setting ([details](#cr-053)) — fixed in #179
- [x] **CR-054** `LOW` · cleanup — `runBlocking` around non-suspend `start`; single-use `servletGroup` lateinits ([details](#cr-054)) — fixed in #179

### prometheus-utils
- [x] **CR-055** `MEDIUM` · API — Factories hard-wired to the default `CollectorRegistry` (carried over) ([details](#cr-055)) — fixed in #180
- [x] **CR-056** `LOW` · robustness — `SystemMetrics.initialize` unrecoverable after partial registration ([details](#cr-056)) — fixed in #180
- [x] **CR-057** `LOW` · bug — `SamplerGaugeCollector` runs the sampler during construction ([details](#cr-057)) — fixed in #180
- [x] **CR-058** `LOW` · bug — `InstrumentedThreadFactory` counts before null check; misleading comment (carried over) ([details](#cr-058)) — fixed in #180
- [x] **CR-059** `LOW` · docs/tests — README `HTTPServer` example needs an unlisted artifact; weak metric tests ([details](#cr-059)) — fixed in #180

### dropwizard-utils
- [x] **CR-060** `LOW` · API — `newBacklogHealthCheck` captures a snapshot; message omits threshold; no `@JvmStatic` (carried over) ([details](#cr-060)) — fixed in #180

### jetty-utils
- [x] **CR-061** `LOW` · bug — `LambdaServlet` returns 200/empty when the lambda throws (carried over) ([details](#cr-061)) — fixed in #180
- [x] **CR-062** `LOW` · bug — `text/plain` without charset is encoded ISO-8859-1 on Jetty ([details](#cr-062)) — fixed in #180
- [x] **CR-063** `LOW` · cleanup — `VersionServlet` duplicates `LambdaServlet`; DSL builders lack default block; `HttpServletGroup` untested (carried over) ([details](#cr-063)) — fixed in #180

### guava-utils
- [x] **CR-064** `MEDIUM` · bug — `ConditionalValue.waitUntil` returns `false` for zero/sub-ms timeout even when already satisfied ([details](#cr-064)) — fixed in #181
- [x] **CR-065** `MEDIUM` · concurrency — `ConditionalValue` misses updates for equal/mutated values (StateFlow conflation) ([details](#cr-065)) — fixed in #181
- [x] **CR-066** `MEDIUM` · concurrency — `GenericMonitor` untimed waits double-`leave()`, masking the real exception ([details](#cr-066)) — fixed in #181
- [x] **CR-067** `MEDIUM` · bug — `GenericMonitor` retry loops overrun `maxWait`, treat ZERO as unlimited, can busy-spin (partly carried over) ([details](#cr-067)) — fixed in #181
- [x] **CR-068** `LOW` · docs — `GenericMonitor` doesn't require state changes inside the monitor ([details](#cr-068)) — fixed in #181
- [x] **CR-069** `LOW` · API — `startSync`/`stopSync` hide `TimeoutException`; no `@Throws`; inconsistent params ([details](#cr-069)) — fixed in #181
- [x] **CR-070** `LOW` · bug — Sub-ms durations truncate to 0; `VerboseCountDownLatch` log flood ([details](#cr-070)) — fixed in #181
- [x] **CR-071** `LOW` · API — `GenericValueWaiter.currValue` settable without notification; unused `initValue` ([details](#cr-071)) — fixed in #181
- [x] **CR-072** `LOW` · API — `ConditionalValue.set` still `yield()`s (carried over) ([details](#cr-072)) — fixed in #181
- [x] **CR-073** `LOW` · cleanup — Demo `main()`s in published JAR; stale Kover exclusion (carried over) ([details](#cr-073)) — fixed in #181
- [x] **CR-074** `LOW` · API — `GuavaDsl` callbacks throw on reassignment; `starting(null)` KDoc false (carried over) ([details](#cr-074)) — fixed in #181
- [x] **CR-075** `LOW` · API — `EMPTY_BYTE_ARRAY` is public (carried over) ([details](#cr-075)) — fixed in #181
- [x] **CR-076** `LOW` · security — `unzip()` has no decompressed-size limit ([details](#cr-076)) — fixed in #181
- [x] **CR-077** `LOW` · docs — guava-utils README misdescribes `genericServiceListener` and logging factories ([details](#cr-077)) — fixed in #181
- [x] **CR-078** `LOW` · tests — No timed-wakeup, `maxWait`-elapsed, or service-helper tests; stale comment ([details](#cr-078)) — fixed in #181

### zipkin-utils
- [x] **CR-079** `LOW` · tests — `ZipkinDsl` tests never verify the config block; leak `Tracing` on failure (carried over) ([details](#cr-079)) — fixed in #181
- [x] **CR-080** `LOW` · docs — zipkin-utils and redis-utils have no README (linked from llms.txt) ([details](#cr-080)) — fixed in #181

### script-utils (common / java / kotlin / python)
- [ ] **CR-081** `MEDIUM` · concurrency — Pooled engines lost forever when a waiting borrower is cancelled ([details](#cr-081)) — in progress (#182)
- [ ] **CR-082** `MEDIUM` · security — Expression evaluators and their pools have no JVM-exit guard ([details](#cr-082)) — in progress (#182)
- [ ] **CR-083** `MEDIUM` · bug — Auto-imported `System` shadow breaks all other `System.*` calls in Kotlin scripts ([details](#cr-083)) — in progress (#182)
- [ ] **CR-084** `MEDIUM` · bug — `KotlinScript` generates uncompilable casts (`Regex`, `listOf`, nested generics) ([details](#cr-084)) — in progress (#182)
- [ ] **CR-085** `MEDIUM` · leak — `JavaScript` imports/isolation leak across pool borrowers and grow unbounded ([details](#cr-085)) — in progress (#182)
- [ ] **CR-086** `MEDIUM` · leak — Kotlin expression evaluator pools never reset; REPL history grows forever ([details](#cr-086)) — in progress (#182)
- [ ] **CR-087** `MEDIUM` · bug — Variables added after first `eval` never bound (Java silently returns default); `add()` unsynchronized (carried over, extended) ([details](#cr-087)) — in progress (#182)
- [ ] **CR-088** `LOW` · security — Python guard blocks harmless `sys.exit` but misses `java.lang.System.exit` ([details](#cr-088)) — in progress (#182)
- [ ] **CR-089** `LOW` · docs — Python guard KDoc inverted about string literals; `def exit(self)` rejected ([details](#cr-089)) — in progress (#182)
- [ ] **CR-090** `LOW` · robustness — Pools accept `size <= 0` and hang; `AbstractExprEvaluatorPool<T>` ignores `T` ([details](#cr-090)) — in progress (#182)
- [ ] **CR-091** `LOW` · leak — Pools never close their instances ([details](#cr-091)) — in progress (#182)
- [ ] **CR-092** `LOW` · concurrency — Expression evaluators share the manager's global `Bindings` ([details](#cr-092)) — in progress (#182)
- [ ] **CR-093** `LOW` · security — Binding names unvalidated and spliced raw into generated source ([details](#cr-093)) — in progress (#182)
- [ ] **CR-094** `LOW` · bug — `javaEquiv` emits invalid Java for `Char`, `Any`, nested generics (carried over) ([details](#cr-094)) — in progress (#182)
- [ ] **CR-095** `LOW` · bug — `JavaScript.varDecls` uses runtime `simpleName`; raw `IllegalArgumentException` escapes (carried over) ([details](#cr-095)) — in progress (#182)
- [ ] **CR-096** `LOW` · docs — No-op `close()`, undocumented `eval()`, undocumented `evalScript` field contract (carried over) ([details](#cr-096)) — in progress (#182)
- [ ] **CR-097** `LOW` · cleanup — Public `engine`, `toTempName`, dead `error()`, duplicated binding flush (carried over) ([details](#cr-097)) — in progress (#182)
- [ ] **CR-098** `LOW` · tests — Misleading guard tests; missing coverage for the defects above ([details](#cr-098)) — in progress (#182)

### email-utils
- [ ] **CR-099** `MEDIUM` · bug — Webhook models can't decode real Resend payloads with a default `Json` ([details](#cr-099))
- [ ] **CR-100** `LOW` · API — Default `email()` stylesheet exists only in test resources ([details](#cr-100))
- [ ] **CR-101** `LOW` · API — `Parameters.getEmail` doesn't normalize (carried over) ([details](#cr-101))
- [ ] **CR-102** `LOW` · logging — `sendEmail` logs-then-rethrows; logs recipient addresses at INFO (partly carried over) ([details](#cr-102))

### recaptcha-utils
- [ ] **CR-103** `MEDIUM` · concurrency — `verifyRecaptcha` swallows `CancellationException` ([details](#cr-103))
- [ ] **CR-104** `LOW` · bug — `remoteip` sends a reverse-DNS hostname ([details](#cr-104))
- [ ] **CR-105** `LOW` · security — Enabled-but-misconfigured fails open silently; duplicated dead gate (carried over) ([details](#cr-105))

### redis-utils
- [ ] **CR-106** `MEDIUM` · bug — `withRedis` family never takes its documented connection-failure path ([details](#cr-106))
- [ ] **CR-107** `MEDIUM` · bug — URL parsing drops the database index and protocol ([details](#cr-107))
- [ ] **CR-108** `LOW` · bug — `"none"` placeholder password still sent as AUTH (carried over) ([details](#cr-108))
- [ ] **CR-109** `LOW` · error handling — Pool helpers catch only `JedisConnectionException` ([details](#cr-109))
- [ ] **CR-110** `LOW` · performance — `testOnBorrow` + `testOnReturn` add two PINGs per command ([details](#cr-110))
- [ ] **CR-111** `LOW` · security — SSL scheme detection uses default-locale `lowercase` ([details](#cr-111))
- [ ] **CR-112** `LOW` · API — Pool-size validation rejects `-1`, accepts `0` ([details](#cr-112))

### exposed-utils
- [ ] **CR-113** `MEDIUM` · bug — `ResultRow.get(index)` / `toRowString()` throw on SQL NULL ([details](#cr-113))
- [ ] **CR-114** `LOW` · API — `upsert(conflictIndex)` doesn't validate the index; hides native options; stale KDoc ([details](#cr-114))

### grpc-utils
- [ ] **CR-115** `MEDIUM` · bug — `serverTlsContext()` returns a builder without ALPN, which gRPC's Netty server rejects ([details](#cr-115))
- [ ] **CR-116** `LOW` · bug — `enableRetry = false` doesn't disable retries (carried over, re-diagnosed) ([details](#cr-116))
- [ ] **CR-117** `LOW` · robustness — `shutdownWithJvm` validates its timeout only inside the hook ([details](#cr-117))
- [ ] **CR-118** `LOW` · docs — README says all client TLS paths are optional; trust path is required ([details](#cr-118))
- [ ] **CR-119** `LOW` · API — `channel()` has no `tlsContext` default (carried over) ([details](#cr-119))
- [ ] **CR-120** `LOW` · API — `streamObserver` exposes helper type and set-once callbacks (carried over) ([details](#cr-120))

### Cross-module test-scaffolding
- [ ] **CR-121** `LOW` · tests — ktor-server `ServletRoute` lacks init/405/non-ASCII tests ([details](#cr-121))
- [ ] **CR-122** `LOW` · tests — Redis null-path tests mock a private function the real code never exercises ([details](#cr-122))
- [ ] **CR-123** `LOW` · tests — grpc TLS/retry tests assert only `authority()` / `isServer` ([details](#cr-123))

### Build & publishing
- [x] **CR-124** `HIGH` · publishing — service-utils exposes `implementation` dependency types in its public API ([details](#cr-124)) — fixed in #174
- [ ] **CR-125** `MEDIUM` · publishing — grpc-utils (Netty `SslContext`) and script-utils-java (`Isolation`) leak `implementation` types ([details](#cr-125))
- [ ] **CR-126** `LOW` · deps — Catalog `kotlin` entry also pins `kotlin-reflect`; POMs mix 2.4.10 / 2.4.20 ([details](#cr-126))
- [ ] **CR-127** `LOW` · deps — Redundant/unused dependency declarations ([details](#cr-127))
- [ ] **CR-128** `LOW` · build — `make build` ("without tests") still runs KMP `jvmTest` via Kover ([details](#cr-128))
- [ ] **CR-129** `LOW` · build — `coverage-clean` misses JVM-module test results; build cache restores them ([details](#cr-129))
- [ ] **CR-130** `LOW` · build — `ksp` plugin not declared in root `plugins {}` ([details](#cr-130))
- [ ] **CR-131** `LOW` · publishing — Signing skipped unless in-memory key present; POM `developerConnection` lacks `git@` ([details](#cr-131))

### CI
- [ ] **CR-132** `MEDIUM` · security — `test.yml` has no `permissions` block; repo default token is write ([details](#cr-132))
- [ ] **CR-133** `LOW` · security — `kdocs.yml` grants `pages`/`id-token` write to the PR build job ([details](#cr-133))
- [ ] **CR-134** `LOW` · ci — Redundant Lint step; Apple targets never built in CI ([details](#cr-134))
- [ ] **CR-135** `LOW` · ci — No concurrency cancellation; actions pinned by mutable tags ([details](#cr-135))

### Project documentation
- [ ] **CR-136** `MEDIUM` · docs — llms.txt misdescribes several modules ([details](#cr-136))
- [ ] **CR-137** `LOW` · docs — CLAUDE.md format command skips KMP modules ([details](#cr-137))
- [ ] **CR-138** `LOW` · docs — Other CLAUDE.md drift (Dokka location, return-value checker, Dependabot ignores) ([details](#cr-138))
- [ ] **CR-139** `LOW` · cleanup — Stale/duplicated top-level files (old reviews, `.codeclimate.yml`, `system.properties`, `.wercker/`) ([details](#cr-139))

---

## Details

### core-utils

<a id="cr-001"></a>
#### CR-001 — `toObjectSecure` disables its allow-list by default; no `ObjectInputFilter`
**Severity:** High · **Category:** security · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:85-146`, `core-utils/README.md:202,355`

`allowedClasses` defaults to `emptySet()`, and the check is `if (allowedClasses.isNotEmpty() && !allowedClasses.contains(clazz))`. The default call therefore relies entirely on a 7-prefix blocklist. That list misses common gadget chains such as commons-beanutils `BeanComparator` and Spring/Groovy gadgets. There is also no depth, reference or array limit, so small deeply-nested `HashSet` payloads can still cause a DoS under the 10 MB size cap. The README says the function "deserializes only classes you allow-list" and recommends it for untrusted data. `IOExtensionsTests.kt:109-115` confirms that the empty default disables the check.

**Fix:**
- Require a non-empty `allowedClasses`.
- Install a JEP 290 filter: `setObjectInputFilter(ObjectInputFilter.Config.createFilter("maxdepth=…;maxrefs=…;maxarray=…"))`.
- Correct the README.

<a id="cr-002"></a>
#### CR-002 — Blocklist prefix `java.lang.Runtime` rejects every `RuntimeException` subclass
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:119-144`

`isDangerousClass` uses `startsWith`, and `"java.lang.RuntimeException".startsWith("java.lang.Runtime")` is true. `resolveClass` runs for every superclass descriptor, so deserializing any `IllegalStateException`, `IllegalArgumentException` or similar throws `SecurityException`. This happens even when the class is allow-listed, because the blocklist is checked first. `java.lang.RuntimePermission` is caught the same way.

**Fix:** Match `java.lang.*` entries by exact name, and keep prefix matching for package entries ending in `.`. Add a round-trip test for an exception payload.

<a id="cr-003"></a>
#### CR-003 — `maskUrlCredentials` fabricates credentials/host when `@` is in path or query
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:329-343`

Any string containing both `://` and `@` is rewritten as `scheme://*****:*****@<substringAfterLast("@")>`. For example, `https://api.example.com/users?email=bob@corp.com` becomes `https://*****:*****@corp.com`. The logged URL then shows the wrong host and credentials that never existed. The KDoc example also shows `xxxxx`, but the code emits `*****`.

**Fix:** Search for `@` only within the authority, which ends at the first `/`, `?` or `#` after `://`. Mask only in that case. Fix the KDoc and add path/query tests.

<a id="cr-004"></a>
#### CR-004 — Glob→regex (`toPattern` / `asRegex`) escapes only `.`
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:291-319`

Other regex metacharacters pass through unescaped:
- `"file(1).txt".asRegex()` does not match `file(1).txt`, but does match `file1.txt`.
- `"a+b"` matches `aab`.
- `"[abc"` throws `PatternSyntaxException`.
- Input that literally contains the `__SINGLE__DOT__` sentinel is corrupted.

**Fix:** Build the pattern one character at a time: `*`→`.*`, `?`→`.`, and backslash-escape every other metacharacter. Drop the sentinel.

<a id="cr-005"></a>
#### CR-005 — `Long.length` off by one for values near 10^16–10^18
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/NumberExtensions.kt:55-60`

`log10(abs(toDouble()))` loses precision above 2^53. `999_999_999_999_999_999L.toDouble() == 1.0E18` (verified), so the function returns 19 instead of 18. The same happens for `99_999_999_999_999_999L` and `9_999_999_999_999_999L`. The existing tests sweep only `0..1e7` and `MAX-1e7..MAX`, so they miss this range.

**Fix:** Count digits with integer division. Add tests for `10^n - 1` and `10^n` for every `n`.

<a id="cr-006"></a>
#### CR-006 — `toISO8601` drops seconds on round minutes
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/DateUtils.kt:102-114`

The function builds on `LocalDateTime.toString()`. kotlinx-datetime documents that this "will not include seconds" for a round minute (verified in the 0.8.0-0.6.x-compat sources). So `LocalDateTime(2024,3,15,8,30,0).toISO8601()` returns `2024-03-15T08:30Z`, while the same minute plus 500 ms returns `…T08:30:00Z`. Output length therefore varies, which breaks fixed-width consumers and strict parsers.

**Fix:** Use an explicit `LocalDateTime.Format { … second() … }`. Add a zero-seconds test.

<a id="cr-007"></a>
#### CR-007 — Two-digit years computed as `year - 2000`; `versionDesc` fallback prints `12/31/-31`
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/DateUtils.kt:140,171,191`, `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/Version.kt:105`

- `LocalDate(1999,12,31).toMMDDYY()` returns `12/31/-1`.
- A year of 2100 prints as `100`.
- A class without `@Version` formats epoch 0 in America/Los_Angeles, so the output is `Build Date: Wed 12/31/-31 16:00:00`.

Tests only assert `shouldContain "Version: Unknown"`.

**Fix:** Use `year.mod(100).lpad(2)`. Emit `Unknown` for the build date in the fallback. Assert the exact fallback string in tests.

<a id="cr-008"></a>
#### CR-008 — `singleSetReference` compares by reference; non-null `initValue` makes it unsettable
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/delegate/AtomicDelegates.kt:44-57,118-138`

`AtomicReference.compareAndSet` compares by identity:
- `singleSetReference(initValue = 1000, compareValue = 1000)` boxes two distinct `Integer`s, so the first set throws "already set". Runtime-built strings fail the same way; the existing test passes only because string literals are interned.
- `singleSetReference(initValue = "x")` leaves `compareValue = null`, so every set throws.
- Assigning `null` while the value is still `null` does not consume the single set, which is inconsistent with `SingleAssignVar`.

**Fix:** Load the current value, check `cur == compareValue` by equality, then CAS against `cur`. Validate or document how `initValue` and `compareValue` pair.

<a id="cr-009"></a>
#### CR-009 — `typeParameterCount` counts the superclass's type args
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ReflectExtensions.kt:21-40`; used by `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:100-123`

The KDoc says it counts "type parameters of this object's class", but the code reads `javaClass.genericSuperclass`. That gives wrong answers in both directions:
- **Generic classes report 0:** `Box<T>`, `Pair`, `Triple`, `Optional`, `AtomicReference` and `IntArray` (the KDoc says arrays always return 1).
- **Non-generic subclasses report the parent's count:** `java.util.Properties` reports 2, and `class IntList : ArrayList<Int>()` reports 1.

In script-utils this has two effects. `add("p", 1 to "a")` is accepted with no types and then generates the uncompilable `as Pair`. `Properties` can't be bound at all: `add()` demands type args, and supplying them generates `Properties<Any, Any>`. The script reviewer confirmed both casts with `kotlinc`.

**Fix:** Use `javaClass.typeParameters.size`, keeping the `Array` special case. This keeps every value currently pinned in `ReflectExtensionTests` (`ArrayList`→1, maps→2). Add tests for `Pair`, `Properties` and user generic classes, and re-run the script-utils suites.

<a id="cr-010"></a>
#### CR-010 — `ContentRoot.file(path)` ignores the root
**Severity:** Medium · **Category:** bug / docs · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:33-38,52,94`, `core-utils/README.md:233-234`

The interface KDoc promises "the given relative path within this root", but neither implementation uses the root:
- `FileSystemSource("/var/data").file("config.json")` reads `./config.json`.
- `GitHubRepo(...).file("README.md")` returns `UrlSource("README.md")`, which isn't a valid URL.

The tests at `ContentSourceTests.kt:167-172,212-218` lock in this behavior.

**Fix:** Resolve against the root: `File(pathPrefix, path)` and `[rawSourcePrefix, path].join()`. Update the tests.

<a id="cr-011"></a>
#### CR-011 — `GitLabFile` targets the HTML `/-/blob/` page instead of `/-/raw/`
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:141,190-217`, `core-utils/README.md:246-247`

The URL uses `-/blob`, so `content` returns GitLab's HTML viewer page. The reviewer verified with curl: `/-/blob/` returns `text/html` and `/-/raw/` returns `text/plain`. `GitLabRepo.rawSourcePrefix = sourcePrefix` rewrites nothing, contradicting the README.

**Fix:** Use `-/raw` and set `rawSourcePrefix` to match. Expose a separate `blobUrl` if a browser link is wanted. Update `ContentSourceTests.kt:236-256`.

<a id="cr-012"></a>
#### CR-012 — core-utils README snippets don't compile or misdescribe behavior
**Severity:** Low · **Category:** docs · **Carried over:** partly (criticalSection)
**Where:** `core-utils/README.md:136-145,202-217,246-247,280,284`

| Line | Snippet | Problem |
|---|---|---|
| 140 | `shared.withLock { it.length }` | The lambda has a receiver, so `it` is unresolved. |
| 213 | `toObjectSecure<MyType>(setOf(MyType::class.java.name))` | Wrong signature: the function isn't reified and takes `Set<Class<*>>`. |
| 280 | `waitForPortAvailable(port = 8080)` | This is a member of `object MiscFuncs`, so the star import doesn't reach it. |
| 284 | `getBanner("banner.txt")` | Missing the required `logger` argument. |
| 142-144 | `criticalSection { println("runs once") }` | The block always runs. The code matches its KDoc, which only says it sets a flag while running; the README claims mutual exclusion. |
| 215 | "Tamper detection" | See [CR-016](#cr-016). |

**Fix:** Correct the snippets. Consider compiling README snippets in a doc test.

<a id="cr-013"></a>
#### CR-013 — `UrlSource.content`: no timeouts, refetch on every access, deprecated `URL(String)`
**Severity:** Low · **Category:** robustness · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:224-231`

`URL(source).readText()` uses infinite connect and read timeouts, so a hung server blocks the thread forever. `content` is a getter, so every access re-fetches. The `URL(String)` constructor has been deprecated since JDK 20.

**Fix:** Open the connection through `URI(source).toURL().openConnection()` and set timeouts. Document the per-access fetch or cache the result.

<a id="cr-014"></a>
#### CR-014 — `join`/`toPath`: multi-char separators and empty elements
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:125-132`

- A leading separator is stripped with `substring(1)`, so `["a", "::b"].join("::")` gives `a:::b`.
- An empty middle element gives `a//b`. The README's `GitHubFile(..., srcPath = "", ...)` example produces `master//README.md`; GitHub redirects that, but it's still wrong.

**Fix:** Use `removePrefix(separator)` and skip empty elements.

<a id="cr-015"></a>
#### CR-015 — `GitHubRepo.rawSourcePrefix` replace-all and custom-domain inconsistency
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:118,178-179`

`sourcePrefix.replace(GITHUB, GITHUB_USER_CONTENT)` rewrites every `github.com`, including one inside a repo name like `bob.github.com`. It changes nothing for a GitHub Enterprise domain, while `GitHubFile` always hardcodes `raw.githubusercontent.com`.

**Fix:** Build the raw prefix from its parts, using one helper shared by both classes.

<a id="cr-016"></a>
#### CR-016 — Unkeyed SHA-256 checksum billed as tamper detection; magic `32`
**Severity:** Low · **Category:** security / docs · **Carried over:** partly (magic number)
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:148-172`, `core-utils/README.md:215`

Anyone who can modify the bytes can recompute the checksum, so it detects accidental corruption only. Throwing `SecurityException` and calling it "tamper detection" implies authenticity it doesn't provide. The digest length `32` / `0..31` is hardcoded.

**Fix:** Document it as a corruption check, or add an HMAC variant compared with `MessageDigest.isEqual`. Name the length constant.

<a id="cr-017"></a>
#### CR-017 — `toByteArraySecure` duplicates `toByteArray`; `ReplaceWith` hints don't compile
**Severity:** Low · **Category:** API · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:35-74`

The two serializers are byte-identical; serialization was never the unsafe side. `ReplaceWith("toObjectSecure(expectedClass, allowedClasses)")` references identifiers that don't exist at the call site, so the IDE quick-fix produces broken code.

**Fix:** Un-deprecate `toByteArray` or make one an alias of the other. Use message-only deprecation on `toObject`.

<a id="cr-018"></a>
#### CR-018 — `getBanner`/`ReadResources` use the library's classloader
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/Banner.kt:37`, `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/MiscFuncsJvm.kt:157-161`

`logger.javaClass.classLoader` is kotlin-logging's classloader, not the caller's, and `ReadResources` uses core-utils' own. Under servlet containers, plugin hosts or OSGi, application resources aren't found.

**Fix:** Accept a `ClassLoader` or `Class<*>`, defaulting to the thread context classloader. Deprecate the `KLogger`-based lookup.

<a id="cr-019"></a>
#### CR-019 — `readProperties` isn't `.properties`-compatible and applies files partially
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/PropertyFunctions.kt:21-44`

- An indented `# comment` is loaded as a property.
- `!` comments, `key: value`, escapes, continuations and `\uXXXX` aren't supported.
- A line like `=v` throws.
- If a later file is missing, earlier files have already been applied.
- There is no KDoc.

**Fix:** Verify every file exists first, load each with `java.util.Properties().load()`, then copy the entries. Add KDoc.

<a id="cr-020"></a>
#### CR-020 — `waitForPortAvailable` gives up silently; `repeatWithSleep` sleeps after last iteration
**Severity:** Low · **Category:** API / docs · **Carried over:** no
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/MiscFuncsJvm.kt:84-101,125-145`

`waitForPortAvailable` is documented as "blocks until … available", but after `maxAttempts` it only logs a warning and returns `Unit`. `repeatWithSleep(3)` sleeps three times instead of twice.

**Fix:** Return `Boolean` or throw on timeout. Skip the sleep after the final iteration.

<a id="cr-021"></a>
#### CR-021 — `hostInfo` double lookup, `!!`, commented-out code
**Severity:** Low · **Category:** cleanup · **Carried over:** yes
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/MiscFuncsJvm.kt:47-56`

`InetAddress.getLocalHost()` is called twice, so the hostname and address can come from different lookups. The `!!` on Java non-null getters is unnecessary, and there is a commented-out log line.

**Fix:** Look up once (`val lh = InetAddress.getLocalHost()`) and delete the comment.

<a id="cr-022"></a>
#### CR-022 — `captureStdout` swaps global `System.out`, default charset
**Severity:** Low · **Category:** docs / concurrency · **Carried over:** yes
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/MiscFuncsJvm.kt:103-119`

Concurrent callers clobber each other, and output from other threads leaks into the capture. The function uses the platform charset.

**Fix:** Document that it isn't thread-safe. Use UTF-8 for both the `PrintStream` and `toString`.

<a id="cr-023"></a>
#### CR-023 — `toCsv` performs no escaping, undocumented
**Severity:** Low · **Category:** docs · **Carried over:** yes
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/MiscExtensions.kt:33-40`

`["a,b","c"].toCsv()` returns `a,b, c`, which a CSV parser reads as three fields.

**Fix:** Add a KDoc warning, or deprecate it in favor of a clearer name.

<a id="cr-024"></a>
#### CR-024 — `atomicInteger`/`atomicLong` default to -1; dead commented-out delegate
**Severity:** Low · **Category:** API / cleanup · **Carried over:** partly
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/delegate/AtomicDelegates.kt:41-42,67-81,101-116`

`var hits by atomicInteger()` starts at -1. A commented-out `nullableReference` factory and class are still in the source.

**Fix:** Default to 0, or explain the sentinel in the KDoc. Delete the dead code.

<a id="cr-025"></a>
#### CR-025 — Boundary tests missing, weak assertions, 10M-iteration sweep in `commonTest`
**Severity:** Low · **Category:** tests · **Carried over:** no
**Where:** `core-utils/src/commonTest/kotlin/com/pambrose/util/StringExtensionTests.kt:48-54`, `core-utils/src/jvmTest/kotlin/com/pambrose/util/StringExtensionEdgeCaseTests.kt:63-72`, `core-utils/src/jvmTest/kotlin/com/pambrose/util/VersionTests.kt:68-86`, `core-utils/src/commonTest/kotlin/com/pambrose/util/DateUtilsTest.kt:189-195`

- **Missing regression tests:** none exist yet for CR-002 through CR-009.
- **Weak assertions:** `withLineNumbers` is checked with `shouldContain "1"`, and `toAdjustedString` with `shouldEndWith "s"`.
- **Expensive sweep:** the `length` spec runs about 40M assertions in `commonTest`, on every JS/wasm/Native target, and still misses the failing range from CR-005.

**Fix:** Replace the sweeps with boundary cases and assert exact strings.

---

### json-utils

<a id="cr-026"></a>
#### CR-026 — `s.toJsonString()` and `s.toJsonString(prettyPrint = true)` do different things
**Severity:** Medium · **Category:** API · **Carried over:** no
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:238,247-248`

The no-argument call resolves to the non-generic `String.toJsonString()`, which parses the string and reformats it. Passing any argument selects the generic `T.toJsonString(prettyPrint)`, which serializes the string as a JSON string literal:
- `"""{"a":1}""".toJsonString(true)` returns `"{\"a\":1}"`.
- `"hello".toJsonString()` throws, but `"hello".toJsonString(false)` returns `"\"hello\""`.

In generic code, a `String` always takes the generic path.

**Fix:** Rename the String overload (e.g. `String.reformatJson(prettyPrint)`) and keep a `@Deprecated` shim for ABI compatibility. Test both spellings.

<a id="cr-027"></a>
#### CR-027 — Non-`OrNull` accessors turn JSON `null` into `"null"` / `false`
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:40-49,95-97,120-138`

`JsonNull.content == "null"` (verified in kotlinx-serialization-json 1.11.0). So for `{"value": null}`:
- `stringValue("value")` returns the string `"null"`.
- `booleanValue("value")` returns `false`.
- `intValue("value")` throws `NumberFormatException`.

A missing value silently becomes plausible data.

**Fix:** Throw `IllegalArgumentException` on `JsonNull` in the non-null accessors. Document it and add tests.

<a id="cr-028"></a>
#### CR-028 — `*OrNull` accessors throw on type mismatch, contrary to README
**Severity:** Medium · **Category:** bug / docs · **Carried over:** no
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:123-157`, `json-utils/README.md:250`

For `{"name":"test","obj":{}}`, `intValueOrNull("name")` throws `NumberFormatException` and `stringValueOrNull("obj")` throws `IllegalArgumentException`. The README recommends the OrNull variants "for untrusted input", which is exactly when these throw.

**Fix:** Make the OrNull variants type-safe (`as? JsonPrimitive`, `toIntOrNull()`, …), or correct the README and KDoc.

<a id="cr-029"></a>
#### CR-029 — `booleanValue` treats any non-`"true"` as `false`
**Severity:** Low · **Category:** bug · **Carried over:** yes
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:49`

`String.toBoolean()` returns `false` for `"yes"`, `1` or `"garbage"`, while the int and double accessors throw on bad content.

**Fix:** Use `toBooleanStrict()`, and `toBooleanStrictOrNull()` in the OrNull variant.

<a id="cr-030"></a>
#### CR-030 — `isNumber` true for quoted numeric strings
**Severity:** Low · **Category:** API · **Carried over:** yes
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:69`

`JsonPrimitive("42")` reports both `isString` and `isNumber`.

**Fix:** Also require `!isString && this !is JsonNull`, and add a test.

<a id="cr-031"></a>
#### CR-031 — Internal `JsonElementUtils` logger holder is public
**Severity:** Low · **Category:** API · **Carried over:** yes
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:20,321-324`

The object's own KDoc calls it an internal logger holder, but it is published API.

**Fix:** Replace it with a file-private logger, deprecating the object first.

<a id="cr-032"></a>
#### CR-032 — Path navigation: inconsistent empty segments; dotted keys unreachable
**Severity:** Low · **Category:** API · **Carried over:** partly
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:79-84,95-97,111,168`

`getByPath("a//b")` drops empty segments, but `get("a..b")` and `get("")` look up the key `""` and throw. Every key is split on `.`, so a key like `{"a.b": 1}` is unreachable. Neither limitation is documented.

**Fix:** Use one empty-segment policy and document the dotted-key limitation. Consider a literal-key accessor.

<a id="cr-033"></a>
#### CR-033 — README examples missing `get` import; `size` on an array throws
**Severity:** Low · **Category:** docs · **Carried over:** no
**Where:** `json-utils/README.md:54,111,121`, `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:200`

The snippets use `element["name"]` without importing `com.pambrose.common.json.get`. `if (fruits.isArray) println(fruits.size)` throws, because `JsonElement.size = jsonObject.size`.

**Fix:** Add the import. Make `size` handle arrays, or change the example.

<a id="cr-034"></a>
#### CR-034 — `isEmpty()` KDoc says "blank"; `JsonNull` counts as non-empty
**Severity:** Low · **Category:** docs · **Carried over:** no
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:202-212`

`JsonPrimitive("   ").isEmpty()` is `false`, and `JsonNull.isEmpty()` is `false` because its content is `"null"`.

**Fix:** Align the KDoc with the code (or switch to `isBlank`) and handle `JsonNull` explicitly.

<a id="cr-035"></a>
#### CR-035 — `deepCopy()` string round-trip throws on NaN/Infinity
**Severity:** Low · **Category:** bug / performance · **Carried over:** no
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:197`

`Json.decodeFromString(Json.encodeToString(this))` throws `SerializationException` for `JsonPrimitive(Double.NaN)`, and it serializes the whole tree on every call.

**Fix:** Copy structurally, recursing through `JsonObject` and `JsonArray`; primitives are immutable.

<a id="cr-036"></a>
#### CR-036 — json-utils tests that cannot fail or are unfinished
**Severity:** Low · **Category:** tests · **Carried over:** no
**Where:** `json-utils/src/commonTest/kotlin/com/pambrose/json/JsonContentUtilsTest.kt:175-192,225-260`, `json-utils/src/commonTest/kotlin/com/pambrose/json/JsonElementUtilsTest.kt:348-349`

- **Can't fail:** the "lenient parsing" test parses valid strict JSON; `ignoreUnknownKeys` doesn't affect tree parsing. The deep-copy test checks `!==`, which proves nothing.
- **Unfinished or weak:** "empty and null handling" ends in a TODO, and the malformed-JSON test uses try/catch.
- **Missing:** no regression tests exist for CR-026 through CR-035.

**Fix:** Decode into a `@Serializable` class with unknown keys. Use `shouldThrow` and add regression specs.

---

### ktor-client-utils

<a id="cr-037"></a>
#### CR-037 — `blockingGet` lacks `httpClient`/`expectSuccess`; tests don't verify `setUp`/closing
**Severity:** Low · **Category:** API / tests · **Carried over:** yes (API part)
**Where:** `ktor-client-utils/src/jvmMain/kotlin/com/pambrose/common/dsl/KtorDslJvm.kt:38-47`, `ktor-client-utils/src/jvmTest/kotlin/com/pambrose/common/dsl/KtorDslJvmTests.kt:38-84`, `ktor-client-utils/src/commonTest/kotlin/com/pambrose/common/dsl/KtorDslTests.kt:16-46`

- **Client handling:** each call builds and tears down its own client, so a client can't be reused or mocked, and there is no `expectSuccess` option.
- **`setUp` test:** its server ignores request headers, so removing `setUp.invoke(this)` would still pass.
- **Other gaps:** nothing tests that created clients are closed while caller-provided ones stay open, or that `expectSuccess = true` throws on a 500.

**Fix:** Add `httpClient: HttpClient? = null` and `expectSuccess: Boolean = false`, keeping the old overload for the JVM ABI. Echo headers in the test handler and assert the client lifecycle.

---

### ktor-server-utils

<a id="cr-038"></a>
#### CR-038 — `Route.servlet` calls no-arg `init()`; Ktor `/healthcheck` NPEs on every request
**Severity:** High · **Category:** bug · **Carried over:** no (found independently by two reviewers)
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:44`, `service-utils/src/main/kotlin/com/pambrose/common/service/GenericKtorService.kt:84`

Containers call `init(ServletConfig)`, which stores the config and then calls `init()`. The bridge calls only `init()`, so any `init(ServletConfig)` override never runs and `getServletConfig()` stays null. Dropwizard's `HealthCheckServlet` assigns `filter` and `mapper` only in `init(ServletConfig)` (verified in metrics-jakarta-servlets 4.2.40 sources, lines 95-125). Under `GenericKtorService`, every `GET /healthcheck` reaches `registry.runHealthChecks(filter)` with `filter == null`. `thread_deadlock` and `all_services_healthy` are always registered, so that is an NPE and a 500 on every request.

The Jetty variant is unaffected, because `ServletHolder` calls `init(ServletConfig)`. No test issues a request to this endpoint (see [CR-047](#cr-047)).

**Fix:** Call `servlet.init(config)` with a minimal `ServletConfig`/`ServletContext` stub: a name, empty init params, `getAttribute` returning null, and `log` delegating to a logger. Add a test that mounts `HealthCheckServlet` and asserts 200 plus a JSON body.

<a id="cr-039"></a>
#### CR-039 — `sendError` throws, so unsupported methods return 500 instead of 405/501
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:140-147`, `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:45-46`, `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletRequest.kt:111,154`

The route has no method selector, so every HTTP method reaches `service()`. `HttpServlet`'s default `doPost` and friends call `resp.sendError(405, …)`, which throws `UnsupportedOperationException`. `POST /ping` therefore returns 500 with a stack trace. `sendRedirect`, `getPathInfo`, `getAttribute` and `isCommitted` also throw, even though each has a trivial contract value.

**Fix:**
- `sendError`: set the status, clear the buffer, and write the message.
- `sendRedirect`: 302 plus a `Location` header.
- `getPathInfo()` and `getAttribute()`: return null (back attributes with a map).
- `isCommitted`: return false.
- Add a POST-to-GET-only-servlet test that expects 405.

<a id="cr-040"></a>
#### CR-040 — `HerokuHttpsRedirect` defaults `host` to `localhost`
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt:71,139-144`

The plugin always replaces the request host with `feature.host`, which defaults to `"localhost"`. An unconfigured `install(HerokuHttpsRedirect)` sends `http://myapp.herokuapp.com/x` to `https://localhost/x` with a 301, which browsers cache permanently. Tests assert only the status code.

**Fix:** Make `host: String? = null` and override the host only when it is set. Assert the `Location` header.

<a id="cr-041"></a>
#### CR-041 — Servlet `destroy()` never called
**Severity:** Low · **Category:** lifecycle · **Carried over:** yes
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:44-45`

`HealthCheckServlet.destroy()` shuts down its registry's executor, but it is never called.

**Fix:** `application.monitor.subscribe(ApplicationStopped) { servlet.destroy() }`, or document that the caller owns cleanup.

<a id="cr-042"></a>
#### CR-042 — Servlet character encoding ignored by writer and Content-Type
**Severity:** Low · **Category:** bug · **Carried over:** yes
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:85-101`, `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:56-59`

`PrintWriter(buffer, true)` uses the platform charset, which is locale-dependent on JDK 17. `setCharacterEncoding` is stored but never used, `setContentType("…; charset=X")` doesn't update the encoding, and the route forwards no charset.

**Fix:** Build the writer with the configured charset and parse `charset=` in `setContentType`. Add a non-ASCII test.

<a id="cr-043"></a>
#### CR-043 — Parameter lookups case-insensitive; comment contradicts `getParameterMap`
**Severity:** Low · **Category:** docs / API · **Carried over:** no
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletRequest.kt:59-76`

Ktor `Parameters` is case-insensitive (verified in ktor-http 3.5.2). With `?id=1&ID=2`, `getParameterValues("id")` returns `["1","2"]`, while `parameterMap["ID"]` is null. The source comment claims all accessors agree regardless of casing.

**Fix:** Fix the comment and document the case-insensitivity, or return a case-insensitive `TreeMap` from `getParameterMap`.

<a id="cr-044"></a>
#### CR-044 — `excludeSuffix`/`excludePrefix` match query string; redirect tests assert status only
**Severity:** Low · **Category:** bug / tests · **Carried over:** no
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt:92-105`, `ktor-server-utils/README.md:98`, `ktor-server-utils/src/test/kotlin/com/pambrose/common/features/HerokuHttpsRedirectTests.kt:34-51`

The checks match `origin.uri`, which includes the query string, so `/robots.txt?v=2` isn't excluded by `excludeSuffix(".txt")`. The README example `excludeSuffix(".well-known")` should be a prefix. Tests never check `Location`, the 302 variant, a custom predicate, or a missing header.

**Fix:** Match on `call.request.path()`, fix the README, and extend the tests.

---

### service-utils

<a id="cr-045"></a>
#### CR-045 — Jetty admin/metrics paths with a leading slash become `//ping` → 404
**Severity:** Medium · **Category:** bug · **Carried over:** yes
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/ServletService.kt:52,69`, `service-utils/src/main/kotlin/com/pambrose/common/service/GenericService.kt:75-78`, `service-utils/src/main/kotlin/com/pambrose/common/service/MetricsService.kt:52,77`

`GenericKtorService` applies `ensureLeadingSlash()`, but the Jetty path always registers `"/$path"`. A config value of `"/ping"`, as used in the test fixtures, becomes `"//ping"`. That is an EXACT `ServletPathSpec` (verified in jetty-http 12.1.13), so requests for `/ping` get a 404. The same config works under Ktor and fails under Jetty.

**Fix:** Normalize once, in `ServletService`/`MetricsService` or in the servlet-group `addServlet`. Add request-level tests for both variants.

<a id="cr-046"></a>
#### CR-046 — Failed `startUp()`/`shutDown()` leaves already-started sub-services running
**Severity:** Medium · **Category:** resource leak · **Carried over:** no
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:166-201`

`startUp()` starts Zipkin, then metrics, then the admin servlet service, with no rollback. When `startUp()` throws (say the admin port is taken), Guava calls only `notifyFailed` and never `shutDown()` (verified in Guava 33.7.1). The metrics Jetty server keeps its port, and its non-daemon `QueuedThreadPool` can keep the process alive.

In `shutDown()`, a failing `servletServiceOrNull?.stopSync()` throws before metrics and Zipkin are stopped and before the hook is removed.

**Fix:** Track started sub-services and stop them in reverse order on failure. In `shutDown()`, stop each in its own `runCatching` and rethrow the first failure with the rest attached as suppressed. Add an occupied-port test.

<a id="cr-047"></a>
#### CR-047 — No test issues an HTTP request to any admin/metrics endpoint
**Severity:** Medium · **Category:** tests · **Carried over:** partly
**Where:** `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:167-202`

Both service variants are started and stopped, but nothing GETs ping, version, healthcheck, threaddump or metrics. That is why CR-038 and CR-045 pass CI. There is also no failed-startup test, and `ZipkinReporterServiceShutdownTests` leaks the real `OkHttpSender`.

**Fix:** Use a loopback JDK `HttpClient` on `freePort()` to GET each admin path for both variants, asserting status and body. Add a port-conflict startup test.

<a id="cr-048"></a>
#### CR-048 — Services added after init are silently unmanaged; test asserts the wrong thing
**Severity:** Low · **Category:** API / docs · **Carried over:** no
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:148-161,207-218`, `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:216-221`

`ServiceManager` takes an immutable copy of `services` when it is built during init. Services added later never reach the manager, the `all_services_healthy` check, or the failure listener. The base class never starts or stops them either, and none of this is documented. The existing test adds services after init and checks only `services.size`.

**Fix:** Add KDoc stating the ordering and that the caller owns start/stop. Fail fast after init. Assert against `servicesByState()`.

<a id="cr-049"></a>
#### CR-049 — `ZipkinReporterService.shutDown` drops queued spans
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/ZipkinReporterService.kt:69-75`

`AsyncReporter.close()` clears the pending queue and logs "Dropped N spans" (verified in zipkin-reporter 3.5.3). Spans recorded just before a graceful stop are lost.

**Fix:** Call `runCatching { reporter.flush() }` before `close()`, and verify the order in the MockK test.

<a id="cr-050"></a>
#### CR-050 — `DropwizardExports` registered globally, never unregistered
**Severity:** Low · **Category:** resource leak · **Carried over:** no
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:121`

The exporter is registered in the constructor and never unregistered. A stopped service keeps exporting stale values, and a second instance in the same JVM adds duplicate metric families.

**Fix:** Keep a reference to the exporter and unregister it in `shutDown()`, or register in `startUp()` instead.

<a id="cr-051"></a>
#### CR-051 — Failure logged at info; Zipkin URL `//`; no init guard
**Severity:** Low · **Category:** robustness · **Carried over:** yes
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:93-99,139,157`

- A service failure is logged at `info`.
- The Zipkin URL is built as `http://host:port/${path}`, so a leading-slash path produces `//`.
- If `initServletService()` was never called, `startUp()` fails with an opaque `UninitializedPropertyAccessException`.
- Calling init twice fails halfway through and orphans the first `ServletService`.

**Fix:** Log at `error`, use `removePrefix("/")`, and add an `initialized` flag with `check(...)` calls.

<a id="cr-052"></a>
#### CR-052 — `ZipkinConfig.serviceName` never used
**Severity:** Low · **Category:** API / docs · **Carried over:** yes
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/ZipkinConfig.kt:22,28`, `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:141`, `service-utils/src/main/kotlin/com/pambrose/common/service/ZipkinReporterService.kt:59`

The KDoc says `serviceName` identifies the service in traces, but only the URL is passed through.

**Fix:** Pass `serviceName` in and use it as the default for `newTracing()`.

<a id="cr-053"></a>
#### CR-053 — Admin and metrics servers bind all interfaces, no host setting
**Severity:** Low · **Category:** security · **Carried over:** no
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/ServletService.kt:46`, `service-utils/src/main/kotlin/com/pambrose/common/service/MetricsService.kt:48`, `service-utils/src/main/kotlin/com/pambrose/common/service/KtorServletService.kt:51`

Thread dumps, health details and `/metrics` are served unauthenticated on `0.0.0.0`, and neither config offers a way to bind to loopback.

**Fix:** Add a `host` setting to `AdminConfig`/`MetricsConfig` and pass it to the Jetty `ServerConnector` and to `embeddedServer`.

<a id="cr-054"></a>
#### CR-054 — `runBlocking` around non-suspend `start`; single-use `servletGroup` lateinits
**Severity:** Low · **Category:** cleanup · **Carried over:** no
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/KtorServletService.kt:65-69`, `service-utils/src/main/kotlin/com/pambrose/common/service/GenericService.kt:52`, `service-utils/src/main/kotlin/com/pambrose/common/service/GenericKtorService.kt:55`

`EmbeddedServer.start(Boolean)` is not a suspend function, so the `runBlocking` wrapper does nothing. Each `servletGroup` lateinit is used once and could be a local.

**Fix:** Call `ktorServer.start(wait = false)` directly, and replace the lateinits with local `val`s.

---

### prometheus-utils

<a id="cr-055"></a>
#### CR-055 — Factories hard-wired to the default `CollectorRegistry`
**Severity:** Medium · **Category:** API · **Carried over:** yes
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/dsl/PrometheusDsl.kt:38-78`, `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt:49`, `prometheus-utils/src/main/kotlin/com/pambrose/common/concurrent/InstrumentedThreadFactory.kt:37-51`

Callers can't use an isolated registry or unregister anything. Two `InstrumentedThreadFactory` instances with the same name throw from the constructor.

**Fix:** Add `registry: CollectorRegistry = CollectorRegistry.defaultRegistry` to each factory and constructor.

<a id="cr-056"></a>
#### CR-056 — `SystemMetrics.initialize` unrecoverable after partial registration
**Severity:** Low · **Category:** robustness · **Carried over:** no
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SystemMetrics.kt:61-93`

`register` throws on a duplicate name, so the call throws if `DefaultExports.initialize()` already ran. That propagates out of the `GenericService` constructor. If a later exporter fails, `initialized` stays false and every retry fails on the first exporter. When a second call asks for different exporters, it is silently ignored.

**Fix:** Register each exporter in its own `runCatching`, warn on duplicates, and set `initialized = true` in `finally`.

<a id="cr-057"></a>
#### CR-057 — `SamplerGaugeCollector` runs the sampler during construction
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt:49`

The class doesn't implement `Describable`, so registering it calls `collect()` on the constructing thread (verified in simpleclient 0.16.0). A sampler that reads uninitialized state logs a misleading NaN warning at startup.

**Fix:** Implement `Collector.Describable`.

<a id="cr-058"></a>
#### CR-058 — `InstrumentedThreadFactory` counts before null check; misleading comment
**Severity:** Low · **Category:** bug · **Carried over:** yes
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/concurrent/InstrumentedThreadFactory.kt:53-69`

`created.inc()` runs before Kotlin's generated null check. When the delegate returns null, which the `ThreadFactory` contract allows, the counter is inflated and an NPE escapes. The comment about the invariant "running + terminated < created" is also wrong.

**Fix:** Declare the return type as `Thread?` and write `delegate.newThread(...)?.also { created.inc() }`. Fix the comment.

<a id="cr-059"></a>
#### CR-059 — README `HTTPServer` example needs an unlisted artifact; weak metric tests
**Severity:** Low · **Category:** docs / tests · **Carried over:** no
**Where:** `prometheus-utils/README.md` ("Exposing Metrics"), `prometheus-utils/src/test/kotlin/com/pambrose/common/metrics/SystemMetricsTests.kt`, `prometheus-utils/src/test/kotlin/com/pambrose/common/dsl/PrometheusDslTests.kt`

`io.prometheus.client.exporter.HTTPServer` requires `simpleclient_httpserver`, which the module doesn't provide. `SystemMetricsTests` never checks that a collector is actually registered, and two DSL tests assert `shouldNotBe null` on non-null types.

**Fix:** Name the extra dependency in the README. Assert `defaultRegistry.getSampleValue(...)`.

---

### dropwizard-utils

<a id="cr-060"></a>
#### CR-060 — `newBacklogHealthCheck` captures a snapshot; message omits threshold; no `@JvmStatic`
**Severity:** Low · **Category:** API / docs · **Carried over:** yes
**Where:** `dropwizard-utils/src/main/kotlin/com/pambrose/common/util/MetricsUtils.kt:30-58`, `dropwizard-utils/src/main/kotlin/com/pambrose/common/dsl/MetricsDsl.kt:32`

- **Snapshot:** `backlogSize: Int` is captured by value, so the check returns the same result forever. The README warns about this, but the KDoc still says "current".
- **Message:** "Large size: N" never states the threshold.
- **Java callers:** without `@JvmStatic` they need `INSTANCE`.
- **Tests:** the `size == threshold` boundary is untested.

**Fix:** Add a `() -> Int` overload and deprecate the `Int` form. Include the threshold in the message, add `@JvmStatic`, and test the boundary.

---

### jetty-utils

<a id="cr-061"></a>
#### CR-061 — `LambdaServlet` returns 200/empty when the lambda throws
**Severity:** Low · **Category:** error handling · **Carried over:** yes
**Where:** `jetty-utils/src/main/kotlin/com/pambrose/common/servlet/LambdaServlet.kt:50-55`

The status is set to 200 before `block()` runs inside `writer.use`. If the lambda throws, closing the writer commits the response, so Jetty can't send a 500.

**Fix:** Evaluate `val body = block()` first, then set the status and write.

<a id="cr-062"></a>
#### CR-062 — `text/plain` without charset is encoded ISO-8859-1 on Jetty
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `jetty-utils/src/main/kotlin/com/pambrose/common/servlet/LambdaServlet.kt:43,53`, `jetty-utils/src/main/kotlin/com/pambrose/common/servlet/VersionServlet.kt:44`

Jetty infers `text/plain=iso-8859-1` (verified in jetty-http 12.1.13), so non-Latin-1 characters become `?`. The Ktor bridge behaves differently.

**Fix:** Set `characterEncoding = "UTF-8"`, or declare `text/plain; charset=utf-8` as the content type.

<a id="cr-063"></a>
#### CR-063 — `VersionServlet` duplicates `LambdaServlet`; DSL builders lack default block; `HttpServletGroup` untested
**Severity:** Low · **Category:** cleanup · **Carried over:** yes
**Where:** `jetty-utils/src/main/kotlin/com/pambrose/common/servlet/VersionServlet.kt:33-52`, `jetty-utils/src/main/kotlin/com/pambrose/common/dsl/JettyDsl.kt:34-45`, `service-utils/src/main/kotlin/com/pambrose/common/service/HttpServletGroup.kt`

`VersionServlet.doGet` is a line-for-line copy of `LambdaServlet.doGet`, so fixes like CR-061 and CR-062 have to be applied twice. `server(port, block)` and `servletContextHandler(block)` require an explicit `{}`. `HttpServletGroup` has no tests.

**Fix:** Write `class VersionServlet(v) : LambdaServlet({ v })`, add `= {}` defaults, and add `HttpServletGroupTests`.

---

### guava-utils

<a id="cr-064"></a>
#### CR-064 — `ConditionalValue.waitUntil` returns `false` for zero/sub-ms timeout even when already satisfied
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConditionalValue.kt:83-90` (also `ConditionalBoolean.waitUntilTrue/False`)

`withTimeoutOrNull(timeout.inWholeMilliseconds.milliseconds)` truncates the timeout, and `withTimeoutOrNull` returns null immediately when the timeout is ≤ 0 (verified in kotlinx-coroutines 1.11.0). So `ConditionalBoolean(true).waitUntilTrue(Duration.ZERO)` returns `false`, while `BooleanMonitor` and `BooleanWaiter` return `true` for the same call.

**Fix:** Check `predicate(flowValue.value)` first, then pass the `Duration` through without the millisecond conversion. Add tests for zero and sub-millisecond timeouts.

<a id="cr-065"></a>
#### CR-065 — `ConditionalValue` misses updates for equal/mutated values (StateFlow conflation)
**Severity:** Medium · **Category:** concurrency · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConditionalValue.kt:59-100`

`MutableStateFlow` ignores assignments that are `equals` to the current value. Mutating a list in place and calling `set(sameList)` emits nothing, so a waiter blocks until its timeout, or forever with the default `INFINITE`. Both the demo `main3` and a test use shared mutable lists.

**Fix:** Document that values must be immutable and that equal values are conflated. Use `MutableSharedFlow(replay = 1)` or an identity wrapper if identity notification is wanted.

<a id="cr-066"></a>
#### CR-066 — `GenericMonitor` untimed waits double-`leave()`, masking the real exception
**Severity:** Medium · **Category:** concurrency / error handling · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericMonitor.kt:61-81,130-135`

When a guard's `isSatisfied()` throws, Guava's `enterWhenUninterruptibly(guard)` already calls `leave()` in its own `finally` (verified in Guava 33.7.1 `Monitor.java`, lines 571-590). `waitUntilTrue()` and `waitUntilFalse()` then call `leave()` again unconditionally. `unlock()` throws `IllegalMonitorStateException`, which replaces the subclass's exception. `waitUntilTrueWithInterruption()` has a similar double release when a subclass already holds the monitor.

**Fix:** Call `leave()` only after `enter…` returns normally, as the timed variants already do. Add a test with a throwing `monitorSatisfied`.

<a id="cr-067"></a>
#### CR-067 — `GenericMonitor` retry loops overrun `maxWait`, treat ZERO as unlimited, can busy-spin
**Severity:** Medium · **Category:** bug · **Carried over:** partly (triplicated loop, `(-1).seconds` sentinel)
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericMonitor.kt:167-306`

- **Overrun:** each pass waits the full `timeout` before checking `maxWait`, so `timeout = 10s, maxWait = 1s` blocks for about 10 s.
- **ZERO means unlimited:** `maxWait > 0.seconds` treats `Duration.ZERO` as no limit.
- **Busy-spin:** a sub-millisecond `timeout` truncates to 0, and with `block = null` the loop spins at 100% CPU.

**Fix:** Move the loop into one helper that waits `minOf(timeout, maxWait - elapsed)`. Use `Duration.INFINITE` for no limit and require `timeout ≥ 1ms`.

<a id="cr-068"></a>
#### CR-068 — `GenericMonitor` doesn't require state changes inside the monitor
**Severity:** Low · **Category:** docs · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericMonitor.kt:34-55`

Guava re-evaluates guards only on `leave()` or on a wait. A subclass that changes `monitorSatisfied` state outside `enter()`/`leave()` strands its waiters forever.

**Fix:** Document the rule and provide a `protected inline fun <T> mutate(block: () -> T)` helper.

<a id="cr-069"></a>
#### CR-069 — `startSync`/`stopSync` hide `TimeoutException`; no `@Throws`; inconsistent params
**Severity:** Low · **Category:** API / docs · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericExecutionThreadService.kt:30-48`, `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericIdleService.kt:30-48`

- **Exceptions:** Guava throws `TimeoutException` (checked) and `IllegalStateException`. Without `@Throws`, Java callers can't catch the timeout.
- **KDoc:** it reads as if a timeout returns normally.
- **Parameters:** the two classes use different names and defaults (`timeout` 30 s vs `maxWait` 15 s).

**Fix:** Add `@Throws(TimeoutException::class)` and document both exceptions. Unify the parameter names and defaults.

<a id="cr-070"></a>
#### CR-070 — Sub-ms durations truncate to 0; `VerboseCountDownLatch` log flood
**Severity:** Low · **Category:** bug · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/VerboseCountDownLatch.kt:62-67`, `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConcurrentExtensions.kt:51`

With a timeout below 1 ms, `await(0, MILLISECONDS)` returns immediately, and the verbose loop then logs at INFO in a tight spin.

**Fix:** Convert with `inWholeNanoseconds` and `NANOSECONDS`, and `require(timeout.isPositive())` in the verbose loop.

<a id="cr-071"></a>
#### CR-071 — `GenericValueWaiter.currValue` settable without notification; unused `initValue`
**Severity:** Low · **Category:** API / concurrency · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericValueWaiter.kt:86,91`

`protected var currValue` lets a subclass assign it without the lock or `checkCondition`, so waiters hang. The field isn't `@Volatile`. `initValue` is stored but never read after construction.

**Fix:** Give the property a `private set` (or a locked getter), and drop `val` from `initValue`.

<a id="cr-072"></a>
#### CR-072 — `ConditionalValue.set` still `yield()`s
**Severity:** Low · **Category:** API · **Carried over:** yes
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConditionalValue.kt:92-100`

`yield()` doesn't guarantee that waiters run, and it forces `set` to be `suspend`.

**Fix:** Remove `yield()` and consider a non-suspending `set`, noting that this is a binary-compatibility change. Document conflation.

<a id="cr-073"></a>
#### CR-073 — Demo `main()`s in published JAR; stale Kover exclusion
**Severity:** Low · **Category:** cleanup · **Carried over:** yes (demos)
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConditionalValue.kt:103-186`, `build.gradle.kts:94-97`

`main`, `main2`, `main3` and `main4` ship publicly in `ConditionalValueKt`. `koverExcludeClasses` lists `GenericValueWaiterKt*`, but that facade class no longer exists; confirmed against the compiled classes, and `GenericValueWaiter.kt` has no top-level functions.

**Fix:** Move the demos to `src/test` or a samples source set, and delete both Kover exclusions.

<a id="cr-074"></a>
#### CR-074 — `GuavaDsl` callbacks throw on reassignment; `starting(null)` KDoc false
**Severity:** Low · **Category:** API / docs · **Carried over:** yes
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/dsl/GuavaDsl.kt:72-74,132-136,166-173`

Each callback slot uses `singleAssign()`, so calling `running {}` twice throws, which is undocumented. `starting(null)` claims to "clear", but it actually consumes the slot.

**Fix:** Use plain `@Volatile` vars, or document single assignment. Make `starting`'s parameter non-null.

<a id="cr-075"></a>
#### CR-075 — `EMPTY_BYTE_ARRAY` is public
**Severity:** Low · **Category:** API · **Carried over:** yes
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/util/ZipExtensions.kt:28-29`

**Fix:** Make it `private` or `internal`.

<a id="cr-076"></a>
#### CR-076 — `unzip()` has no decompressed-size limit
**Severity:** Low · **Category:** security · **Carried over:** no
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/util/ZipExtensions.kt:82-101`

A small gzip bomb expands into a `String` until the JVM throws `OutOfMemoryError`. Corrupt input throws `ZipException`/`EOFException`, and neither is documented. There is no in-repo caller, so exposure depends on consumers.

**Fix:** Add an optional `maxBytes` enforced with `ByteStreams.limit`, and document the exceptions.

<a id="cr-077"></a>
#### CR-077 — guava-utils README misdescribes `genericServiceListener` and logging factories
**Severity:** Low · **Category:** docs · **Carried over:** no
**Where:** `guava-utils/README.md:61-62,140-150,239-241`

`genericServiceListener(logger)` only builds a listener. Following the README drops the result, and nothing gets logged; every real call site wraps it in `addListener(..., directExecutor())`. The `debug`/`info`/`warn`/`error` factories belong to the `BooleanMonitor` companion, not `GenericMonitor`.

**Fix:** Show `addListener(service.genericServiceListener(logger), MoreExecutors.directExecutor())`, and move the factories to the `BooleanMonitor` entry. The llms.txt problems are tracked in [CR-136](#cr-136).

<a id="cr-078"></a>
#### CR-078 — No timed-wakeup, `maxWait`-elapsed, or service-helper tests; stale comment
**Severity:** Low · **Category:** tests · **Carried over:** no
**Where:** `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/GenericMonitorTests.kt:47-96`, `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/GenericValueWaiterTests.kt:158-159`

- **No blocked-wait test:** the "timeout returns true" tests join the setter thread before waiting, so no test has a thread blocked in a timed wait that `set()` wakes.
- **`maxWait` elapsed time:** never asserted.
- **Service helpers:** no tests for `startSync`/`stopSync` timeouts or for `genericServiceListener`.
- **Stale comment:** it still describes the removed `monitorSatisfied` implementation.

**Fix:** Add blocked-waiter and elapsed-time tests plus regression tests for CR-064 through CR-067, and fix the comment.

---

### zipkin-utils

<a id="cr-079"></a>
#### CR-079 — `ZipkinDsl` tests never verify the config block; leak `Tracing` on failure
**Severity:** Low · **Category:** tests · **Carried over:** yes
**Where:** `zipkin-utils/src/test/kotlin/com/pambrose/common/dsl/ZipkinDslTests.kt:28-91`

`tracing shouldNotBe null` on a non-null type proves nothing; dropping `.apply(block)` would still pass. Each test calls `close()` without `try`/`finally`, and `Tracing.current()` is global state.

**Fix:** Assert `tracing.sampler() shouldBe Sampler.NEVER_SAMPLE` after configuring it, and wrap each test in `.use {}`.

<a id="cr-080"></a>
#### CR-080 — zipkin-utils and redis-utils have no README
**Severity:** Low · **Category:** docs · **Carried over:** no
**Where:** `zipkin-utils/`, `redis-utils/`, `llms.txt:26,33`

Every other module has a README, and llms.txt links to both missing files.

**Fix:** Add both READMEs, or remove the links; see [CR-136](#cr-136).

---

### script-utils (common / java / kotlin / python)

<a id="cr-081"></a>
#### CR-081 — Pooled engines lost forever when a waiting borrower is cancelled
**Severity:** Medium · **Category:** concurrency / leak · **Carried over:** no
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScriptPool.kt:38-59`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluatorPool.kt:36-80`

Both pools borrow with `channel.receive()` on a `Channel(size)` that has no `onUndeliveredElement`. The kotlinx docs say a suspended `receive` that is cancelled just as it gets an element throws, and the element is lost (verified in kotlinx-coroutines 1.11.0). If a waiter is cancelled (timeout, client disconnect) at the moment another caller recycles an engine, the pool permanently shrinks by one. At zero, every `eval` suspends forever and every `blockingEval` blocks forever.

**Fix:** Pass `Channel(size, onUndeliveredElement = { channel.trySend(it) })`, or use a `Semaphore` plus a `ConcurrentLinkedQueue`. Add a cancellation test.

<a id="cr-082"></a>
#### CR-082 — Expression evaluators and their pools have no JVM-exit guard
**Severity:** Medium · **Category:** security · **Carried over:** no
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluator.kt:32-46`; affects `KotlinExprEvaluator`/`PythonExprEvaluator` and their pools

`KotlinScript`, `JavaScript` and `PythonScript` all run `ScriptGuards`, but `AbstractExprEvaluator.eval`/`compute` pass the input straight to `engine.eval`. Evaluators are the API most likely to receive user-supplied predicates, and `KotlinExprEvaluatorPool(5).blockingEval("kotlin.system.exitProcess(0) == Unit")` terminates the host JVM. No KDoc warns about this.

**Fix:** Add an overridable `checkExpr(expr)` hook called from `eval`/`compute`, implemented in the Kotlin and Python evaluators. Add the "not a sandbox" KDoc and tests.

<a id="cr-083"></a>
#### CR-083 — Auto-imported `System` shadow breaks all other `System.*` calls in Kotlin scripts
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/System.kt:27-35`, `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:46,74`

Every script gets `import com.pambrose.common.script.System` prepended. That object defines only `exit`, and an explicit import outranks the default `java.lang.*` import. So `System.currentTimeMillis()`, `System.getenv()`, `System.getProperty()` and `System.out` all fail with "unresolved reference" (verified with kotlinc). `ScriptGuards.checkNoJvmExit` already rejects literal `System.exit(`, so the shadow is redundant.

**Fix:** Drop the shadow import. Add a test for `eval("System.currentTimeMillis() > 0")`.

<a id="cr-084"></a>
#### CR-084 — `KotlinScript` generates uncompilable casts (`Regex`, `listOf`, nested generics)
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:56-63`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:81`

The generated cast is built from the runtime class's qualified name with `kotlin.` stripped off. That fails in three ways (each confirmed with kotlinc):
- **Sub-packages:** `Regex` becomes `text.Regex`, which doesn't resolve.
- **Private runtime classes:** `listOf(1,2)` becomes `java.util.Arrays.ArrayList`, which is private.
- **Nested type arguments:** `List<String>` renders as `collections.List<kotlin.String>`.

`add()` accepts these values, and the first `eval` then fails with a compile error in generated code.

**Fix:** Emit fully-qualified names, and cast to the declared `KType` rather than the runtime class, e.g. `inline fun <reified T> add(name, value: T)`. Add tests for `listOf`, `emptyList`, `Regex` and a nested generic.

<a id="cr-085"></a>
#### CR-085 — `JavaScript` imports/isolation leak across pool borrowers and grow unbounded
**Severity:** Medium · **Category:** bug / leak · **Carried over:** no
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:46,79-91`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:62-67`, `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScriptPool.kt:32-41`

`resetContext` (final) clears the values, types and engine context, but not `JavaScript.imports` or the engine's `isolation`. As a result:
- **Unbounded growth:** each borrower's `import(...)` calls accumulate forever, so after N borrows every eval compiles an N-line header.
- **Conflicting imports:** a `java.util.List` import followed by a later borrower's `java.awt.List` is a javac error.
- **Isolation carry-over:** one borrower's `assignIsolation` silently applies to the next.

**Fix:** Add an overridable reset hook that clears imports and restores default isolation. `resetContext` runs from `AbstractScript`'s `init`, before `JavaScript.imports` is initialized, so call the hook only from the pool's recycle path or make it null-tolerant. Add a pool test.

<a id="cr-086"></a>
#### CR-086 — Kotlin expression evaluator pools never reset; REPL history grows forever
**Severity:** Medium · **Category:** performance / leak · **Carried over:** no
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluatorPool.kt:47,72-80`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluator.kt:32-46`

The Kotlin JSR-223 engine appends every compiled snippet's class and instance to an untrimmed `history` in `ENGINE_SCOPE` (verified in kotlin-scripting 2.4.10). `AbstractScriptPool` resets on recycle, but `AbstractExprEvaluatorPool` never does. A long-lived pool therefore retains every generated class, and each eval gets slower: O(n) per eval, O(n²) overall.

**Fix:** Reset the context on recycle, or every N evaluations. Document the accumulation for standalone evaluators.

<a id="cr-087"></a>
#### CR-087 — Variables added after first `eval` never bound (Java silently returns default); `add()` unsynchronized
**Severity:** Medium · **Category:** bug / concurrency · **Carried over:** yes (Python only in May; affects all three engines)
**Where:** `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:80-86`, `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:125-128,169-172`, `script-utils-python/src/main/kotlin/com/pambrose/common/script/PythonScript.kt:52-79`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:95-130`

Bindings are flushed only while `initialized` is false:
- **Kotlin and Python:** `add("a",1); eval("a"); add("b",2); eval("b")` fails with an unresolved-name error.
- **Java:** worse, because it doesn't fail. The regenerated class declares `public int b;` but `b` was never bound, so `eval("b")` silently returns `0`.
- **Thread safety:** `eval` is `@Synchronized`, but `add`, `PythonScript.add` and `resetContext` are not, and they mutate the maps `eval` iterates.

**Fix:** In `add()` after initialization, bind only the new name. Don't re-flush everything, which would overwrite values the Java engine pulled back. Synchronize `add` and `resetContext`, or document one thread per instance.

<a id="cr-088"></a>
#### CR-088 — Python guard blocks harmless `sys.exit` but misses `java.lang.System.exit`
**Severity:** Low · **Category:** security · **Carried over:** no
**Where:** `script-utils-python/src/main/kotlin/com/pambrose/common/script/PythonScript.kt:60-74,91-94`

Under JSR-223, `sys.exit()`, `exit()` and `quit()` surface as a `ScriptException`; `PyScriptEngine` catches the `PyException` (verified in Jython 2.7.4). But `from java.lang import System; System.exit(0)` and `Runtime.getRuntime().halt(0)` really do kill the JVM, and they pass the guard. `ScriptGuards.checkNoJvmExit` isn't called here.

**Fix:** Call `ScriptGuards.checkNoJvmExit(code)` in `PythonScript.eval`. Clarify in the KDoc what the Python-specific patterns actually prevent.

<a id="cr-089"></a>
#### CR-089 — Python guard KDoc inverted about string literals; `def exit(self)` rejected
**Severity:** Low · **Category:** docs / bug · **Carried over:** no
**Where:** `script-utils-python/src/main/kotlin/com/pambrose/common/script/PythonScript.kt:30-34,93-94`

The KDoc says the guard "does not inspect string literals", but the regexes do match inside strings, so `print('call exit(1)')` is rejected. The KDoc also says user methods like `obj.exit()` are allowed, yet `def exit(self):` is rejected.

**Fix:** Reword the KDoc. Exclude `def` definitions or document the limitation, and add a test.

<a id="cr-090"></a>
#### CR-090 — Pools accept `size <= 0` and hang; `AbstractExprEvaluatorPool<T>` ignores `T`
**Severity:** Low · **Category:** robustness / API · **Carried over:** no
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScriptPool.kt:33-38`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluatorPool.kt:32-36`

`Channel(0)`, `Channel(-1)` and `Channel(-2)` are the rendezvous, conflated and buffered special values. With those sizes the pool creates no instances, and `eval` hangs forever. The expression pool also declares `Channel<AbstractExprEvaluator>`, so its `T` parameter is unused.

**Fix:** Add `require(size > 0)` in both base classes, and use `Channel<T>`.

<a id="cr-091"></a>
#### CR-091 — Pools never close their instances
**Severity:** Low · **Category:** resource leak · **Carried over:** no
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScriptPool.kt:33-60`, `script-utils-python/src/main/kotlin/com/pambrose/common/script/PythonScriptPool.kt:31-40`

`PythonScript.close()` releases a per-engine `PySystemState`, but neither pool can be closed. Per-request or per-test pools accumulate interpreters. If the constructor fails partway through, the instances already created leak.

**Fix:** Make the pools `Closeable`: close the channel, then drain it and close each instance. Clean up on a partial constructor failure.

<a id="cr-092"></a>
#### CR-092 — Expression evaluators share the manager's global `Bindings`
**Severity:** Low · **Category:** concurrency / API · **Carried over:** no
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEngine.kt:36-41`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluator.kt:22-24`

`ScriptEngineManager.getEngineByExtension` installs the manager's single `SimpleBindings` as each engine's GLOBAL_SCOPE (verified in the JDK sources). `AbstractScript` replaces it, but `AbstractExprEvaluator` doesn't, so every evaluator in the JVM shares one unsynchronized map.

**Fix:** Call `engine.resetContext()` in the evaluator's `init`.

<a id="cr-093"></a>
#### CR-093 — Binding names unvalidated and spliced raw into generated source
**Severity:** Low · **Category:** security / error handling · **Carried over:** no
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:95-130`, `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:62,82-83`, `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:61,155-161`

Invalid identifiers such as `my-var` or `class` produce confusing compile errors. A name like `x = 0; Runtime.getRuntime().halt(0); val y` executes, because `ScriptGuards` scans the code but never the generated `varDecls`.

**Fix:** In `add()`, require a valid, non-keyword identifier.

<a id="cr-094"></a>
#### CR-094 — `javaEquiv` emits invalid Java for `Char`, `Any`, nested generics
**Severity:** Low · **Category:** bug · **Carried over:** yes
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:93-107`

Only `Int` is mapped to a Java type. `Char` stays `Char`, `Any` stays `Any`, and `List<Int>` becomes `collections.List<kotlin.Int>`. All of these are javac errors.

**Fix:** Map from the `KType` classifier via `javaObjectType.name` and render type arguments recursively.

<a id="cr-095"></a>
#### CR-095 — `JavaScript.varDecls` uses runtime `simpleName`; raw `IllegalArgumentException` escapes
**Severity:** Low · **Category:** bug / error handling · **Carried over:** yes
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:53-65`

`listOf(1,2)` has the runtime class `Arrays$ArrayList`, whose `simpleName` is `ArrayList`. With `java.util.ArrayList` imported, `Field.set` throws `IllegalArgumentException`, which java-scriptengine doesn't wrap in `ScriptException`.

**Fix:** Emit the nearest public canonical type, and wrap engine failures in `ScriptException`.

<a id="cr-096"></a>
#### CR-096 — No-op `close()`, undocumented `eval()`, undocumented `evalScript` field contract
**Severity:** Low · **Category:** docs / API · **Carried over:** yes
**Where:** `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:76-94`, `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:109-136,180-182`, `script-utils-python/src/main/kotlin/com/pambrose/common/script/PythonScript.kt:60`

`KotlinScript.close()` and `JavaScript.close()` are `// Placeholder`. `KotlinScript.eval` and `PythonScript.eval` have no KDoc. `evalScript` requires every bound variable to be a public field of the user's class, which is undocumented.

**Fix:** Implement `close()` or document it as a no-op, add KDoc to both `eval` methods, and document the field contract.

<a id="cr-097"></a>
#### CR-097 — Public `engine`, `toTempName`, dead `error()`, duplicated binding flush
**Severity:** Low · **Category:** cleanup · **Carried over:** yes
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEngine.kt:36`, `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:59,68`, `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:125-128,169-172`

- **`engine`:** public, so callers can bypass the bindings bookkeeping.
- **`toTempName`:** `internal` with a bare `_tmp` suffix.
- **Dead `error("No qualified name…")`:** it can't be reached.
- **Duplicated flush:** the binding-flush block is copied twice in `JavaScript`.

**Fix:** Make `engine` `protected`, extract `ensureBindings()`, remove the dead code, and make `toTempName` private.

<a id="cr-098"></a>
#### CR-098 — Misleading guard tests; missing coverage for the defects above
**Severity:** Low · **Category:** tests · **Carried over:** partly
**Where:** `script-utils-java/src/test/kotlin/com/pambrose/common/script/JavaScriptTests.kt:213-221`, `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/KotlinScriptTests.kt:263`, `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/ScriptPoolContractTests.kt`

- **Java "illegal calls" test:** passes only because Python syntax doesn't compile as Java.
- **Kotlin guard test:** uses `com.lang.System.exit(1)`, a nonexistent package.
- **Missing tests:** CR-081 through CR-093.

**Fix:** Fix both guard tests and add a Kotest case per defect, using `withTimeout` for the pool tests.

---

### email-utils

<a id="cr-099"></a>
#### CR-099 — Webhook models can't decode real Resend payloads with a default `Json`
**Severity:** Medium · **Category:** bug / tests · **Carried over:** no
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/webhook/Data.kt:36-55`, `email-utils/src/main/kotlin/com/pambrose/common/webhook/Bounce.kt:27-31`, `email-utils/src/main/kotlin/com/pambrose/common/webhook/ResendWebhookMsg.kt:29-39`

Resend's documented `email.bounced` payload contains fields the models don't declare (verified against resend.com/docs/webhooks/emails/bounced on 2026-09-14):
- `data`: `broadcast_id`, `message_id`, `template_id`, `tags`.
- `bounce`: `subType`, `type`.

The library ships no `Json` instance and doesn't document `ignoreUnknownKeys`, so decoding with a default `Json` throws. Even with lenient decoding, the bounce classification (`Permanent`/`Suppressed`) is lost. Tests round-trip only the models' own output. Click's camelCase fields are correct and should not change.

**Fix:**
- Add the missing nullable fields.
- Ship a preconfigured `ResendWebhookMsg.decode(body)` that sets `ignoreUnknownKeys`.
- Test against the verbatim documented payloads.
- Consider adding Svix signature verification.

<a id="cr-100"></a>
#### CR-100 — Default `email()` stylesheet exists only in test resources
**Severity:** Low · **Category:** API · **Carried over:** no
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/EmailUtils.kt:69-94`

`cssFilename` defaults to `"css/email.css"`, which exists only in `src/test/resources`. A consumer using the default gets `IllegalArgumentException`.

**Fix:** Ship a default stylesheet in main resources, make the parameter nullable, or document the requirement.

<a id="cr-101"></a>
#### CR-101 — `Parameters.getEmail` doesn't normalize
**Severity:** Low · **Category:** API · **Carried over:** yes
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/Email.kt:72`

`toResendEmail()` lowercases and trims, but `getEmail()` wraps the raw value, so the same address compares unequal depending on which path produced it.

**Fix:** Route `getEmail()` through `toResendEmail()`, or document why it doesn't.

<a id="cr-102"></a>
#### CR-102 — `sendEmail` logs-then-rethrows; logs recipient addresses at INFO
**Severity:** Low · **Category:** logging / privacy · **Carried over:** partly
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/ResendService.kt:53-72`

Callers who log the rethrown exception get it twice. Every to/cc/bcc address is written to INFO logs, which puts PII in routine output.

**Fix:** Drop the failure log or lower it to debug. Log recipient counts and the response id at INFO.

---

### recaptcha-utils

<a id="cr-103"></a>
#### CR-103 — `verifyRecaptcha` swallows `CancellationException`
**Severity:** Medium · **Category:** concurrency · **Carried over:** no
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:121-124,154-161`

`catch (e: Exception)` also catches cancellation, for example on client disconnect or shutdown. The cancellation is logged at ERROR and converted to `false`, and `validateRecaptcha` then keeps responding from a cancelled coroutine.

**Fix:** Rethrow `CancellationException` before the generic catch, or use core-utils' `runCatchingCancellable`.

<a id="cr-104"></a>
#### CR-104 — `remoteip` sends a reverse-DNS hostname
**Severity:** Low · **Category:** bug / performance · **Carried over:** no
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:153`

`origin.remoteHost` can trigger a blocking reverse lookup on the CIO engine (verified in Ktor 3.5.2), and it sends a hostname where Google expects an IP.

**Fix:** Use `origin.remoteAddress`.

<a id="cr-105"></a>
#### CR-105 — Enabled-but-misconfigured fails open silently; duplicated dead gate
**Severity:** Low · **Category:** security / cleanup · **Carried over:** yes (gate)
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:89-92,142,209-212`

With `isRecaptchaEnabled = true` but a key missing, `validateRecaptcha` returns `true` without logging, so there is no bot protection and no warning. The inner gate in `verifyRecaptcha` is unreachable.

**Fix:** Log a WARN once (or fail closed) when enabled but misconfigured, and remove the inner gate.

---

### redis-utils

<a id="cr-106"></a>
#### CR-106 — `withRedis` family never takes its documented connection-failure path
**Severity:** Medium · **Category:** bug / docs · **Carried over:** no
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:112-119,283-373`

`RedisClient.builder().build()` doesn't connect (verified in jedis 8.0.1), so the `catch (JedisConnectionException)` around `createRedisClient` can never fire. When Redis is down, the block receives a non-null client and the first command throws, breaking the documented "passes null / returns null" promise. The null-path tests pass only because they mock the private `createRedisClient` to throw. `RedisUtilsMockTests.kt:245` gets a non-null client with no server running.

**Fix:** Call `ping()` inside the `try` after creating the client, as the pool variants already do, and close the client on failure. Test against an unreachable port.

<a id="cr-107"></a>
#### CR-107 — URL parsing drops the database index and protocol
**Severity:** Medium · **Category:** bug · **Carried over:** no
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:85-119,167-174`

Only host, port and userinfo are read. `redis://h:6379/3` silently connects to database 0, and `?protocol=` is ignored. Jedis's `DefaultJedisClientConfig.builder(URI)` already handles both.

**Fix:** Start from the URI-aware Jedis builder and layer the timeouts and placeholder rules on top. Add a DB-index test.

<a id="cr-108"></a>
#### CR-108 — `"none"` placeholder password still sent as AUTH
**Severity:** Low · **Category:** bug · **Carried over:** yes
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:66,80-107`

The `"none"` check suppresses only the username, so the default URL `redis://user:none@localhost:6379` sends the password `none`. On the legacy `AUTH none` path, a server with no password returns an error. Confidence is medium: the server response depends on the Redis version and handshake.

**Fix:** Treat `password == "none"` as no password, or change the default URL to `redis://localhost:6379`.

<a id="cr-109"></a>
#### CR-109 — Pool helpers catch only `JedisConnectionException`
**Severity:** Low · **Category:** error handling · **Carried over:** no
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:191-269`

Pool exhaustion and borrow-validation failures surface as a plain `JedisException`, and auth failures as `JedisAccessControlException` (verified in jedis 8.0.1). All of these skip the documented null path.

**Fix:** Catch `JedisException`, and add tests with `maxPoolSize = 1`.

<a id="cr-110"></a>
#### CR-110 — `testOnBorrow` + `testOnReturn` add two PINGs per command
**Severity:** Low · **Category:** performance · **Carried over:** no
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:159-161`

Each pooled command costs three round-trips, and `withRedisPool`'s own `ping()` adds three more.

**Fix:** Drop `testOnReturn`, rely on `testWhileIdle`, or make these settings configurable.

<a id="cr-111"></a>
#### CR-111 — SSL scheme detection uses default-locale `lowercase`
**Severity:** Low · **Category:** security · **Carried over:** no
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:91`

On a Turkish-locale JVM, `REDISS://` lowercases to `redıss://` (dotless ı). SSL isn't detected, and the password is sent in plaintext.

**Fix:** Use `URI(redisUrl).scheme.equals("rediss", ignoreCase = true)`.

<a id="cr-112"></a>
#### CR-112 — Pool-size validation rejects `-1`, accepts `0`
**Severity:** Low · **Category:** API · **Carried over:** no
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:142-145`

In commons-pool2, `-1` means unlimited, but it is rejected. `0` is accepted and creates a pool that can never lend. The ordering `minIdle ≤ maxIdle ≤ maxTotal` isn't validated.

**Fix:** Require `> 0` (or allow `-1` explicitly), and validate the idle ordering.

---

### exposed-utils

<a id="cr-113"></a>
#### CR-113 — `ResultRow.get(index)` / `toRowString()` throw on SQL NULL
**Severity:** Medium · **Category:** bug / docs · **Carried over:** no
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/ExposedUtils.kt:63-77`

`?.let { this[it.key] } ?: throw` treats a legitimately null column value as "no value at index", so the lookup throws. `toRowString()` resolves `this[it]` (an `Int`) to the same extension, so any row containing a NULL throws. That contradicts its own KDoc ("null renders as `null`"). The lookup is also O(n²) in the column count, and no test covers a nullable column.

**Fix:** Look up the expression first, throw only when it is absent, then return `this[expr]`. Iterate `fieldIndex.keys` in `toRowString`. Add an H2 test with a nullable column.

<a id="cr-114"></a>
#### CR-114 — `upsert(conflictIndex)` doesn't validate the index; hides native options; stale KDoc
**Severity:** Low · **Category:** API / docs · **Carried over:** no
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:24-33`

A non-unique index, or an index from another table, fails at runtime on PostgreSQL. The overload hides Exposed's `onUpdate`, `onUpdateExclude` and `where` parameters. The KDoc references a `PostgresTables.kt` that doesn't exist.

**Fix:** `require(conflictIndex.unique && conflictIndex.table == this)`, forward the options, and fix the KDoc.

---

### grpc-utils

<a id="cr-115"></a>
#### CR-115 — `serverTlsContext()` returns a builder without ALPN, which gRPC's Netty server rejects
**Severity:** Medium · **Category:** bug / API · **Carried over:** no
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/utils/TlsUtils.kt:157-212`

The client side starts from `GrpcSslContexts.forClient()`, but `serverTlsContext` starts from plain `SslContextBuilder.forServer(...)`; only `buildServerTlsContext` applies `GrpcSslContexts.configure`. The KDoc invites callers to customize the builder and build it themselves. Doing so yields a context without ALPN, and `NettyServerBuilder.sslContext` then throws "ALPN must be configured" (verified in grpc-netty 1.84.0).

**Fix:** Use `GrpcSslContexts.forServer(certFile, keyFile)`. Add a test that passes a hand-built context to `GrpcDsl.server`.

<a id="cr-116"></a>
#### CR-116 — `enableRetry = false` doesn't disable retries
**Severity:** Low · **Category:** bug / docs · **Carried over:** yes (May's diagnosis was wrong)
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:53-54,113-117`

grpc-java enables retry by default (verified in grpc-core 1.84.0), and the DSL never calls `disableRetry()`, so the flag does nothing. The in-process branch silently ignores the retry and authority options.

**Fix:** `if (enableRetry) builder.enableRetry() else builder.disableRetry()`, or make the parameter `Boolean?`. Fix the KDoc.

<a id="cr-117"></a>
#### CR-117 — `shutdownWithJvm` validates its timeout only inside the hook
**Severity:** Low · **Category:** robustness · **Carried over:** no
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/utils/ServerExtensions.kt:30-42,71`

`shutdownWithJvm(Duration.ZERO)` registers without error. At JVM exit, the `require(timeout > 0)` inside the hook throws before `shutdown()` runs.

**Fix:** Validate when the hook is registered. In the hook, catch `Exception` and still call `shutdownNow()`.

<a id="cr-118"></a>
#### CR-118 — README says all client TLS paths are optional; trust path is required
**Severity:** Low · **Category:** docs / API · **Carried over:** no
**Where:** `grpc-utils/README.md:132-138`, `grpc-utils/src/main/kotlin/com/pambrose/common/utils/TlsUtils.kt:114`

`require(trustPath.isNotEmpty())` contradicts the README. It also rules out public-CA servers that rely on the JVM trust store. The builder functions aren't documented.

**Fix:** Fall back to the default trust manager when the path is empty, or correct the README. Document the builders.

<a id="cr-119"></a>
#### CR-119 — `channel()` has no `tlsContext` default
**Severity:** Low · **Category:** API · **Carried over:** yes
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:61-69`

**Fix:** Default it to `PLAINTEXT_CONTEXT`, matching `server()`.

<a id="cr-120"></a>
#### CR-120 — `streamObserver` exposes helper type and set-once callbacks
**Severity:** Low · **Category:** API · **Carried over:** yes
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:189-227`

The inferred return type is `StreamObserverHelper<T>`, and registering a callback twice throws. Neither is documented.

**Fix:** Declare the return type as `StreamObserver<T>`. Document single assignment or use nullable vars.

---

### Cross-module test scaffolding

<a id="cr-121"></a>
#### CR-121 — ktor-server `ServletRoute` lacks init/405/non-ASCII tests
**Severity:** Low · **Category:** tests · **Carried over:** no
**Where:** `ktor-server-utils/src/test/kotlin/com/pambrose/common/servlet/ServletRouteTests.kt`

This is the regression net for CR-038, CR-039, CR-041 and CR-042:
- a servlet that overrides `init(ServletConfig)`;
- a POST to a GET-only servlet (expect 405);
- non-ASCII output;
- `destroy()` running on application stop.

**Fix:** Add one spec per scenario.

<a id="cr-122"></a>
#### CR-122 — Redis null-path tests mock a private function the real code never exercises
**Severity:** Low · **Category:** tests · **Carried over:** no
**Where:** `redis-utils/src/test/kotlin/com/pambrose/common/redis/RedisUtilsMockTests.kt:245-253` and the `mockkObject` null-path specs

The tests force `createRedisClient` to throw, a path production code can't reach (CR-106). They should exercise real failure modes: an unreachable port, pool exhaustion and auth failure.

**Fix:** Replace the mocked throws with loopback or unreachable-port scenarios once CR-106 is fixed.

<a id="cr-123"></a>
#### CR-123 — grpc TLS/retry tests assert only `authority()` / `isServer`
**Severity:** Low · **Category:** tests · **Carried over:** no
**Where:** `grpc-utils/src/test/kotlin/com/pambrose/common/utils/TlsUtilsTests.kt`, `grpc-utils/src/test/kotlin/com/pambrose/common/dsl/GrpcDslTests.kt`

"serverTlsContext returns a buildable builder" checks only `isServer`, and the TLS/retry channel test checks only `authority()`. Neither would catch CR-115 or CR-116.

**Fix:** Pass a hand-built server context to `GrpcDsl.server`, and assert the retry configuration.

---

### Build & publishing

<a id="cr-124"></a>
#### CR-124 — service-utils exposes `implementation` dependency types in its public API
**Severity:** High · **Category:** publishing · **Carried over:** no
**Where:** `service-utils/build.gradle.kts:5-15`; API surface in `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:62-99`, `ZipkinReporterService.kt:40-59`, `HttpServletGroup.kt:35`, `KtorServletService.kt:44-48`

`AbstractGenericService` extends guava-utils' `GenericExecutionThreadService`, and its public or protected members expose:
- Dropwizard `HealthCheckRegistry`, `MetricRegistry` and `JmxReporter`;
- Brave `Tracing`;
- Ktor `Application`;
- Jakarta `HttpServlet`.

All of these come in through `implementation` dependencies, which the published `service-utils-3.2.3.pom` lists as `<scope>runtime</scope>` (verified in `~/.gradle/caches`). A consumer subclassing `GenericService` fails with "Cannot access class … check your module classpath" unless it adds those modules itself.

**Fix:**
- Switch these to `api`: guava-utils, dropwizard-utils, zipkin-utils, ktor-server-utils, jetty-utils and `libs.dropwizard.jmx`.
- Keep true internals (okhttp sender, CIO, compression, call-logging, prometheus servlet) as `implementation`.
- Add a consumer-style compile check, e.g. a tiny test-fixture subclass that uses only an `api` classpath.

<a id="cr-125"></a>
#### CR-125 — grpc-utils (Netty `SslContext`) and script-utils-java (`Isolation`) leak `implementation` types
**Severity:** Medium · **Category:** publishing · **Carried over:** no
**Where:** `grpc-utils/build.gradle.kts:8` with `grpc-utils/src/main/kotlin/com/pambrose/common/utils/TlsUtils.kt:36,47`; `script-utils-java/build.gradle.kts:7` with `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:89`

- `TlsContext.sslContext: SslContext?` and `TlsContextBuilder.builder: SslContextBuilder` are Netty types that arrive through runtime-scoped grpc-netty (verified in the grpc-utils 3.2.3 POM).
- `assignIsolation(Isolation)` takes a type from runtime-scoped java-scriptengine.

**Fix:** Use `api(libs.grpc.netty)` and keep the rest of the bundle as `implementation`. Make java-scriptengine `api`, or wrap `Isolation` in a library-owned enum.

<a id="cr-126"></a>
#### CR-126 — Catalog `kotlin` entry also pins `kotlin-reflect`; POMs mix 2.4.10 / 2.4.20
**Severity:** Low · **Category:** dependency hygiene / docs · **Carried over:** no
**Where:** `gradle/libs.versions.toml:22-25,73`, `CLAUDE.md` (Kotlin hold paragraph), `README.md:6,234`, `llms.txt:10`

CLAUDE.md says the held `kotlin` ref governs only `kotlin-scripting-*`, but it also pins `kotlin-reflect`. core-utils-jvm exports reflect as `api`, so the published POM carries stdlib 2.4.20 with reflect 2.4.10 (verified in the cached core-utils-jvm 3.2.3 POM). README and llms.txt still advertise "Kotlin 2.4.10".

**Fix:** Give `kotlin-reflect` its own version aligned with the compiler, keeping only scripting on the held ref. Update the docs to say "compiled with 2.4.20; scripting pinned to 2.4.10".

<a id="cr-127"></a>
#### CR-127 — Redundant/unused dependency declarations
**Severity:** Low · **Category:** dependency hygiene · **Carried over:** no
**Where:** `ktor-server-utils/build.gradle.kts:8-10`, `script-utils-java/build.gradle.kts:4`, `script-utils-kotlin/build.gradle.kts:4`, `script-utils-python/build.gradle.kts:4`, `ktor-server-utils/README.md`

- ktor-server-utils declares `implementation(libs.kotlin.reflect)`, which is unused and already exported by core-utils. Its README lists "Kotlin Reflect" as a dependency.
- Its `api(project(":core-utils"))` leaks more than it needs, since its main sources import no `com.pambrose.*` code.
- The three script-utils engines re-declare `api(project(":core-utils"))`, which script-utils-common already exports.

**Fix:** Remove the redundant declarations. Narrow the ktor-server-utils `api` at the next major version, because that changes the POM.

<a id="cr-128"></a>
#### CR-128 — `make build` ("without tests") still runs KMP `jvmTest` via Kover
**Severity:** Low · **Category:** build · **Carried over:** no
**Where:** `Makefile:26-27`, `README.md:257`

`check` depends on the total `koverVerify`, which depends on the instrumented test tasks (verified in the Kover 0.9.9 sources, but not executed). `-x test -x allTests` doesn't exclude `jvmTest`, and no verify rules are configured.

**Fix:** Add `-x koverVerify`, use `assemble lintKotlin detekt`, or set `verify { onCheck = false }`.

<a id="cr-129"></a>
#### CR-129 — `coverage-clean` misses JVM-module test results; build cache restores them
**Severity:** Low · **Category:** build · **Carried over:** no
**Where:** `Makefile:58-60`

`cleanAllTests` exists only in the KMP modules. With `org.gradle.caching=true`, cleaned test tasks come back `FROM-CACHE`.

**Fix:** Run `./gradlew cleanTest cleanAllTests`, and use `--rerun-tasks` or `--no-build-cache` for fresh coverage runs.

<a id="cr-130"></a>
#### CR-130 — `ksp` plugin not declared in root `plugins {}`
**Severity:** Low · **Category:** build · **Carried over:** no
**Where:** `build.gradle.kts:41-54`, `core-utils/build.gradle.kts:4`, `json-utils/build.gradle.kts:4`, `ktor-client-utils/build.gradle.kts:4`

Every other module plugin is declared at the root with `apply false`, so they all load in one classloader. KSP resolves separately in each KMP module. No failure has been observed; this is classloader hygiene.

**Fix:** Add `alias(libs.plugins.ksp) apply false` to the root plugins block.

<a id="cr-131"></a>
#### CR-131 — Signing skipped unless in-memory key present; POM `developerConnection` lacks `git@`
**Severity:** Low · **Category:** publishing · **Carried over:** no
**Where:** `build.gradle.kts:431,436-439`

- **Signing:** running `publishAndReleaseToMavenCentral` outside the Makefile uploads unsigned artifacts, which fail only later in Central validation. vanniktech 0.37.0's `signAllPublications()` already requires signing only for non-SNAPSHOT versions (verified).
- **SCM URL:** `scm:git:ssh://github.com/...` is missing the `git@` user.

**Fix:** Call `signAllPublications()` unconditionally, and use `scm:git:ssh://git@$scmHost.git`.

---

### CI

<a id="cr-132"></a>
#### CR-132 — `test.yml` has no `permissions` block; repo default token is write
**Severity:** Medium · **Category:** security · **Carried over:** no
**Where:** `.github/workflows/test.yml:1-9`

The repository's default workflow permission is `write` (verified via `gh api repos/pambrose/common-utils/actions/permissions/workflow`). Pushes and same-repo PRs therefore run the full Gradle build (plugins, npm toolchains) and codecov with a write-scoped token, which `actions/checkout` persists in `.git/config`. `kdocs.yml` already narrows its permissions.

**Fix:** Add a top-level `permissions: { contents: read }`, and optionally set `persist-credentials: false`. Consider switching the repo default to read.

<a id="cr-133"></a>
#### CR-133 — `kdocs.yml` grants `pages`/`id-token` write to the PR build job
**Severity:** Low · **Category:** security · **Carried over:** no
**Where:** `.github/workflows/kdocs.yml:9-42`

The permissions are set at workflow level, so `./gradlew :dokkaGenerate` runs on PR code with Pages-write and OIDC rights. Fork PRs get read-only tokens, so exposure is limited to same-repo branches.

**Fix:** Set top-level `contents: read` and move `pages: write` and `id-token: write` to the `deploy` job.

<a id="cr-134"></a>
#### CR-134 — Redundant Lint step; Apple targets never built in CI
**Severity:** Low · **Category:** ci · **Carried over:** no
**Where:** `.github/workflows/test.yml:23-29`

`./gradlew build` already runs `lintKotlin` and `detekt` through `check`. CI is ubuntu-only with `kotlin.native.ignoreDisabledTargets=true`, so macOS and iOS breakage first surfaces during a local publish.

**Fix:** Drop the Lint step. Add a `macos-latest` job (push to master) running `macosArm64Test` and `iosSimulatorArm64Test` for the three KMP modules.

<a id="cr-135"></a>
#### CR-135 — No concurrency cancellation; actions pinned by mutable tags
**Severity:** Low · **Category:** ci · **Carried over:** no
**Where:** `.github/workflows/test.yml:1-35`

Superseded PR builds (JS, wasm, native, detekt) run to completion. Actions pinned to tags like `@v7` can be retagged and would then run with the token from CR-132.

**Fix:** Add `concurrency: { group: test-${{ github.ref }}, cancel-in-progress: ${{ github.event_name == 'pull_request' }} }`. Consider SHA pins, which Dependabot keeps current.

---

### Project documentation

<a id="cr-136"></a>
#### CR-136 — llms.txt misdescribes several modules
**Severity:** Medium · **Category:** docs · **Carried over:** no
**Where:** `llms.txt:24-39`

llms.txt is meant for AI coding tools, so every error here turns directly into wrong generated code:

| Module | What llms.txt says | What's actually true |
|---|---|---|
| redis-utils, zipkin-utils | Links to their READMEs | Neither README exists ([CR-080](#cr-080)) |
| exposed, grpc, dropwizard, prometheus, zipkin | "Standalone" | Each declares `api(project(":core-utils"))` |
| script-utils-java | "JavaScript engine" | It compiles Java source |
| dropwizard-utils | "JMX integration" | There is none |
| guava-utils | "service lifecycle (GenericService)" and "archive (ZIP)" | Neither exists; the module has gzip helpers |
| service-utils | Depends on core-utils only | It also depends on six sibling modules |
| json-utils | "dot-notation paths" | Correct for `get`, but `getByPath` splits on `/` |

**Fix:** Correct each description and dependency list, and add the missing READMEs or remove the links.

<a id="cr-137"></a>
#### CR-137 — CLAUDE.md format command skips KMP modules
**Severity:** Low · **Category:** docs · **Carried over:** no
**Where:** `CLAUDE.md` ("Code Quality")

In KMP modules the kotlinter tasks are named `formatKotlinCommonMain`, `formatKotlinJvmTest`, and so on (verified in kotlinter 5.7.0). `./gradlew formatKotlinMain formatKotlinTest` therefore silently skips core-utils, json-utils and ktor-client-utils.

**Fix:** Document `./gradlew formatKotlin`.

<a id="cr-138"></a>
#### CR-138 — Other CLAUDE.md drift (Dokka location, return-value checker, Dependabot ignores)
**Severity:** Low · **Category:** docs · **Carried over:** no
**Where:** `CLAUDE.md` (Build Configuration, Experimental Kotlin Features, Dependabot paragraph); `build.gradle.kts:75,241-249,382-386`; `.github/dependabot.yml:36-52`

- **Dokka:** HTML setup lives in `configureDokka()`, not in `configurePublishing`.
- **Return-value checker:** the global `-Xreturn-value-checker=check` flag (production compilations) is undocumented.
- **Dependabot:** the netty-tcnative and Kotlin 2.4.20 ignore rules aren't mentioned.

**Fix:** Update the three sections, including when the Kotlin ignore should be removed.

<a id="cr-139"></a>
#### CR-139 — Stale/duplicated top-level files
**Severity:** Low · **Category:** cleanup · **Carried over:** no
**Where:** `code-review.md`, `docs/CODE_REVIEW.md`, `.codeclimate.yml`, `system.properties`, `.gitignore:3`

- **Root `code-review.md`:** still lists items fixed in #118 (e.g. its Top Priorities #2 and #3) as open.
- **`docs/CODE_REVIEW.md`:** dated 2026-03-01 against v2.6.3.
- **`.codeclimate.yml`:** runs checkstyle on Java, but there is one Java file and the README badge is Codacy.
- **`system.properties`:** a Heroku buildpack file in an undeployed library.
- **`.gitignore`:** still lists `.wercker/`.

**Fix:** Delete or archive both old reviews; this document supersedes them. Remove `.codeclimate.yml`, `system.properties` and `.wercker/` once confirmed unused.

---

## Verified non-issues (do not re-report)

- **Resend `Click` webhook camelCase `@SerialName`s** (`ipAddress`, `userAgent`, `linkTags`) are correct per Resend's docs.
- **`GenericValueWaiter` rewrite** was traced for registration/timeout/cancellation races and is correct.
- **`GenericMonitor` timed waits** correctly `leave()` only on success.
- **`yarnResolutions` pins** (ws, serialize-javascript, diff, brace-expansion, js-yaml) all landed in both lockfiles. The pin-only wasm entries match CLAUDE.md.
- **Version consistency:** 3.2.3 matches across gradle.properties, README, llms.txt, CHANGELOG and RELEASE_NOTES. The catalog `gradle-wrapper` entry matches the wrapper properties.
- **Detekt runs in CI** with type resolution via `check`.
- **`KotlinScript` context reset** drops REPL state (state lives in ENGINE_SCOPE). Each `PythonScript` has its own `PySystemState`.
