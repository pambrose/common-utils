# Test Coverage Review — common-utils

|                     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Review date**     | 2026-09-16                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Project version** | 4.0.0                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| **Commit reviewed** | `142e5c0` (master)                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **Scope**           | All 19 modules' tests, the Kover configuration, `codecov.yml`, `scripts/coverage-packages.py`, and the CI test workflow                                                                                                                                                                                                                                                                                                                                                           |
| **Method**          | A fresh aggregated Kover report (`./gradlew koverXmlReport`) was broken down by module, package, class and source line. Five parallel reviewers, one per module group, then traced every missed line or branch to its cause and read the tests for weak assertions, missing edge cases, platform gaps and timing hazards. Where a finding depends on library behaviour, it was checked against the dependency sources in `~/.gradle/caches` (Kotlin/JS stdlib 2.4.20, ktor-http 3.5.2, Kover 0.9.9, Jedis, Exposed) or with `javap`. Every finding that claims a bug was re-read in the source before being written up. |
| **Related**         | [`CODE_REVIEW_2026-09-14.md`](CODE_REVIEW_2026-09-14.md) (all 139 items closed). This review doesn't repeat any of them.                                                                                                                                                                                                                                                                                                                                                          |

## How to track progress

- Every action item has a stable ID (`TC-001` … `TC-083`). **Never renumber.** If an item is dropped, close it as won't-fix instead of deleting it.
- **Status lives only in the [Tracker](#tracker) checklist.** It is the single source of truth.
  - `- [ ]` open
  - `- [x]` done: append `— fixed in #NNN` (PR) or a commit SHA
  - `- [x]` won't fix: append `— won't fix: <reason>`
  - `- [ ]` in progress: append `— in progress (#NNN)` and leave the box unchecked
- The [Details](#details) section is reference material: the gap, the evidence and the test to add. It carries no status.
- Categories:
  - `bug`: a defect that the missing test would expose. Land the fix and the test together.
  - `gap`: behaviour with no test.
  - `weak`: code that runs under test but whose result is not really checked.
  - `platform`: common code that is tested only on the JVM, or a target that is never run.
  - `flaky`: a timing or environment hazard.
  - `safety`: a test that could damage the test run if the code under test regressed.
  - `infra`: coverage tooling.
  - `perf`: test run time.
- Quick progress counts:

  ```bash
  grep -c '^- \[x\] \*\*TC-' docs/TEST_COVERAGE_REVIEW_2026-09-16.md   # closed
  grep -c '^- \[ \] \*\*TC-' docs/TEST_COVERAGE_REVIEW_2026-09-16.md   # open
  grep    '^- \[ \] \*\*TC-.*`HIGH`' docs/TEST_COVERAGE_REVIEW_2026-09-16.md   # open high-severity
  ```

## Summary

Coverage by the numbers is already high: **98.3% line** (40 of 2,376 lines missed) and **89.1% branch** (114 of 1,042 branches missed), against floors of 90% and 80%. Chasing the remaining percentage points isn't worth much. About half of the missed branches are compiler-generated:
- about 30 can't be reached at all ([Appendix A](#appendix-a--kover-misses-no-test-can-reach));
- 27 are serializer code in `webhook` ([TC-030](#tc-030)).

The value of this review lies in what the percentages hide.

**Severity counts:** 12 high · 39 medium · 32 low (83 items).

Main findings:

1. **Eight latent bugs, each with no test that would expose it.** In each case the code runs under test, but never with the input that breaks it. None of them repeats a September code-review item, but two came in with those fixes: the TC-010 timeout conversion arrived with the CR-013 fix (#176), and `accessibleClass` (TC-073) arrived in #182.
   - [TC-008](#tc-008) `singleToDoubleQuoted` corrupts input that has surrounding whitespace.
   - [TC-010](#tc-010) `UrlSource` throws for `Duration.INFINITE` and for any timeout of about 25 days or more.
   - [TC-022](#tc-022) `getByPath` throws instead of returning `null` when the path passes through a non-object.
   - [TC-038](#tc-038) The servlet bridge returns a 500 when a servlet sets a malformed content type.
   - [TC-067](#tc-067) A Redis URL without a port connects to port -1, and malformed URLs escape `withRedis`'s null path.
   - [TC-073](#tc-073) The script engines generate `Object<Integer>` / `kotlin.Any<kotlin.Int>` for arrays.
   - [TC-074](#tc-074) `JavaScript.renderType` generates `?[]` for `Array<*>`.
   - [TC-076](#tc-076) An engine pool loses an instance when `reset()` throws, so a size-1 pool then hangs forever.
2. **Code that runs under test but whose results aren't checked.** The high items:
   - [TC-066](#tc-066) The real `withRedis` connect-and-close path never runs.
   - [TC-069](#tc-069) No test proves `upsert` uses the conflict index it is given.
   - [TC-051](#tc-051) No gRPC test ever completes a TLS handshake.
   - [TC-042](#tc-042) The health check is never shown to detect a sub-service that dies.
   - [TC-034](#tc-034) Malformed reCAPTCHA responses are never tested.
3. **Common code is tested only on the JVM, and some of it behaves differently on other platforms.** Several `commonMain` functions have specs only in `jvmTest`, including the core-utils utilities ([TC-009](#tc-009)), json-utils' integration suite ([TC-025](#tc-025)) and ktor-client-utils' client-creating paths ([TC-027](#tc-027)). Differences confirmed in the Kotlin/JS 2.4.20 stdlib sources:
   - JS prints `1.0` as `"1"`.
   - JS parses numbers with unary `+`, so `"0x10"` is a number and `"4f"` is not.
   - JS `substring` swaps or clamps out-of-range indices instead of throwing.

   Separately, the Apple and Windows native tests never run in CI ([TC-002](#tc-002)).
4. **Tests that would kill the Gradle worker if a guard regressed** ([TC-075](#tc-075)). Several script-guard tests call `System.exit` / `halt` for real, relying on the guard to stop them.
5. **A single aggregate floor.** Any module smaller than about 200 lines, which is all but four of them, could lose every covered line and the 90% line floor would still pass ([TC-001](#tc-001)).
6. **Timing hazards.** A port-allocation race runs under `org.gradle.parallel=true` ([TC-048](#tc-048)), and several tests have wall-clock upper bounds or wait on "let the waiter register" delays ([TC-021](#tc-021), [TC-059](#tc-059), [TC-081](#tc-081)).

### Coverage snapshot

The report is JVM-only: Kover does not instrument JS, wasm or native. Per-module figures were computed by mapping each `sourcefile` in `build/reports/kover/report.xml` to its module. Coverage is aggregated across modules, so script-utils-common is credited with the coverage it gets from the three engine modules' tests.

| Module              | Lines | Line % | Lines missed | Branches | Branch % | Branches missed |
|---------------------|------:|-------:|-------------:|---------:|---------:|----------------:|
| script-utils-java   |    46 |   93.5 |            3 |       28 |     60.7 |              11 |
| email-utils         |   125 |   99.2 |            1 |      108 |     75.0 |              27 |
| exposed-utils       |    49 |  100.0 |            0 |       24 |     79.2 |               5 |
| script-utils-common |   142 |   97.2 |            4 |       76 |     85.5 |              11 |
| ktor-server-utils   |   287 |  100.0 |            0 |       74 |     86.5 |              10 |
| recaptcha-utils     |    76 |   98.7 |            1 |       60 |     86.7 |               8 |
| json-utils          |   110 |   98.2 |            2 |      102 |     87.3 |              13 |
| guava-utils         |   252 |   96.4 |            9 |       86 |     91.9 |               7 |
| core-utils          |   531 |   98.9 |            6 |      230 |     93.5 |              15 |
| prometheus-utils    |    72 |   98.6 |            1 |       16 |     93.8 |               1 |
| service-utils       |   305 |   97.7 |            7 |       58 |     94.8 |               3 |
| redis-utils         |   114 |   97.4 |            3 |       80 |     97.5 |               2 |
| grpc-utils          |   140 |  100.0 |            0 |       80 |     98.8 |               1 |
| script-utils-kotlin |    31 |   96.8 |            1 |        6 |    100.0 |               0 |
| script-utils-python |    38 |   94.7 |            2 |        8 |    100.0 |               0 |
| ktor-client-utils   |    25 |  100.0 |            0 |        4 |    100.0 |               0 |
| dropwizard-utils    |    10 |  100.0 |            0 |        2 |    100.0 |               0 |
| jetty-utils         |    17 |  100.0 |            0 |        0 |        — |               0 |
| zipkin-utils        |     3 |  100.0 |            0 |        0 |        — |               0 |

Test counts per target for the KMP modules show how much less the non-JVM targets run. The figures come from the local `build/test-results` on macOS. CI runs `linuxX64Test` in place of the Apple targets.

| Module            | jvmTest | jsNodeTest | wasmJsNodeTest | macosArm64Test | iosSimulatorArm64Test |
|-------------------|--------:|-----------:|---------------:|---------------:|----------------------:|
| core-utils        |     275 |         86 |             85 |             85 |                    85 |
| json-utils        |      73 |         66 |             65 |             65 |                    65 |
| ktor-client-utils |      10 |          4 |              3 |              3 |                     3 |

The slowest JVM suites are script-utils-kotlin (63 s, about half of it in three loop tests, see [TC-082](#tc-082)), service-utils (21 s), redis-utils (18 s) and script-utils-python (16 s).

## Suggested PR batches

| Batch                                              | Items                                                            |
|----------------------------------------------------|------------------------------------------------------------------|
| 1. Latent bugs (fix + regression test together)    | TC-008, TC-010, TC-022, TC-038, TC-067, TC-073, TC-074, TC-076   |
| 2. Test safety and flakiness                       | TC-075, TC-048, TC-049, TC-059, TC-081, TC-021                   |
| 3. Transport and security paths                    | TC-051 – TC-054, TC-034 – TC-037                                 |
| 4. service-utils lifecycle + servlet bridge        | TC-039 – TC-047, TC-050                                          |
| 5. KMP platform parity + CI                        | TC-002, TC-009, TC-024 – TC-029                                  |
| 6. redis / exposed                                 | TC-066 – TC-072                                                  |
| 7. guava / prometheus / dropwizard concurrency     | TC-055 – TC-065                                                  |
| 8. script-utils test harness + gaps                | TC-077 – TC-083                                                  |
| 9. Remaining core-utils / json / email items       | TC-011 – TC-020, TC-023, TC-030 – TC-033                         |
| 10. Coverage tooling                               | TC-001, TC-003 – TC-007                                          |

---

## Tracker

### Cross-cutting (tooling and CI)
- [x] **TC-001** `MEDIUM` · infra — Coverage floor is a single aggregate; most modules could drop to 0% without tripping it ([details](#tc-001)) — fixed in #187
- [x] **TC-002** `MEDIUM` · platform — Apple and Windows native tests never run in CI ([details](#tc-002)) — fixed in #187
- [x] **TC-003** `LOW` · infra — `make coverage-packages` prints instruction coverage, not the line/branch figures the floors use ([details](#tc-003)) — fixed in #187
- [x] **TC-004** `LOW` · infra — Codecov patch target (70%) is far below the project's level ([details](#tc-004)) — fixed in #187
- [x] **TC-005** `LOW` · infra — No mutation testing to catch "runs but isn't checked" tests ([details](#tc-005)) — fixed in #187
- [x] **TC-006** `LOW` · infra — Java-facing API (`@JvmStatic` bridges, `@JvmName` facades) is never exercised or ABI-checked ([details](#tc-006)) — fixed in #187
- [x] **TC-007** `LOW` · infra — Simplify unreachable branches instead of trying to test them ([details](#tc-007)) — fixed in #187 (the `RedisUtils` catch is left for TC-067)

### core-utils
- [ ] **TC-008** `HIGH` · bug — `singleToDoubleQuoted` corrupts input that has surrounding whitespace ([details](#tc-008))
- [ ] **TC-009** `HIGH` · platform — `commonMain` utilities tested only on the JVM; confirmed JS differences ([details](#tc-009))
- [ ] **TC-010** `MEDIUM` · bug — `UrlSource` throws for `Duration.INFINITE` or ≥ ~25-day timeouts ([details](#tc-010))
- [ ] **TC-011** `MEDIUM` · weak — `Short.MAX_VALUE` regression test never uses `MAX_VALUE` ([details](#tc-011))
- [ ] **TC-012** `MEDIUM` · weak — `Atomic` concurrency tests can't detect a missing `Mutex` ([details](#tc-012))
- [ ] **TC-013** `MEDIUM` · gap — `DateUtils`: `localDateNow`/`localDateTimeNow`/`age(tz)` untested; expectations copy the implementation ([details](#tc-013))
- [ ] **TC-014** `MEDIUM` · gap — `toObjectSecure`: exact-name blocklist and JVM-filter merge never hit; messages unchecked ([details](#tc-014))
- [ ] **TC-015** `LOW` · gap — `linesBetween` reversed-boundary guard has no test ([details](#tc-015))
- [ ] **TC-016** `LOW` · gap — `getBanner` missing resource / interior blank lines; null context classloader fallback ([details](#tc-016))
- [ ] **TC-017** `LOW` · weak — `Version.plainStr` never called; `Version` assertions only check presence ([details](#tc-017))
- [ ] **TC-018** `LOW` · gap — `criticalSection` flag state and single-assignment thread safety untested ([details](#tc-018))
- [ ] **TC-019** `LOW` · gap — No DST gap/overlap tests for zone-offset formatting ([details](#tc-019))
- [ ] **TC-020** `LOW` · gap — Edge cases: negative years, surrogate pairs, non-ASCII capitalization, checksum boundary, property override order, port-wait retry ([details](#tc-020))
- [ ] **TC-021** `LOW` · flaky/weak — Wall-clock upper bounds, a real `getLocalHost()` call, and no-op assertions ([details](#tc-021))

### json-utils
- [ ] **TC-022** `HIGH` · bug — `getByPath` throws instead of returning `null` when the path crosses a non-object ([details](#tc-022))
- [ ] **TC-023** `MEDIUM` · weak — `jsonObjectValueOrNull` / `jsonElementListOrNull` are only ever tested for `null` ([details](#tc-023))
- [ ] **TC-024** `MEDIUM` · platform — Number parsing (`doubleValue*`, `isNumber`, `toMap`) differs across platforms and is untested there ([details](#tc-024))
- [ ] **TC-025** `MEDIUM` · platform — `JsonIntegrationTest` is JVM-only just for `System.currentTimeMillis`; has a wall-clock assertion ([details](#tc-025))
- [ ] **TC-026** `LOW` · gap — Wrong-type receivers, `forEachJsonObject` skipping, `deepCopy` identity, Long overflow ([details](#tc-026))

### ktor-client-utils
- [ ] **TC-027** `MEDIUM` · platform — Client-creating paths tested only on the JVM; native has no engine ([details](#tc-027))
- [ ] **TC-028** `MEDIUM` · gap — Default `expectSuccess=false`, `expectSuccess` with a provided client, and `HttpTimeout` untested ([details](#tc-028))
- [ ] **TC-029** `LOW` · weak — Common tests never check the request method/URL; no-op assertion ([details](#tc-029))

### email-utils
- [ ] **TC-030** `MEDIUM` · gap — Webhook branch misses are generated code, but `decode`'s error contract and new serial names are untested ([details](#tc-030))
- [ ] **TC-031** `MEDIUM` · gap — The documented IPv4-literal branch of `isValidEmail` is never tested ([details](#tc-031))
- [ ] **TC-032** `LOW` · gap — `Email`'s serialized form is never tested ([details](#tc-032))
- [ ] **TC-033** `LOW` · weak — `shouldContain "2"` log assertion always passes ([details](#tc-033))

### recaptcha-utils
- [ ] **TC-034** `HIGH` · gap — Malformed or non-2xx siteverify responses (fail-closed path) never tested ([details](#tc-034))
- [ ] **TC-035** `MEDIUM` · weak — Tests copy the production `Json` config instead of using it ([details](#tc-035))
- [ ] **TC-036** `MEDIUM` · weak — Widget test never checks the site key (or that the secret is absent) ([details](#tc-036))
- [ ] **TC-037** `LOW` · gap — Empty `remoteip`, use after `close()`, config re-reads; specs that test only themselves ([details](#tc-037))

### ktor-server-utils
- [ ] **TC-038** `HIGH` · bug — Malformed servlet content type turns a successful response into a 500; throwing servlet untested ([details](#tc-038)) — in progress
- [ ] **TC-039** `LOW` · gap — `sendRedirect(clearBuffer = false)` and the buffer discard are unverified ([details](#tc-039)) — in progress
- [ ] **TC-040** `LOW` · gap — `HerokuHttpsRedirect` `sslPort` and non-matching excludes untested ([details](#tc-040)) — in progress
- [ ] **TC-041** `LOW` · gap — Multi-valued headers and `Content-Type` set via `setHeader` untested end to end ([details](#tc-041)) — in progress (fixed rather than pinned: a `Content-Type` header now sets the content type and charset, and the bridge no longer duplicates `Content-Length`)

### service-utils
- [ ] **TC-042** `HIGH` · gap — Health check never shown to detect a sub-service that stopped while running ([details](#tc-042))
- [ ] **TC-043** `MEDIUM` · gap — Port-in-use rollback tested only for the Jetty variant ([details](#tc-043))
- [ ] **TC-044** `MEDIUM` · gap — `MetricsService` / `ServletService` / `KtorServletService` never constructed directly ([details](#tc-044))
- [ ] **TC-045** `MEDIUM` · gap — Admin-disabled Ktor service never started; `servletInit` / `initKtor` hooks never passed ([details](#tc-045))
- [ ] **TC-046** `MEDIUM` · weak — Metrics export flags always `false`; JMX reporter never asserted ([details](#tc-046))
- [ ] **TC-047** `MEDIUM` · gap — Shutdown hook never run; `close()` edge cases; ports never shown released ([details](#tc-047))
- [ ] **TC-048** `MEDIUM` · flaky — `freePort()` race under parallel builds; `freePort() to freePort()` can collide ([details](#tc-048))
- [ ] **TC-049** `LOW` · flaky — Host-binding tests silently skip without a non-loopback address ([details](#tc-049))

### jetty-utils
- [ ] **TC-050** `LOW` · weak — `LambdaServlet` error handling checked only with mocks; no-op assertions ([details](#tc-050))

### grpc-utils
- [ ] **TC-051** `HIGH` · gap — No test completes a TLS or mutual-TLS handshake ([details](#tc-051))
- [ ] **TC-052** `MEDIUM` · gap — TLS files that exist but are invalid are never tested ([details](#tc-052))
- [ ] **TC-053** `MEDIUM` · weak — Builder selection, streaming observer and graceful shutdown checked only shallowly ([details](#tc-053))
- [ ] **TC-054** `LOW` · weak — Misnamed "mutual auth" test; stream-observer test with no assertion ([details](#tc-054))

### guava-utils
- [ ] **TC-055** `MEDIUM` · gap — `GenericValueWaiter`: non-satisfying update, timeout/set race, cancel with finite timeout ([details](#tc-055))
- [ ] **TC-056** `MEDIUM` · weak — `BooleanMonitor` log helpers: levels unchecked, `@JvmStatic` bridges uncovered ([details](#tc-056)) — `@JvmStatic` bridge half fixed in #187 (with TC-006); log-level assertions still open
- [ ] **TC-057** `MEDIUM` · gap — `GenericMonitor` interruption and timed-guard-failure paths untested ([details](#tc-057))
- [ ] **TC-058** `MEDIUM` · gap — Service failure paths and `GuavaDsl.serviceManager` untested in this module ([details](#tc-058))
- [ ] **TC-059** `MEDIUM` · flaky — Wall-clock bounds, unsynchronized flags after short joins, delay-based waiter registration ([details](#tc-059))
- [ ] **TC-060** `LOW` · gap — `ZipExtensions` near-miss magic, negative `maxBytes`, bad CRC, truncated body ([details](#tc-060))
- [ ] **TC-061** `LOW` · gap — `thread(latch)` defaults / throwing block; interrupted `withLock` and latch await ([details](#tc-061))
- [ ] **TC-062** `LOW` · weak — `GuavaFuncsTests` cannot fail ([details](#tc-062))

### prometheus-utils
- [ ] **TC-063** `MEDIUM` · gap — `SystemMetrics` retry path untested; repeat-call test cannot fail ([details](#tc-063))
- [ ] **TC-064** `LOW` · gap — Duplicate registration, throwing runnables, partial registration in `InstrumentedThreadFactory` ([details](#tc-064))

### dropwizard-utils
- [ ] **TC-065** `LOW` · weak — Map health check never shown to be live; error message unchecked ([details](#tc-065))

### redis-utils
- [ ] **TC-066** `HIGH` · gap — Real `withRedis` connect/ping/close path never runs; URL and `close()` never verified ([details](#tc-066))
- [ ] **TC-067** `MEDIUM` · bug — URL without a port uses port -1; malformed URLs escape the null path ([details](#tc-067))
- [ ] **TC-068** `LOW` · weak — `shouldNotBe null` on non-null clients; `printStackTrace` effect unverified ([details](#tc-068))

### exposed-utils
- [ ] **TC-069** `HIGH` · weak — No test proves `upsert` uses the given conflict index ([details](#tc-069))
- [ ] **TC-070** `MEDIUM` · weak — Transaction helpers' read-only, isolation and rollback behaviour unverified ([details](#tc-070))
- [ ] **TC-071** `LOW` · gap — `toRowString` empty-value filter; `CustomExpr` never run against H2 ([details](#tc-071))
- [ ] **TC-072** `LOW` · weak — Null-database tests are weak and order-dependent; stale comment ([details](#tc-072))

### script-utils (common / java / kotlin / python)
- [ ] **TC-073** `HIGH` · bug — `accessibleClass` falls back to `Any` but keeps the type arguments; arrays fail to compile ([details](#tc-073))
- [ ] **TC-074** `HIGH` · bug — `JavaScript.renderType` emits `?[]` for `Array<*>`; array/star/type-parameter branches untested ([details](#tc-074))
- [ ] **TC-075** `HIGH` · safety — Guard tests either pass without a guard or would kill the test JVM if it regressed ([details](#tc-075))
- [ ] **TC-076** `MEDIUM` · bug — `withInstance` loses the instance when `reset()` throws ([details](#tc-076))
- [ ] **TC-077** `MEDIUM` · infra — script-utils-common has no direct tests; add a fake `ScriptEngine` ([details](#tc-077))
- [ ] **TC-078** `MEDIUM` · gap — Pool concurrency cap, close with a waiting borrower, and failed cleanup untested ([details](#tc-078))
- [ ] **TC-079** `MEDIUM` · weak — Null-result error message untested; type-mismatch messages checked loosely; Python non-Boolean path untested ([details](#tc-079))
- [ ] **TC-080** `MEDIUM` · gap — Binding retry / re-add, `nullGlobalContext` after recycle, Java cross-engine parity ([details](#tc-080))
- [ ] **TC-081** `MEDIUM` · flaky — Pool cancellation test can pass without exercising cancellation ([details](#tc-081))
- [ ] **TC-082** `MEDIUM` · perf — ~31 s of loop tests (600 REPL compiles) that never check a result ([details](#tc-082))
- [ ] **TC-083** `LOW` · gap — `verbose = true`, evaluator `close()`, `ScriptUtils` context, REPL history reset ([details](#tc-083))

---

## Details

### Cross-cutting (tooling and CI)

<a id="tc-001"></a>
#### TC-001 — Coverage floor is a single aggregate
**Severity:** Medium · **Category:** infra
**Where:** `build.gradle.kts:102-119`

`koverVerify` checks one application-wide figure. 2,336 of 2,376 lines are covered, and the 90% floor needs 2,139, so up to 197 covered lines could disappear without failing the build. Only core-utils, service-utils, ktor-server-utils and guava-utils have more covered lines than that; any of the other fifteen modules could lose all its coverage and CI would stay green. Branches have similar slack: 94 covered branches could go, roughly all of json-utils'.

**Fix:** Add a second rule grouped by package. Kover 0.9.9 supports this (`GroupingEntityType.PACKAGE`, checked in the plugin sources). The weakest package line figure today is 96.0% (`concurrent`), so a 90% floor passes now:

```kotlin
rule("per-package line floor") {
    groupBy = GroupingEntityType.PACKAGE
    bound {
        minValue = 90
        coverageUnits = CoverageUnit.LINE
        aggregationForGroup = AggregationType.COVERED_PERCENTAGE
    }
}
```

Don't add a per-package *branch* rule. `response` has only 4 branches, 2 of them unreachable ([Appendix A](#appendix-a--kover-misses-no-test-can-reach)), so it sits at 50% and would block the build for no reason. Packages also span modules (`com.pambrose.common.dsl` lives in six), so this is a partial safeguard and doesn't replace per-module thresholds.

<a id="tc-002"></a>
#### TC-002 — Apple and Windows native tests never run in CI
**Severity:** Medium · **Category:** platform
**Where:** `.github/workflows/test.yml:19-20`, `build.gradle.kts:277-289, 316-322`

The only job runs on `ubuntu-latest`, so `macosArm64Test`, `iosSimulatorArm64Test` and `iosX64Test` run only on a developer's Mac, and `mingwX64Test` runs nowhere. The local results confirm the Apple suites run on this machine, but a contributor on Linux, or CI, never sees them. Kotlin/Native behaviour differs across the Apple, Linux and mingw targets in exactly the areas these modules touch: time zones, number formatting, and client engines (see [TC-027](#tc-027)).

**Fix:** Add a `macos-latest` job that runs `allTests` for `:core-utils`, `:json-utils` and `:ktor-client-utils`. Consider a `windows-latest` job for `mingwX64Test`, or document that the mingw target ships without being tested. macOS runners cost more minutes, so the job can be limited to pushes to `master` if that matters.

<a id="tc-003"></a>
#### TC-003 — `make coverage-packages` prints instruction coverage
**Severity:** Low · **Category:** infra
**Where:** `scripts/coverage-packages.py:17-21`, `CLAUDE.md` (Coverage section), `build.gradle.kts:97-101`

CLAUDE.md describes `make coverage-packages` as "the per-package table that shows where branch coverage is actually weak". The script only reads the `INSTRUCTION` counter, but the verification floors, and the figures quoted in the build comment, are LINE and BRANCH. The package this review found weakest (`webhook`, 74.5% branch) looks healthy in instruction terms.

**Fix:** Print line % and branch % columns (plus missed counts) and sort by branch %. Optionally add a `--by-module` mode that maps each `sourcefile` to its module, as was done for the snapshot table above.

<a id="tc-004"></a>
#### TC-004 — Codecov patch target is 70%
**Severity:** Low · **Category:** infra
**Where:** `codecov.yml:8-11`

The project sits at 98% line coverage, yet a PR can add code that is 30% untested and still pass the patch check. With `threshold: 1%` on the project status, a PR can also drop about 24 covered lines unnoticed.

**Fix:** Raise the patch target to about 90%. Consider Codecov components or flags per module so the PR comment shows which module lost coverage.

<a id="tc-005"></a>
#### TC-005 — No mutation testing
**Severity:** Low · **Category:** infra

About a third of the findings below are `weak`: the code runs under test, but a wrong result would still pass. Line and branch coverage can't detect that, but mutation testing can.

**Fix:** Try PIT on one or two JVM modules first (guava-utils and exposed-utils have the most `weak` findings), using the `info.solidsoft.pitest` Gradle plugin (1.19.0) with `io.kotest:kotest-extensions-pitest:6.2.5`, which matches the catalog's Kotest version (both checked on Maven Central / the Gradle plugin portal). Run it on demand (e.g. a `make mutation` target), not in `check`. The KMP modules need extra wiring, because the plugin expects a `java` source set.

<a id="tc-006"></a>
#### TC-006 — Java-facing API is never exercised or ABI-checked
**Severity:** Low · **Category:** infra
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/BooleanMonitor.kt:68-164`, `email-utils/src/main/kotlin/com/pambrose/common/email/Email.kt:30-33`, the `@file:JvmName` / `@file:JvmMultifileClass` files in `core-utils/src/commonMain` and `core-utils/src/jvmMain`

CLAUDE.md says the split common/JVM files use `@JvmName` + `@JvmMultifileClass` "so the compiled JVM facade classes (and therefore the published JVM ABI) are unchanged", but nothing checks that. Missed lines Kover reports that only Java can reach:
- `BooleanMonitor`'s eight `@JvmStatic` bridges (the class's 8 missed lines);
- `Email.getValue()`, the boxed getter.

No module has a `src/test/java` directory.

**Fix:** Enable ABI validation. KGP 2.4.20, which the build already uses, ships `AbiValidationExtension` (experimental). The kotlinx `binary-compatibility-validator` plugin is the established alternative. Commit the dumps, so a facade rename or a lost `@JvmStatic` fails `check`. For the bridges themselves, use the reflection pattern already in `dropwizard-utils/src/test/kotlin/com/pambrose/common/dsl/MetricsDslTests.kt:59-61` (see [TC-056](#tc-056)).

<a id="tc-007"></a>
#### TC-007 — Simplify unreachable branches instead of trying to test them
**Severity:** Low · **Category:** infra
**Where:** see [Appendix A](#appendix-a--kover-misses-no-test-can-reach)

About 30 of the 114 missed branches are compiler-generated null checks, `COROUTINE_SUSPENDED` comparisons, or guards already implied by an earlier `require`. A few of them can be removed with a small source change:
- `TlsUtils.kt:151`: drop the redundant `&& keyPath.isNotEmpty()`.
- `KtorServletResponse.kt:120, 146`: `printWriter ?: PrintWriter(…).also { printWriter = it }`.
- `AtomicDelegates.kt:90`: drop the `= null` default on the private constructor.
- `RecaptchaService.kt:92`: drop the `= null` default on a non-null `String`.
- `RedisUtils.kt:158-161`: the `catch` around `createRedisClient` is effectively dead, because building a client never connects. It does, however, catch nothing that malformed URLs throw; see [TC-067](#tc-067) before deleting it.

The rest need no action. Do **not** add Kover class exclusions to hide them ([TC-030](#tc-030) explains why that backfires for serialization code).

### core-utils

<a id="tc-008"></a>
#### TC-008 — `singleToDoubleQuoted` corrupts input that has surrounding whitespace
**Severity:** High · **Category:** bug
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:27, 63-67`; test `core-utils/src/commonTest/kotlin/com/pambrose/util/StringExtensionTests.kt:139-147`

`isSingleQuoted()` trims the string before checking, but `singleToDoubleQuoted` then calls `subSequence(1, length - 1)` on the *untrimmed* string. `"  'x'  "` becomes `"\" 'x' \""` instead of `"\"x\""`. Inner backslashes are also not escaped (`'a\"b'`).

**Test:** `"  'x'  ".singleToDoubleQuoted() shouldBe "\"x\""`. Add an inner-backslash case once the intended escaping is decided. **Fix:** trim once and slice the trimmed value.

<a id="tc-009"></a>
#### TC-009 — `commonMain` utilities tested only on the JVM
**Severity:** High · **Category:** platform
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/{ArrayUtils,ListUtils,MiscExtensions,MiscFuncs,AtomicUtils,StringExtensions}.kt`; specs in `core-utils/src/jvmTest/kotlin/com/pambrose/util/{ArrayUtilsTests,ListUtilsTests,MiscExtensionsTests,MiscFuncsTests,StringExtensionEdgeCaseTests}.kt`

These run only in `jvmTest`:
- `ArrayUtils` and `ListUtils`;
- `simpleClassName` and `toCsv`;
- `isNull`, `lpad`, `rpad` and `capitalizeFirstChar`;
- `criticalSection`;
- `nullIfBlank`, `ensureSuffix`, `substringBetween`, `withLineNumbers`, `pathOf`, `maskUrlCredentials`, `obfuscate`, `maxLength` and `asBracketed`.

The JS results for these functions differ from the JVM's. Confirmed in `kotlin-stdlib-js-2.4.20-sources.jar`:

| Behaviour                         | JVM                  | JS                                                               | Affected                                                                                               |
|-----------------------------------|----------------------|------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `1.0.toString()`                  | `"1.0"`              | `"1"`                                                            | `ArrayUtils.asString(doubleArrayOf(1.0))`: `ArrayUtilsTests.kt:71` expects `"[1.0]"`                     |
| `String.substring(start, end)`    | bounds-checked       | native `substring` (`stringJs.kt:293`): swaps/clamps              | `"a".trimEnds()` returns `"a"` instead of throwing; `"abc".maxLength(-1)` returns `""` instead of throwing |
| `toDoubleOrNull` / `toFloatOrNull` | `screenFloatValue`   | unary `+` (`numberConversions.kt:117`)                           | `isDouble`/`isFloat`: `"4f"` is true on the JVM, false on JS; `"0x10"` is false on the JVM, true on JS |

The common specs for `isFloat`/`isDouble` (`StringExtensionTests.kt:115-137`) only use `""`, `"a"`, `"4"` and `"4.0"`.

**Test:** Move the specs above to `commonTest`. For each divergent case, either make the implementation consistent or pin the platform-specific result with an `expect`/`actual` test helper and document it in the KDoc.

<a id="tc-010"></a>
#### TC-010 — `UrlSource` throws for very long timeouts
**Severity:** Medium · **Category:** bug
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:237-238`

The timeouts were added by the CR-013 fix (#176). `connectTimeout.inWholeMilliseconds.toInt()`: `Duration.INFINITE` is `Long.MAX_VALUE` ms, which becomes `-1`, and `URLConnection.setConnectTimeout(-1)` throws `IllegalArgumentException`. Any duration over `Int.MAX_VALUE` ms (≈ 24.8 days) wraps around the same way.

**Test:** `UrlSource(fileUrl, readTimeout = Duration.INFINITE).content` should read the file. **Fix:** clamp with `coerceIn(0, Int.MAX_VALUE.toLong())`, and map `INFINITE` to `0`, which is `URLConnection`'s "infinite".

<a id="tc-011"></a>
#### TC-011 — `Short.MAX_VALUE` regression test never uses `MAX_VALUE`
**Severity:** Medium · **Category:** weak
**Where:** `core-utils/src/jvmTest/kotlin/com/pambrose/util/BugFixVerificationTests.kt:214-223`, `core-utils/src/commonTest/kotlin/com/pambrose/util/NumberExtensionTests.kt:29`, `core-utils/src/commonMain/kotlin/com/pambrose/common/util/NumberExtensions.kt:74-77`

The test named "short times works at max value" uses `n = 100`, and the common test uses 1000. Reintroducing the Bug #22 `Short` counter, which overflows to -32768 and loops forever, would pass both.

**Test (commonTest):** `Short.MAX_VALUE times { count++ }` gives `count == 32767`, and the last index is `32766`. A negative receiver runs zero iterations. Give the test a timeout so a regression fails instead of hanging.

<a id="tc-012"></a>
#### TC-012 — `Atomic` concurrency tests can't detect a missing `Mutex`
**Severity:** Medium · **Category:** weak
**Where:** `core-utils/src/commonTest/kotlin/com/pambrose/concurrent/AtomicTests.kt:53-68`, `core-utils/src/jvmTest/kotlin/com/pambrose/util/BugFixVerificationTests.kt:87-96`, `core-utils/src/commonMain/kotlin/com/pambrose/common/concurrent/Atomic.kt:55-58`

`setWithLock { it + 1 }` never suspends between the read and the write, and the launched coroutines inherit the test's dispatcher. A lost update therefore has almost no chance to happen, and on JS none at all, so the test passes with the lock removed.

**Test:** Run the JVM variant on `Dispatchers.Default`, and add a suspension inside the critical section (the lambda is `inline`, so `yield()` is allowed): `setWithLock { v -> yield(); v + 1 }`. Without the mutex, 1,000 such updates reliably lose some.

<a id="tc-013"></a>
#### TC-013 — `DateUtils` gaps and expectations that copy the implementation
**Severity:** Medium · **Category:** gap
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/DateUtils.kt:79, 86-87, 254, 267`; tests `core-utils/src/commonTest/kotlin/com/pambrose/util/DateUtilsTest.kt:170-174, 181-187, 200-203`

- `localDateNow` and `localDateTimeNow` (79, 86-87) are never called. A common test would also back up the claim that the default zone works on JS/wasm without a tz database.
- The non-null path of `LocalDateTime?.age(tz)` (254) is untested.
- The default argument of `toAdjustedString` (267) is never used.
- `DateUtilsTest.kt:181-187` builds its expected strings with the same expressions as the code under test.

**Tests:**
- `localDateNow(UTC)` falls between two `Clock.System.todayIn(UTC)` calls. Also call both functions with the default argument.
- `(now - 1.hours).toLocalDateTime(fixedOffsetMinus4).age(fixedOffsetMinus4)` is about 1 h, and `.age(UTC)` is about 5 h (which proves the zone is used).
- `95.seconds.toAdjustedString() shouldBe "1m 35s"` (the KDoc example). Use literals like `"1h 30m 45.25s"`, and add a negative case.
- `DateUtilsTest.kt:170-174` needs an upper bound on `age`. `:200-203` tests kotlinx-datetime rather than this library, so drop it.

<a id="tc-014"></a>
#### TC-014 — `toObjectSecure` blocklist and filter-merge paths
**Severity:** Medium · **Category:** gap
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:118, 140`; test `core-utils/src/jvmTest/kotlin/com/pambrose/util/IOExtensionsTests.kt:104-116`

- The exact-name blocklist (140) is never hit: `Runtime`, `Process` and `ProcessBuilder` aren't `Serializable`, so no test stream can contain them unless it is patched by hand.
- Merging with a JVM-wide `jdk.serialFilter` (118) is untested.
- The whitelist tests assert only the exception type.

**Tests:**
- Serialize `42`, then replace `java.lang.Integer` with `java.lang.Runtime` in the bytes (same length). `toObjectSecure(bytes, setOf(Integer::class, Number::class))` should throw `SecurityException` with "Blocked dangerous class".
- In a forked test JVM (`Test` task with `-Djdk.serialFilter=!java.util.ArrayList`), an allow-listed `ArrayList` should still be rejected.
- Assert the "Class not in whitelist" message.

<a id="tc-015"></a>
#### TC-015 — `linesBetween` reversed-boundary guard
**Severity:** Low · **Category:** gap
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:206`

The `startIdx + 1 > endIdx` arm never runs. The existing case ends at `subList(3, 3)`, which is legal. Without the guard, a reversed range throws.

**Test:** `listOf("a","b","c").linesBetween(Regex("c"), Regex("a"))` and `listOf("a","b").linesBetween(Regex("a"), Regex("a"))` both return `[]`.

<a id="tc-016"></a>
#### TC-016 — `getBanner` and resource loading
**Severity:** Low · **Category:** gap
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/Banner.kt:37`, `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/MiscFuncsJvm.kt:175`; test `core-utils/src/jvmTest/kotlin/com/pambrose/util/BannerTests.kt:36-50`

- The missing-resource path of the class-loader overload is untested.
- `BannerTests` never checks that blank lines in the middle survive, so an implementation that dropped all blank lines would pass.
- `readResourceFile`'s fallback for a `null` context class loader is untested.

**Tests:**
- `getBanner("missing", URLClassLoader(arrayOf(), null))` throws `IllegalArgumentException`.
- Assert the exact banner body, including the interior blank line.
- With `Thread.currentThread().contextClassLoader = null` (restored in `finally`), `readResourceFile("test-banner.txt")` still succeeds.

<a id="tc-017"></a>
#### TC-017 — `Version` assertions
**Severity:** Low · **Category:** weak
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/Version.kt:80`; tests `core-utils/src/jvmTest/kotlin/com/pambrose/util/VersionTests.kt:47, 65, 77`, `core-utils/src/jvmTest/kotlin/com/pambrose/util/BugFixVerificationTests.kt:184-188`

`plainStr` is never called. The other tests only assert non-null, or that a key is present.

**Test:** `Version.plainStr("1.0", "2025-01-01", 1_711_929_600_000)` equals the exact expected string, computed in a fixed zone. Replace the presence checks with exact values: the inputs are deterministic.

<a id="tc-018"></a>
#### TC-018 — `criticalSection` and single-assignment thread safety
**Severity:** Low · **Category:** gap
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/AtomicUtils.kt:32-39`, `core-utils/src/jvmMain/kotlin/com/pambrose/common/delegate/SingleAssignVar.kt:53`; test `core-utils/src/jvmTest/kotlin/com/pambrose/util/BugFixVerificationTests.kt:119-135`

No test checks that the flag is `true` inside the block, or that it is reset when the block throws. The thread-safety claims in the KDoc of `singleSetReference` / `SingleAssignVar` are untested.

**Tests:**
- `flag.criticalSection { flag.load() } shouldBe true`.
- Throw inside the block, then assert `flag.load() == false`.
- 100 writers race on `Dispatchers.Default`: exactly one succeeds, and the stored value is the winner's.

<a id="tc-019"></a>
#### TC-019 — DST gap/overlap tests
**Severity:** Low · **Category:** gap
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/DateUtils.kt:169-170`; test `core-utils/src/jvmTest/kotlin/com/pambrose/util/DateUtilsJvmTests.kt`

The offset shown for a local time inside a DST gap or overlap is not pinned.

**Test (jvmTest, where named zones are available):** In `America/New_York`, format `2026-03-08T02:30` (gap) and `2026-11-01T01:30` (overlap), and assert the offset printed for each.

<a id="tc-020"></a>
#### TC-020 — Edge cases
**Severity:** Low · **Category:** gap
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/{DateUtils,MiscFuncs,StringExtensions}.kt`, `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/{MiscFuncsJvm,PropertyFunctions,IOExtensions}.kt`

- **Negative years:** `Int.lpad` pads after the sign (`(-1).toString().padStart(4, '0')`), so `LocalDate(-1, 1, 1).toMMDDYYYY()` gives `"01/01/00-1"`. Pin or fix it.
- **Surrogate pairs:** `obfuscate` and `maxLength` can split them (`"a😀".maxLength(2)`).
- **Non-ASCII capitalization:** `capitalizeFirstChar` with `"ǆa"` (→ `"ǅa"`) and `"ßa"`.
- **`verifyChecksum`:** test inputs of exactly 31 and 32 bytes, the two sides of the length check.
- **`readProperties`:** later files overriding earlier ones is documented but untested; `PropertyFunctionsTests.kt:132-140` uses distinct keys.
- **`waitForPortAvailable`** (`MiscFuncsJvm.kt:139-147`):
  - no retry-then-succeed test (close the blocking socket after ~100 ms);
  - its `catch (Exception)` swallows the `IllegalArgumentException` from an invalid port.
- **`Int.repeat`:** only exercised incidentally (`TimeTests.kt:43`).

<a id="tc-021"></a>
#### TC-021 — Timing hazards and no-op assertions
**Severity:** Low · **Category:** flaky/weak
**Where:** `core-utils/src/jvmTest/kotlin/com/pambrose/util/{MiscFuncsTests,ReadResourcesTests,MiscJavaFuncsTests}.kt`

- **Timing:**
  - `MiscFuncsTests.kt:155-162`: the `< 850 ms` upper bound leaves 250 ms of slack.
  - `ReadResourcesTests.kt:47-51`: asserts `elapsed < 1000`.
  - `MiscFuncsTests.kt:164-167`: a second real `InetAddress.getLocalHost()` call can fail DNS on a sandboxed runner. Extract an `internal` resolver function so the `"Unknown"` fallback can be tested directly.
- **No-op assertions:**
  - `MiscFuncsTests.kt:46-50`: `shouldNotBe null` on non-null types.
  - `MiscJavaFuncsTests.kt:111-114`: a constructor call that exists only for coverage.

### json-utils

<a id="tc-022"></a>
#### TC-022 — `getByPath` throws instead of returning `null` when the path crosses a non-object
**Severity:** High · **Category:** bug
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:127-129`; related test `json-utils/src/commonTest/kotlin/com/pambrose/json/JsonElementUtilsTest.kt:556`

`acc?.jsonObject?.get(key)`: the `jsonObject` extension *throws* `IllegalArgumentException` for a primitive or `JsonNull`, so `"""{"a":1}""".toJsonElement().getByPath("a/b")` throws. The dot-path `getOrNull("a.b")` returns `null` for the same input, and the KDoc promises `null`. The Kover miss on this line is the unreachable `?.` arm after `jsonObject`.

**Test:** `getByPath("a/b")` on `{"a":1}` and on `{"a":null}` returns `null`. **Fix:** `(acc as? JsonObject)?.get(key)`.

<a id="tc-023"></a>
#### TC-023 — OrNull accessors only ever tested for `null`
**Severity:** Medium · **Category:** weak
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:203, 214`

`jsonObjectValueOrNull` and `jsonElementListOrNull` appear in 9 test calls, all of which expect `null`, so an implementation that always returned `null` would pass.

**Test:** For `{"obj":{"k":1},"list":[1]}`, `jsonObjectValueOrNull("obj")` equals the object, and `jsonElementListOrNull("list") shouldBe listOf(JsonPrimitive(1))`.

<a id="tc-024"></a>
#### TC-024 — Number parsing differs across platforms
**Severity:** Medium · **Category:** platform
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:61, 100, 182` (and `toMap`)

`doubleValue`, `doubleValueOrNull` and `isNumber` use the platform `toDouble`/`toDoubleOrNull`:
- The JVM accepts `1.5f` and `0x1p3`.
- JS (unary `+`) accepts `0x10` and rejects `1.5f`.

`primitiveContentOrNull` doesn't check `isString`, so `{"n":"1.5f"}.doubleValueOrNull("n")` returns `1.5` on the JVM and `null` on JS. `toMap()` renders `JsonPrimitive(1.0).content` as `"1.0"` on the JVM and `"1"` on JS.

**Test:** Decide the contract, then pin it in `commonTest`. Expect the test to fail on one platform until parsing uses a common JSON-number check.

<a id="tc-025"></a>
#### TC-025 — `JsonIntegrationTest` is JVM-only
**Severity:** Medium · **Category:** platform
**Where:** `json-utils/src/jvmTest/kotlin/com/pambrose/json/JsonIntegrationTest.kt:50, 436, 478-499`

The suite is in `jvmTest` only because it calls `System.currentTimeMillis`. Its null, missing-key and nested-path checks would run on js, wasmJs and linuxX64 if it used `kotlin.time`. Lines 478-499 assert a wall-clock time under 1,000 ms, which can flake on a slow runner and proves nothing about correctness.

**Fix:** Switch to `TimeSource.Monotonic`, move the suite to `commonTest`, and delete the timing assertion.

<a id="tc-026"></a>
#### TC-026 — Small json-utils gaps
**Severity:** Low · **Category:** gap
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:93, 100, 173, 182, 192, 239-240`; test `json-utils/src/commonTest/kotlin/com/pambrose/json/JsonElementUtilsTest.kt:344-349`

- **Wrong-type receivers:**
  - `isString`/`isNumber` on a `JsonObject`/`JsonArray` should be `false`.
  - `int/double/booleanValueOrNull` on an object or array should be `null`.
- **`forEachJsonObject`:** should skip array elements that aren't objects, and throw for a primitive or `JsonNull` receiver.
- **`deepCopy`:** the test checks only equality, so a copy that returned `this` would pass. Add `shouldNotBeSameInstanceAs` for the root and for nested elements.
- **Long values:** `JsonIntegrationTest.kt:154, 253` use `intValue(...).toLong()`, which hides the lack of a `Long` accessor. Add a millisecond-timestamp case to show the overflow.

### ktor-client-utils

<a id="tc-027"></a>
#### TC-027 — Client-creating paths tested only on the JVM
**Severity:** Medium · **Category:** platform
**Where:** `ktor-client-utils/src/commonMain/kotlin/com/pambrose/common/dsl/KtorDsl.kt:41-65`, `ktor-client-utils/build.gradle.kts:12-27`; test `ktor-client-utils/src/jvmTest/kotlin/com/pambrose/common/dsl/KtorDslJvmTests.kt:51-76`

When `httpClient` is `null`, the DSL creates `HttpClient {}`. On native this throws "Failed to find HTTP client engine implementation": the module declares no native engine, in either main or test dependencies. JS and wasm fall back to the built-in Js engine. Only 3–4 common tests run on non-JVM targets, against 10 on the JVM.

**Fix:** Move the "closes a client it created" test to `commonTest`. For native, either add a test expecting `IllegalStateException` or add an engine (e.g. Darwin/Curl) to the native test dependencies, and document that consumers must supply one.

<a id="tc-028"></a>
#### TC-028 — `expectSuccess` and `HttpTimeout` behaviour
**Severity:** Medium · **Category:** gap
**Where:** `ktor-client-utils/src/commonMain/kotlin/com/pambrose/common/dsl/KtorDsl.kt:41-65`

**Tests** (loopback server, as in the JVM suite):
- `blockingGet(url)` against a 500 returns status 500 with body `"boom"` (the default is `expectSuccess = false`).
- `withHttpClient(client500, expectSuccess = true)` does not throw, because the flag is ignored for a provided client.
- With `setUp = { timeout { requestTimeoutMillis = 100 } }` against a handler that sleeps 1 s, expect `HttpRequestTimeoutException`. This proves `install(HttpTimeout)` happened.

<a id="tc-029"></a>
#### TC-029 — Weak ktor-client assertions
**Severity:** Low · **Category:** weak
**Where:** `ktor-client-utils/src/commonTest/kotlin/com/pambrose/common/dsl/KtorDslTests.kt:48-66`, `ktor-client-utils/src/jvmTest/kotlin/com/pambrose/common/dsl/KtorDslJvmTests.kt:72`

The common tests never check the request's method or URL (`MockEngine.requestHistory`) and never pass `setUp`. `KtorDslJvmTests.kt:72` is a no-op non-null check, and never verifies that the client it created was closed.

### email-utils

<a id="tc-030"></a>
#### TC-030 — Webhook misses are generated code, with two real gaps behind them
**Severity:** Medium · **Category:** gap
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/webhook/{Data,Bounce,Click,Header,ResendWebhookMsg}.kt`

Every missed branch in `webhook` (27, the project's lowest package at 74.5%) is in code that kotlinx.serialization generates: `write$Self` and the synthetic deserialization constructor. `write$Self` has several branches per defaulted property, and `encodeDefaults` is never on in the tests.

| Class              | Branches missed | Why                                                                                     |
|--------------------|----------------:|-----------------------------------------------------------------------------------------|
| `Data`             |              17 | 9 `encodeDefaults` arms; `tags`, `broadcastId`, `messageId`, `templateId` never encoded non-null |
| `Bounce`           |               7 | 2 `encodeDefaults` arms; `subType`/`type` never encoded; missing-`message` throw        |
| `Click`            |               1 | `encodeDefaults`                                                                        |
| `Header`           |               1 | missing-required-field throw                                                            |
| `ResendWebhookMsg` |               1 | missing-required-field throw                                                            |

Two real behaviours hide behind these:
- `ResendWebhookMsg.decode`'s `@throws` contract (`ResendWebhookMsg.kt:63-65`).
- The encoded serial names of the four newer `Data` fields.

**Tests (bring the package to 100% branch):**
- `decode` with `created_at` removed, and `decode("not json")`, each throw `SerializationException`.
- Round-trip a `Data` with the four newer fields set, and assert the JSON contains `"broadcast_id"`, etc.
- `Json { encodeDefaults = true }.encodeToString(Bounce("m"))` gives `{"message":"m","subType":null,"type":null}`.
- A `Header` with a missing field throws `SerializationException`.

**Don't exclude this code in Kover.** Kover 0.9.9 filters only whole classes (`classes`, `packages`, `annotatedBy`, `inheritedFrom`). `write$Self` carries only `@JvmStatic`, and the constructor is synthetic with no annotations. The only way to hide them is to exclude every `@Serializable` class, which would hide real code too.

<a id="tc-031"></a>
#### TC-031 — IPv4-literal email domains
**Severity:** Medium · **Category:** gap
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/EmailUtils.kt:36-45` (documented at `:52`)

The regex has an explicit IPv4 alternative that no test uses.

**Test:** `user@192.168.1.1` is valid. These are invalid:
- `user@256.1.1.1`
- `user@[127.0.0.1]`
- `user@-bad.com`
- `user@example.com.`
- a domain with a 64-letter TLD

<a id="tc-032"></a>
#### TC-032 — `Email` serialized form
**Severity:** Low · **Category:** gap
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/Email.kt:30-33`

No email test uses `Json`. (The Kover miss on line 33 is the boxed `getValue()` getter; see [TC-006](#tc-006).)

**Test:** `Json.encodeToString(Email("a@b.com")) shouldBe "\"a@b.com\""`, and decoding it gives the same value back.

<a id="tc-033"></a>
#### TC-033 — Vacuous log assertion
**Severity:** Low · **Category:** weak
**Where:** `email-utils/src/test/kotlin/com/pambrose/common/email/ResendServiceTests.kt:137-139`

`message shouldContain "2"` always passes, because the previous line already requires `"id-123"`, which contains a 2.

**Fix:** Assert the full message: `"Sent email [id-123] to 2 to, 1 cc, and 1 bcc recipients"`.

### recaptcha-utils

<a id="tc-034"></a>
#### TC-034 — Malformed or non-2xx siteverify responses
**Severity:** High · **Category:** gap
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:79, 108-124`; test `recaptcha-utils/src/test/kotlin/com/pambrose/common/recaptcha/RecaptchaServiceTests.kt:178`

The only error test uses an engine that throws. This security check is supposed to fail closed, yet no test shows that an HTML error page, an empty object or a mistyped `success` is rejected. This also accounts for the missing-`success` branch among the 4 misses on line 79.

**Test:** One MockEngine case per response, each expecting 400 "reCAPTCHA verification failed":
- 500 `text/html`
- 200 `{}`
- 200 `not json`
- 200 `{"success":"yes"}`
- 200 `{"success":null}`

<a id="tc-035"></a>
#### TC-035 — Tests copy the production `Json` config
**Severity:** Medium · **Category:** weak
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:56-66`, `recaptcha-utils/src/test/kotlin/com/pambrose/common/recaptcha/RecaptchaTestSupport.kt:55-65`

`mockVerificationClient` rebuilds the client with its own copy of `ignoreUnknownKeys = true, coerceInputValues = true`. Removing either flag from production would fail no test, because no mock response has an unknown key.

**Fix:** Share one `internal` client factory that takes an engine. **Test:** `{"success":true,"score":0.9,"action":"login"}` and `{"success":true,"error-codes":null}` both pass.

<a id="tc-036"></a>
#### TC-036 — Widget site key
**Severity:** Medium · **Category:** weak
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:201-204`; test `recaptcha-utils/src/test/kotlin/com/pambrose/common/recaptcha/RecaptchaTests.kt:56`

The test only checks that the output contains `"g-recaptcha"`.

**Test:** The output contains `data-sitekey="site"` and does **not** contain the secret key.

<a id="tc-037"></a>
#### TC-037 — Smaller recaptcha gaps
**Severity:** Low · **Category:** gap
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:95, 102, 201, 236-237`; tests `recaptcha-utils/src/test/kotlin/com/pambrose/common/recaptcha/{RecaptchaConfigTests,RecaptchaTests}.kt`

- **Empty `remoteip`:** `postToken(engine, remoteAddress = "")` should send no `remoteip` field.
- **Config re-reads (`:95`, `:201`):** a config getter that changes between reads (MockK `returnsMany listOf("secret", null)`) makes `validateRecaptcha` throw `IllegalArgumentException` outside `runCatchingCancellable`. Pin that behaviour, or read the keys once.
- **After `close()` (documented at `:236-237`):** swap in the mock client, call `close()`, post a token, and assert the result.
- **Specs that exercise no production code:** `RecaptchaConfigTests.kt` (all of it), `RecaptchaTests.kt:69-89` and `:237-246`. Remove them or point them at the service.

### ktor-server-utils

<a id="tc-038"></a>
#### TC-038 — Malformed servlet content type becomes a 500
**Severity:** High · **Category:** bug
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:70, 76-77`, `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:96, 240, 243`

`ServletRoute` calls `ContentType.parse(it)` with no `runCatching`. ktor-http 3.5.2 throws `BadContentTypeFormatException` for values without a `/` (`ContentTypes.kt:139-160`), so a servlet that finishes normally but sets `"bogus"` produces a 500. `KtorServletResponse` already guards its own parses (`:96`, `:239`), and those guarded arms are uncovered for the same reason. A servlet that *throws* (`:70`) is also untested: nothing shows the result is a 500 that doesn't leak partial output.

**Test (`testApplication`):**
- A servlet doing `resp.contentType = "bogus"; writer.print("x")`.
- A servlet throwing `ServletException`.

**Fix:** Fall back to `application/octet-stream` (or pass the raw header through) when parsing fails.

<a id="tc-039"></a>
#### TC-039 — `sendRedirect` buffer handling
**Severity:** Low · **Category:** gap
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:176`; test `ktor-server-utils/src/test/kotlin/com/pambrose/common/servlet/KtorServletResponseTests.kt:184-195`

Neither test writes a body before redirecting, so the buffer discard is never checked, and the `clearBuffer = false` branch is uncovered.

**Test:** Write a body, then call `sendRedirect(loc, 303, false)`: the body is kept. Write a body, then call `sendRedirect(loc)`: the body is cleared.

<a id="tc-040"></a>
#### TC-040 — `HerokuHttpsRedirect` options
**Severity:** Low · **Category:** gap
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt:77, 146`

No test sets `sslPort`.

**Tests:**
- With `sslPort = 8443`, the redirect goes to `https://host:8443/…`.
- A path that doesn't match any configured exclude is still redirected.

<a id="tc-041"></a>
#### TC-041 — Header edge cases through the bridge
**Severity:** Low · **Category:** gap
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:71-75`, `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:71-76`

- Multi-valued `addHeader` is never tested end to end.
- `setHeader("Content-Type", "…; charset=ISO-8859-1")` does not set the writer's charset, so the header and the body encoding can disagree.

Pin both behaviours.

### service-utils

<a id="tc-042"></a>
#### TC-042 — Health check with a dead sub-service
**Severity:** High · **Category:** gap
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:280-294`; tests `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:341, 508`

`all_services_healthy` is only ever evaluated while every service is `NEW`, so the `!== RUNNING` filter never drops anything (Kover: 1 of 2 branches at 289). Catching a sub-service that died is the point of this check.

**Test:** Start a service with admin and metrics enabled, call `metricsService.stopSync()`, then GET `/healthcheck`. Expect a 500 whose body names `TERMINATED` and the metrics service and has no `RUNNING:` entry.

<a id="tc-043"></a>
#### TC-043 — Port-in-use rollback for the Ktor variant
**Severity:** Medium · **Category:** gap
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/KtorServletService.kt:75`; Jetty test `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:389-406`

CIO's `start(wait = false)` surfaces a bind failure through its startup job. If that ever stopped propagating, the service would report `RUNNING` with no admin listener.

**Test:** Mirror the Jetty test with the Ktor service.

<a id="tc-044"></a>
#### TC-044 — Services never constructed directly
**Severity:** Medium · **Category:** gap
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/MetricsService.kt:45-46`, `service-utils/src/main/kotlin/com/pambrose/common/service/ServletService.kt:44-45`, `service-utils/src/main/kotlin/com/pambrose/common/service/KtorServletService.kt:47-49`

These are the missed lines: default constructor arguments. The `Generic*` classes always pass every argument, so these defaults never run.

**Test:**
- Build `MetricsService(port, "metrics")`, `ServletService(port, group)` and `KtorServletService(port, group)` directly. Start each, GET an endpoint, and check `toString()`.
- For `MetricsService`, assert that `healthCheck.execute().isHealthy` goes false, then true, then false across start and stop.

<a id="tc-045"></a>
#### TC-045 — Admin-disabled Ktor service; init hooks
**Severity:** Medium · **Category:** gap
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/GenericKtorService.kt:59, 73-88`, `service-utils/src/main/kotlin/com/pambrose/common/service/GenericService.kt:69-80`

An admin-disabled `TestKtorService` is constructed (`GenericServiceTests.kt:303, 312, 320`) but never started. No test passes `servletInit` or `initKtor`.

**Tests:**
- Start and close an admin-disabled Ktor service with metrics on: the metrics endpoint answers, and the service ends `TERMINATED`.
- Register an extra servlet and an `initKtor` route, and GET both.

<a id="tc-046"></a>
#### TC-046 — Metrics flags and JMX reporter
**Severity:** Medium · **Category:** weak
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:136-144, 204-205, 227`; test `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:116-121`

Every test sets all export flags to `false`, so swapped arguments to `SystemMetrics.initialize` would pass. The JMX reporter is never checked.

**Tests:**
- `mockkObject(SystemMetrics)` and verify that distinct flag values arrive in the right parameters.
- The MBean `metrics:name=<counter>,type=counters` is registered while running and gone after `close()`.

<a id="tc-047"></a>
#### TC-047 — Shutdown hook, `close()` edge cases, port release
**Severity:** Medium · **Category:** gap
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:217, 309-316`; tests `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:347-348, 358-359`

**Tests:**
- **Shutdown hook:** call `registeredShutDownHook!!.run()`. The service ends `TERMINATED`, its ports are released, and the hook is cleared.
- **`removeShutdownHook` failure:** make it throw `IllegalStateException`, using the `mockkStatic(Runtime::class)` pattern from `grpc-utils/src/test/kotlin/com/pambrose/common/utils/ServerExtensionsTests.kt:41-50`. `close()` still succeeds.
- **`close()` edge cases:** call `close()` on a never-started service, and call it twice.
- **Port release:** the normal stop tests only check `isRunning`. After `close()`, bind the port again (`ServerSocket(port).close()`) for both variants.

<a id="tc-048"></a>
#### TC-048 — `freePort()` race
**Severity:** Medium · **Category:** flaky
**Where:** `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:57` (used at `:328-329, 352, 367, 378, 390, 409, 439, 477, 490, 524, 540, 549`), `gradle.properties:9`

`freePort()` closes its socket before the server binds. With `org.gradle.parallel=true`, other modules' test JVMs (Jetty, gRPC, Ktor, loopback `HttpServer`s) bind ephemeral ports at the same time. `freePort() to freePort()` (`:367, 378, 524, 549`) can also return the same port twice.

**Fix:** Hold all the probe sockets open until every port has been allocated. Better still, let the services bind to port 0 and expose the bound port.

<a id="tc-049"></a>
#### TC-049 — Host-binding tests silently skip
**Severity:** Low · **Category:** flaky
**Where:** `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:532, 552-553`

Both tests quietly pass when there is no non-loopback IPv4 address. A firewall that drops connections makes `:553` fail, while `:533` passes without testing anything.

**Fix:** Skip explicitly with Kotest's `enabledIf`, so the report shows the skip, and use a connect timeout.

### jetty-utils

<a id="tc-050"></a>
#### TC-050 — `LambdaServlet` error handling and no-op assertions
**Severity:** Low · **Category:** weak
**Where:** `jetty-utils/src/test/kotlin/com/pambrose/common/servlet/{LambdaServletDoGetTests,LambdaServletTests}.kt`, `jetty-utils/src/test/kotlin/com/pambrose/common/dsl/JettyDslTests.kt`

- **Mock-only error handling:** it is checked only with mocks (`LambdaServletDoGetTests.kt:107-117`). Using the real Jetty helper in `ServletEncodingTests.kt:39-54`, assert that:
  - a throwing lambda returns 500;
  - a POST returns 405;
  - `Cache-Control` is actually sent.
- **No-op assertions:**
  - `LambdaServletTests.kt:26-39` and `JettyDslTests.kt:33, 44` check `shouldNotBe null` on constructors.
  - `JettyDslTests.kt:29` never checks that port 8080 reached the connector.

### grpc-utils

<a id="tc-051"></a>
#### TC-051 — No TLS or mutual-TLS handshake is ever performed
**Severity:** High · **Category:** gap
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/utils/TlsUtils.kt:136-158, 210-221`, `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:125-128, 168-169`; tests `grpc-utils/src/test/kotlin/com/pambrose/common/dsl/GrpcDslTests.kt:224-233, 237-248`

The TLS contexts are built but never used on a connection. The TLS server tests never start the server; they assert only `services.shouldBeEmpty()` / `isShutdown`, so they would pass even if the `sslContext` were silently dropped.

**Test:** The committed fixtures support a real handshake: `server-cert.pem` is self-signed with SAN `localhost`, and `client-cert.pem` is self-signed.
- Start a Netty server on port 0 with `buildServerTlsContext(server-cert, server-key, trust = client-cert)`.
- Connect with `channel("localhost", port, buildClientTlsContext(client-cert, client-key, trust = server-cert))`. The echo call succeeds.
- Each of these gets `UNAVAILABLE`:
  - a client with no client certificate;
  - a client that trusts the wrong CA;
  - a plaintext client.

A loopback connection on port 0 is still hermetic, although elsewhere the convention is the in-process transport.

<a id="tc-052"></a>
#### TC-052 — Invalid TLS material
**Severity:** Medium · **Category:** gap
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/utils/TlsUtils.kt:82, 137, 153-156, 211-214`

Every negative test uses a *missing* path. None covers:
- swapped certificate and key files;
- garbage PEM content;
- a mismatched key/cert pair.

The documented `@Throws(SSLException)` path never runs.

**Test:** Pin which exception each case raises (Netty's `IllegalArgumentException` at builder time or `SSLException` at `build()`). Add one test showing that an encrypted PKCS#8 key fails with a clear error, since the API takes no key password.

<a id="tc-053"></a>
#### TC-053 — Shallow gRPC builder, streaming and shutdown checks
**Severity:** Medium · **Category:** weak
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:93-106, 125-128, 215-246`, `grpc-utils/src/main/kotlin/com/pambrose/common/utils/ServerExtensions.kt:70-80`

- **Builder selection:** `GrpcDslTests.kt:128-149` asserts only the authority. Using the existing `withStubbedBuilder` helper, verify:
  - `sslContext` vs `usePlaintext()` is chosen correctly, for both the channel and the server;
  - `maxRetryAttempts = -1` never calls the retry method, and `0` does;
  - a blank `overrideAuthority` is ignored.
- **Stream observer:** it never runs in a real streaming RPC. Use an in-process server-streaming call to assert `onNext` order and that `onError` receives a `StatusRuntimeException`. Double registration is tested only for `onNext` (`StreamObserverHelperTests.kt:123-132`).
- **`shutdownGracefully`:** tested only with mocks. With an in-process server and a blocked in-flight call, assert that the server terminates and the client sees `CANCELLED`/`UNAVAILABLE`.

<a id="tc-054"></a>
#### TC-054 — Misnamed and assertion-free gRPC tests
**Severity:** Low · **Category:** weak
**Where:** `grpc-utils/src/test/kotlin/com/pambrose/common/utils/TlsContextTests.kt:38-43`, `grpc-utils/src/test/kotlin/com/pambrose/common/dsl/StreamObserverHelperTests.kt:100-108`

- `TlsContextTests.kt:38-43` is named "mutual auth" but tests plaintext, duplicating `TlsUtilsTests.kt:259-262`.
- `StreamObserverHelperTests.kt:100-108` has no assertion.

### guava-utils

<a id="tc-055"></a>
#### TC-055 — `GenericValueWaiter` paths
**Severity:** Medium · **Category:** gap
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericValueWaiter.kt:134, 168`; test `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/GenericValueWaiterTests.kt:182-194`

- **Non-satisfying update (168):** no test updates the value without satisfying a waiter. **Test:** an `IntWaiter` waiting for `== 3` is still active after `checkCondition(1)` and `(2)`, and completes `true` after `(3)`.
- **Timeout/set race (134):** can be made deterministic, because predicates are evaluated under the lock. **Test:**
  - Run the waiter on `Dispatchers.Default` with a 50 ms timeout.
  - Use the predicate `{ if (it == 1) { Thread.sleep(200); true } else false }`.
  - Expect `true` and no "Already resumed" error.
- **Cancellation with a finite timeout:** the existing cancellation test uses an infinite timeout, so no timeout job is ever armed. **Test:** cancel a `waitUntilTrue(5.seconds)` and assert it returns promptly.

<a id="tc-056"></a>
#### TC-056 — `BooleanMonitor` log helpers
**Severity:** Medium · **Category:** weak
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/BooleanMonitor.kt:59-164`; test `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/BugFixVerificationTests.kt:35-73`

The helper tests pass only because logback defaults to DEBUG (the module has no `logback-test.xml`), and none of them checks the level. The class's 8 missed lines are the `@JvmStatic` static bridges, which Kotlin callers never use.

**Test:**
- Attach a `ListAppender` (as `service-utils/src/test/kotlin/com/pambrose/common/service/GenericServiceTests.kt:82` does) and assert the level and message for all 8 helpers.
- Call the bridges through reflection: `BooleanMonitor::class.java.getMethod("debug", String::class.java).invoke(null, "m")`. Also assert `Modifier.isStatic`, following `MetricsDslTests.kt:59-61`.

<a id="tc-057"></a>
#### TC-057 — `GenericMonitor` interruption paths
**Severity:** Medium · **Category:** gap
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericMonitor.kt:87-90, 125-128, 207-211`; test `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/GenericMonitorTests.kt:151-169, 280-285`

**Tests:**
- The uninterruptible `waitUntilTrue()` ignores an interrupt, then restores the interrupt flag.
- The *timed* interruptible waits throw `InterruptedException`; only the untimed one is tested today.
- Timed waits whose guard throws; only the untimed ones are covered.

<a id="tc-058"></a>
#### TC-058 — Service failure paths
**Severity:** Medium · **Category:** gap
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericServiceListener.kt:38`, `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericIdleService.kt:36`, `guava-utils/src/main/kotlin/com/pambrose/common/dsl/GuavaDsl.kt:52-55`; test `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/GenericServicesTests.kt:86-96`

Untested:
- the listener's `failed` handler (the tests verify only its four info calls);
- `startSync` throwing `IllegalStateException` when `startUp` fails (the KDoc promise);
- `stopSync` throwing `TimeoutException`.

`GuavaDsl.serviceManager` is covered only by service-utils' tests.

**Test:** Use a real `ServiceManager` with a failing service, and assert the listener's `healthy`, `stopped` and `failure` callbacks.

<a id="tc-059"></a>
#### TC-059 — guava-utils timing hazards
**Severity:** Medium · **Category:** flaky
**Where:** `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/{GenericMonitorTests,GenericValueWaiterTests,VerboseCountDownLatchTests}.kt`

- **Upper wall-clock bounds:** `GenericMonitorTests.kt:305-306` (under 1 s for a 200 ms wait) and `:354`, and `GenericValueWaiterTests.kt:46, 213` (under 2 s).
- **Short joins with an unsynchronized flag:** `GenericMonitorTests.kt:44-53, 60-69` and `VerboseCountDownLatchTests.kt:36-50` call `t.join(1000)` and then read a `completed` flag that isn't `@Volatile`. `completed shouldBe false` after `delay(50)` proves nothing if the thread hasn't started yet. Use a latch with a 5 s await, as `GenericMonitorTests.kt:137-149` already does.
- **Delay-based waiter registration:** `GenericValueWaiterTests.kt:62, 81, 116, 134, 186` use a `delay` to "let the waiter register". If it hasn't registered yet, the test silently takes the already-satisfied path. Use `launch(start = CoroutineStart.UNDISPATCHED)`.

<a id="tc-060"></a>
#### TC-060 — `ZipExtensions` edge cases
**Severity:** Low · **Category:** gap
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/util/ZipExtensions.kt:63, 81`; test `guava-utils/src/test/kotlin/com/pambrose/util/ZipExtensionTests.kt:84-86`

**Tests:**
- `[0x1f, 0x00]` is not zipped, and `unzip` returns the raw bytes (63).
- A negative `maxBytes` throws `IllegalArgumentException` (81).
- `maxBytes` is ignored for input that isn't gzipped.
- A bad CRC trailer throws `ZipException`, and a truncated body throws `EOFException`; only a truncated 10-byte header is tested today.

<a id="tc-061"></a>
#### TC-061 — `thread(latch)` and interrupted waits
**Severity:** Low · **Category:** gap
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConcurrentExtensions.kt:91`, `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/VerboseCountDownLatch.kt:58-68`

The missed instructions on line 91 are the `start = true` default, which no test uses.

**Tests:**
- `thread(latch) {}` runs.
- `thread(latch, start = false, name = …, isDaemon = true)` leaves the thread in state `NEW`, with the given name and daemon flag.
- A block that throws still counts the latch down.
- An interrupted `Semaphore.withLock` acquire leaves the permit count unchanged.
- An interrupted `await(timeout, msg)` throws `InterruptedException`.

<a id="tc-062"></a>
#### TC-062 — `GuavaFuncsTests` cannot fail
**Severity:** Low · **Category:** weak
**Where:** `guava-utils/src/test/kotlin/com/pambrose/common/util/GuavaFuncsTests.kt:26-32`

`shouldBeTypeOf<Boolean>()` on a `Boolean` always passes.

**Fix:** Compare against `System.getProperty("os.name")`, and assert `!(isMac && isWindows)`.

### prometheus-utils

<a id="tc-063"></a>
#### TC-063 — `SystemMetrics` retry path
**Severity:** Medium · **Category:** gap
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SystemMetrics.kt:48-49, 76-94`; test `prometheus-utils/src/test/kotlin/com/pambrose/common/metrics/SystemMetricsTests.kt:46-52`

The `else` (retry later) arm is never taken. The repeat-call test can't fail: a duplicate registration throws `IllegalArgumentException`, which line 86 swallows, so skipping and re-attempting look the same.

**Test:** Use a `spyk(CollectorRegistry(true))` whose `register` throws `IllegalStateException` once.
- After the first `initialize`, nothing is registered; after the second, it is.
- Count `register` calls to prove that a third call makes zero attempts.
- Cover the KDoc promise that a cleared registry is not re-registered.

<a id="tc-064"></a>
#### TC-064 — `InstrumentedThreadFactory` and DSL registration
**Severity:** Low · **Category:** gap
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/concurrent/InstrumentedThreadFactory.kt:43-57, 72-79`, `prometheus-utils/src/main/kotlin/com/pambrose/common/dsl/PrometheusDsl.kt`

**Tests:**
- Registering a duplicate name in the same registry throws `IllegalArgumentException`, both through `PrometheusDsl` and through the factory.
- A runnable that throws still increments `terminated` and decrements `running`.
- When only the `running` name collides, the already-registered `created` counter is left behind. Pin or fix this.

### dropwizard-utils

<a id="tc-065"></a>
#### TC-065 — Map health check and error message
**Severity:** Low · **Category:** weak
**Where:** `dropwizard-utils/src/main/kotlin/com/pambrose/common/util/MetricsUtils.kt:77`; test `dropwizard-utils/src/test/kotlin/com/pambrose/common/dsl/MetricsDslTests.kt:49-57`

- **Live map:** the tests use only immutable maps, so nothing shows `newMapHealthCheck` reads the map on every check. **Test:** create the check, mutate the `HashMap`, and assert the next result changes.
- **Error message:** the throwing-block test doesn't assert the result's `error`.

### redis-utils

<a id="tc-066"></a>
#### TC-066 — Real `withRedis` path never runs
**Severity:** High · **Category:** gap
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:152-172, 324-332` (and the sibling overloads at `:350, 370, 389`); tests `redis-utils/src/test/kotlin/com/pambrose/common/redis/RedisUtilsMockTests.kt:52-55, 185-199`

The mock tests stub the whole of `connectOrNull`, so the real sequence never runs: create, `ping()`, hand the client to the block, then close it with `use`. The test named "uses the default redis url" matches `any<String>()`, and no test verifies `close()`. Line 168, where `close()` throws during failure cleanup, is also untested.

**Tests:**
- **Success path:** stub the private `createRedisClient` instead, with a mock whose `ping()` returns `"PONG"`. Verify:
  - one `ping()`;
  - the same instance reaches the block;
  - exactly one `close()`, including when the block throws.
- **Default URL:** capture the URL and assert `redis://user:none@localhost:6379`. Skip the test when `REDIS_URL` is set.
- **Failure cleanup:** make both `ping()` and `close()` throw `JedisException`. The block receives `null`, and the suppressed exception is logged.

<a id="tc-067"></a>
#### TC-067 — Redis URL edge cases
**Severity:** Medium · **Category:** bug
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:95-99, 144-149, 155-161`

- **Missing port:** `createRedisClient` passes `URI.port` straight to `HostAndPort`, so `redis://host` gives port `-1`. Jedis then fails with a `JedisConnectionException`, and `withRedis` passes `null` to the block even when a server is listening on 6379. Jedis' own `JedisURIHelper.isValid` rejects port -1; this library doesn't check it.
- **Malformed URLs:** `connectOrNull` catches only `JedisException`, so these escape `withRedis` instead of taking the documented null path:
  - `redis://host:6379/abc` (`NumberFormatException` from the database index);
  - `?protocol=9` (`IllegalArgumentException`);
  - a malformed URI (`URISyntaxException`).

**Test:** Pin each case. **Fix:** default the port to 6379, or reject the URL up front. Decide whether malformed URLs should throw (then document it) or take the null path.

<a id="tc-068"></a>
#### TC-068 — Weak Redis assertions
**Severity:** Low · **Category:** weak
**Where:** `redis-utils/src/test/kotlin/com/pambrose/common/redis/{RedisUtilsTests,RedisUtilsMockTests,BugFixVerificationTests}.kt`

- **No-op checks:** `client shouldNotBe null` on a non-null return type (`RedisUtilsTests.kt:61-91`, `RedisUtilsMockTests.kt:228-236`, `BugFixVerificationTests.kt:42-74`). Assert instead:
  - the pool values that were passed in;
  - the documented defaults (10 / 5 / 1 / 1 s);
  - that a user-only URL leaves both user and password unset in `clientConfig`.
- **`printStackTrace`:** its effect is never verified (`RedisUtilsMockTests.kt:152-161`).

### exposed-utils

<a id="tc-069"></a>
#### TC-069 — `upsert` conflict index never proven used
**Severity:** High · **Category:** weak
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:54-67`; tests `exposed-utils/src/test/kotlin/com/pambrose/common/exposed/UpsertStatementTests.kt:33-40, 71-105`

When Exposed is given no keys, it falls back to the primary key, then to the first unique index. The test table has no primary key and exactly one unique index, so the tests would still pass if the wrapper dropped `keys`.

**Tests:**
- Declare a second unique index *first* (e.g. on `id`). Upsert on the email index twice with different ids, and assert one row whose `id` was updated.
- Check that `where` is passed through: on H2 it should throw `UnsupportedByDialectException` ("MERGE implementation of UPSERT doesn't support single WHERE clause").

<a id="tc-070"></a>
#### TC-070 — Transaction helper settings
**Severity:** Medium · **Category:** weak
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/ExposedUtils.kt:92-145`; test `exposed-utils/src/test/kotlin/com/pambrose/common/exposed/ExposedUtilsTests.kt:120-153`

The tests check only row counts and that the duration is at least zero.

**Tests:**
- Inside each block, `readOnly` is `true` for `readonlyTx` and `timedReadOnlyTx`, and `false` for `timedTransaction`.
- Passing `TRANSACTION_SERIALIZABLE` sets `transactionIsolation`.
- An exception thrown inside `timedTransaction` propagates, and the insert is rolled back.

<a id="tc-071"></a>
#### TC-071 — `toRowString` filter and `CustomExpr` on H2
**Severity:** Low · **Category:** gap
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/ExposedUtils.kt:81`, `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/CustomExpr.kt`; test `exposed-utils/src/test/kotlin/com/pambrose/common/exposed/CustomExprTests.kt:28-46`

- **Empty-value filter:** a row with `name = ""` is left out of the `toRowString` output.
- **`CustomExpr`:** select `dateTimeExpr("LOCALTIMESTAMP")` and read it back as a date-time. The current tests never run SQL and are near-duplicates of each other.

<a id="tc-072"></a>
#### TC-072 — Null-database tests
**Severity:** Low · **Category:** weak
**Where:** `exposed-utils/src/test/kotlin/com/pambrose/common/exposed/BugFixVerificationTests.kt:36-58`

The tests assert `shouldThrow<Exception>` and "not an NPE". They also depend on test order: Exposed resolves a `null` database to the last one registered, so they pass only because every other spec unregisters its database. The comment at `:36` describes a `REPEATABLE_READ` fallback that doesn't exist.

**Fix:** Assert `IllegalStateException` with "No database specified", make the setup explicit, and fix the comment.

### script-utils (common / java / kotlin / python)

<a id="tc-073"></a>
#### TC-073 — `accessibleClass` fallback keeps the type arguments
**Severity:** High · **Category:** bug
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:237-249`, `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:60-67`, `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt`

When no public supertype has the registered number of type parameters, `accessibleClass` returns `Any::class`, but the callers still append the registered type arguments. Take an `Array<Int>` value registered with `typeOf<Int>()`:
- none of `Integer[]`'s supertypes (`Object`, `Cloneable`, `Serializable`) has one type parameter;
- the Java engine declares `java.lang.Object<java.lang.Integer> arr;`, which fails with "type java.lang.Object does not take parameters" (reproduced by the reviewer in jshell);
- the Kotlin engine takes the same path and would cast to `kotlin.Any<kotlin.Int>`.

`Collections.unmodifiableMap(m).entries` fails the same way. `accessibleClass` was introduced in #182 (CR-081 – CR-098); before that, the Kotlin engine rendered arrays as `Array<kotlin.Int>`. Lines 242, 246-247, 258 and 260 (the arity skip, the exhausted search and the fallback) are exactly the uncovered ones.

**Test:** In both engines, `add("arr", arrayOf(1, 2), typeOf<Int>())`, then evaluate `arr.size` / `arr.length`. Pin `varDecls` in `JavaEquivCharacterizationTests`, which needs no compile. **Fix:** drop the type arguments when falling back, or handle arrays explicitly.

<a id="tc-074"></a>
#### TC-074 — `JavaScript.renderType` and `Array<*>`
**Severity:** High · **Category:** bug
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:109-118`

For `Array<*>`, the star projection renders as `"?"`, and the array branch emits `"?[]"`. A `HashMap<String, Array<*>>` field then fails with "> or ',' expected". The array, star and type-parameter branches are uncovered (3 of 4 branches at 115), which makes this class the weakest in the project (60.7% branch).

**Test:** Call `params()` directly; this is cheap and needs no compile.
- `typeOf<Array<Int>>()` → `java.lang.Integer[]`
- `IntArray` → `int[]`
- `List<*>` → `java.util.List<?>`
- `Array<*>` → `Object[]`
- a type-parameter classifier (`List::class.typeParameters[0].createType()`) → `Object`

<a id="tc-075"></a>
#### TC-075 — Guard tests: some pass without a guard, others would kill the test JVM
**Severity:** High · **Category:** safety
**Where:** `script-utils-java/src/test/kotlin/com/pambrose/common/script/JavaScriptTests.kt:220-242`, `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/KotlinScriptTests.kt:255-268`, `script-utils-python/src/test/kotlin/com/pambrose/common/script/PythonScriptTests.kt:199-202`

- **Pass without a guard:** some cases fail to compile anyway, so they'd throw `ScriptException` with no guard at all.
  - `eval("System.exit(0)")`: `exit` is `void`.
  - `evalScript("System.exit(0);")`: not a class body.
  - `eval("exitProcess(0)")`: unresolved without an import.
  - Python `sys.exit()` without `import sys`.
- **Would kill the test JVM:** other cases are valid code. If the guard regressed, `KotlinScriptTests.kt:261-266` and `JavaScriptTests.kt:225-226, 237` would terminate the Gradle test worker instead of failing a test.

**Fix:**
- Assert the guard's own message (`"Illegal call to a JVM termination method"`, `"Illegal call to sys.exit()"`).
- Put the calls in code that is compiled but never runs: `{ System.exit(1) }` in Kotlin, `if (false) System.exit(0);` in Java, as `ExprEvaluatorTests` already does.

<a id="tc-076"></a>
#### TC-076 — `withInstance` loses the instance when `reset()` throws
**Severity:** Medium · **Category:** bug
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEnginePool.kt:83-91`

```kotlin
} finally {
  reset(instance)
  returnToPool(instance)
}
```

If `reset` throws, `returnToPool` never runs. The instance is neither returned nor closed, and in a size-1 pool the next `withInstance` suspends forever.

**Test** (with the fake pool from [TC-077](#tc-077)): make `reset` throw, then show that a second borrow from a size-1 pool completes, or closes the instance. **Fix:** nest `returnToPool` in its own `finally` (or close the instance when reset fails).

<a id="tc-077"></a>
#### TC-077 — script-utils-common has no direct tests
**Severity:** Medium · **Category:** infra
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/{AbstractEnginePool,AbstractExprEvaluator,AbstractExprEvaluatorPool,AbstractScript,AbstractScriptPool}.kt`

In the module's own Kover report, all five abstract classes are at 0/N lines; their aggregate coverage comes entirely from the engine modules. As a result:
- testing shared logic costs a full compiler start-up;
- the defensive paths ([TC-076](#tc-076), [TC-078](#tc-078)) can't be reached cheaply;
- all 8 `AbstractScriptAddMessageTests` start a Kotlin engine (2.4 s) without evaluating anything;
- `ClosingEvaluator` / `ClosingEvaluatorPool` in `ScriptPoolContractTests` extend `AbstractExprEvaluator("kts")` but never compile anything.

**Fix:** Add a test-only `FakeEngine : AbstractScriptEngine` registered through `src/test/resources/META-INF/services/javax.script.ScriptEngineFactory` (extension `"fake"`). Move the tests above to it, and add tests for:
- `accessibleClass` (via a subclass that exposes it);
- binding retry;
- the default `isReserved` / `bindVariables`;
- the deprecated `initialized` (`AbstractScript.kt:62-63, 188`).

<a id="tc-078"></a>
#### TC-078 — Pool behaviour under contention and failure
**Severity:** Medium · **Category:** gap
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEnginePool.kt:66-73, 99-110`; tests `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/ScriptPoolContractTests.kt:254-258`, `KotlinScriptTests.kt:292`, `script-utils-python/src/test/kotlin/com/pambrose/common/script/PythonScriptTests.kt:271`

**Tests:**
- **Failed cleanup (`:69`):** during `populate` cleanup, `close()` throws; assert `e.suppressed.size`.
- **Concurrency cap:** the existing tests borrow one at a time, so nothing shows the pool caps concurrency at `size`. Run 20 coroutines on `Dispatchers.Default`, record the peak number of simultaneous borrowers with an `AtomicInt`, and assert it is at most `size` and that all finish.
- **Close with a waiting borrower:** `close()` while a borrower is suspended; expect `ClosedReceiveChannelException`.

<a id="tc-079"></a>
#### TC-079 — Evaluator error messages
**Severity:** Medium · **Category:** weak
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluator.kt:53`; tests `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/BugFixVerificationTests.kt:80, 88`, `script-utils-python/src/test/kotlin/com/pambrose/common/script/PythonScriptTests.kt`

- **Null result:** `eval("null")` → "… got null" is never tested.
- **Loose type-mismatch checks:** the existing checks only require "Boolean" in the message, not "got Integer" / "got String".
- **Python:** the non-Boolean path is untested (`eval("1+2")`, `eval("None")`).

<a id="tc-080"></a>
#### TC-080 — Binding and cross-engine gaps
**Severity:** Medium · **Category:** gap
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:204, 210-217`, `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScriptPool.kt:35`, `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:134-146`

- **Binding:** retry after a binding failure is untested, and so is re-adding a name (`add("x", 1); eval; add("x", "s"); eval("x")`) in every engine.
- **`nullGlobalContext` after recycle:** Kotlin checks only the initial instances (`BugFixVerificationTests.kt:132-140`), and Python's test (`PythonScriptPoolTests.kt:106-111`) checks only that eval works. **Test:** on a size-1 pool, borrow twice and assert the `GLOBAL_SCOPE` bindings are `null`.
- **Java parity:**
  - The `evalScript` success path (a bound variable assigned to its field, plus imports) is untested.
  - `JavaScriptTests.kt:256-265` asserts only `b`, where the Kotlin and Python versions assert `a + b`.
  - `JavaScriptPool` is never shown to reset variables between borrowers.

<a id="tc-081"></a>
#### TC-081 — Cancellation test can pass without testing cancellation
**Severity:** Medium · **Category:** flaky
**Where:** `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/ScriptPoolContractTests.kt:170-202`

The test relies on `delay(200.milliseconds)`. If the holder releases the instance before the waiter's `receive` runs, the waiter simply succeeds, and the test still passes.

**Fix:** Assert `waiter.isCancelled shouldBe true`, or drive the scenario with a `StandardTestDispatcher`.

<a id="tc-082"></a>
#### TC-082 — Slow loop tests that check nothing
**Severity:** Medium · **Category:** perf
**Where:** `script-utils-kotlin/src/test/kotlin/com/pambrose/common/script/KotlinScriptTests.kt:271-302`, `script-utils-python/src/test/kotlin/com/pambrose/common/script/PythonScriptTests.kt:260-281`

The three Kotlin loops take 16.9 s, 7.3 s and 6.8 s: about 31 s of the module's 63 s, for 600 REPL compiles. They assert only `shouldNotThrow` and never look at the returned value. The Python loops have the same weakness.

**Fix:** Cut to about 5 iterations and assert `eval("$i == $i") shouldBe true`.

<a id="tc-083"></a>
#### TC-083 — Smaller script-utils gaps
**Severity:** Low · **Category:** gap
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:142-143, 180-181`, `script-utils-python/src/main/kotlin/com/pambrose/common/script/PythonExprEvaluator.kt:33`, `script-utils-common/src/test/kotlin/com/pambrose/common/script/{ScriptUtilsTests,AbstractEngineTests}.kt`

- **`verbose = true`:** never exercised. Call it once and assert the result.
- **`PythonExprEvaluator.close()`:** never called; `PythonScriptTests.kt:261, 272` don't close their evaluator or pool. Use `.use {}`.
- **`ScriptUtilsTests`:**
  - `:49-56` tests only `SimpleBindings`.
  - `:58-71` never sets a binding, and never checks `GLOBAL_SCOPE` or that `resetContext(true)` gives `null`.
- **`AbstractEngineTests`:** its three tests are near-duplicates.
- **REPL history reset:** checked only by context identity (`ScriptPoolContractTests.kt:209-216`). **Test:** `compute("val y = 1"); resetContext(); shouldThrow { compute("y") }`.
- **`AbstractScriptAddMessageTests.kt:103-107`:** never evaluates `props`.

---

## Appendix A — Kover misses no test can reach

Checked against the per-method counters in `report.xml` and against `javap` output. These need no test. Where a source change would remove the miss, it is listed under [TC-007](#tc-007).

| Location                                                             | Why it can't be covered                                                                              |
|----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `core-utils` `Version.kt:87, 100, 111-113`                           | Extra null checks on non-null annotation values; branches inlined from `findAnnotation`             |
| `core-utils` `AtomicDelegates.kt:90`                                 | Synthetic default-argument constructor from `= null` on a private constructor                        |
| `json-utils` `JsonElementUtils.kt:129` (`jsonObject?.` arm)          | `jsonObject` never returns null; goes away with the [TC-022](#tc-022) fix                            |
| `json-utils` `JsonElementUtils.kt:330`                               | Body of an `inline reified` function; the inlined copies are covered where it is called             |
| `json-utils` `JsonElementUtils.kt:407`                               | Deprecated `logger` holder, never referenced; remove in the next major version                      |
| `email-utils` `webhook/*` (27 branches)                              | kotlinx.serialization `write$Self` / synthetic constructor; covered by the [TC-030](#tc-030) tests   |
| `email-utils` `Email.kt:33`                                          | Boxed `getValue()` getter, called only from Java or reflection                                       |
| `recaptcha-utils` `RecaptchaService.kt:92, 102` (null arm)           | `remoteAddress` is a non-null `String`                                                               |
| `ktor-server-utils` `ResponseUtils.kt:39, 53`                        | Compiler compares a constant with `COROUTINE_SUSPENDED` after the inlined lambda                     |
| `ktor-server-utils` `ServletRoute.kt:61`                             | `lateinit` initialization guard                                                                      |
| `ktor-server-utils` `ServletRoute.kt:77` (elvis)                     | `ContentType.parse` never returns null                                                               |
| `ktor-server-utils` `KtorServletResponse.kt:120, 146`                | `?: error(…)` right after the field is assigned                                                      |
| `service-utils` `AbstractGenericService.kt:310`                      | Null branch reachable only by calling `shutDown` through reflection                                  |
| `grpc-utils` `TlsUtils.kt:151`                                       | `keyPath.isNotEmpty()` is already guaranteed by the `require` at `:141-144`                          |
| `guava-utils` `GuavaFuncs.kt:23, 26`                                 | `.orEmpty()` on `os.name`, which the JVM always sets                                                 |
| `redis-utils` `RedisUtils.kt:72`                                     | Reachable only with the `REDIS_URL` environment variable set                                         |
| `redis-utils` `RedisUtils.kt:160-161`                                | Building a client never connects, so the `JedisException` catch is effectively dead                   |
| `exposed-utils` `ExposedUtils.kt:67, 94, 115, 138`                   | Compiler null checks after `?.` on values that are never null                                        |
| `script-utils-common` `AbstractScript.kt:241` (`?: 0`)               | `typeMap` and `valueMap` are kept in step; callers loop over `valueMap`                              |
| `script-utils-common` `AbstractScript.kt:242` (`.kotlin`), `:273`    | Compiler null check; defensive `runCatching` fallback that needs a JVM-public Kotlin synthetic class |
| `script-utils-common` `AbstractExprEvaluator.kt:53` (2 of 3 branches) | Compiler null checks; the third is the real null-result case in [TC-079](#tc-079)                    |
| `script-utils-java` `JavaScript.kt:67`                               | Compiler null check                                                                                   |
| `*Script$Companion`, `AbstractScript.kt:252`, `JavaScript.kt:197`, `KotlinScript.kt:95`, `PythonScript.kt:85` | Unused synthetic `getX()` accessors on private companion objects; the field initializers do run |

## Appendix B — Reproducing the numbers

```bash
./gradlew koverXmlReport koverHtmlReport        # add --rerun-tasks to force the tests to run again
make coverage-packages                          # instruction coverage per package (see TC-003)
open build/reports/kover/html/index.html        # per-class line/branch drill-down
```

Line-level misses come from the `<sourcefile><line mi=… mb=…>` entries in `build/reports/kover/report.xml`. Per-module figures come from mapping each `package/sourcefile` pair to the module whose `src/*Main` directory contains it. Per-target test counts come from `*/build/test-results/<task>/*.xml`.
