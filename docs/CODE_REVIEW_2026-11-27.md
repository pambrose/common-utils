# Code Review — common-utils (2026-11-27)

|                     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Review date**     | 2026-11-27                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **Project version** | 5.3.1 (working tree on `release-5.3.1`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Commit reviewed** | `9e663f0` (`release-5.3.1`, including uncommitted 5.3.1 release-prep changes)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| **Scope**           | All 19 modules plus `common-utils-bom` (main and test sources, READMEs, module build files, `api/` dumps), root build, Makefile, CI workflows, Dependabot, Detekt and Codecov config, scripts, `kotlin-js-store/`, and project docs                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| **Method**          | Seven parallel reviewers, one per module group plus build/CI/docs. Library behaviour was checked against cached dependency jars and sources: Exposed 1.6.1, Ktor 3.6.2, Jetty 12.1.15, Jakarta Servlet 6.1.0, grpc-java 1.86.0, netty-tcnative 2.0.86.Final, Jedis 8.1.0, resend-java 4.32.0, Prometheus Java client 1.10.0, Dropwizard 4.2.40, Brave 6.3.1, Zipkin Reporter 3.5.3, Guava 33.8.0-jre, kotlin-scripting 2.4.21, java-scriptengine 2.0.0, Jython 2.7.4, and kotlinx-serialization 1.13.0. Findings were reproduced against the compiled 5.3.1 jars via `jshell` and `javap` — including live loopback Ktor CIO and Jetty 12.1.15 probes for the headline HTTP/servlet findings — and re-checked by hand before write-up. The full `./gradlew check` was not run. |
| **Related**         | [`CODE_REVIEW_2026-09-14.md`](archive/CODE_REVIEW_2026-09-14.md) (CR-001…CR-139), [`TEST_COVERAGE_REVIEW_2026-09-16.md`](TEST_COVERAGE_REVIEW_2026-09-16.md) (TC-001…TC-083), and [`CODE_REVIEW_2026-09-24.md`](archive/CODE_REVIEW_2026-09-24.md) (RV-001…RV-093) are all fully closed. Nothing here repeats them. When an item follows up an earlier fix or a 5.x PR that was incomplete or introduced a regression, it names the CR/RV/PR id.                                                                                                                                                                                                                                                                                                   |

## At a glance

**49 issues:** 1 high · 17 medium · 31 low (R3-050 to R3-052, three more low items found while fixing phase 1, were added later). Nothing is critical, the 5.0.0–5.3.1 rework largely held, and `ktor-client-utils`, `dropwizard-utils`, `jetty-utils`, CI/supply-chain, and the new `make release` / `release-check` automation are clean.

- **One high item:**
  - [R3-011](#r3-011): `HerokuHttpsRedirect` splices `call.request.uri` directly onto the HTTPS origin with its trailing `/` stripped (introduced in RV-026). On Ktor CIO, `GET @evil.com/phish HTTP/1.1` with `X-Forwarded-Proto: http` redirects to `https://myapp.herokuapp.com@evil.com/phish` even when `host` is explicitly pinned, and standard absolute-form requests produce a malformed `https://hosthttp://host/...` URL.
- **Security and defensive gaps (5 additional items):**
  - [R3-001](#r3-001): `toObjectSecure` still admits `TC_STRING`, does not override `resolveProxyClass` (bypassing `DANGEROUS_PACKAGES`), calls `Class.forName` before checking `allowedClasses`, and misses `[L...;` array descriptors in `isDangerousClass`.
  - [R3-002](#r3-002): `maskUrlCredentials` skips the second URL whenever two pathless URLs are separated by non-whitespace punctuation (CSV, compact JSON).
  - [R3-015](#r3-015): Jetty admin and metrics 404/500 error pages still disclose `org.eclipse.jetty.ee11.servlet.ServletHandler$Default404Servlet-...` and internal exception messages/causes (missed by a case-sensitive `"Jetty"` test check).
  - [R3-039](#r3-039): `RedisUtils.urlDetails` does not validate the URL scheme or port range, silently downgrading non-`rediss` TLS schemes (`tls://`, `redis+tls://`) to plaintext.
- **Resource leaks and concurrency races (6 medium items):**
  - [R3-018](#r3-018): `SystemMetrics.forget()` paired with `enableMemoryPoolsExports = true` permanently leaks a GC `NotificationListener` on every `GarbageCollectorMXBean` per cycle (upstream Prometheus 1.10.0 never detaches it).
  - [R3-024](#r3-024): `ScriptUtils.resetContext` replaces `scriptEngine.context` without clearing the old `ENGINE_SCOPE`, leaving `ScriptVariables`, `K2ReplState`, and Python bindings strongly retained in `lastScriptContext` / `interp.locals` until the next `eval()`.
  - [R3-016](#r3-016), [R3-025](#r3-025), [R3-035](#r3-035), [R3-038](#r3-038): `AbstractGenericService.startUp()` leaks all started sub-services if `addShutdownHook()` throws; `AbstractScript.params` default argument reads `typeMap` outside `@Synchronized`; concurrent `RecaptchaService.close()` leaks a spurious `CancellationException`; cancelling `withSuspendingRedis` while connecting leaks the `RedisClient` pool.
- **Remaining 5.0.0 follow-ups:**
  - `script-utils` type-generation edge cases ([R3-026](#r3-026)–[R3-030](#r3-030)), Ktor servlet 304/204 header fabrication ([R3-012](#r3-012)), `GrpcDsl.server(bindAddress = "")` ([R3-043](#r3-043)), and a small tail of ABI/dependency cleanups for 6.0.0 ([R3-008](#r3-008), [R3-045](#r3-045)–[R3-047](#r3-047)).

## How to track progress

- IDs (`R3-001` … `R3-049`) are stable. **Never renumber.** If an item is dropped, close it as won't-fix instead of deleting it.
- Status lives only in the checklist below:
  - `- [ ]` open
  - `- [x]` done, with `— fixed in #NNN` or a commit SHA appended
  - `- [x]` won't fix, with `— won't fix: <reason>` appended
  - `- [ ]` in progress, with `— in progress (#NNN)` appended
- [Details](#details) holds the evidence and the suggested fix, and carries no status.
- Items marked *(PLAUSIBLE)* were traced in code but not reproduced; confirm them before fixing. (All items in this review were confirmed by code trace, bytecode inspection, or live reproduction.)
- Quick counts:

  ```bash
  grep -c '^- \[x\] \*\*R3-' docs/CODE_REVIEW_2026-11-27.md   # closed
  grep -c '^- \[ \] \*\*R3-' docs/CODE_REVIEW_2026-11-27.md   # open
  grep    '^- \[ \] \*\*R3-.*`HIGH`' docs/CODE_REVIEW_2026-11-27.md   # open high-severity
  ```

---

## Issues

### core-utils
- [x] **R3-001** `MEDIUM` · security — `toObjectSecure` has allow-list/blocklist bypasses (`TC_STRING`, `resolveProxyClass`, array descriptors) and loads unallowed classes before checking the whitelist (follows CR-001, RV-001) ([details](#r3-001)) — fixed in #244
- [x] **R3-002** `MEDIUM` · security — `maskUrlCredentials` leaves subsequent URL credentials unmasked when URLs are separated by punctuation (follows RV-002) ([details](#r3-002)) — fixed in #244
- [x] **R3-003** `LOW` · bug — `toObjectSecure` throws `NullPointerException` instead of `ClassCastException` on a serialized `null` stream ([details](#r3-003)) — fixed in #244
- [ ] **R3-004** `LOW` · bug — `String.trimEnds` overflows `2 * len` for `len >= 2^30`, bypassing its `require` check ([details](#r3-004))
- [ ] **R3-005** `LOW` · bug — `String.isBracketed` returns `true` for a single-character string when `startChar == endChar` ([details](#r3-005))
- [ ] **R3-006** `LOW` · bug — Remaining negative-value edge cases in `Int.lpad`, `Duration.format`, and `MiscJavaFuncs.sleepSecs` (follows RV-006) ([details](#r3-006))
- [ ] **R3-007** `LOW` · docs — `core-utils/README.md` recommends `SingleAssignVar.singleAssign()` without noting its 5.0.0 deprecation (follows RV-010) ([details](#r3-007))

### json-utils
- [ ] **R3-008** `MEDIUM` · API — `json-utils` leaks `implementation`-scoped `KLogger` in its public multiplatform ABI via deprecated `JsonElementUtils.logger` (follows CR-031, RV-012) ([details](#r3-008))
- [ ] **R3-009** `LOW` · bug — `String.reformatJson` throws `JsonEncodingException` on `NaN`, `Infinity`, `-Infinity`, and overflowing numbers, contradicting the README (follows RV-016) ([details](#r3-009))
- [ ] **R3-010** `LOW` · docs — `json-utils/README.md` conversion snippet omits `import com.pambrose.common.json.parseJson` and does not compile (follows RV-017) ([details](#r3-010))

### ktor-server-utils
- [x] **R3-011** `HIGH` · security — `HerokuHttpsRedirect` allows open redirects and malformed URLs on non-origin-form request targets even when `host` is pinned, and trusts the `Host` header by default (follows CR-040, RV-026) ([details](#r3-011)) — fixed in #244
- [ ] **R3-012** `MEDIUM` · bug — `ServletRoute` fabricates `Content-Type: application/octet-stream` and `Content-Length: 0` on `304 Not Modified`, `204 No Content`, and bodyless responses (follows RV-023, RV-025) ([details](#r3-012))
- [ ] **R3-013** `LOW` · bug — `KtorServletResponse` violates Jakarta Servlet 6.1 contracts for committed responses, `setContentType(null)`, `reset()`, and null header names (follows RV-024, RV-025) ([details](#r3-013))
- [ ] **R3-014** `LOW` · bug — `KtorServletRequest.getCookies()` crashes with HTTP 500 on any `Cookie` header containing a non-token cookie name (follows RV-025) ([details](#r3-014))
- [ ] **R3-050** `LOW` · bug — `HerokuHttpsRedirect`'s `excludePrefix` / `excludeSuffix` see an absolute-form request target's full URL on Ktor CIO, so such a request is redirected instead of excluded (follows R3-011) ([details](#r3-050))

### service-utils
- [x] **R3-015** `MEDIUM` · security — Jetty admin and metrics 404/500 error pages still disclose Jetty (`org.eclipse.jetty...Default404Servlet`) and internal exception messages/causes (follows RV-031) ([details](#r3-015)) — fixed in #244
- [ ] **R3-016** `MEDIUM` · leak — `AbstractGenericService.startUp()` leaks all started sub-services if `Runtime.addShutdownHook()` throws (follows CR-046) ([details](#r3-016))
- [ ] **R3-017** `LOW` · docs — `service-utils/README.md` still documents removed Ktor plugins (`call-logging`, `compression`) and attributes a bind `host` to `ZipkinConfig` (follows RV-034) ([details](#r3-017))

### prometheus-utils
- [ ] **R3-018** `MEDIUM` · leak — `SystemMetrics.forget()` with `enableMemoryPoolsExports = true` permanently leaks GC `NotificationListener`s (follows #208, #226) ([details](#r3-018))
- [ ] **R3-019** `LOW` · API — `InstrumentedThreadFactory` has no way to unregister its three metrics ([details](#r3-019))
- [ ] **R3-020** `LOW` · tests — `SystemMetricsTests` clears and leaves collectors in `PrometheusRegistry.defaultRegistry` without cleanup (follows RV-040, #226) ([details](#r3-020))

### guava-utils
- [ ] **R3-021** `LOW` · API — `ByteArray.unzip` and `Semaphore.withLock` omit `@Throws` for checked exceptions, while mangled Kotlin-`Duration` overloads keep unreachable `@Throws` (follows RV-046) ([details](#r3-021))
- [ ] **R3-022** `LOW` · API — `Service.genericServiceListener` leaks mutable `GuavaDsl.ServiceListenerHelper` as its public return type instead of `Service.Listener` ([details](#r3-022))

### zipkin-utils
- [ ] **R3-023** `LOW` · docs — `zipkin-utils/README.md` example calls deprecated `AsyncReporter.create(Sender)` and leaks `reporter` and `sender` ([details](#r3-023))

### script-utils (common / java / kotlin / python)
- [ ] **R3-024** `MEDIUM` · leak — `ScriptUtils.resetContext` leaves previous `ENGINE_SCOPE` bindings strongly retained by Kotlin and Jython engines until the next `eval()` (follows RV-048, RV-049) ([details](#r3-024))
- [ ] **R3-025** `MEDIUM` · concurrency — `AbstractScript.params` default argument reads unsynchronized `typeMap` outside the `@Synchronized` monitor (follows RV-056) ([details](#r3-025))
- [ ] **R3-026** `MEDIUM` · bug — Private generic classes with a non-generic public supertype (`IntStream`, `LongStream`, `DoubleStream`) cannot be bound as their public type (follows RV-052) ([details](#r3-026))
- [ ] **R3-027** `MEDIUM` · bug — `AbstractScript.isPubliclyAccessible()` accepts non-static inner classes of generic outer classes, generating uncompilable Kotlin and Java types (follows RV-051, RV-052) ([details](#r3-027))
- [ ] **R3-028** `MEDIUM` · bug — `KotlinScript.isReserved` does not reject all-underscore identifiers (`_`, `__`, `___`), poisoning the script instance (follows RV-048) ([details](#r3-028))
- [ ] **R3-029** `MEDIUM` · bug — `JavaScript.renderType` throws unwrapped `NullPointerException` on local/anonymous `KType` and silently drops `in`/`out` variance projections ([details](#r3-029))
- [ ] **R3-030** `LOW` · bug — `JavaScript.import` / `addImport` accepts primitives, arrays, and unnamed-package classes, generating invalid `import` statements (follows RV-053) ([details](#r3-030))
- [ ] **R3-031** `LOW` · leak — `AbstractEnginePool.close()` aborts draining on the first `instance.close()` failure, leaking remaining idle instances ([details](#r3-031))
- [ ] **R3-032** `LOW` · docs — `script-utils-common/README.md` and `AbstractExprEvaluatorPool` KDoc have stale `valueMap` type, contradictory isolation text, and a non-reproducing `run { val y = 1 }` example (follows RV-049, RV-059) ([details](#r3-032))
- [ ] **R3-033** `LOW` · tests — No end-to-end test exercises `KotlinExprEvaluatorPool`'s `resetEvery` behavior on the real Kotlin engine (follows RV-049) ([details](#r3-033))

### email-utils
- [ ] **R3-034** `LOW` · docs — `ResendService` docs and comments claim each instance owns its own OkHttp client, stale since `resend-java` 4.32.0 shares a singleton default `HttpClient` (follows RV-061) ([details](#r3-034))

### recaptcha-utils
- [ ] **R3-035** `MEDIUM` · concurrency — Concurrent `RecaptchaService.close()` races with `verifyRecaptcha` and propagates a spurious `CancellationException` to an uncancelled caller (follows RV-065) ([details](#r3-035))
- [ ] **R3-036** `LOW` · leak — `RecaptchaService` eagerly starts a live `HttpClient(CIO)` (NIO `Selector` + coroutine) in `<clinit>` even when reCAPTCHA is disabled (follows RV-065) ([details](#r3-036))
- [ ] **R3-037** `LOW` · docs — `recaptcha-utils/README.md` lists `internal data class RecaptchaResponse` as public API ([details](#r3-037))

### redis-utils
- [ ] **R3-038** `MEDIUM` · leak — `withSuspendingRedis` / `withSuspendingNonNullRedis` leak `RedisClient` if cancelled while connecting (follows RV-066) ([details](#r3-038))
- [x] **R3-039** `MEDIUM` · security — `RedisUtils.urlDetails` does not validate URL scheme or port range, silently downgrading non-`rediss` TLS schemes to plaintext ([details](#r3-039)) — fixed in #244
- [x] **R3-040** `LOW` · bug — `RedisUtils.urlDetails` splits `uri.userInfo` after percent-decoding, corrupting usernames containing `%3A` ([details](#r3-040)) — fixed in #244
- [ ] **R3-041** `LOW` · docs — `redis-utils/README.md` lists `internal class RedisInfo` as public API ([details](#r3-041))
- [ ] **R3-051** `LOW` · security — An unescaped `/` in a Redis URL's password ends the authority early, and Jedis's database-index error then quotes the rest of the password (follows R3-039) ([details](#r3-051))

### exposed-utils
- [ ] **R3-042** `LOW` · bug — `checkConflictIndexSupported` misses H2 in MySQL/MariaDB compatibility mode, and `UpsertStatementTests` comment is stale for Exposed 1.6.1 (follows RV-070) ([details](#r3-042))

### grpc-utils
- [ ] **R3-043** `MEDIUM` · bug — `GrpcDsl.server(bindAddress = ...)` silently binds loopback on `""` and defers `UnresolvedAddressException` to `start()` on blank or whitespace-padded addresses (follows RV-075) ([details](#r3-043))
- [ ] **R3-044** `LOW` · API — `TlsUtils.clientTlsContextBuilder` and `serverTlsContext` declare impossible `@Throws(SSLException::class)` and are omitted from `grpc-utils/README.md` API Reference ([details](#r3-044))

### jetty-utils
- [ ] **R3-052** `LOW` · security — `JettyDsl.servletContextHandler` sets no error handler, so a servlet exception's class and message reach the client on Jetty's default 500 page (follows R3-015) ([details](#r3-052))

### Cross-module API surface
- [ ] **R3-045** `LOW` · API — Six empty public `Companion` objects remain in the published ABI across `guava-utils`, `script-utils-common`, and `service-utils` (follows RV-078) ([details](#r3-045))

### Build & publishing
- [ ] **R3-046** `LOW` · build — Four modules (`zipkin-utils`, `redis-utils`, `guava-utils`, `grpc-utils`) still export `:core-utils` as `api` without exposing any `core-utils` types (follows RV-079) ([details](#r3-046))
- [ ] **R3-047** `LOW` · build — `prometheus-utils` exports `prometheus-metrics-instrumentation-jvm` as `api` although no type from it appears in the public ABI ([details](#r3-047))
- [ ] **R3-048** `LOW` · build — `service-utils` declares `dropwizard-jmx` twice (`api` and `implementation` via single-use bundle), and `llms.txt` omits it from `service-utils`'s `api` list ([details](#r3-048))
- [ ] **R3-049** `LOW` · publishing — `Makefile` publishing env setup does not guard against an empty GPG passphrase or failed secret export, and `build.gradle.kts` treats a blank `signingInMemoryKey` as present ([details](#r3-049))

---

## Plan: order of fixes

Rules used to order the work:
1. Input an outsider controls comes first.
2. Next, silent wrong behaviour, resource leaks, and concurrency races.
3. Then smaller correctness bugs, diagnostics, and tests.
4. Docs and build cleanup follow.
5. Anything that removes a public ABI declaration or narrows transitive `api` dependencies waits for the next major (`6.0.0`), per `AGENTS.md`'s versioning policy.

Each step is sized as one PR, with regression tests for every code change.

### Phase 1 — Security and exposure (patch release, 5.3.2)

| Step | Items                        | Why now                                                                                                                                                                                                                                   |
|------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | R3-011                       | Open redirect on every Ktor CIO app using `HerokuHttpsRedirect`, even with `host` pinned. Fix target normalization and host validation, document pinning `host`, and add raw-socket tests.                                                  |
| 2    | R3-001, R3-002, R3-003       | Hardens `toObjectSecure` against `TC_STRING`, proxy-class, pre-whitelist resolution, array-descriptor, and `TC_NULL` inputs, and fixes `maskUrlCredentials` on punctuation-separated URLs — all in `core-utils`.                          |
| 3    | R3-015                       | Unauthenticated Jetty admin/metrics 404/500 pages still reveal Jetty and exception messages. Switch to Jetty's core `ErrorHandler` (or disable `isShowOrigin` / causes) and make the test case-insensitive.                                |
| 4    | R3-039, R3-040               | `RedisUtils.urlDetails` silently turns `tls://` / `redis+tls://` into plaintext and corrupts `%3A` in usernames. Both live in the same 20-line parser.                                                                                    |

### Phase 2 — Leaks, concurrency, and protocol correctness (5.3.2 or 5.4.0)

| Step | Items                        | Why now                                                                                                                                                                                                                                   |
|------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 5    | R3-012, R3-013, R3-014       | `ktor-server-utils` servlet bridge: drop fabricated `Content-Type` / `Content-Length: 0` on 304/204/bodyless replies, align `KtorServletResponse` with Servlet 6.1, and skip malformed cookie names in `getCookies()`.                      |
| 6    | R3-016, R3-017               | `service-utils`: move `addShutdownHook` inside `startUp()`'s rollback `runCatching`, and fix the two stale `README.md` descriptions.                                                                                                      |
| 7    | R3-038, R3-041               | `redis-utils`: close `RedisClient` under `NonCancellable` if cancelled during `connectOrNullOnIo`, and remove `internal class RedisInfo` from the README.                                                                                 |
| 8    | R3-035, R3-036, R3-037       | `recaptcha-utils`: distinguish client-closure cancellation from caller cancellation in `verifyRecaptcha`, make `httpClient` lazy so disabled apps don't open an NIO `Selector`, and drop `RecaptchaResponse` from the README.             |
| 9    | R3-018, R3-019, R3-020       | `prometheus-utils`: fix or document the `JvmMemoryPoolAllocationMetrics` GC listener leak on `forget()`, add `InstrumentedThreadFactory.unregister()`, and clean up default-registry tests.                                                |
| 10   | R3-043, R3-044 (docs half)   | `grpc-utils`: trim and validate `bindAddress` in `createNettyServer`, fix `TlsUtils` KDoc, and add `clientTlsContextBuilder`/`serverTlsContext`/`TlsContextBuilder` to `grpc-utils/README.md`.                                            |

### Phase 3 — script-utils fixes (5.4.0)

| Step | Items                                      | Why now                                                                                                                                                                                                                                   |
|------|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 11   | R3-024, R3-025, R3-026, R3-027, R3-031     | `script-utils-common`: clear old `ENGINE_SCOPE` in `resetContext` so idle pooled engines don't pin `ScriptVariables` and `K2ReplState`, synchronize `params` default lookup, fix `IntStream` and generic-outer inner-class checks, and drain all instances in `AbstractEnginePool.close()`. |
| 12   | R3-028, R3-029, R3-030, R3-032, R3-033     | `script-utils-kotlin` and `script-utils-java`: reject all-underscore names in `KotlinScript.isReserved`, guard `canonicalName` and render variance in `JavaScript.renderType`, validate `JavaScript.import`, add end-to-end `KotlinExprEvaluatorPool(resetEvery)` tests, and fix stale docs. |

### Phase 4 — Remaining low items (5.4.0)

| Step | Items                                                          | Notes                                                                                                                                                                                                                                     |
|------|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 13   | R3-004, R3-005, R3-006, R3-007                                 | `core-utils`: `trimEnds` overflow, `isBracketed`, `lpad`/`Duration.format`/`sleepSecs` edge cases, and README deprecation note for `SingleAssignVar`.                                                                                     |
| 14   | R3-009, R3-010, R3-021, R3-023, R3-034, R3-042                 | Small cross-module fixes: `json-utils` `reformatJson` / README import, `guava-utils` `@Throws` / Java `Duration` overloads, `zipkin-utils` README, `email-utils` `ResendService` docs, and `exposed-utils` H2 MySQL/MariaDB mode check.  |
| 15   | R3-048, R3-049                                                 | Build & publishing: drop duplicate `dropwizard-jmx` / single-use bundle + update `llms.txt`, and tighten `Makefile` GPG/keychain guards and blank `signingInMemoryKey` check.                                                             |

### Phase 5 — Next major (6.0.0): ABI and dependency-surface changes

| Step | Items                                      | Notes                                                                                                                                                                                                                                     |
|------|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 16   | R3-008, R3-022, R3-044 (`@Throws`), R3-045 | Remove deprecated `JsonElementUtils` (or hide/promote `kotlin-logging` during 5.x), narrow `genericServiceListener` return type to `Service.Listener`, drop impossible `@Throws(SSLException::class)` from `TlsUtils` builder factories, and make the 6 empty `Companion` objects `private`. Run `make abi-update` on macOS. |
| 17   | R3-046, R3-047                             | Drop unused `api(project(":core-utils"))` from `zipkin-utils`, `redis-utils`, `guava-utils`, and `grpc-utils`; demote `prometheus-metrics-instrumentation-jvm` to `implementation` in `prometheus-utils`. Update `AGENTS.md`, `llms.txt`, module READMEs, and `CHANGELOG.md`. |

**Dependencies between items:**
- R3-039 and R3-040 both modify `RedisUtils.urlDetails`, so do them together.
- R3-026 and R3-027 both touch `AbstractScript`'s supertype/accessibility resolution, so do them together.
- R3-008 can be mitigated in 5.x (promoting `kotlin-logging` to `api` or marking `JsonElementUtils` `DeprecationLevel.HIDDEN`) and closed completely when `JsonElementUtils` is removed in 6.0.0.
- R3-046 affects `AGENTS.md` and `llms.txt`; update them in the same PR.

---

## Details

### core-utils

<a id="r3-001"></a>
#### R3-001 — `toObjectSecure` has allow-list/blocklist bypasses and loads unallowed classes before checking the whitelist
**Severity:** Medium · **Category:** security · **Verdict:** confirmed (all four vectors reproduced via `jshell` against `core-utils-jvm-5.3.1.jar`) · **Follows up:** CR-001, RV-001
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:102-112,118-169`

Four concrete gaps remain in `SecureObjectInputStream` / `toObjectSecure`:
1. **`TC_STRING` bypasses `allowedClasses`:** `ObjectInputStream.readString()` never calls `resolveClass`. Deserializing `"pwned".toByteArray()` with `toObjectSecure(Serializable::class.java, setOf(Integer::class.java))` succeeds and returns `"pwned"` even though `String::class.java` is not in `allowedClasses`.
2. **`resolveProxyClass` is not overridden:** `TC_PROXYCLASSDESC` streams invoke `resolveProxyClass(String[] interfaces)`, not `resolveClass`. With `allowedClasses = setOf(Proxy::class.java)`, a crafted stream deserialized and returned a dynamic proxy (`jdk.proxy2.$Proxy1`) implementing `javax.management.MBeanServer` (`instanceof MBeanServer == true`), bypassing both `allowedClasses` and `DANGEROUS_PACKAGES` (`"javax.management."`). Even when `Proxy` is not allow-listed, `resolveProxyClass` resolves arbitrary interface names and defines proxy classes before `Proxy` is rejected in `resolveClass`.
3. **`resolveClass` loads unallowed classes before checking `allowedClasses`:** Line 141 calls `super.resolveClass(desc)` (`Class.forName(name, false, loader)`) *before* checking `allowedClasses.contains(clazz)` at line 144. While `initialize = false` prevents `<clinit>` from running, untrusted input can still load arbitrary non-blocklisted classes and use the exception type as a classpath oracle: with `allowedClasses = setOf(Integer::class.java)`, a stream naming `java.util.ArrayList` throws `SecurityException: Class not in whitelist`, whereas `com.nope.NoSuchClss` throws `ClassNotFoundException`.
4. **`isDangerousClass` misses array descriptors:** Serialized array descriptors use JVM internal names such as `"[Ljava.lang.ProcessBuilder;"`. `ProcessBuilder[0]` deserialized cleanly (`length = 0`) when allow-listed, bypassing `DANGEROUS_CLASSES` (`"java.lang.ProcessBuilder"`).

**Fix:**
- Precompute `allowedClassNames = allowedClasses.map { it.name }.toSet()` and check `className in allowedClassNames` *before* calling `super.resolveClass(desc)` (keeping `clazz in allowedClasses` afterward for classloader safety).
- Normalize array descriptors in `isDangerousClass` (strip leading `[` and unwrap `L...;`) so arrays of blocked classes or packages are rejected too.
- Override `resolveProxyClass(interfaces: Array<String>)` to reject every interface unless `!isDangerousClass(it)` and `it in allowedClassNames` (or reject dynamic proxies outright unless explicitly opted in).
- After `ois.readObject()`, verify `obj != null && obj.javaClass in allowedClasses` (or enforce `String` via `allowedClasses` / `ObjectInputFilter`).
- Add regression tests for all four vectors.

<a id="r3-002"></a>
#### R3-002 — `maskUrlCredentials` leaves subsequent URL credentials unmasked when separated by punctuation
**Severity:** Medium · **Category:** security · **Verdict:** confirmed (reproduced via `jshell` against `core-utils-jvm-5.3.1.jar`) · **Follows up:** RV-002
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:403-420`

`maskUrlCredentials` scans `authorityEnd` until the first `/`, `?`, `#`, or whitespace, then advances the loop with `schemeEnd = indexOf("://", authorityEnd)`. When a URL without a path is followed by another URL separated by non-whitespace punctuation (such as `,`, `"`, `'`, `)`, `;`, `]` — common in CSV, compact JSON, or structured logs), `authorityEnd` lands on the first `/` of the *next* URL's `://`. Searching from `authorityEnd` skips past that second `://`, leaving the second URL's credentials in plaintext.

Reproduced outputs:
- `"https://u1:p1@h1,https://u2:p2@h2"` → `"https://*****:*****@h1,https://u2:p2@h2"`
- `"https://h1,https://u2:p2@h2"` → `"https://h1,https://u2:p2@h2"` (neither masked)
- `"""{"a":"https://u1:p1@h1","b":"https://u2:p2@h2"}"""` → `"""{"a":"https://*****:*****@h1","b":"https://u2:p2@h2"}"""`

**Fix:**
Compute `val nextScheme = indexOf("://", authorityStart)` first, bound the authority scan to `if (nextScheme == -1) length else nextScheme`, and advance with `schemeEnd = nextScheme`. Also stop `authorityEnd` on non-RFC-3986 delimiters (`"`, `'`, `<`, `>`, `{`, `}`) so an `@` outside a URL cannot over-mask across punctuation. Add regression tests for comma-separated and JSON-embedded URLs.

<a id="r3-003"></a>
#### R3-003 — `toObjectSecure` throws `NullPointerException` instead of `ClassCastException` on a serialized `null` stream
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (reproduced via `jshell` against `core-utils-jvm-5.3.1.jar`)
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:103-110`

A valid 5-byte Java serialization stream containing `TC_NULL` (`AC ED 00 05 70`) makes `ois.readObject()` return `null`. Line 106 evaluates `!expectedClass.isInstance(null)` (true), and line 107 dereferences `obj.javaClass.name` on `null`, throwing `NullPointerException` instead of the documented `ClassCastException`.

**Fix:** Use `obj?.javaClass?.name ?: "null"` in the `ClassCastException` message at line 107, and add a `TC_NULL` test case.

<a id="r3-004"></a>
#### R3-004 — `String.trimEnds` overflows `2 * len` for `len >= 2^30`, bypassing its `require` check
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (reproduced via `jshell` against `core-utils-jvm-5.3.1.jar`)
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:315-321`

Line 319 checks `require(trimmed.length >= 2 * len)`. When `len` is in `(1 shl 30)..Int.MAX_VALUE`, `2 * len` overflows to a negative `Int` (`2 * 1073741824 == -2147483648`), so the guard passes. `"hello".trimEnds(1 shl 30)` then throws `StringIndexOutOfBoundsException` on the JVM instead of the documented `IllegalArgumentException` (and on JS, `substring` silently clamps out-of-range indices).

**Fix:** Change line 319 to `require(len <= trimmed.length / 2) { ... }`.

<a id="r3-005"></a>
#### R3-005 — `String.isBracketed` returns `true` for a single-character string when `startChar == endChar`
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (reproduced via `jshell` against `core-utils-jvm-5.3.1.jar`)
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:251-254` (compare `:27,33`)

`isSingleQuoted()` and `isDoubleQuoted()` check `length >= 2` so `"'"` and `"\""` return `false`. `isBracketed(startChar, endChar)` omits that check: `"|".isBracketed('|', '|')` returns `true`, matching the same character as both opener and closer.

**Fix:** Use `trim().run { length >= 2 && startsWith(startChar) && endsWith(endChar) }`.

<a id="r3-006"></a>
#### R3-006 — Remaining negative-value edge cases in `Int.lpad`, `Duration.format`, and `MiscJavaFuncs.sleepSecs`
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (all three reproduced via `jshell` against `core-utils-jvm-5.3.1.jar`) · **Follows up:** RV-006
**Where:**
- `core-utils/src/commonMain/kotlin/com/pambrose/common/util/MiscFuncs.kt:60-68`
- `core-utils/src/jvmMain/kotlin/com/pambrose/common/time/Durations.kt:63-64,71-87`
- `core-utils/src/jvmMain/java/com/pambrose/common/util/MiscJavaFuncs.java:32-34`

Three edge cases remain after RV-006:
- **`Int.lpad`:** `5.lpad(-1)` and `(-5).lpad(-1, ' ')` throw `IllegalArgumentException`, whereas `(-5).lpad(-1)` silently returns `"-5"` because `(width - 1).coerceAtLeast(0)` clamps negative widths to `0`. Worse, `(-5).lpad(Int.MIN_VALUE)` overflows `width - 1` to `Int.MAX_VALUE` and attempts to allocate a ~2 GB string.
- **`Duration.format`:** `(-500).milliseconds.format(includeMillis = false)` returns `"-0:00:00:00"`, because `negative` checks `millis < 0` even when milliseconds are omitted and truncated seconds are `0`. Separately, KDoc lines 63–64 state that an infinite duration formats as "the largest finite one, `106751991167:07:12:55`", whereas Kotlin's largest finite `Duration` is `Long.MAX_VALUE / 2 - 1` ms (`53375995583:15:36:27.902`), not `Long.MAX_VALUE` ms. (Corrected while fixing: `Long.MAX_VALUE / 2` ms is `Duration.INFINITE` itself.)
- **`MiscJavaFuncs.sleepSecs`:** `Math.min(time, Long.MAX_VALUE / 1000) * 1000` only guards positive overflow. `sleepSecs(Long.MIN_VALUE)` overflows to `0` and returns without throwing `IllegalArgumentException` (unlike `sleepSecs(-1)`), and `sleepSecs(Long.MIN_VALUE / 1000 - 1)` overflows to `+9223372036854775616` ms (~292 million years).

**Fix:**
- `lpad`: `require(width >= 0) { "Desired length $width is less than zero." }` before branching.
- `Duration.format`: compute `negative` from `if (includeMillis) millis < 0 else millis <= -1000L`, and change the KDoc wording to "`Long.MAX_VALUE` milliseconds".
- `MiscJavaFuncs.sleepSecs`: add `if (time < 0) throw new IllegalArgumentException("timeout value is negative");`.

<a id="r3-007"></a>
#### R3-007 — `core-utils/README.md` recommends `SingleAssignVar.singleAssign()` without noting its 5.0.0 deprecation
**Severity:** Low · **Category:** docs · **Verdict:** confirmed · **Follows up:** RV-010
**Where:** `core-utils/README.md:192-198,427`

The README recommends `SingleAssignVar.singleAssign()` for JVM write-once properties and lists it in the API table without mentioning that it was deprecated in 5.0.0 (RV-010) in favor of `AtomicDelegates.singleSetReference()` (unlike `toObject` on line 422, which is labeled deprecated).

**Fix:** Mark `singleAssign` as deprecated in favor of `AtomicDelegates.singleSetReference` (or remove the standalone example) in `core-utils/README.md`.

---

### json-utils

<a id="r3-008"></a>
#### R3-008 — `json-utils` leaks `implementation`-scoped `KLogger` in its public multiplatform ABI
**Severity:** Medium · **Category:** API / build · **Verdict:** confirmed (verified in `build.gradle.kts`, `api/json-utils.api`, and `api/json-utils.klib.api`) · **Follows up:** CR-031, RV-012
**Where:**
- `json-utils/build.gradle.kts:13-18`
- `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:505-509`
- `json-utils/api/json-utils.klib.api:25-27`; `json-utils/api/json-utils.api:18-21`
- `core-utils/build.gradle.kts:12-23`

RV-012 moved `kotlin-logging` out of `core-utils`'s `commonMain` `api` into `jvmMain` `api`, and `json-utils` declares `implementation(libs.kotlin.logging)` in `commonMain`. However, the deprecated `object JsonElementUtils` (`DeprecationLevel.WARNING`) still exports `val logger: io.github.oshai.kotlinlogging.KLogger` in `json-utils.klib.api:27` and `json-utils.api:20`. On the JVM, this leak is masked only because `core-utils` exports `kotlin-logging` in `jvmMain`; on JS, wasmJs, Native, and `commonMain`, `KLogger` sits in `json-utils`'s public ABI even though `kotlin-logging` is not exported as `api` anywhere in its transitive graph.

**Fix:**
- For 5.x (preserving binary compatibility): either promote `implementation(libs.kotlin.logging)` to `api(libs.kotlin.logging)` in `json-utils/build.gradle.kts:17`, or narrow `JsonElementUtils` to `DeprecationLevel.HIDDEN` so Kotlin callers and Dokka no longer see it while compiled binaries keep working.
- For 6.0.0: remove `JsonElementUtils` entirely and keep `implementation(libs.kotlin.logging)`.

<a id="r3-009"></a>
#### R3-009 — `String.reformatJson` throws `JsonEncodingException` on `NaN`, `Infinity`, `-Infinity`, and overflowing numbers
**Severity:** Low · **Category:** bug / docs · **Verdict:** confirmed (reproduced via `jshell` against `json-utils-jvm-5.3.1.jar`) · **Follows up:** RV-016
**Where:**
- `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:364,382-383,419-425`
- `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonContentUtils.kt:35-48`
- `json-utils/README.md:167-170`

`json-utils/README.md:167-170` states that "`parseJson` (and `reformatJson`, which uses it) accepts only valid JSON. ... `NaN`, `Infinity` and `-Infinity`, which kotlinx writes for non-finite doubles, are accepted."

While `"[NaN, Infinity, -Infinity]".parseJson()` succeeds, `reformatJson` calls `parseJson().toJsonString(prettyPrint)`, which encodes through `JsonContentUtils.prettyFormat` / `rawFormat` (both have `allowSpecialFloatingPointValues = false`). Reproduced outputs:
- `"[NaN, Infinity, -Infinity]".reformatJson(false)` → throws `JsonEncodingException`
- `"""{"a": NaN}""".reformatJson(true)` → throws `JsonEncodingException`
- `"[1e500]".reformatJson(false)` (valid RFC 8259 JSON whose exponent overflows `Double` to `Infinity`) → throws `JsonEncodingException`

**Fix:** Either enable `allowSpecialFloatingPointValues` if `reformatJson` is meant to accept them, or update `README.md` and KDoc to state that `reformatJson` / `toFormattedString` / `toJsonString` reject non-finite doubles because `JsonContentUtils` keeps `allowSpecialFloatingPointValues = false`.

<a id="r3-010"></a>
#### R3-010 — `json-utils/README.md` conversion snippet omits `import com.pambrose.common.json.parseJson`
**Severity:** Low · **Category:** docs · **Verdict:** confirmed · **Follows up:** RV-017
**Where:** `json-utils/README.md:142-150`

Line 150 calls `"""{"a":1}""".parseJson()`, but the import block on lines 142–147 omits `import com.pambrose.common.json.parseJson`, so the snippet does not compile as written.

**Fix:** Add `import com.pambrose.common.json.parseJson` to `json-utils/README.md:142-147`.

---

### ktor-server-utils

<a id="r3-011"></a>
#### R3-011 — `HerokuHttpsRedirect` allows open redirects and malformed URLs on non-origin-form request targets even when `host` is pinned
**Severity:** High · **Category:** security / bug · **Verdict:** confirmed (reproduced live against Ktor 3.6.2 CIO server via raw TCP socket; verified in `io.ktor.http.cio.HttpParserKt.parseUri` and `CIOConnectionPoint.getServerHost`) · **Follows up:** CR-040, RV-026
**Where:**
- `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt:48,73,145-151`
- `ktor-server-utils/README.md:83-94`

To preserve raw query strings (RV-026), `HerokuHttpsRedirect` strips the trailing `/` from the HTTPS origin and concatenates `call.request.uri` verbatim:

```kotlin
val origin =
  URLBuilder(
    protocol = URLProtocol.HTTPS,
    host = feature.host ?: call.request.origin.serverHost,
    port = feature.redirectPort,
  ).buildString().removeSuffix("/")
val redirectUrl = origin + call.request.uri
```

Ktor CIO's `HttpParserKt.parseUri` accepts any non-empty, non-space request-target token and passes it unchanged into `call.request.uri`. Reproduced live against a Ktor 3.6.2 CIO server with `host = "myapp.herokuapp.com"` explicitly pinned:
- **Open redirect via `@`:** `GET @evil.com/phish HTTP/1.1` + `X-Forwarded-Proto: http` → `301 Moved Permanently`, `Location: https://myapp.herokuapp.com@evil.com/phish` (browsers treat `myapp.herokuapp.com` as userinfo and navigate to `https://evil.com/phish`).
- **Open redirect via `.`:** `GET .evil.com/phish HTTP/1.1` + `X-Forwarded-Proto: http` → `301 Moved Permanently`, `Location: https://myapp.herokuapp.com.evil.com/phish`.
- **Broken RFC 9112 §3.2.2 absolute-form requests:** `GET http://myapp.herokuapp.com/hello?b=2&a=1 HTTP/1.1` + `X-Forwarded-Proto: http` → `301 Moved Permanently`, `Location: https://myapp.herokuapp.comhttp://myapp.herokuapp.com/hello?b=2&a=1`.
- **Unvalidated `Host` header default:** When `host` is left at its default `null`, `call.request.origin.serverHost` comes from the client's `Host` header (`CIOConnectionPoint.getServerHost()` returns `hostHeaderValue.substringBeforeLast(":")` with no validation). Any deployment reachable without a front proxy enforcing `Host` redirects `Host: evil.com` + `X-Forwarded-Proto: http` to `https://evil.com/...`, and neither the KDoc nor `README.md` advises pinning `host`.

**Fix:**
- Normalize `call.request.uri` before appending to `origin`: if it is an absolute-form URI (`http://...` or `https://...`), extract the raw path+query starting at the first `/` after the authority (defaulting to `"/"`); otherwise require it to start with `"/"` (rejecting non-origin-form targets like `@evil.com/phish` with `400 Bad Request` or prepending `"/"`).
- Validate that the chosen host contains only valid hostname / IP-literal characters (rejecting `@`, `/`, `?`, `#`, whitespace), and document in KDoc and `README.md` that `host` should be configured explicitly unless an upstream router enforces the `Host` header.
- Add raw-socket tests in `HerokuHttpsRedirectTests` covering `@evil.com/phish`, `.evil.com/phish`, and absolute-form `http://myapp.example.com/hello?b=2&a=1`.

<a id="r3-012"></a>
#### R3-012 — `ServletRoute` fabricates `Content-Type: application/octet-stream` and `Content-Length: 0` on `304`, `204`, and bodyless responses
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed (reproduced live against Ktor 3.6.2 CIO server; verified in `BaseApplicationResponse.commitHeaders` and `BaseApplicationResponseKt.getForbidsContentLength`) · **Follows up:** RV-023, RV-025
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:91-111`

Lines 91–96 always default a missing `Content-Type` to `ContentType.Application.OctetStream`, and line 110 always calls `call.respondBytes(response.getBodyBytes(), contentType)` for non-`HEAD` requests. In Ktor 3.6.2, `forbidsContentLength` covers `1xx` and `204` but *not* `304`, so passing `ByteArrayContent(ByteArray(0), ContentType.Application.OctetStream, null)` causes Ktor to emit headers that violate RFC 9110.

Reproduced live against a Ktor CIO server:
- `GET /s304` (servlet sets status `304`, no body, no content type) → `HTTP/1.1 304 Not Modified` with `Content-Length: 0` and `Content-Type: application/octet-stream` (overwriting cached `Content-Type` on conditional GETs such as `LastModifiedServlet`).
- `GET /s204` (servlet sets status `204`, no body, no content type) → `HTTP/1.1 204 No Content` with `Content-Type: application/octet-stream`.
- `302 Found` (`sendRedirect`), `405 Method Not Allowed` (`sendError`), and `HEAD` on `304`/`204` similarly receive a fabricated `Content-Type: application/octet-stream` (and `Content-Length: 0` on `HEAD`).

**Fix:**
- Keep `contentType` nullable (`ContentType?`), falling back to `ContentType.Application.OctetStream` only when a non-empty body has no content type (or a malformed content type was explicitly set).
- For statuses that forbid a body (`1xx`, `204`, `304`), respond with `OutgoingContent.NoContent` with `contentLength = null` and `contentType = parsedContentType`.
- For `HEAD` on other statuses, keep `contentLength = response.getBodySize().toLong()` and pass the nullable `contentType`.
- Add assertions in `ServletRouteTests` that `304` and `204` emit neither `Content-Type: application/octet-stream` nor `Content-Length: 0`.

<a id="r3-013"></a>
#### R3-013 — `KtorServletResponse` violates Jakarta Servlet 6.1 contracts for committed responses, `setContentType(null)`, `reset()`, and null header names
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (verified against `jakarta.servlet-api-6.1.0-sources.jar`) · **Follows up:** RV-024, RV-025
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:75-154,192-221,241-248`

Four divergences from the Jakarta Servlet 6.1 specification:
1. **Committed-response state:** `sendError` (`:192-205`) and `sendRedirect` (`:211-221`) do not check `check(!committed)` (Servlet 6.1 requires throwing `IllegalStateException` once committed). Likewise, `setStatus`, `setHeader`/`addHeader` (and Date/Int variants), `setContentType`, and `setCharacterEncoding` all mutate state after `committed == true` instead of being no-ops.
2. **`setContentType(null)` leaves character encoding set (`:136-140`):** Servlet 6.1 requires `setContentType(null)` (when uncommitted and before `getWriter()`) to clear both the content type and any previously set character encoding.
3. **`reset()` does not clear writer/stream state (`:241-248`):** Servlet 6.1 explicitly allows `getWriter(); reset(); getOutputStream()`, but `writerUsed`, `streamUsed`, `printWriter`, `servletOutputStream`, and `charEncodingValue` are left intact, so `getOutputStream()` throws `IllegalStateException`.
4. **Null header names (`:84-124`):** Servlet 6.1 defines `setHeader`/`addHeader`/`setDateHeader`/`addDateHeader`/`setIntHeader`/`addIntHeader` with a `null` `name` as no-ops, whereas the Kotlin overrides take non-null `name: String` and throw NPE from Java servlets.

**Fix:**
- Check `check(!committed)` in `sendError` and `sendRedirect`, and guard `setStatus`, header setters, `setContentType`, and `setCharacterEncoding` with `if (committed) return`.
- When `setContentType(null)` is called, also clear `charEncodingValue` (via `setCharacterEncoding(null as String?)`).
- In `reset()`, reset `writerUsed`, `streamUsed`, `printWriter`, `servletOutputStream`, and `charEncodingValue`.
- Accept `name: String?` on the six header methods and return immediately when `name == null`.

<a id="r3-014"></a>
#### R3-014 — `KtorServletRequest.getCookies()` crashes with HTTP 500 on a non-token cookie name
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (verified via Ktor 3.6.2 `CookieKt.clientCookieHeaderPattern` and Jakarta Servlet 6.1 `Cookie.java:122-133,372-382`) · **Follows up:** RV-025
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletRequest.kt:125-129`

Ktor's `clientCookieHeaderPattern` accepts cookie names containing RFC 2616/6265 reserved token separators (`()<>@,;:\"/[]?`) or non-ASCII characters (for example `Cookie: a/b=1; session=xyz`), whereas Jakarta Servlet 6.1's `Cookie(name, value)` validates `name` and throws `IllegalArgumentException`. Because `getCookies()` calls `Cookie(name, value)` in `.map { ... }` without handling invalid names, a single malformed or third-party cookie in the request header causes any servlet calling `req.getCookies()` to fail with HTTP 500.

**Fix:** Use `mapNotNull { (name, value) -> runCatching { Cookie(name, value) }.getOrNull() }` and add a test with `Cookie: bad/name=1; good=2` asserting only `good=2` is returned.

---

### service-utils

<a id="r3-050"></a>
#### R3-050 — `HerokuHttpsRedirect` exclusion predicates miss absolute-form request targets
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (found while fixing R3-011; reproduced against a Ktor 3.6.2 CIO server via raw TCP socket) · **Follows up:** R3-011
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt`

On Ktor CIO, a request with an absolute-form target, such as `GET http://myapp.example.com/health HTTP/1.1` with `X-Forwarded-Proto: http`, reaches the exclusion predicates with the full URL as its path, so `excludePrefix("/health")` does not match and the request is redirected instead of excluded. Since R3-011 the redirect itself uses only the target's path and query.

**Fix:** Evaluate the exclusion predicates against the same normalized path the redirect uses, so an absolute-form request is excluded exactly when its origin-form equivalent would be. Add raw-socket tests.

---

<a id="r3-015"></a>
#### R3-015 — Jetty admin and metrics 404/500 error pages still disclose Jetty and internal exception details
**Severity:** Medium · **Category:** security / tests · **Verdict:** confirmed (reproduced live against Jetty 12.1.15 via `JettyServerKt.jettyServer`; verified in `jetty-ee11-servlet-12.1.15-sources.jar` `ErrorHandler.java:40-46,151-179`) · **Follows up:** RV-031
**Where:**
- `service-utils/src/main/kotlin/com/pambrose/common/service/JettyServer.kt:21,45`
- `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:808`

`JettyServer.kt:45` installs `org.eclipse.jetty.ee11.servlet.ErrorHandler().apply { isShowStacks = false }`. However, in Jetty 12.1.15:
- `ee11.servlet.ErrorHandler`'s constructor sets `setShowOrigin(true)` (`ErrorHandler.java:42`). Reproduced live on `GET /missing` (404): the response body includes
  `<tr><th>SERVLET:</th><td>org.eclipse.jetty.ee11.servlet.ServletHandler$Default404Servlet-5119fb47</td></tr>`, disclosing Jetty and its EE11 servlet handler package on every 404.
- `GenericServiceTests.kt:808` (`body() shouldNotContain "Jetty"`) missed this because `org.eclipse.jetty...` is lowercase `"jetty"` and `shouldNotContain` is case-sensitive.
- On a 500 error, `ee11.servlet.ErrorHandler.writeErrorHtmlMessage` (`ErrorHandler.java:174-178`) unconditionally prints the exception message and loops over `CAUSED BY` (ignoring `isShowCauses`). Reproduced live on a servlet throwing `RuntimeException("internal-db-secret-error")`: the HTML body contained the full exception class, message, servlet holder name, and `CAUSED BY` row.

**Fix:**
- Use Jetty's core `org.eclipse.jetty.server.handler.ErrorHandler()` (which defaults `isShowStacks = false`, `isShowCauses = false`, `isShowOrigin = false`, and respects `isShowCauses`) or install a minimal `ErrorHandler` that renders only the status code and reason phrase.
- Update `GenericServiceTests.kt:808` to assert `body().lowercase() shouldNotContain "jetty"`, and add a 500-error assertion checking that neither servlet class names nor exception messages leak.

<a id="r3-016"></a>
#### R3-016 — `AbstractGenericService.startUp()` leaks all started sub-services if `Runtime.addShutdownHook()` throws
**Severity:** Medium · **Category:** leak / lifecycle · **Verdict:** confirmed (by trace) · **Follows up:** CR-046
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:216-243`

`startUp()` records reverse-order `stopActions` inside `runCatching { ... }` for Zipkin, `DropwizardExports`, `MetricsService`, `JmxReporter`, and `servletServiceOrNull`. Lines 241–242 run *after* the `runCatching` block:

```kotlin
if (registersShutDownHook)
  shutDownHook = shutDownHookAction(this).also { Runtime.getRuntime().addShutdownHook(it) }
```

If `Runtime.getRuntime().addShutdownHook(it)` throws (for example `IllegalStateException("Shutdown in progress")` when a service starts concurrently with JVM shutdown, or `SecurityException`), Guava transitions the service to `FAILED` without calling `shutDown()`, and none of `stopActions` run. Every already-started sub-service (admin port, metrics port, `DropwizardExports` on `PrometheusRegistry.defaultRegistry`, `JmxReporter` MBeans, `ZipkinReporterService`) is leaked.

**Fix:** Move lines 241–242 inside the `runCatching { ... }` block so any failure registering the shutdown hook triggers the same reverse-order rollback as every other startup step, and add a unit test verifying rollback when hook registration throws.

<a id="r3-017"></a>
#### R3-017 — `service-utils/README.md` still documents removed Ktor plugins and attributes a bind `host` to `ZipkinConfig`
**Severity:** Low · **Category:** docs · **Verdict:** confirmed · **Follows up:** RV-034
**Where:** `service-utils/README.md:40-41,156-158,306`; `gradle/libs.versions.toml:163-165`; `service-utils/src/main/kotlin/com/pambrose/common/service/ZipkinConfig.kt:30-36`

- RV-034 removed `ktor-server-call-logging` and `ktor-server-compression` from `ktor-server-service` (now only `ktor-server-cio`), but lines 156–158 still state that `service-utils` uses Ktor's compression and call-logging plugins as `implementation` dependencies, and line 306 lists `- Ktor server CIO, call logging, and compression`.
- Lines 40–41 state that `AdminConfig`, `MetricsConfig`, and `ZipkinConfig` each carry "an optional `host` to restrict the bind address". `ZipkinConfig` has no bind-address `host`; it has a required `hostname: String` targeting the Zipkin collector.

**Fix:** Update lines 156–158 and 306 to reflect that only `ktor-server-cio` is bundled, and restrict the optional `host` description on lines 40–41 to `AdminConfig` and `MetricsConfig`.

---

### prometheus-utils

<a id="r3-018"></a>
#### R3-018 — `SystemMetrics.forget()` with `enableMemoryPoolsExports = true` permanently leaks GC `NotificationListener`s
**Severity:** Medium · **Category:** leak · **Verdict:** confirmed (verified in `prometheus-metrics-instrumentation-jvm-1.10.0-sources.jar` `JvmMemoryPoolAllocationMetrics.java:70-87`) · **Follows up:** #208, #226
**Where:**
- `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SystemMetrics.kt:123-125,147-151`
- `prometheus-utils/README.md:146-148,153`

`JvmMemoryPoolAllocationMetrics.register(registry)` registers `jvm_memory_pool_allocated_bytes_total` and attaches a new `AllocationCountingNotificationListener(allocatedCounter)` to every platform `GarbageCollectorMXBean` via `((NotificationEmitter) bean).addNotificationListener(listener, null, null)`. Prometheus Java client 1.10.0 never removes that listener, and the listener class is package-private with no handle returned.

Consequently, neither `registry.clear()`, `registry.unregister(...)`, nor `SystemMetrics.forget(registry)` detaches it. Every cycle of `registry.clear(); SystemMetrics.forget(registry); SystemMetrics.initialize(enableMemoryPoolsExports = true, registry = registry)` — the test-harness pattern documented at `SystemMetrics.kt:140` and `README.md:147-148` — and every isolated `PrometheusRegistry()` initialized with `enableMemoryPoolsExports = true` permanently leaks a `NotificationListener`, its `Counter`, and its `HashMap<String, Long>` on every `GarbageCollectorMXBean` for the lifetime of the JVM, running `synchronized handleNotification` on every GC cycle.

**Fix:**
- At minimum, document this upstream Prometheus 1.x limitation in `SystemMetrics.forget` and `enableMemoryPoolsExports` KDoc and `prometheus-utils/README.md`, warning callers not to enable `enableMemoryPoolsExports` repeatedly across `clear()`/`forget()` cycles.
- To make repeated registration leak-free, replace direct delegation to `JvmMemoryPoolAllocationMetrics` with a single JVM-wide GC `NotificationListener` dispatching to active registries (or a removable listener handle detached by `forget()`).

<a id="r3-019"></a>
#### R3-019 — `InstrumentedThreadFactory` has no way to unregister its three metrics
**Severity:** Low · **Category:** API · **Verdict:** confirmed (`prometheus-utils/api/prometheus-utils.api:1-6`)
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/concurrent/InstrumentedThreadFactory.kt:45-77`

`InstrumentedThreadFactory` registers `<name>_threads_created`, `<name>_threads_running`, and `<name>_threads_terminated` in `init`, stores them in `private val` fields, and discards `registry`. Unlike `PrometheusDsl` (which returns the collector) and `SamplerGaugeCollector` (which implements `Collector`), callers have no handle and no `unregister()` / `close()` method. Recreating a thread pool with the same `name` on the same registry always throws `IllegalArgumentException` unless the entire registry is cleared.

**Fix:** Retain `private val registry: PrometheusRegistry` and add `fun unregister()` (or implement `AutoCloseable`) calling `registry.unregister` on all three collectors.

<a id="r3-020"></a>
#### R3-020 — `SystemMetricsTests` clears and leaves collectors in `PrometheusRegistry.defaultRegistry` without cleanup
**Severity:** Low · **Category:** tests · **Verdict:** confirmed · **Follows up:** RV-040, #226
**Where:** `prometheus-utils/src/test/kotlin/com/pambrose/common/metrics/SystemMetricsTests.kt:98-112,205-214`

Unlike `PrometheusDslTests.kt:145-148` and `SamplerGaugeCollectorTests.kt:222-226`, which restore `PrometheusRegistry.defaultRegistry` in `finally` blocks, `SystemMetricsTests`:
- registers six JVM metric sets onto `PrometheusRegistry.defaultRegistry` (`:98-112`) without cleanup;
- calls `PrometheusRegistry.defaultRegistry.clear()` and registers `enableThreadExports` (`:205-214`) without a `finally` block, wiping collectors registered by other specs and leaving `jvm_threads_*` behind after the spec finishes.

**Fix:** Wrap both default-registry tests in `try { ... } finally { PrometheusRegistry.defaultRegistry.clear(); SystemMetrics.forget() }`.

---

### guava-utils

<a id="r3-021"></a>
#### R3-021 — `ByteArray.unzip` and `Semaphore.withLock` omit `@Throws`, while mangled Kotlin-`Duration` overloads keep unreachable `@Throws`
**Severity:** Low · **Category:** API · **Verdict:** confirmed (verified via `javap -p` on `ZipExtensionsKt`, `ConcurrentUtils`, `GenericMonitor`, and `VerboseCountDownLatch`) · **Follows up:** RV-046
**Where:**
- `guava-utils/src/main/kotlin/com/pambrose/common/util/ZipExtensions.kt:76-80`; `guava-utils/README.md:258-261`
- `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConcurrentExtensions.kt:65-72`
- `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericMonitor.kt:129-133,194-198,211-216`
- `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/VerboseCountDownLatch.kt:43-68`

- `ZipExtensionsKt.unzip(byte[])` and `unzip(byte[], long)` are annotated `@JvmOverloads` for Java callers and documented with `@throws java.io.IOException`, yet neither overload declares `throws java.io.IOException` in bytecode. Java callers cannot write `catch (IOException e)` around `ZipExtensionsKt.unzip(...)`, and checked `IOException`s escape uncaught.
- `ConcurrentUtils.withLock(Semaphore, Function0)` is directly Java-callable and invokes `Semaphore.acquire()`, yet omits `@Throws(InterruptedException::class)` and KDoc `@throws InterruptedException`.
- Conversely, `GenericMonitor.waitUntilTrueWithInterruption` (`-LRDsOJo`, `-VtjQ1oo`, `-NqJ4yvY`) and `VerboseCountDownLatch.await-VtjQ1oo` still carry `@Throws(InterruptedException::class)` on Kotlin-`Duration`-mangled methods that Java cannot call, without `java.time.Duration` overloads (the pattern RV-046 noted).

**Fix:** Add `@Throws(IOException::class)` to `ByteArray.unzip` and `@Throws(InterruptedException::class)` to `Semaphore.withLock`, and either add `java.time.Duration` overloads for `GenericMonitor.waitUntilTrueWithInterruption` / `VerboseCountDownLatch.await` (matching `GenericIdleService` and `GenericExecutionThreadService`) or drop the unreachable `@Throws` annotations on the mangled methods.

<a id="r3-022"></a>
#### R3-022 — `Service.genericServiceListener` leaks mutable `GuavaDsl.ServiceListenerHelper` as its public return type
**Severity:** Low · **Category:** API / docs · **Verdict:** confirmed (`guava-utils/api/guava-utils.api:105`)
**Where:**
- `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericServiceListener.kt:30-33`
- `guava-utils/api/guava-utils.api:105`
- `guava-utils/README.md:301`

`genericServiceListener` omits an explicit return type (`= serviceListener { ... }`), so Kotlin infers its published ABI return type as `GuavaDsl.ServiceListenerHelper` instead of `Service.Listener`, contradicting both its KDoc (`@return a configured [Service.Listener]`) and `guava-utils/README.md:301`. Returning `ServiceListenerHelper` allows callers to mutate `starting`/`running`/etc. callbacks after construction, silently replacing the logging handlers.

**Fix:** Add the explicit return type `: Service.Listener` in `6.0.0` (changing a return type is an ABI break) and run `make abi-update`; align `README.md:301` in the meantime if desired.

---

### zipkin-utils

<a id="r3-023"></a>
#### R3-023 — `zipkin-utils/README.md` example uses deprecated `AsyncReporter.create(Sender)` and leaks `reporter` and `sender`
**Severity:** Low · **Category:** docs · **Verdict:** confirmed (verified in `zipkin-reporter-3.5.3-sources.jar`, `zipkin-sender-okhttp3-3.5.3-sources.jar`, and `zipkin-reporter-brave-3.5.3-sources.jar`)
**Where:** `zipkin-utils/README.md:27-47,63-67`

- `OkHttpSender` extends `SenderAdapter extends Sender implements BytesMessageSender`. Because `Sender` is more specific than `BytesMessageSender`, `AsyncReporter.create(sender)` (`README.md:39-40`) resolves to `@Deprecated AsyncReporter.create(Sender)` (deprecated since 3.2, slated for removal in 4.0) instead of `AsyncReporter.create(BytesMessageSender)`.
- Lines 63–67 advise calling `tracing.close()`, which does not flush or close `reporter` (background flusher thread) or `sender` (OkHttp thread/connection pool) — compare `ZipkinReporterService.kt:98-103`.
- Upstream `ZipkinSpanHandler` javadoc also recommends `AsyncZipkinSpanHandler.create(sender as BytesMessageSender)` for Zipkin JSON V2 to avoid inline span conversion on request threads.

**Fix:** Update the README example to pass `sender as BytesMessageSender` (or use `AsyncZipkinSpanHandler.create(sender as BytesMessageSender)`) and show flushing and closing `reporter`/`handler` and `sender` alongside `tracing.close()`.

---

### script-utils (common / java / kotlin / python)

<a id="r3-024"></a>
#### R3-024 — `ScriptUtils.resetContext` leaves previous `ENGINE_SCOPE` bindings strongly retained by Kotlin and Jython engines
**Severity:** Medium · **Category:** leak · **Verdict:** confirmed (verified live via reflection on `KotlinScript`/`KotlinJsr223ScriptEngineImpl` and `PythonScript`/`PyScriptEngine`) · **Follows up:** RV-048, RV-049
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/ScriptUtils.kt:56-63` (affects `AbstractScript.kt:82-88`, `AbstractExprEvaluator.kt:79`, `KotlinScriptPool`, `KotlinExprEvaluatorPool`, `PythonScriptPool`)

`ScriptUtils.resetContext` assigns a fresh `SimpleScriptContext` to `scriptEngine.context` without clearing the old `ENGINE_SCOPE` `Bindings`. Both upstream engines keep a strong reference to the previous `ScriptContext` until the next `eval()`:
- **`KotlinJsr223ScriptEngineImpl`** stores `lastScriptContext` (only overwritten on `compileAndEval`/`compile`). Live reflection after `ks.add("secret", obj); ks.eval("secret"); ks.resetContext(false)` confirmed `lastScriptContext !== eng.context` and its `ENGINE_SCOPE` still contains `["__variables", "kotlin.script.engine", "kotlin.script.state"]` — retaining both `ScriptVariables` (every host object bound by the previous borrower) and the entire previous `K2ReplState` (the compiler/REPL history that `KotlinExprEvaluatorPool`'s `resetEvery` tried to free) for the whole idle lifetime of a pooled instance.
- **Jython's `PyScriptEngine`** calls `interp.setLocals(new PyScriptEngineScope(this, context))` on `eval()`, retaining the old `ScriptContext` until the next `eval()`. Live reflection confirmed `interp.locals.context !== pyEng.context` and its `ENGINE_SCOPE` still holds the previous borrower's bound object after `resetContext(false)`.

**Fix:** In `ScriptUtils.resetContext`, call `getBindings(ENGINE_SCOPE)?.clear()` *before* assigning the new `SimpleScriptContext` (do not clear `GLOBAL_SCOPE`, because during initial construction `GLOBAL_SCOPE` still points to the `ScriptEngineManager`'s shared global bindings). Live testing confirmed that clearing `ENGINE_SCOPE` drops `ScriptVariables`, `K2ReplState`, and Python bindings immediately while subsequent `eval()` calls on both engines succeed.

<a id="r3-025"></a>
#### R3-025 — `AbstractScript.params` default argument reads unsynchronized `typeMap` outside `@Synchronized`
**Severity:** Medium · **Category:** concurrency · **Verdict:** confirmed (verified via `javap -c -p -v` on `AbstractScript.class`) · **Follows up:** RV-056
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:108-112`

RV-056 added `@Synchronized` to `open fun params(name: String, types: Array<out KType> = typeMap[name] ?: error(...))`. In Kotlin, default parameter expressions compile into a synthetic `public static params$default(...)` method that does **not** carry `ACC_SYNCHRONIZED` and emits no `monitorenter`. Bytecode inspection confirms `params$default` reads the unsynchronized `LinkedHashMap` `typeMap` outside the monitor before delegating to synchronized `params(String, KType[])`, so a 1-arg `params(name)` call races with concurrent `add()` / `resetContext()`.

**Fix:** Extract `@Synchronized private fun registeredTypes(name: String): Array<out KType> = typeMap[name] ?: error("No type parameters registered for $name")` and use `types: Array<out KType> = registeredTypes(name)` in `params`. This keeps the exact binary and source ABI while synchronizing the default lookup.

<a id="r3-026"></a>
#### R3-026 — Private generic classes whose public supertype is non-generic (`IntStream`, `LongStream`, `DoubleStream`) cannot be bound as their public type
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed (reproduced live in `KotlinScript` and `JavaScript`) · **Follows up:** RV-052
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:151-162`

`IntStream.of(1, 2)` has runtime class `java.util.stream.IntPipeline$Head<E_IN>` (package-private, `typeParameterCount == 1`), while its public supertype `java.util.stream.IntStream` declares 0 type parameters (same for `LongStream`, `DoubleStream`, and any private generic class implementing a non-generic interface):
- `add("s", IntStream.of(1, 2))` hits line 151 (`paramCnt > 0 && types.isEmpty()`) and throws `ScriptException: Expected 1 type parameter to be specified for "s"`.
- `add("s", IntStream.of(1, 2), typeOf<Int>())` is accepted, then `accessibleClass` finds no nameable supertype with arity 1 (`IntStream` has 0, `BaseStream` has 2) and falls back to `kotlin.Any` / `java.lang.Object`, losing the `IntStream` API.
- The RV-052 allowance at lines 159–162 handles `0 → N` (`Comparator.naturalOrder()`), but misses `N → 0` because line 151 rejects `types.isEmpty()` before checking `isPubliclyAccessible()`.

**Fix:** Before line 151's `paramCnt > 0 && types.isEmpty()` branch, permit `types.isEmpty()` when `!value.javaClass.isPubliclyAccessible()` and `value.javaClass.nameableSupertypes().firstOrNull()?.typeParameters?.isEmpty() == true` (checking the *nearest* nameable supertype so `listOf(1, 2)` — whose nearest nameable supertype is `AbstractList<E>`, despite also implementing 0-arity `RandomAccess` — still requires 1 type parameter). Add tests for `IntStream.of(1, 2)` in `KotlinScriptTests` and `JavaScriptTests`.

<a id="r3-027"></a>
#### R3-027 — `AbstractScript.isPubliclyAccessible()` accepts non-static inner classes of generic outer classes, generating uncompilable types
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed (reproduced live in `KotlinScript` with Guava's `TypeToken.of(String.class).getTypes()`) · **Follows up:** RV-051, RV-052
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:299-307`

`isPubliclyAccessible()` checks module export and public visibility along `enclosingClass`, but does not check whether a non-static member class has a generic enclosing owner (`outer.typeParameters.isNotEmpty()`). For Guava's `TypeToken<T>.TypeSet` (or any `class Outer<T> { inner class Inner }`), `accessibleClass` returns `TypeToken.TypeSet::class`, and `KotlinScript.castType` emits `as com.google.common.reflect.TypeToken.TypeSet` without outer type arguments. Reproduced live: `KotlinScript.eval` fails with `ScriptException: ERROR One type argument expected. Use 'TypeToken<*>.TypeSet' if you do not intend to pass type arguments.` In `JavaScript`, an inner class with its own type parameters (`Outer<T>.Inner<U>`) similarly emits `Outer.Inner<Integer>`, which `javac` rejects on a raw outer type.

**Fix:** In `isPubliclyAccessible()`, reject any class that has a non-static enclosing owner with `typeParameters.isNotEmpty()` (walking `c` while `c.isMemberClass && !Modifier.isStatic(c.modifiers)` to `c.enclosingClass`), so `accessibleClass` resolves to the nearest nameable supertype (such as `ForwardingSet` / `Set`).

<a id="r3-028"></a>
#### R3-028 — `KotlinScript.isReserved` does not reject all-underscore identifiers (`_`, `__`, `___`), poisoning the script instance
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed (reproduced live on Kotlin 2.4.21 `KotlinScript`) · **Follows up:** RV-048
**Where:** `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:48`

`AbstractScript.IDENTIFIER` (`[A-Za-z_][A-Za-z0-9_]*`) admits `"_"`, `"__"`, `"___"`, and `KotlinScript.isReserved` only checks `KOTLIN_KEYWORDS`, `"bindings"`, and `"__variables"`. Calling `add("_", 42)` or `add("__", 42)` succeeds instead of throwing `ScriptException`, and `eval(...)` then fails with `ScriptException: ERROR Names _, __, ___, ... are reserved in Kotlin.` Because `AbstractScript.prepare()` leaves failed variables in `unboundNames`, every subsequent `eval()` on that `KotlinScript` instance fails until `resetContext`. (`JavaScript` already rejects `"_"` via `SourceVersion.isKeyword("_")`.)

**Fix:** Update `KotlinScript.isReserved` to `name in KOTLIN_KEYWORDS || name == "bindings" || name == VARIABLES_BINDING || name.all { it == '_' }`, and add a regression test for `"_"`, `"__"`, and `"___"`.

<a id="r3-029"></a>
#### R3-029 — `JavaScript.renderType` throws unwrapped `NullPointerException` on local/anonymous `KType` and drops `in`/`out` variance
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed (reproduced live via `jshell` against `JavaScript`)
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:140-149`

- Lines 145–147 read `clazz.canonicalName` (and `clazz.componentType.canonicalName`), which is `null` for local, anonymous, or hidden classes. Passing `typeOf<Local>()` to `JavaScript.add` / `params` throws an unwrapped `NullPointerException` (and an array of a local class renders `"null[]"`).
- Line 141 `type.arguments.map { argument -> argument.type?.let(::renderType) }` ignores `argument.variance` (`KVariance.OUT` / `KVariance.IN`). Confirmed live: `typeOf<List<out Number>>()` renders as `java.util.List<java.lang.Number>` instead of `java.util.List<? extends java.lang.Number>`, and `KVariance.IN` drops `? super`.

**Fix:** Check `clazz.canonicalName` (and `clazz.componentType.canonicalName`) for `null` and throw `IllegalArgumentException` / `ScriptException` with a clear message. Render `KVariance.OUT` as `"? extends $rendered"` and `KVariance.IN` as `"? super $rendered"` for non-array type arguments (keeping plain `$rendered` for `Array<out T>`).

<a id="r3-030"></a>
#### R3-030 — `JavaScript.import` / `addImport` accepts primitives, arrays, and unnamed-package classes
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (reproduced live via `jshell` against `JavaScript`) · **Follows up:** RV-053
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:96-99`

`import(clazz)` checks only `requireNotNull(clazz.canonicalName)`. Primitives (`int.class`), arrays (`String[].class`), and classes in the unnamed package all have non-null canonical names (`"int"`, `"java.lang.String[]"`, `"DefaultPkgFoo"`). Confirmed live: all three are accepted and generate `import int;`, `import java.lang.String[];`, and `import DefaultPkgFoo;`, causing `javac` to reject every subsequent `eval` / `evalScript` on that instance until `resetForReuse`.

**Fix:** Reject `clazz.isPrimitive`, `clazz.isArray`, and `clazz.packageName.isEmpty()` alongside `clazz.canonicalName == null` with `require(...)` (`IllegalArgumentException`), and update the KDoc.

<a id="r3-031"></a>
#### R3-031 — `AbstractEnginePool.close()` aborts draining on the first `instance.close()` failure
**Severity:** Low · **Category:** leak / bug · **Verdict:** confirmed (by trace)
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEnginePool.kt:89-95,108-111`

`close()` runs `generateSequence { channel.tryReceive().getOrNull() }.forEach { it.close() }`. If closing the first idle instance throws, the loop terminates immediately and all remaining idle instances in `channel` are never closed (compare `populate()` at lines 69–72, which closes every instance and attaches extra failures via `addSuppressed`). Similarly, in `withInstance` (`:89-95`), if `reset(instance)` throws and `finally { returnToPool(instance) }` calls `instance.close()` (because the pool closed concurrently) which also throws, the close exception masks the reset exception without `addSuppressed`.

**Fix:** In `close()`, drain all instances from `channel`, closing each inside `runCatching`, accumulating secondary failures with `addSuppressed` and rethrowing the first. In `withInstance`, attach any exception from `returnToPool` to a pending `reset` exception via `addSuppressed`. Add a test in `AbstractEnginePoolTests` using `FakeEvaluator(failClose = true)`.

<a id="r3-032"></a>
#### R3-032 — Stale and inaccurate documentation in `script-utils-common/README.md` and `AbstractExprEvaluatorPool.kt`
**Severity:** Low · **Category:** docs · **Verdict:** confirmed (code inspection + live check of `run { val y = 1; true }`) · **Follows up:** RV-049, RV-059
**Where:**
- `script-utils-common/README.md:162-163,226,251-254`
- `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluatorPool.kt:26-27`

- `README.md:226` still lists `protected val valueMap: MutableMap<String, Any>` even though RV-059 narrowed it to `Map<String, Any>` (`AbstractScript.kt:64`).
- `README.md:162-163` says `AbstractExprEvaluatorPool` resets with `resetContext` "so no borrower sees the previous borrower's variables", contradicting lines 135–136 and `KotlinExprEvaluatorPool`'s `resetEvery = 20` default.
- `README.md:251-254` omits the `(size: Int, resetEvery: Int)` constructor and `val resetEvery: Int` property.
- `AbstractExprEvaluatorPool.kt:26-27` cites ``(such as `val y = 1` inside `run { }`)`` as a declaration that persists for later borrowers; verified live that `val y = 1` inside `run { }` is lambda-local and does *not* persist (a top-level declaration such as `val y = 1; y > 0` does).

**Fix:** Update `valueMap` to `Map<String, Any>`, align the `resetEvery` description and API reference in `script-utils-common/README.md`, and change the KDoc example in `AbstractExprEvaluatorPool.kt` to ``(such as `val y = 1; y > 0`)``.

<a id="r3-033"></a>
#### R3-033 — No end-to-end test exercises `KotlinExprEvaluatorPool`'s `resetEvery` behavior on the real Kotlin engine
**Severity:** Low · **Category:** tests · **Verdict:** confirmed · **Follows up:** RV-049
**Where:** `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/ScriptPoolContractTests.kt:39-45,137-144`

`ScriptPoolContractTests` exercises only `ProbeEvaluatorPool : AbstractExprEvaluatorPool<KotlinExprEvaluator>(1)` (`resetEvery = 1`). No test in `script-utils-kotlin` constructs `KotlinExprEvaluatorPool(size, resetEvery)` with `resetEvery > 1`, checks `DEFAULT_RESET_EVERY`, checks `resetEvery <= 0` rejection, or verifies on the real Kotlin JSR-223 engine that REPL declarations survive `resetEvery - 1` borrows and disappear after the `resetEvery`-th return.

**Fix:** Add a test in `ScriptPoolContractTests` using `KotlinExprEvaluatorPool(size = 1, resetEvery = 2)` verifying `eval("val y = 1; y > 0") == true`, `eval("y == 1") == true`, and `shouldThrow<ScriptException> { eval("y == 1") }` on the third borrow, plus a check that `resetEvery = 0` throws `IllegalArgumentException`.

---

### email-utils

<a id="r3-034"></a>
#### R3-034 — `ResendService` docs and comments claim each instance owns its own OkHttp client
**Severity:** Low · **Category:** docs · **Verdict:** confirmed (`javap` of `Resend`, `BaseService`, `HttpClient`, `HttpClient$DefaultHolder`, and `Webhooks` in `resend-java-4.32.0.jar`) · **Follows up:** RV-061
**Where:**
- `email-utils/src/main/kotlin/com/pambrose/common/email/ResendService.kt:32`
- `email-utils/README.md:274-281,287-288`
- `email-utils/src/test/kotlin/com/pambrose/common/email/ResendServiceTests.kt:107`

`ResendService.kt:32` and `ResendServiceTests.kt:107` state that `Resend.emails()` builds a new OkHttp client on every call, and `README.md:287-288` states that "Each `ResendService` creates its own `Resend` client, and the HTTP client behind it, in its constructor and reuses them for every send."

In `resend-java 4.32.0`, `Resend(apiKey)` calls `HttpClient.getDefault()`, which returns a JVM-wide singleton (`HttpClient$DefaultHolder.INSTANCE` wrapping one shared `OkHttpClient`), and `Resend.emails()` passes that shared `httpClient` into `new Emails(apiKey, this.httpClient)`. Caching `private val emails` in the constructor still avoids per-send `Emails` allocations, so no code change is required, but the docs are stale. (Separately, `resend-java 4.32.0` now ships constant-time Svix verification via `Resend(apiKey).webhooks().verify(VerifyWebhookOptions)`, worth mentioning in `README.md:274-281`.)

**Fix:** Update `email-utils/README.md:287-288` and the comments in `ResendService.kt:32` / `ResendServiceTests.kt:107` to describe caching one `Emails` handle backed by the Resend SDK's shared default HTTP client, and optionally mention `Resend.webhooks().verify(...)` in the Security Considerations section.

---

### recaptcha-utils

<a id="r3-035"></a>
#### R3-035 — Concurrent `RecaptchaService.close()` races with `verifyRecaptcha` and propagates a spurious `CancellationException`
**Severity:** Medium · **Category:** concurrency / bug · **Verdict:** confirmed (code trace + `javap` of `HttpClient.close()` and `HttpClientEngine.executeWithinCallContext` in `ktor-client-core-jvm-3.6.2.jar`) · **Follows up:** RV-065
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:117-146,265-278`

`verifyRecaptcha()` fetches `val client = activeClient()` and executes `client.submitForm(...).body()` inside `runCatchingCancellable`. If another thread calls `RecaptchaService.close()` in the window after `activeClient()` returns `client` and before `submitForm` finishes (e.g., Ktor auto-reload or two embedded apps sharing a JVM, the exact scenario RV-065 addressed), `HttpClient.close()` completes `clientJob` and Ktor fails `submitForm` with a `CancellationException` (`JobCancellationException: Parent job is Completed`). Because `runCatchingCancellable` unconditionally rethrows every `CancellationException` without checking `currentCoroutineContext().isActive`, that client-closure cancellation escapes `verifyRecaptcha` and aborts an active, uncancelled Ktor route handler instead of failing closed (`false` → HTTP 400) or retrying with a fresh client.

**Fix:** In `verifyRecaptcha`, check `currentCoroutineContext().ensureActive()` on `CancellationException`; when the caller is still active and `!client.isActive`, either retry once with a fresh `activeClient()` or log and return `false`.

<a id="r3-036"></a>
#### R3-036 — `RecaptchaService` eagerly starts a live `HttpClient(CIO)` in `<clinit>` even when reCAPTCHA is disabled
**Severity:** Low · **Category:** leak / performance · **Verdict:** confirmed (`javap` of `CIOEngine.<init>` and `ActorSelectorManager` in `ktor-client-cio-jvm-3.6.2.jar` and `ktor-network-jvm-3.6.2.jar`) · **Follows up:** RV-065
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:61-62,265-278`

`internal var httpClient = clientFactory()` runs eagerly in `object RecaptchaService`'s static initializer (`<clinit>`). In Ktor 3.6.2, `HttpClient(CIO)` immediately constructs `CIOEngine` → `ActorSelectorManager`, launching a coroutine that calls `SelectorProvider.provider().openSelector()`, allocating an OS `Selector` (file descriptors) until `close()` is called. Any application that touches `RecaptchaService` with `isRecaptchaEnabled = false` (such as rendering forms in dev/test) starts an unused CIO engine that leaks unless `close()` is wired up. Since `activeClient()` already constructs clients on demand under `clientLock`, eager initialization is unnecessary.

**Fix:** Change `internal var httpClient: HttpClient? = null`, construct lazily in `activeClient()` (tracking a `closed` flag so the `"Creating a new reCAPTCHA HttpClient after RecaptchaService.close()"` log still fires only on recreation), and make `close()` a no-op when `httpClient == null`.

<a id="r3-037"></a>
#### R3-037 — `recaptcha-utils/README.md` lists `internal data class RecaptchaResponse` as public API
**Severity:** Low · **Category:** docs · **Verdict:** confirmed (verified against `RecaptchaService.kt:103` and `recaptcha-utils/api/recaptcha-utils.api`)
**Where:** `recaptcha-utils/README.md:18,172-176`; `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:103`

`RecaptchaResponse` is `internal` and absent from `recaptcha-utils.api`, yet the README lists it under both `## Features` and `## API Reference`. Consumers cannot import or reference it.

**Fix:** Remove `RecaptchaResponse` from `recaptcha-utils/README.md` (lines 18 and 172–176).

---

### redis-utils

<a id="r3-038"></a>
#### R3-038 — `withSuspendingRedis` / `withSuspendingNonNullRedis` leak `RedisClient` if cancelled while connecting
**Severity:** Medium · **Category:** leak / concurrency · **Verdict:** confirmed (code trace + `javap` of `StandaloneClientBuilder.createDefaultConnectionProvider` and `PooledConnectionProvider` in `jedis-8.1.0.jar`) · **Follows up:** RV-066
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:410-438,448-451`

`withSuspendingRedis` and `withSuspendingNonNullRedis` acquire a client via `val client = connectOrNullOnIo(redisUrl, printStackTrace)` (`withContext(ioDispatcher) { connectOrNull(...) }`) and only enter `client.useOnIo { ... }` on the next line. Inside `connectOrNull`, `createRedisClient` builds a `RedisClient` backed by `PooledConnectionProvider` (commons-pool2 `GenericObjectPool`), and `client.ping()` performs blocking, non-interruptible socket I/O on `Dispatchers.IO`.

If the caller's coroutine is cancelled while `connectOrNull` is running, `connectOrNull` finishes and returns the open `RedisClient` to `withContext(ioDispatcher)`. Because the coroutine's `Job` is cancelled, `withContext` discards the return value and throws `CancellationException`; `client.useOnIo` is never reached, and `client.close()` is never called — leaking the `ConnectionPool` and open TCP socket. (`useOnIo` at lines 454–463 guards cancellation during `block` with `NonCancellable`, but `connectOrNullOnIo` has no matching guard.)

**Fix:** Manage the acquired `RedisClient` in a single helper rather than returning a live `Closeable` across a cancellable `withContext` boundary: assign `var client: RedisClient? = null` inside `try`, run `connectOrNull` on `ioDispatcher`, invoke `block`, and close `client` in `finally` under `withContext(NonCancellable + ioDispatcher)` (preserving suppressed exceptions as in `useOnIo`). Add a test cancelling the coroutine while `ping()` is in flight and verifying `client.close()` is called once.

<a id="r3-039"></a>
#### R3-039 — `RedisUtils.urlDetails` does not validate URL scheme or port range, silently downgrading non-`rediss` TLS schemes to plaintext
**Severity:** Medium · **Category:** security / bug · **Verdict:** confirmed (`jshell` probe + `javap` of `JedisURIHelper.isValid` and `HostAndPort` in `jedis-8.1.0.jar`)
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:107-136,158-166`

- **Unvalidated scheme:** `urlDetails()` validates URI syntax and `!uri.host.isNullOrBlank()`, and `clientConfig()` checks `scheme.equals("rediss", ignoreCase = true)`, but `uri.scheme` is never checked. Unlike Jedis's own `JedisURIHelper.isValid(URI)`, `RedisUtils` accepts any scheme — including common TLS schemes like `tls://alice:secret@prod:6379` or `redis+tls://...`, or typos like `rediss2://...`. Because `isSslScheme` is `false`, `RedisUtils` silently connects in plaintext and transmits credentials unencrypted instead of throwing `IllegalArgumentException`.
- **Unvalidated port range / non-numeric port:** Verified in `jshell`: `URI("redis://localhost:99999")` succeeds with `port = 99999`, and `URI("redis://localhost:0")` succeeds with `port = 0`. `HostAndPort` does not validate ports, so `newRedisClient` succeeds and `withRedis` later swallows the resulting `JedisConnectionException` (wrapping `port out of range:99999`) as a connection failure instead of failing fast with `IllegalArgumentException`. A non-numeric port like `redis://localhost:abc` yields `uri.host == null` in `java.net.URI`, producing the misleading error `"Redis URL host could not be parsed; host names may contain only letters, digits, '-' and '.'"`.

**Fix:** In `urlDetails()`, require `JedisURIHelper.isRedisScheme(uri) || JedisURIHelper.isRedisSSLScheme(uri)` (without echoing the URL or credentials in the message) and `uri.port == -1 || uri.port in 1..65535`. Add tests for rejected schemes (`tls://...`, `http://...`) and invalid ports (`:0`, `:99999`).

<a id="r3-040"></a>
#### R3-040 — `RedisUtils.urlDetails` splits `uri.userInfo` after percent-decoding, corrupting usernames containing `%3A`
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (`jshell` probe + `javap` of `JedisURIHelper.getUser`/`getPassword` in `jedis-8.1.0.jar`)
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:125-126`

Line 125 parses credentials via `uri.userInfo?.split(":", limit = 2).orEmpty()`. `URI.getUserInfo()` percent-decodes before returning:
- `URI("redis://tenant%3Aalice:secret@localhost:6379").userInfo` returns `"tenant:alice:secret"`, yielding `user = "tenant"` and `password = "alice:secret"`.
- `URI("redis://tenant%3Aalice@localhost:6379").userInfo` returns `"tenant:alice"`, fabricating `password = "alice"` when no password was given.

By contrast, `JedisURIHelper` splits `uri.rawUserInfo` on `:` (`limit = 2`) first and then percent-decodes each component (preserving literal `+`).

**Fix:** Split `uri.rawUserInfo` on `":"` with `limit = 2` before percent-decoding each half (preserving literal `+`, matching `JedisURIHelper.decode`), and add tests for `%3A`, `%40`, and `+` in usernames and passwords.

<a id="r3-041"></a>
#### R3-041 — `redis-utils/README.md` lists `internal class RedisInfo` as public API
**Severity:** Low · **Category:** docs · **Verdict:** confirmed (verified against `RedisUtils.kt:92` and `redis-utils/api/redis-utils.api`)
**Where:** `redis-utils/README.md:126`; `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:92`

`RedisInfo` is `internal` and absent from `redis-utils.api`, yet `redis-utils/README.md:126` lists it in `## API Reference`.

**Fix:** Remove `RedisInfo` from `redis-utils/README.md:126`.

---

### exposed-utils

<a id="r3-042"></a>
#### R3-042 — `checkConflictIndexSupported` misses H2 in MySQL/MariaDB compatibility mode, and test comment is stale for Exposed 1.6.1
**Severity:** Low · **Category:** bug / tests · **Verdict:** confirmed (`javap -p -c` on `exposed-core-1.6.1.jar`: `UpsertBuilder$Companion.getFunctionProvider`, `UpsertStatement.prepareSQL`, and `MysqlFunctionProvider.upsert`) · **Follows up:** RV-070
**Where:**
- `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:78,89-98`
- `exposed-utils/src/test/kotlin/com/pambrose/common/exposed/UpsertStatementTests.kt:180-186`

`checkConflictIndexSupported` checks only `if (dialect is MysqlDialect)`. However, Exposed's `UpsertStatement.prepareSQL` calls `UpsertBuilder.Companion.getFunctionProvider(dialect)`, which in `exposed-core-1.6.1.jar` switches on `H2Dialect.h2Mode` and returns `MysqlFunctionProvider.INSTANCE` for both `H2CompatibilityMode.MySQL` and `H2CompatibilityMode.MariaDB`.

On H2 in `MODE=MySQL` or `MODE=MariaDB`, `checkConflictIndexSupported(currentDialect)` does not trigger, so `upsert(conflictIndex)` evaluates `body` before Exposed's `MysqlFunctionProvider.upsert` throws its own `UnsupportedByDialectException`. Meanwhile, `UpsertStatementTests.kt:180` claims `// H2 in MySQL mode keeps H2's own upsert, so the dialect check is exercised directly.`, which is no longer true for Exposed 1.6.1's upsert path.

**Fix:** Check `UpsertBuilder.getFunctionProvider(dialect) is MysqlFunctionProvider` in `checkConflictIndexSupported`, add an end-to-end test using H2 with `MODE=MySQL` verifying `upsert(conflictIndex)` fails fast before `body` executes, and update the comment at line 180.

---

### grpc-utils

<a id="r3-051"></a>
#### R3-051 — A Redis URL password with an unescaped `/` leaks through the database-index error
**Severity:** Low · **Category:** security · **Verdict:** confirmed (found while fixing R3-039) · **Follows up:** R3-039
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt` (`urlDetails()`, `clientConfig()`)

`java.net.URI` ends the authority at the first unescaped `/`, so in `redis://alice:123/ss@host` the rest of the password becomes the path. `clientConfig()` hands the path to `JedisURIHelper` as the database index, and its `NumberFormatException` quotes it: `For input string: "ss@host"`. R3-039 made the parser's own messages repeat nothing from the URL; this one comes from Jedis.

**Fix:** Validate the database index in `urlDetails()` (an empty path, `/`, or `/<digits>`), and throw `IllegalArgumentException` with a message that quotes nothing from the URL, noting that `/`, `?`, `#` and `@` in a password must be percent-encoded. Add tests that check every URL-taking entry point.

---

<a id="r3-043"></a>
#### R3-043 — `GrpcDsl.server(bindAddress = ...)` mishandles empty, blank, and whitespace-padded addresses
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed (code inspection + `jshell` verification of `java.net.InetSocketAddress`) · **Follows up:** RV-075
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:158-205`

`createNettyServer` checks only `if (bindAddress == null)` (`:194,197`) and passes any non-null string straight to `InetSocketAddress(bindAddress, port)` (`:200`) without trimming or rejecting blank values:
- `bindAddress = ""` (the empty-string convention used by `hostName`, `overrideAuthority`, `inProcessServerName`, and every `TlsUtils` path parameter in the same module): `new InetSocketAddress("", port)` calls `InetAddress.getByName("")`, which silently binds **loopback (`localhost/127.0.0.1`)** instead of rejecting the invalid address or listening on every interface, while logging `"Listening for gRPC traffic on :8080"`.
- `bindAddress = "   "` or `" 127.0.0.1 "` (padded, whereas `overrideAuthority` on line 100 and all `TlsUtils` paths trim): `InetSocketAddress` does not throw — it constructs an unresolved address (`isUnresolved() == true`), deferring failure until `server.start()` throws `java.nio.channels.UnresolvedAddressException` inside Netty.

**Fix:** Trim and validate `bindAddress` in `createNettyServer`:

```kotlin
val host = bindAddress?.trim()
if (host != null)
  require(host.isNotEmpty()) {
    "bindAddress must not be blank; omit it or pass null to listen on every interface"
  }
```

Use `host` in both the log message and `InetSocketAddress(host, port)`, and add tests in `GrpcDslTests` for `""`, `"   "`, and `" 127.0.0.1 "`.

<a id="r3-044"></a>
#### R3-044 — `TlsUtils.clientTlsContextBuilder` and `serverTlsContext` declare impossible `@Throws(SSLException::class)` and are omitted from `README.md`
**Severity:** Low · **Category:** API / docs · **Verdict:** confirmed (`javap -p` on `GrpcSslContexts` in `grpc-netty-1.86.0.jar` and `SslContextBuilder` in `netty-handler-4.2.17.Final.jar`)
**Where:**
- `grpc-utils/src/main/kotlin/com/pambrose/common/utils/TlsUtils.kt:122,125,195,198`
- `grpc-utils/README.md:222-231`

- Both `clientTlsContextBuilder` (`:125`) and `serverTlsContext` (`:198`) declare `@Throws(SSLException::class)` and KDoc `@throws SSLException`. None of the methods they call (`GrpcSslContexts.forClient()`, `forServer(File, File)`, `trustManager(File)`, `keyManager(File, File)`, `clientAuth(ClientAuth)`) declares or throws `SSLException`; invalid PEM/key material throws `IllegalArgumentException`, and `SSLException` is thrown only later by `SslContextBuilder.build()` (as `InvalidTlsMaterialTests.kt:35-38,145-172` already documents). Java callers are forced to handle a checked exception that can never be thrown.
- Separately, `grpc-utils/README.md:222-231` omits `clientTlsContextBuilder`, `serverTlsContext`, and `TlsContextBuilder` from the API Reference even though Usage Examples (`:178-179`) reference them.

**Fix:** Update the KDoc on both functions to document `IllegalArgumentException` (noting `SSLException` is thrown by `build()`), add the three missing entries to `grpc-utils/README.md`, and remove `@Throws(SSLException::class)` from those two builder factories in `6.0.0` (binary-compatible on the JVM, but Java source callers catching only `SSLException` would hit a compile error).

---

### jetty-utils

<a id="r3-052"></a>
#### R3-052 — `JettyDsl.servletContextHandler` leaves servlet exceptions on Jetty's default 500 page
**Severity:** Low · **Category:** security · **Verdict:** confirmed by source (found while fixing R3-015; `ServletChannelState.handleException` and the core `ErrorHandler` in Jetty 12.1.15) · **Follows up:** R3-015
**Where:** `jetty-utils/src/main/kotlin/com/pambrose/common/dsl/JettyDsl.kt`

`servletContextHandler` sets no error handler, so errors fall to the server's default `DynamicErrorHandler`. When a servlet throws, Jetty sets the error message to the exception's `toString()`, which that handler prints in the page's title, heading and message row, and it adds a "Powered by Jetty" footer unless `sendServerVersion` is off. service-utils' admin and metrics servers install their own status-only handler since R3-015; jetty-utils' DSL users get the default.

**Fix:** Install a handler that shows only the status code, its reason phrase and the request URI by default, as service-utils does, or document how to install one. Add tests for the 404 and 500 pages.

---

### Cross-module API surface

<a id="r3-045"></a>
#### R3-045 — Six empty public `Companion` objects remain in the published ABI
**Severity:** Low · **Category:** API · **Verdict:** confirmed (scanned all 19 `*/api/*.api` dumps and verified corresponding sources) · **Follows up:** RV-078
**Where:**
- `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/VerboseCountDownLatch.kt:70-72` (`guava-utils/api/guava-utils.api:122-123`)
- `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEngine.kt:67-72` (`script-utils-common/api/script-utils-common.api:10-11`)
- `service-utils/src/main/kotlin/com/pambrose/common/service/KtorServletService.kt:105-107` (`service-utils/api/service-utils.api:100-101`)
- `service-utils/src/main/kotlin/com/pambrose/common/service/MetricsService.kt:103-105` (`service-utils/api/service-utils.api:153-154`)
- `service-utils/src/main/kotlin/com/pambrose/common/service/ServletService.kt:86-88` (`service-utils/api/service-utils.api:169-170`)
- `service-utils/src/main/kotlin/com/pambrose/common/service/ZipkinReporterService.kt:108-115` (`service-utils/api/service-utils.api:202-203`)

RV-078 hid the logger-only `Companion` objects on `LambdaServlet`, `VersionServlet`, `SamplerGaugeCollector`, and `ResendService`, but missed these six classes. Each declares a public `companion object` whose members are all `private`, emitting six empty `public final class ...$Companion` classes into the published ABI.

**Fix:** In `6.0.0`, change all six to `private companion object` (or file-private `private val`s) and run `make abi-update` on macOS.

---

### Build & publishing

<a id="r3-046"></a>
#### R3-046 — Four modules still export `:core-utils` as `api` without exposing any `core-utils` types
**Severity:** Low · **Category:** build / deps · **Verdict:** confirmed (verified all `src/main` imports, same-package references, and ABI dumps) · **Follows up:** RV-079
**Where:**
- `zipkin-utils/build.gradle.kts:4`; `zipkin-utils/README.md:80`
- `redis-utils/build.gradle.kts:4`; `redis-utils/README.md:145`
- `guava-utils/build.gradle.kts:4`; `guava-utils/README.md:323`
- `grpc-utils/build.gradle.kts:4`; `grpc-utils/README.md:244`

RV-079 dropped `api(project(":core-utils"))` from five modules so standalone consumers would not inherit `core-utils`'s full `api` surface (`kotlinx-coroutines`, `kotlinx-datetime`, `kotlin-reflect`, `kotlin-logging`), and `AGENTS.md:15` warns against depending on `core-utils` for one helper. Four more modules still export `api(project(":core-utils"))` with zero `core-utils` types in their public ABI:
- **`zipkin-utils`:** Uses zero code from `core-utils` (its only file imports `brave.Tracing` alone).
- **`redis-utils`:** Uses zero `core-utils` code; it needs only `implementation(libs.kotlin.logging)` and `implementation(libs.kotlinx.coroutines)`.
- **`guava-utils`:** Uses zero `core-utils` code; it needs only `api(libs.kotlin.logging)` (for `KLogger` in `genericServiceListener`) and `implementation(libs.kotlinx.coroutines)`.
- **`grpc-utils`:** Uses only `isNull()` (1 call), `isNotNull()` (2 calls), `toDoubleQuoted()` (3 calls), `singleSetReference()` (3 private fields), and two private `KotlinLogging` loggers — none in its ABI.

**Fix:** In `6.0.0`, drop `core-utils` from `zipkin-utils`, `redis-utils`, and `guava-utils` (declaring `kotlin-logging` / `kotlinx-coroutines` directly), and either demote `grpc-utils`'s dependency to `implementation` or inline the four trivial helpers and add `implementation(libs.kotlin.logging)`. Update `AGENTS.md`, `llms.txt`, the four module READMEs, and `CHANGELOG.md`.

<a id="r3-047"></a>
#### R3-047 — `prometheus-utils` exports `prometheus-metrics-instrumentation-jvm` as `api` without exposing any of its types
**Severity:** Low · **Category:** build / API · **Verdict:** confirmed (`prometheus-utils/build.gradle.kts:7`, `prometheus-utils/api/prometheus-utils.api`) · **Follows up:** #208
**Where:** `prometheus-utils/build.gradle.kts:7`; `llms.txt:32`

`api(libs.prometheus.metrics.instrumentation.jvm)` was carried over from `api(libs.prometheus.hotspot)` during the Prometheus 1.x migration (#208). No type from `io.prometheus.metrics.instrumentation.jvm.*` appears in `prometheus-utils.api`; `Jvm*Metrics` and `ProcessMetrics` are constructed strictly inside `SystemMetrics.initialize(...)`.

**Fix:** In `6.0.0`, demote `api(libs.prometheus.metrics.instrumentation.jvm)` to `implementation(libs.prometheus.metrics.instrumentation.jvm)` (unless exporting `JvmMetrics` on consumers' compile classpath is an intentional convenience), and update `llms.txt:32`.

<a id="r3-048"></a>
#### R3-048 — `service-utils` declares `dropwizard-jmx` twice, and `llms.txt` omits it from `service-utils`'s `api` list
**Severity:** Low · **Category:** build / docs · **Verdict:** confirmed
**Where:** `service-utils/build.gradle.kts:12,19`; `gradle/libs.versions.toml:158-161`; `llms.txt:38`

`service-utils/build.gradle.kts:12` declares `api(libs.dropwizard.jmx)` (required because `AbstractGenericService.getJmxReporter(): JmxReporter` is public), and line 19 also declares `implementation(libs.bundles.dropwizard.service)`, which is `["dropwizard-servlets", "dropwizard-jmx"]` and is used nowhere else. Separately, `llms.txt:38` lists every other `api` dependency of `service-utils` but omits Dropwizard `metrics-jmx` (which `service-utils/README.md:8` does list).

**Fix:** Replace `implementation(libs.bundles.dropwizard.service)` with `implementation(libs.dropwizard.servlets)`, drop the single-entry `dropwizard-service` bundle from `gradle/libs.versions.toml`, and add Dropwizard `metrics-jmx` to `llms.txt:38`.

<a id="r3-049"></a>
#### R3-049 — `Makefile` publishing env setup does not guard against an empty GPG passphrase or failed secret export
**Severity:** Low · **Category:** publishing / build · **Verdict:** confirmed (verified `Makefile`, `build.gradle.kts`, and POSIX `/bin/sh` exit-status semantics)
**Where:** `Makefile:35-38,48-50,178,181,305-314`; `build.gradle.kts:594-605`

- Unlike `_check-maven-central-env` (`Makefile:319`, which fails on a missing *or empty* keychain entry), `_check-gpg-env` (`Makefile:312`) checks only `security`'s exit status, so an empty `gradle-signing-password` passes preflight.
- In `Makefile:35-38` and `48-50`, secrets are fetched via inline environment assignments (`ORG_GRADLE_PROJECT_...="$(...)" ./gradlew ...`). In POSIX `/bin/sh`, a failing command substitution in a prefix assignment does not abort the command — `./gradlew` still runs with that variable set to `""` (e.g., if `gpg --armor --export-secret-keys` fails on pinentry/agent or a Keychain prompt is denied). Line 38 also omits the double quotes around `$$(security ...)` used on lines 36–37 and 49–50.
- `build.gradle.kts:594` checks `providers.gradleProperty("signingInMemoryKey").isPresent`, which is `true` for `""`, bypassing the explanatory `require(hasSigningKey)` guard at line 600 (Gradle still fails later during signing, so an unsigned upload cannot happen, but the failure is less clear).

**Fix:** Check `[ -n "$$(security find-generic-password -a 'gpg-signing' -s 'gradle-signing-password' -w 2>/dev/null)" ]` in `_check-gpg-env` (unless passphrase-less keys are intentionally supported), quote `Makefile:38`, resolve and export the secret variables with `|| exit 1` before invoking `./gradlew`, and change `build.gradle.kts:594` to `providers.gradleProperty("signingInMemoryKey").orNull?.isNotBlank() == true`.

---

## Verified non-issues (do not re-report)

- **`ktor-client-utils`, `dropwizard-utils`, and `jetty-utils`** have no findings. `blockingGet`'s binary-compat `HIDDEN` shim and `expectSuccess`/`HttpTimeout` scoping, `MetricsDsl`/`MetricsUtils`, and `JettyDsl.server(port, host)` / `LambdaServlet` / `VersionServlet` (RV-014, RV-015, RV-041–RV-043) are all accurate and well-tested.
- **`core-utils` recent additions & fixes:** `runCatchingInterruptible` (`ExceptionUtilsJvm.kt` multifile wiring, interrupt semantics, tests), `toPath`/`join`/`pathOf` (RV-005), `Atomic`/`AtomicDelegates`/`criticalSection` (RV-004), `DateUtils` (RV-008 and `toCreated` removal), `ContentSource` scheme anchoring and timeouts (RV-003, RV-009), `PropertyFunctions`, `ReadResourcesTests` port 0 (RV-013), and `waitForPortAvailable` (RV-007) all hold.
- **`json-utils` parsing and accessors:** `kotlinx.serialization.json` 1.13.0 enforces a default `maxNestingDepth` of 400 inside `parseToJsonElement` before `requireJsonLiterals()` runs; `intValue`/`longValue`/`doubleValue` regexes, capped missing-key diagnostics, cached `toFormattedString` formatters, and `BugFixVerificationTests` bytecode calls (RV-018–RV-022) are all sound.
- **`service-utils` 5.x features:** The Prometheus Java client 1.x migration (`#208`), `MetricsConfig` hidden binary-compat constructors and `copy` overloads (`#211`, verified against `5.0.0..HEAD` ABI diff), `registersShutDownHook` and reduced INFO logging (`#239`), `KtorServletService` failed-start cleanup (RV-029), admin path normalization (RV-028, RV-030), double-init guards (RV-032), `private set` properties (RV-033), and `ZipkinReporterService` shutdown are all clean.
- **`prometheus-utils` 1.10.0 migration core:** `PrometheusDsl`, `SamplerGaugeCollector` (`getMetricFamilyDescriptor()`, `PrometheusNaming.validateMetricName`, `Labels.of`, duplicate-name merging), `TrackingRegistry`, and `InstrumentedThreadFactory` thread accounting and partial-registration rollback are correct.
- **`guava-utils` concurrency:** `GenericValueWaiter` / `BooleanWaiter` (RV-044), `ConditionalValue` / `ConditionalBoolean`, `GenericMonitor` / `BooleanMonitor` (RV-045), `GenericIdleService` / `GenericExecutionThreadService` `java.time.Duration` overloads, `ZipExtensions` `maxBytes` + GZIP trailer checks, and the README "Choosing a Waiting Primitive" table (RV-047) all hold.
- **`script-utils` 5.0.0 rework:** `KotlinScript` `ScriptVariables` holder on Kotlin 2.4.21 (RV-048 — lambdas, generic Java classes, `a_tmp` collisions, `"bindings"`, `"__variables"`, rebinding), `JavaScript` `evalScript` field cleanup and `LinkageError` wrapping (RV-050, RV-054, RV-055), `AbstractEngine` classloader fallback (RV-057), `AbstractEnginePool` cancellation / concurrent `close()` / `withInstance` suppression (RV-058), `ScriptGuards` / `PythonGuards` linear-time regexes, and `PythonScript` cross-thread use all hold.
- **`email-utils`, `recaptcha-utils`, `redis-utils`:** `isValidEmail` length caps and regex (RV-060), `ResendService` exception contract (RV-062) and `ResendWebhookMsg` (RV-063), `RecaptchaService` CIO timeouts and `HttpRequestTimeoutException` handling, and `redis-utils` Jedis 8.1.0 API / TLS `sslOptions` / pool validation (RV-067) / `scanKeys` (RV-068) / masked diagnostics (RV-069) are all clean.
- **`exposed-utils` and `grpc-utils`:** `upsert(conflictIndex)` validation for unique/functional/partial/composite indexes (RV-071), `readonlyTx` / `timed*` nesting docs (RV-072), `KotlinSqlLogger` lazy DEBUG logging (RV-073), Exposed 1.6.1 compatibility, `GrpcDsl` retry defaults (RV-074), `GrpcDsl.server` hidden 4-arg binary-compat overload (RV-075), TLS/mTLS (`GrpcSslContexts` OpenSSL/ALPN/HTTPS hostname verification, `PemReader` stream closing), and `netty-tcnative-boringssl-static` 2.0.86.Final classifiers (exact 5-platform match with its POM) all hold.
- **Build, BOM, CI, supply chain, and project docs:**
  - `common-utils-bom` dynamically constrains all 19 code modules plus the 3 KMP `-jvm` artifacts (`RV-084`).
  - `ktor-server-utils` POM `<optional>true</optional>` hook (`RV-080`), Detekt `**/*Tests.kt` excludes (`RV-085`), `gradlew`/`gradlew.bat` text attributes and `distributionSha256Sum` (`RV-086`), `~/.konan` caches with `restore-keys` (`RV-087`), local Maven publish in CI (`RV-088`), SHA-pinned actions with `timeout-minutes` (`RV-089`), and `zizmor --offline .` (0 findings) all hold.
  - `Makefile` `release-check`, `release`, and `release-tag` (`#242`) match all 5 live workflow/job names in `RELEASE_REQUIRED_JOBS` and pass dry-run checks against the 5.3.1 working tree.
  - All 6 `yarnResolutions` pins match both `kotlin-js-store/` lockfiles; `codecov.yml` matches `settings.gradle.kts`; `scripts/coverage-packages.py` matches 100% of main sources; every relative file link and `#anchor` across root docs, `llms.txt`, `docs/*.md`, and all 19 module READMEs resolves; and the uncommitted 5.3.1 release-prep diff is internally consistent across versions, dates, and `RELEASE_NOTES.md` compare links.
