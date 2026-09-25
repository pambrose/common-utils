# Code Review — common-utils (2026-09-24)

|                     |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Review date**     | 2026-09-24                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Project version** | 4.1.0                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **Commit reviewed** | `1ea5adc` (master)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Scope**           | All 19 modules (main and test sources, READMEs, module build files, `api/` dumps), root build, Makefile, CI workflows, Dependabot and Codecov config, project docs                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **Method**          | Seven parallel reviewers, one per module group plus build/CI/docs. Library behaviour was checked against the dependency sources: Exposed 1.5.0, Ktor 3.6.0, Jetty 12.1.13, Servlet 6.1, grpc-java 1.84.0, Jedis 8.0.1, commons-pool2 2.13.1, resend-java 4.25.0, kotlin-scripting 2.4.10/2.4.20, java-scriptengine 2.0.0, Jython 2.7.4, simpleclient 0.16.0 and kotlinx-serialization 1.11.0. Most bug claims were reproduced with small probe programs against the compiled module classes, some of them on live servers. The headline findings were checked again by hand before being written up. The full `./gradlew check` was not run. |
| **Related**         | [`CODE_REVIEW_2026-09-14.md`](CODE_REVIEW_2026-09-14.md) (CR-001…CR-139) and [`TEST_COVERAGE_REVIEW_2026-09-16.md`](TEST_COVERAGE_REVIEW_2026-09-16.md) (TC-001…TC-083) are both fully closed. Nothing here repeats them. When an item follows up an earlier fix that was incomplete or caused a regression, it names the CR/TC id.                                                                                                                                                                                                                                                                                                                         |

## At a glance

**93 issues:** 2 high · 16 medium · 75 low. Nothing is critical, and the earlier CR/TC fixes mostly held.

- **Two high items:**
  - [RV-001](#rv-001): `toObjectSecure` is billed as safe for untrusted input, but a 35-byte payload makes it allocate 80 MB, and a 1.7 KB payload burns about a minute of CPU.
  - [RV-048](#rv-048): `KotlinScript` binds every variable a second time as a typed `<name>_tmp` script property. Binding a lambda breaks the instance, and a name collision silently returns the wrong value. The same mechanism very likely causes the Kotlin 2.4.20 hold, and a verified alternative exists.
- **Regressions and gaps from the September fixes (13 items).** The most important:
  - [RV-074](#rv-074): the CR-116 fix makes every default gRPC channel disable retry, including transparent retry.
  - [RV-049](#rv-049): the CR-086 fix makes the Kotlin expression pools about 8× slower than a lone evaluator.
- **Ktor-side servlet contract gaps.** On the Ktor side:
  - HEAD responses carry a body ([RV-023](#rv-023)).
  - A blank admin path serves a thread dump at `/` ([RV-028](#rv-028)).
  - A failed start leaks a shutdown hook ([RV-029](#rv-029)).

  The Jetty equivalents behave correctly.

## How to track progress

- IDs (`RV-001` … `RV-093`) are stable. **Never renumber.** If an item is dropped, close it as won't-fix instead of deleting it.
- Status lives only in the checklist below:
  - `- [ ]` open
  - `- [x]` done, with `— fixed in #NNN` or a commit SHA appended
  - `- [x]` won't fix, with `— won't fix: <reason>` appended
  - `- [ ]` in progress, with `— in progress (#NNN)` appended
- [Details](#details) holds the evidence and the suggested fix, and carries no status.
- Items marked *(PLAUSIBLE)* were traced in code but not reproduced; confirm them before fixing.
- Quick counts:

  ```bash
  grep -c '^- \[x\] \*\*RV-' docs/CODE_REVIEW_2026-09-24.md   # closed
  grep -c '^- \[ \] \*\*RV-' docs/CODE_REVIEW_2026-09-24.md   # open
  grep    '^- \[ \] \*\*RV-.*`HIGH`' docs/CODE_REVIEW_2026-09-24.md   # open high-severity
  ```

---

## Issues

### core-utils
- [x] **RV-001** `HIGH` · security — `toObjectSecure` filter limits still let a tiny payload exhaust memory or CPU (follows CR-001) ([details](#rv-001)) — fixed in 0a797a4
- [x] **RV-002** `LOW` · security — `maskUrlCredentials` masks only the first URL in a string ([details](#rv-002)) — fixed in 6b09d26
- [x] **RV-003** `LOW` · security — `ContentRoot.file(path)` has no containment; any path containing `://` is fetched as a URL ([details](#rv-003)) — fixed in 6b09d26
- [x] **RV-004** `LOW` · bug — `criticalSection` clears the flag while another section is still running ([details](#rv-004)) — fixed in 6b09d26
- [x] **RV-005** `LOW` · bug — `join`/`toPath`/`pathOf` can double or keep separators, contrary to their KDoc ([details](#rv-005)) — fixed in 6b09d26
- [x] **RV-006** `LOW` · bug — `lpad` and `Duration.format` edge cases: `(-5).lpad(0)` throws, `-INFINITE` is garbled ([details](#rv-006)) — fixed in 6b09d26
- [x] **RV-007** `LOW` · bug — `waitForPortAvailable` can report a busy port as free on macOS, and sleeps after the last attempt *(PLAUSIBLE)* ([details](#rv-007)) — fixed in 6b09d26
- [x] **RV-008** `LOW` · docs — `toUTCDateTime` is billed as an inclusive end-of-day bound but returns 23:59:00.001 ([details](#rv-008)) — fixed in 6b09d26
- [x] **RV-009** `LOW` · API — `UrlSource` timeouts can't be set for repo-based sources or from Java ([details](#rv-009)) — fixed in 6b09d26
- [x] **RV-010** `LOW` · cleanup — `SingleAssignVar` duplicates `singleSetReference` and is JVM-only ([details](#rv-010)) — fixed in 6b09d26
- [x] **RV-011** `LOW` · docs — core-utils README: an import that doesn't compile, a date example that mixes zones, an inconsistent `@Version` example ([details](#rv-011)) — fixed in 6b09d26
- [x] **RV-012** `LOW` · build — core-utils exports JVM-only dependencies from `commonMain`; unused serialization plugin ([details](#rv-012)) — fixed in 7c315a9
- [x] **RV-013** `LOW` · tests — `ReadResourcesTests` picks a port with `ServerSocket(0)` and can stall for ~3 minutes ([details](#rv-013)) — fixed in 6b09d26

### ktor-client-utils
- [x] **RV-014** `LOW` · API — `blockingGet(url, setUp) { … }` no longer compiles, and the CHANGELOG doesn't say so (follows CR-037) ([details](#rv-014)) — fixed in 6b09d26
- [x] **RV-015** `LOW` · docs — README says the `get` member extension can't be imported ([details](#rv-015)) — fixed in 6b09d26

### json-utils
- [x] **RV-016** `MEDIUM` · bug — `reformatJson`/`toJsonElement` accept invalid JSON and silently rewrite bare tokens ([details](#rv-016)) — fixed in 38785ba
- [x] **RV-017** `LOW` · API — `String.toJsonElement()` shadows the generic `T.toJsonElement()` (the CR-026 trap); misplaced KDoc ([details](#rv-017)) — fixed in 38785ba
- [x] **RV-018** `LOW` · bug — `intValue`/`intValueOrNull` accept `007`, `+5` and non-ASCII digits that `isNumber` rejects ([details](#rv-018)) — fixed in 38785ba
- [x] **RV-019** `LOW` · security — Missing-key errors serialize the whole document and put payload text in the message ([details](#rv-019)) — fixed in 38785ba
- [x] **RV-020** `LOW` · API — No `Long` accessor ([details](#rv-020)) — fixed in 38785ba
- [x] **RV-021** `LOW` · docs — `toFormattedString(indent)` throws for non-whitespace indents without documenting it, and builds a new `Json` per call ([details](#rv-021)) — fixed in 38785ba
- [x] **RV-022** `LOW` · tests — `isEmpty`/`size` tests call kotlinx members, not the library's extensions ([details](#rv-022)) — fixed in 38785ba

### ktor-server-utils
- [x] **RV-023** `MEDIUM` · bug — HEAD requests through `Route.servlet` get a body, corrupting keep-alive connections ([details](#rv-023)) — fixed in 086525a
- [x] **RV-024** `LOW` · bug — `KtorServletResponse` throws NPE on `null` arguments the Servlet spec defines ([details](#rv-024)) — fixed in 086525a
- [x] **RV-025** `LOW` · bug — The bridge throws `UnsupportedOperationException` for simple methods, so TRACE, `getLastModified` and `setContentLength` return 500 ([details](#rv-025)) — fixed in 086525a
- [x] **RV-026** `LOW` · bug — `HerokuHttpsRedirect` reorders and lower-cases the query string ([details](#rv-026)) — fixed in 086525a
- [x] **RV-027** `LOW` · API — Servlet adapters export `Nothing`/`Void` members, and the body `KtorServletResponse` holds can't be read ([details](#rv-027)) — fixed in 02d4408

### service-utils
- [x] **RV-028** `MEDIUM` · security — A blank Ktor admin path serves that endpoint (e.g. the thread dump) at `/` instead of disabling it ([details](#rv-028)) — fixed in f24cb8c
- [x] **RV-029** `MEDIUM` · leak — A failed `KtorServletService` start leaks Ktor's shutdown hook and never destroys the servlets (follows CR-046) ([details](#rv-029)) — fixed in 44a3287
- [x] **RV-030** `LOW` · bug — `"ping"` and `"/ping"` collide: Jetty fails to start, Ktor ignores the override ([details](#rv-030)) — fixed in f24cb8c
- [x] **RV-031** `LOW` · security — The Jetty admin and metrics servers disclose the Jetty version ([details](#rv-031)) — fixed in f24cb8c
- [x] **RV-032** `LOW` · bug — `initMetricsAndHealthChecks()` has no double-init guard (follows CR-051) ([details](#rv-032)) — fixed in 44a3287
- [x] **RV-033** `LOW` · API — Lifecycle-critical service properties have public setters ([details](#rv-033)) — fixed in 02d4408
- [x] **RV-034** `LOW` · docs — README example imports `Compression`, which consumers don't get; call-logging and compression ship unused ([details](#rv-034)) — README fixed in 9f225de, bundle entries dropped in 7c315a9
- [x] **RV-035** `LOW` · tests — Self-referential and duplicated config specs ([details](#rv-035)) — fixed in 44a3287

### prometheus-utils
- [x] **RV-036** `LOW` · API — A labelled `SamplerGaugeCollector` can expose only one series, and one Java constructor always throws ([details](#rv-036)) — fixed in 48c5575
- [x] **RV-037** `LOW` · bug — `SamplerGaugeCollector` never validates metric or label names ([details](#rv-037)) — fixed in 48c5575
- [x] **RV-038** `LOW` · docs — `SystemMetrics`'s "duplicates are skipped" is false for a `CollectorRegistry()` ([details](#rv-038)) — fixed in 48c5575
- [x] **RV-039** `LOW` · docs — `InstrumentedThreadFactory` docs give the wrong series names ([details](#rv-039)) — fixed in 48c5575
- [x] **RV-040** `LOW` · tests — Tests leak ~20 collectors, one of them a throwing sampler, into the default registry ([details](#rv-040)) — fixed in 48c5575

### jetty-utils
- [x] **RV-041** `LOW` · docs — README says non-GET methods return 405, but HEAD runs the lambda and OPTIONS/TRACE return 200 ([details](#rv-041)) — fixed in 48c5575
- [x] **RV-042** `LOW` · bug — `LambdaServlet` appends a platform line separator, which the tests `.trim()` away ([details](#rv-042)) — fixed in 48c5575
- [x] **RV-043** `LOW` · API — `JettyDsl.server(port)` can't set a bind address ([details](#rv-043)) — fixed in 48c5575

### guava-utils
- [x] **RV-044** `MEDIUM` · concurrency — `GenericValueWaiter` waits forever on a zero or negative timeout under an immediate dispatcher ([details](#rv-044)) — fixed in 893bcb1
- [x] **RV-045** `LOW` · API — A negative `maxWait` means "forever", but a negative `waitTime` means "check once" ([details](#rv-045)) — fixed in 034678e
- [x] **RV-046** `LOW` · API — `@Throws` on the `Duration` overloads is invisible to Java (follows CR-069) ([details](#rv-046)) — fixed in 034678e
- [x] **RV-047** `LOW` · docs — The README's "Choosing a Waiting Primitive" table is stale ([details](#rv-047)) — fixed in 034678e

### script-utils (common / java / kotlin / python)
- [x] **RV-048** `HIGH` · bug — `KotlinScript`'s per-variable `_tmp` bindings break evals (lambdas, name collisions) and very likely cause the Kotlin 2.4.20 hold ([details](#rv-048)) — fixed in 91f8fc0
- [x] **RV-049** `MEDIUM` · performance — Kotlin expression pools are ~8× slower than a lone evaluator (follows CR-086) ([details](#rv-049)) — fixed in 30370a7
- [x] **RV-050** `MEDIUM` · bug — `JavaScript.evalScript` leaves extra public fields in the bindings, and every later eval fails ([details](#rv-050)) — fixed in 154ebf4
- [x] **RV-051** `MEDIUM` · bug — `accessibleClass` ignores module exports, so a `Charset` is declared as `sun.nio.cs.UTF_8` (follows CR-084/CR-095) ([details](#rv-051)) — fixed in 154ebf4
- [x] **RV-052** `MEDIUM` · bug — The declared type is the first public supertype (a comparator becomes `Enum`); star projections are unusable; callers can't override ([details](#rv-052)) — fixed in 154ebf4
- [x] **RV-053** `LOW` · bug — `JavaScript.import` emits binary names for nested classes, and `import` can't be called from Java ([details](#rv-053)) — fixed in 154ebf4
- [x] **RV-054** `LOW` · docs — The `Isolated` classloader README is wrong, and `NoClassDefFoundError` escapes unwrapped ([details](#rv-054)) — fixed in 154ebf4
- [x] **RV-055** `LOW` · docs — The `JavaScript` null-global-context KDoc is wrong, and passing `true` causes an NPE ([details](#rv-055)) — fixed in 154ebf4
- [x] **RV-056** `LOW` · concurrency — The thread-safety claim doesn't cover the unsynchronized public readers *(PLAUSIBLE)* ([details](#rv-056)) — fixed in 154ebf4
- [x] **RV-057** `LOW` · bug — The shared `ScriptEngineManager` keeps the first caller's context classloader *(PLAUSIBLE)* ([details](#rv-057)) — fixed in 154ebf4
- [x] **RV-058** `LOW` · bug — `withInstance` loses the block's exception when `reset` also throws ([details](#rv-058)) — fixed in 154ebf4
- [x] **RV-059** `LOW` · API — Mutable internals (`valueMap`, `channel`) are exposed as `protected` ([details](#rv-059)) — fixed in 02d4408

### email-utils
- [x] **RV-060** `MEDIUM` · security — `isValidEmail` throws `StackOverflowError` on a ~4 KB input, and has no length cap ([details](#rv-060)) — fixed in c39565e
- [x] **RV-061** `MEDIUM` · leak — `ResendService.sendEmail` builds a new OkHttp client for every email ([details](#rv-061)) — fixed in c39565e
- [x] **RV-062** `LOW` · docs — Network failures surface as `RuntimeException`, not the documented `ResendException` ([details](#rv-062)) — fixed in c39565e
- [x] **RV-063** `LOW` · bug — `ResendWebhookMsg.decode` likely fails on non-email events *(PLAUSIBLE)* ([details](#rv-063)) — documented as email.*-only in 1cb8147 (Resend unreachable, so the model is unchanged)
- [x] **RV-064** `LOW` · docs — A README snippet needs `-Xcollection-literals`, and an example logs the IP address ([details](#rv-064)) — fixed in c39565e

### recaptcha-utils
- [x] **RV-065** `LOW` · API — The process-wide client can't be reopened after the `close()` the README recommends *(PLAUSIBLE)* ([details](#rv-065)) — fixed in 1cb8147

### redis-utils
- [x] **RV-066** `MEDIUM` · concurrency — The `suspend` helpers do blocking Jedis I/O on the caller's dispatcher ([details](#rv-066)) — fixed in 8311321
- [x] **RV-067** `LOW` · bug — Pool idle ordering is still not validated (follows CR-112) ([details](#rv-067)) — fixed in 8311321
- [x] **RV-068** `LOW` · API — `scanKeys` on `UnifiedJedis` fails on cluster clients ([details](#rv-068)) — fixed in 8311321
- [x] **RV-069** `LOW` · diagnostics — Failures are logged without their cause, and underscore hosts are reported as "no host" ([details](#rv-069)) — fixed in 8311321

### exposed-utils
- [x] **RV-070** `MEDIUM` · bug — `upsert(conflictIndex)` always throws on MySQL/MariaDB; README says it works everywhere ([details](#rv-070)) — fixed in 919d410
- [x] **RV-071** `MEDIUM` · bug — Functional and partial unique indexes pass validation; a functional index silently upserts on a different key (follows TC-069) ([details](#rv-071)) — fixed in 919d410
- [x] **RV-072** `LOW` · docs — `readonlyTx` and `timed*` don't enforce read-only or isolation when nested ([details](#rv-072)) — fixed in 919d410
- [x] **RV-073** `LOW` · security — `KotlinSqlLogger` logs bound parameter values at INFO ([details](#rv-073)) — fixed in 919d410

### grpc-utils
- [x] **RV-074** `MEDIUM` · bug — The default channel now disables all retry, including transparent retry (regression from CR-116) ([details](#rv-074)) — fixed in 9680445
- [x] **RV-075** `LOW` · API — `GrpcDsl.server` can't bind an address, and its defaults can never work ([details](#rv-075)) — fixed in 1cb8147
- [x] **RV-076** `LOW` · build — Unused grpc-protobuf and grpc-services are shipped to every consumer ([details](#rv-076)) — fixed in 7c315a9
- [x] **RV-077** `LOW` · docs — README/KDoc drift: `tlsContext` "required", `shutdown()` "throws", client mutual auth ([details](#rv-077)) — fixed in 1cb8147

### Cross-module API surface
- [x] **RV-078** `LOW` · API — Public types and empty companions that should be internal or private ([details](#rv-078)) — fixed in 02d4408

### Build & publishing
- [x] **RV-079** `LOW` · build — Five modules export core-utils as `api` but use little or none of it ([details](#rv-079)) — fixed in 7c315a9
- [x] **RV-080** `LOW` · publishing — ktor-server-utils' `compileOnlyApi` servlet API is published at Maven `compile` scope ([details](#rv-080)) — fixed in 96aa00d
- [x] **RV-081** `LOW` · deps — Dependabot's held Kotlin group also takes `kotlin-reflect`, so CR-126 can recur ([details](#rv-081)) — fixed in 31d5a7a (kotlin-reflect rides the unified `kotlin` version again)
- [x] **RV-082** `LOW` · build — `make coverage-clean` doesn't clear the KMP `jvmTest` results (follows CR-129) *(PLAUSIBLE)* ([details](#rv-082)) — fixed in 96aa00d
- [x] **RV-083** `LOW` · build — The disabled watchOS/tvOS test tasks still link their test binaries *(PLAUSIBLE)* ([details](#rv-083)) — fixed in 96aa00d
- [x] **RV-084** `LOW` · publishing — No BOM for 19 co-versioned artifacts ([details](#rv-084)) — fixed in d728e24
- [x] **RV-085** `LOW` · cleanup — Detekt's `VariableNaming` excludes miss `*Tests.kt` ([details](#rv-085)) — fixed in 96aa00d

### CI & supply chain
- [x] **RV-086** `MEDIUM` · security — The wrapper scripts are marked `binary`, which hides their diffs, and the distribution has no checksum ([details](#rv-086)) — fixed in a700d43
- [x] **RV-087** `LOW` · ci — The Linux job has no Kotlin/Native cache, and the native caches have no restore keys ([details](#rv-087)) — fixed in 96aa00d
- [x] **RV-088** `LOW` · ci — CI never exercises publishing ([details](#rv-088)) — fixed in 96aa00d
- [x] **RV-089** `LOW` · security — Actions are still pinned by mutable tags, and no job has a timeout (follows CR-135) ([details](#rv-089)) — fixed in a700d43

### Project documentation
- [x] **RV-090** `LOW` · docs — llms.txt and the README overstate dropwizard-utils and zipkin-utils (follows CR-136) ([details](#rv-090)) — fixed in 9f225de
- [x] **RV-091** `LOW` · docs — The build script, codecov.yml and CLAUDE.md give different coverage figures ([details](#rv-091)) — fixed in 9f225de
- [x] **RV-092** `LOW` · docs — CLAUDE.md drift (module count, `dsl` package count, compat rationale), and no versioning policy ([details](#rv-092)) — fixed in 9f225de
- [x] **RV-093** `LOW` · docs — "Adding New Modules" is incomplete, and the CHANGELOG history has gaps ([details](#rv-093)) — fixed in 9f225de

---

## Plan: order of fixes

Rules used to order the work:
1. Input an outsider controls comes first.
2. Next, anything silently wrong: wrong results, exposed endpoints, regressions shipped in 4.1.0.
3. Then failures that are loud but real, and leaks.
4. Docs and cleanup follow.
5. Anything that changes the published ABI or the transitive dependencies waits for the next major.

Each step is sized as one PR, in the per-module style of #174–#185, and each PR carries the regression tests its items call for.

### Phase 1 — Security and exposure (patch release, 4.1.1)

| Step | Items                                      | Why now                                                                                                                                                                                                                                                              |
|------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | RV-001                                     | A DoS in the one function the README recommends for untrusted input. Only the filter string, a constant and tests change.                                                                                                                                           |
| 2    | RV-060, RV-061, RV-062, RV-064             | The validator crashes on user-supplied form input, and every email leaks a client. Both are one-line fixes plus tests. The two doc items are in the same module and the same README.                                                                                                        |
| 3    | RV-030 → RV-028, RV-031                    | Normalizing admin paths inside `addServlet`, after its blank check (RV-030), also fixes the thread dump served at `/` (RV-028). Hide the Jetty version in the same PR.                                                                                                                   |
| 4    | RV-086, RV-089                             | Config-only supply-chain hardening. It touches no code, so it can land in parallel with steps 1–3.                                                                                                                                                                             |

### Phase 2 — Silent wrong behaviour and 4.1.0 regressions (4.1.1 or 4.2.0)

| Step | Items                                      | Why now                                                                                                                                                                                                                                                              |
|------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 5    | RV-074                                     | A behaviour change shipped in 4.1.0 that every default gRPC channel picks up. Restore grpc's default (`enableRetry: Boolean? = null`) before more consumers upgrade.                                                                                                                         |
| 6    | RV-023, RV-024, RV-025, RV-026             | The servlet bridge contract. RV-023 corrupts keep-alive connections on the Ktor admin endpoints, which is why it follows step 3.                                                                                                                                               |
| 7    | RV-029, RV-032, RV-035                     | service-utils lifecycle: clean up after a failed Ktor start, guard against double init, and replace the self-referential specs with the regression tests from steps 3 and 6.                                                                                                              |
| 8    | RV-070, RV-071, RV-072, RV-073             | `upsert(conflictIndex)` either fails on MySQL or silently targets the wrong key. The SQL-logger level change is a one-liner in the same module.                                                                                                                                      |
| 9    | RV-044                                     | A lost timeout that turns into an unbounded wait. The fix is small: register the waiter before arming the timeout, or return early when the timeout is not positive.                                                                                                                                       |
| 10   | RV-016, RV-018, RV-020, RV-017, RV-019, RV-021, RV-022 | json-utils strictness. RV-018 and RV-020 share the integer regex. RV-017 needs a deprecation shim, as CR-026 had.                                                                                                                                                                    |
| 11   | RV-066, RV-067, RV-068, RV-069             | redis-utils: move blocking I/O off the caller's dispatcher, and tidy up the pool validation and diagnostics.                                                                                                                                                                              |

### Phase 3 — script-utils rework (4.2.0)

| Step | Items                                      | Why now                                                                                                                                                                                                                                                              |
|------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 12   | RV-048                                     | The highest-leverage fix in the review. Replace the per-variable `_tmp` bindings with a single non-generic holder, then re-run `:script-utils-kotlin:test` on Kotlin 2.4.20. If it passes, lift the Kotlin hold in the same or a follow-up PR: remove the catalog comment, the four Dependabot Kotlin entries, and the CLAUDE.md hold sections. That also settles part of RV-081 and RV-092. |
| 13   | RV-049                                     | The reset policy depends on what RV-048 leaves in the engine scope, so it comes after step 12. Reset every N borrows instead of on every return.                                                                                                                                                                     |
| 14   | RV-050, RV-051, RV-052, RV-053, RV-054, RV-055, RV-056, RV-057, RV-058 | Declaration and import generation plus pool diagnostics. RV-051 and RV-052 both change `accessibleClass`, so do them together.                                                                                                                                                      |

### Phase 4 — Remaining low items (4.2.0)

| Step | Items                                      | Notes                                                                                                                                                                                                                                                                |
|------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 15   | RV-002 – RV-011, RV-013, RV-014, RV-015    | core-utils and ktor-client-utils. RV-014 adds a visible `@Deprecated` overload, which is additive.                                                                                                                                                                                                  |
| 16   | RV-045, RV-046, RV-047                     | guava-utils. RV-046's Java-facing overloads are additive; changing the negative-`maxWait` semantics (RV-045) needs a deprecation cycle first.                                                                                                                                       |
| 17   | RV-036 – RV-043                            | prometheus-utils and jetty-utils. Add the `host` parameter (RV-043) first, then use it in service-utils' `JettyServer` and the jetty test helper.                                                                                                                                     |
| 18   | RV-063, RV-065, RV-075, RV-077             | The remaining email, recaptcha and grpc items. Confirm RV-063 against Resend's current event list before changing anything.                                                                                                                                                                             |
| 19   | RV-080, RV-081, RV-082, RV-083, RV-085, RV-087, RV-088 | Build and CI. RV-081 may already be settled by step 12. Do RV-088 early if a release is imminent.                                                                                                                                                                        |
| 20   | RV-034, RV-090, RV-091, RV-092, RV-093     | Docs. RV-092's versioning policy decides whether Phase 5 needs a major bump or can go into a minor.                                                                                                                                                                  |

### Phase 5 — Next major (5.0.0): ABI and dependency-surface changes

| Step | Items                                      | Notes                                                                                                                                                                                                                                                                |
|------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 21   | RV-078, RV-033, RV-059, RV-027             | These make things `internal`/`private`, use `private set`, and return `: Unit` instead of `Nothing`. Each needs `make abi-update` on macOS, with the changed dumps committed alongside.                                                                                                                                            |
| 22   | RV-012, RV-079, RV-076                     | These narrow the transitive dependencies, so consumers that use them without declaring them will break. json-utils and recaptcha-utils must declare kotlinx-serialization explicitly first. Record each change in the CHANGELOG.                                                                              |
| 23   | RV-084                                     | Add a BOM. This is additive, but it fits naturally with the major, since it gives consumers one line to align every module after the scope changes.                                                                                                                                                 |

**Dependencies between items:**
- RV-030 fixes RV-028.
- RV-048 comes before RV-049, and RV-048 may settle part of RV-081 and RV-092.
- RV-043 comes before the service-utils `JettyServer` cleanup.
- RV-018 and RV-020 share the integer regex.
- RV-012 comes before RV-079: both edit the core-utils export list.
- RV-092's versioning policy decides the release target for Phase 5.

---

## Details

### core-utils

<a id="rv-001"></a>
#### RV-001 — `toObjectSecure` filter limits still let a tiny payload exhaust memory or CPU
**Severity:** High · **Category:** security · **Verdict:** confirmed (payloads run) · **Follows up:** CR-001
**Where:**
- `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/IOExtensions.kt:117,188-190` (filter string and constants)
- KDoc `:66-72`
- `core-utils/README.md:242-244`
- `core-utils/src/jvmTest/kotlin/com/pambrose/util/IOExtensionsTests.kt:174-192`

The JEP 290 filter added for CR-001 is `maxdepth=32;maxarray=10485760`. The array bound reuses the 10 MB *byte* cap as an *element* count. A declared array length is allocated before any element is read, so the bound doesn't limit memory:
- **`long[]`:** a 35-byte payload with the allow-list `{long[]}` allocates 80 MB. It threw `OutOfMemoryError` at `-Xmx48m`.
- **`ArrayList`:** a 62-byte payload with the allow-list `{ArrayList, String}` makes `ArrayList.readObject` allocate `Object[10485760]`, about 40 MB.
- **Nested `HashSet`:** the hashCode bomb, with only `HashSet` allow-listed, fits under depth 32. Depth 30 (1.7 KB) took 62 s, and each extra level doubles the time.

The existing tests probe only 16M elements and depth 100, so neither vector is covered.

**Fix:**
- Set `maxarray` to the input size. Every element costs at least one stream byte, so this bound is sound.
- Lower `maxdepth` to about 16–20, or expose the limits as parameters with safe defaults, and add `maxrefs`.
- Document that hash-based collections are risky to allow-list for untrusted input.
- Add the three payloads above as regression tests.

<a id="rv-002"></a>
#### RV-002 — `maskUrlCredentials` masks only the first URL in a string
**Severity:** Low · **Category:** security · **Verdict:** confirmed
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:381-389`; `core-utils/README.md:436`

Only the first `://` is examined, so later URLs keep their credentials:
- `"from https://a:b@h1 to https://c:d@h2"` becomes `"from https://*****:*****@h1 to https://c:d@h2"`.
- If any other `://` comes first, such as a log prefix or a `redirect=` parameter, the real credentials are left unmasked.

The README pitches the helper for logging, which is where multi-URL strings turn up.

**Fix:** Loop over every `://` and mask each authority, or document that it handles a single URL only.

<a id="rv-003"></a>
#### RV-003 — `ContentRoot.file(path)` has no containment; any path containing `://` is fetched as a URL
**Severity:** Low · **Category:** security / API · **Verdict:** confirmed (by trace)
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:36-46,60,102`

- `FileSystemSource.file("../../etc/passwd")` and absolute paths both escape the root.
- `AbstractRepo.file` treats any path that *contains* `://` as a full URL:
  - `GitHubRepo(...).file("file:///etc/passwd").content` reads a local file.
  - `http://169.254.169.254/...` reaches internal hosts.
  - A legitimate relative path with `://` in its query string throws "URI is not absolute".

The pass-through for absolute paths and URLs is documented. But callers that forward user-supplied paths have no way to turn it off.

**Fix:**
- Treat a path as a URL only when it starts with a scheme (`^[a-zA-Z][a-zA-Z0-9+.-]*://`).
- Offer a strict mode that rejects `..`, absolute paths, and foreign schemes or hosts.
- Mention the risk in the KDoc.

<a id="rv-004"></a>
#### RV-004 — `criticalSection` clears the flag while another section is still running
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (by trace)
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/AtomicUtils.kt:23-39`; `core-utils/README.md:170-172`

The KDoc says the block runs "while this AtomicBoolean is set to true". But `store(false)` runs in `finally` whether or not anyone else is still inside:
- **Nested:** in `flag.criticalSection { flag.criticalSection {}; flag.load() }`, the outer block reads `false`.
- **Concurrent:** when thread B leaves, the flag is cleared while thread A is still inside.

**Fix:** Back the flag with a counter (increment on entry, decrement on exit, flag = count > 0). Otherwise, restore the previous value and document that overlapping use is unsupported.

<a id="rv-005"></a>
#### RV-005 — `join`/`toPath`/`pathOf` can double or keep separators
**Severity:** Low · **Category:** bug / docs · **Verdict:** confirmed (probe)
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:104-112,131-132,144-148,367`; `core-utils/README.md:72-73`

The code has two faults:
- Empty elements are filtered out *before* `removePrefix(separator)`, so an element made only of a separator becomes empty after stripping, and still gets a separator appended.
- Only one leading separator is removed.

Results:
- `pathOf("a", "/", "b")` returns `"a//b"`, and `["a", "//b"].join()` returns `"a//b"`. Both contradict "never produce doubled separators".
- `join` is documented as producing no leading or trailing separators. Yet `["/a/","/b/","c"].join()` returns `"/a/b/c"`, a result pinned at `StringExtensionTests.kt:194`, and `["x","y/"].join()` returns `"x/y/"`.

**Fix:**
- Strip all leading separators, and filter empty elements *after* stripping.
- Reword the KDoc to "does not add" leading or trailing separators, or strip those too.

<a id="rv-006"></a>
#### RV-006 — `lpad` and `Duration.format` edge cases
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (probe)
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/MiscFuncs.kt:60-68`; `core-utils/src/jvmMain/kotlin/com/pambrose/common/time/Durations.kt:70-84`

- `(-5).lpad(0)` throws "Desired length -1 is less than zero", but `5.lpad(0)` returns `"5"`.
- `(-Duration.INFINITE).format()` returns `"--106751991167:-7:-12:-55"`, because `abs(Long.MIN_VALUE)` is still negative.
- A negative sub-millisecond duration formats as `"-0:00:00:00"`.

**Fix:**
- `lpad`: use `padStart((width - 1).coerceAtLeast(0))`.
- `format`: handle infinite durations explicitly, and take the sign from the truncated millisecond value.

<a id="rv-007"></a>
#### RV-007 — `waitForPortAvailable` can report a busy port as free on macOS
**Severity:** Low · **Category:** bug · **Verdict:** plausible (the macOS part was not run; the extra sleep is confirmed)
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/MiscFuncsJvm.kt:139-155`

- `ServerSocket(port)` binds the wildcard address with `SO_REUSEADDR=true`, which is the JDK 21 default. On BSD and macOS that bind succeeds even while another process listens on `127.0.0.1:port`, so the function returns `true` for a port that is still busy. CLAUDE.md describes the same macOS behaviour for the service-utils tests.
- The `catch` block sleeps `delayMs` even after the final failed attempt.

**Fix:**
- Add a `host` parameter and probe that address, or attempt a connect as well.
- Skip the sleep after the last attempt.

<a id="rv-008"></a>
#### RV-008 — `toUTCDateTime` is billed as an inclusive end-of-day bound
**Severity:** Low · **Category:** docs / API · **Verdict:** confirmed
**Where:** `core-utils/src/commonMain/kotlin/com/pambrose/common/util/DateUtils.kt:89-101`

`LocalDate(2024,3,15).toUTCDateTime()` returns `2024-03-15T23:59:00.001`. Used as an inclusive upper bound, it misses everything from 23:59:00.002 to 23:59:59.999, almost a full minute. The name also reads as a plain conversion rather than an end-of-day value.

**Fix:**
- Correct the KDoc.
- Add `endOfDayUtc()` or, better, an exclusive "start of next day" helper, and deprecate this function.

<a id="rv-009"></a>
#### RV-009 — `UrlSource` timeouts can't be set for repo-based sources or from Java
**Severity:** Low · **Category:** API · **Verdict:** confirmed
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:102,191-217,233-239`; `core-utils/api/core-utils.api` (the `UrlSource` constructors)

- `GitHubFile`, `GitLabFile` and `AbstractRepo.file()` all call `UrlSource(String)`. So the timeouts added for CR-013 stay at 10 s / 30 s for every repository source.
- The constructor that takes `Duration`s compiles only to a synthetic mangled constructor, so Java callers can't set timeouts at all.

**Fix:**
- Add timeout parameters to `GitHubFile`, `GitLabFile` and the repo classes.
- Add a Java-friendly factory that takes `java.time.Duration` or milliseconds.

<a id="rv-010"></a>
#### RV-010 — `SingleAssignVar` duplicates `singleSetReference` and is JVM-only
**Severity:** Low · **Category:** cleanup · **Verdict:** confirmed
**Where:** `core-utils/src/jvmMain/kotlin/com/pambrose/common/delegate/SingleAssignVar.kt:25-57`; `core-utils/src/commonMain/kotlin/com/pambrose/common/delegate/AtomicDelegates.kt:54-62,106-127`

Since the CR-008 fix, both use the same holder-plus-CAS logic and have the same semantics. `SingleAssignVar` uses only `kotlin.concurrent.atomics`, yet it sits in `jvmMain`, so non-JVM users can't reach it.

**Fix:** Move it to `commonMain` and delegate to `singleSetReference`, or deprecate it in favour of `singleSetReference`.

<a id="rv-011"></a>
#### RV-011 — core-utils README drift
**Severity:** Low · **Category:** docs · **Verdict:** confirmed (kotlinc)
**Where:** `core-utils/README.md:124,127,135,141,269-283,316-319`

- **Line 124:** `import com.pambrose.common.util.DateUtils.*` fails with "cannot import on demand from object 'DateUtils'".
- **Date example:**
  - It builds `now = localDateTimeNow()` in the system zone, then calls `now.toISO8601()`, which appends `Z` to a local time.
  - It also calls `now.age(TimeZone.UTC)`, which is off by the zone offset on any host not running in UTC.
- **`UrlSource(..., readTimeout = 5.seconds)` snippet (line 283):** it lacks the `seconds` import.
- **`@Version` example:** it annotates `version = "2.2.6"`, then comments that `version()` returns `"3.2.3"`. Its `buildTime` (1757260800000) is 2025-09-07, while `releaseDate` is "2026-09-07".

**Fix:**
- Use member imports.
- Build the date example with `localDateTimeNow(TimeZone.UTC)`.
- Add the missing import.
- Make the `@Version` values and comments agree.

<a id="rv-012"></a>
#### RV-012 — core-utils exports JVM-only dependencies from `commonMain`
**Severity:** Low · **Category:** build · **Verdict:** confirmed
**Where:** `core-utils/build.gradle.kts:3,16-17`; `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/Version.kt:26-27`

- `kotlinx-serialization-json` and `kotlin-logging` are `api` dependencies of `commonMain`, but only `jvmMain` uses them.
- Serialization appears in no public signature on any platform (see the `.api` and `.klib.api` dumps). Yet every JS, wasm and native consumer inherits both.
- The `kotlin.serialization` compiler plugin is applied, but the module has no `@Serializable` types.

**Fix:**
- Move `kotlin-logging` to `jvmMain` `api`, and make `kotlinx-serialization-json` a `jvmMain` `implementation`.
- Drop the serialization plugin.
- json-utils and recaptcha-utils get serialization through core-utils today, so declare it explicitly there first.
- This changes the published dependency surface, so it belongs in Phase 5.

<a id="rv-013"></a>
#### RV-013 — `ReadResourcesTests` picks a port with `ServerSocket(0)`
**Severity:** Low · **Category:** tests · **Verdict:** confirmed
**Where:** `core-utils/src/jvmTest/kotlin/com/pambrose/util/ReadResourcesTests.kt:54-56,89-90`

CLAUDE.md forbids this pattern because of `org.gradle.parallel=true`, but the TC-048 fix was applied only to service-utils. If another test JVM takes the port, the test sleeps through three 60 s retries (`maxAttempts = 3, delayMs = 60_000`) and fails after about 3 minutes.

**Fix:** Use a short `delayMs` with a retry-then-succeed design, or keep one owner for the port from bind through release.

### ktor-client-utils

<a id="rv-014"></a>
#### RV-014 — `blockingGet(url, setUp) { … }` no longer compiles
**Severity:** Low · **Category:** API · **Verdict:** confirmed (same overload shape reproduced)
**Follows up:** CR-037
**Where:** `ktor-client-utils/src/jvmMain/kotlin/com/pambrose/common/dsl/KtorDslJvm.kt:153-174`; `CHANGELOG.md:486-488`

The CR-037 fix put `httpClient` and `expectSuccess` before `setUp`, and made the old signature `HIDDEN`, so source resolution can't see it. A 3.x call like `KtorDsl.blockingGet(url, { header("a","b") }) { … }` now fails with "argument type mismatch … 'Client?' was expected".

The CHANGELOG mentions only the binary shim. The parameter order also no longer matches `HttpClient.get(url, setUp, block)`.

**Fix:** Add a visible `@Deprecated(WARNING)` overload `blockingGet(url, setUp, block)`, with no default for `setUp` and a distinct `@JvmName`, that forwards to the new function. Otherwise, record the source break in the CHANGELOG.

<a id="rv-015"></a>
#### RV-015 — README says the `get` member extension can't be imported
**Severity:** Low · **Category:** docs · **Verdict:** confirmed (probe)
**Where:** `ktor-client-utils/README.md:67-68,136`

The README says `get` "cannot be imported on its own" and "requires `with(KtorDsl)`". But member extensions of an `object` can be imported: `import com.pambrose.common.dsl.KtorDsl.get` compiles and works. core-utils documents the same kind of import for `DateUtils.toMMDDYY`.

**Fix:** Document the member import. Note that it can be ambiguous with `io.ktor.client.request.get` when both are imported and the call ends in a trailing lambda.

### json-utils

<a id="rv-016"></a>
#### RV-016 — `reformatJson`/`toJsonElement` accept invalid JSON and silently rewrite bare tokens
**Severity:** Medium · **Category:** bug / docs · **Verdict:** confirmed on the JVM; the per-platform divergence is plausible
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:320-327,376-382`

The KDoc says `@throws … if this string is not valid JSON`. But kotlinx's tree parser accepts any unquoted token as an unquoted `JsonLiteral`, even with `isLenient = false`. Re-encoding then rewrites those tokens:
- `"hello".reformatJson(false)` returns `"hello"` (quoted)
- `"[1, two, +3]"` returns `[1,"two",3]`
- `{"b": 007}` becomes `7`

So invalid input comes back as different, valid JSON instead of throwing. The encoder falls back to `toDoubleOrNull` for number-like tokens, and that differs by platform: JS accepts `0x10`, and the JVM accepts `1.5f`. So `[0x10]` probably becomes `["0x10"]` on the JVM but `[16]` on JS, which undercuts the cross-platform promise the `jsonNumber` screen was added for.

**Fix:**
- After parsing, walk the tree and throw `SerializationException` for any unquoted primitive that is not `true`, `false`, `null` or a `jsonNumber` match. Reuse the existing regex.
- If that is too strict, change the KDoc and README to say bare tokens are accepted and rewritten.

<a id="rv-017"></a>
#### RV-017 — `String.toJsonElement()` shadows the generic `T.toJsonElement()`
**Severity:** Low · **Category:** API · **Verdict:** confirmed · **Follows up:** CR-026
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:364,376`

This is the same overload trap CR-026 fixed for `toJsonString`:
- `"42".toJsonElement()` parses to the *number* 42.
- In a reified generic helper, `v.toJsonElement()` with `v = "42"` gives the *string* `"42"`.
- `"hello".toJsonElement()` neither throws nor gives a string: it returns the unquoted literal `hello`.
- The KDoc above the generic `T.toJsonElement()` says "Parses this [String] as JSON and re-encodes it as a pretty-printed JSON string". That text belongs to a different function.

**Fix:**
- Rename the parser, for example to `String.parseJson(verbose)`, and keep a `@Deprecated` shim, as CR-026 did.
- Fix the misplaced KDoc.
- Test both spellings.

<a id="rv-018"></a>
#### RV-018 — The int accessors accept text that `isNumber` and `doubleValue` reject
**Severity:** Low · **Category:** bug · **Verdict:** confirmed
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:54,189` (compare with `:64-67,88-90,115`)

Only the double path is screened by `jsonNumber`. The int path calls plain `toInt()` and `toIntOrNull()`:
- For `{"n": 007, "p": +5}`, `intValue("n")` returns 7 and `intValue("p")` returns 5.
- For the same values, `doubleValue` throws and `isNumber` is false.
- `"١٢"` (Arabic-Indic digits) returns 12.

The README's "JSON number syntax only" rule doesn't hold for ints.

**Fix:** Screen with `-?(?:0|[1-9][0-9]*)` before `toIntOrNull()`, and extend the "reject non-JSON numbers" spec to the int accessors.

<a id="rv-019"></a>
#### RV-019 — Missing-key errors serialize the whole document and put payload text in the message
**Severity:** Low · **Category:** security / performance · **Verdict:** confirmed
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:415-418`

`element()` builds `this.toString().take(100)` on every miss. That is O(size) work, and it puts the first 100 characters of the payload, which may include emails or tokens, into an exception message that is usually logged. This conflicts with `toJsonElement(verbose)`, whose KDoc warns against logging sensitive payloads. `"..."` is appended even when nothing was cut.

**Fix:** Report the missing key, the path so far, and a capped list of the available keys, but never the content.

<a id="rv-020"></a>
#### RV-020 — No `Long` accessor
**Severity:** Low · **Category:** API · **Verdict:** confirmed
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:54,183-189`; workaround in `json-utils/src/commonTest/kotlin/com/pambrose/json/JsonIntegrationTest.kt:156,262`

IDs and millisecond timestamps exceed `Int`, so `intValue` throws and `intValueOrNull` returns null. `doubleValue` loses precision above 2^53. The tests fall back to `stringValue(...).toLong()`, and TC-026 only documented the gap.

**Fix:** Add `longValue`, `longValue(vararg)` and `longValueOrNull(vararg)`, screened by the RV-018 regex.

<a id="rv-021"></a>
#### RV-021 — `toFormattedString(indent)` throws for most indents without saying so
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `json-utils/src/commonMain/kotlin/com/pambrose/common/json/JsonElementUtils.kt:300-318`

`toFormattedString("--")` throws `IllegalArgumentException: Only whitespace, tab, newline and carriage return are allowed as pretty print symbols`, but the KDoc says only "the indentation string to use". Every call with a non-default indent also builds a new `Json` instance.

**Fix:** Document the whitespace-only rule with `@throws`, and cache formatters by indent.

<a id="rv-022"></a>
#### RV-022 — `isEmpty`/`size` tests call kotlinx members, not the library's extensions
**Severity:** Low · **Category:** tests · **Verdict:** confirmed
**Where:**
- `json-utils/src/commonTest/kotlin/com/pambrose/json/BugFixVerificationTests.kt:50-68`
- `json-utils/src/commonTest/kotlin/com/pambrose/json/JsonElementUtilsTest.kt:309-313,333-334,480-481`

The receivers are statically typed `JsonArray` and `JsonObject`, which implement `List` and `Map`. A member always beats an extension, so these calls resolve to `List.isEmpty()`, `Map.isEmpty()` and `Map.size`. The "Bug #12" verification tests therefore can't fail if the extensions break.

**Fix:** Declare the values as `JsonElement`, for example `val emptyArray: JsonElement = JsonArray(...)`.

### ktor-server-utils

<a id="rv-023"></a>
#### RV-023 — HEAD requests through `Route.servlet` get a body
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed (live server)
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:71-93`

The route has no method selector, so HEAD requests reach `HttpServlet.service`. In Servlet 6.1, `doHead` simply calls `doGet` unless the `legacyDoHead` init parameter is set, and `KtorServletConfig` sets none. The spec leaves it to the container to drop the body, but Ktor doesn't: CIO and `BaseApplicationResponse` never check the method, and the `AutoHeadResponse` plugin that would is not installed.

A pipelined `HEAD /ping` then `GET /ping` to a `KtorServletService` returned `Content-Length: 5` followed by `pong\n`. The client then reads those bytes as the start of the GET response.

This affects the `GenericKtorService` admin endpoints. The Jetty variant is fine, because Jetty strips the body.

**Fix:** When `call.request.httpMethod == HttpMethod.Head`, send the status, headers and content type without a body: an `OutgoingContent.NoContent` whose `contentLength` is the body size. Add a raw-socket HEAD+GET test.

<a id="rv-024"></a>
#### RV-024 — `KtorServletResponse` throws NPE on `null` arguments the Servlet spec defines
**Severity:** Low · **Category:** bug · **Verdict:** confirmed
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:74-86,97,110`

Servlet 6.1 defines what these calls do with `null`:
- `setHeader(name, null)` removes the header.
- `addHeader(name, null)` does nothing.
- `setContentType(null)` and `setCharacterEncoding(null)` clear the value.

The Kotlin overrides declare non-null `String` parameters, so all four throw "Parameter specified as non-null is null", and the request fails with a 500.

**Fix:** Declare the parameters `String?` and implement the behaviour the spec defines. The JVM signature doesn't change.

<a id="rv-025"></a>
#### RV-025 — The bridge throws `UnsupportedOperationException` for methods with simple answers
**Severity:** Low · **Category:** bug / docs · **Verdict:** confirmed (live server)
**Where:**
- `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:198-228`
- `KtorServletRequest.kt:123-223`
- `KtorServletConfig.kt:190-206`
- `ktor-server-utils/README.md:384-395`

These requests return 500 instead of a response:
- **TRACE:** `HttpServlet.doTrace` calls `setContentLength`.
- **Any servlet that overrides `getLastModified`, on every GET:** `service()` calls `req.getDateHeader` and `resp.setDateHeader`.
- **A plain `resp.setContentLength(n)`:** even though a `Content-Length` header is accepted and then dropped.

These methods also throw although they have simple answers:
- **Response:** `flushBuffer`, `setIntHeader`, `setDateHeader`.
- **Request:**
  - `getCookies` (could return null)
  - `getRequestURL`
  - `isSecure`
  - `getCharacterEncoding`
  - `getDispatcherType`
  - `getServletContext` (a context exists)
- **Context:** `getClassLoader`.

The README also doesn't say that request bodies are unusable: `getInputStream` and `getReader` throw, and `getParameter` ignores form bodies.

**Fix:**
- Make `setContentLength*` a no-op, matching how a `Content-Length` header is treated.
- Implement the date and int headers, `flushBuffer` (as a commit), and the simple request getters.
- Document the request-body limitation.

<a id="rv-026"></a>
#### RV-026 — `HerokuHttpsRedirect` reorders and lower-cases the query string
**Severity:** Low · **Category:** bug / docs · **Verdict:** confirmed (live server)
**Where:** `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt:142-147`; `ktor-server-utils/README.md:351`

`call.url {}` rebuilds the URL from the decoded, case-insensitive `queryParameters`, grouped by name. So `?b=2&a=1&B=3&flag&sig=…` redirects to `?b=2&b=3&a=1&flag&sig=…`. That breaks signed or case-sensitive query strings. The README says the query string is kept.

**Fix:** Build the target from the raw request target: `https://` + host + optional port + `call.request.uri`.

<a id="rv-027"></a>
#### RV-027 — Servlet adapters export `Nothing`/`Void` members, and their body can't be read
**Severity:** Low · **Category:** API · **Verdict:** confirmed (ABI dump)
**Where:** `ktor-server-utils/api/ktor-server-utils.api`, which lists `login`, `logout`, `setCharacterEncoding`, `addCookie`, `set*Header`, `setContentLength`, `flushBuffer`, `reset` and others; `KtorServletResponse.kt:60`

- Members written as `= throw …` infer a `Nothing` return type. The ABI therefore exports `java.lang.Void`-returning methods plus synthetic bridges, and Kotlin callers see `Nothing`.
- `KtorServletResponse` is public, but `getBodyBytes()` is `internal`, so outside code can't read what a servlet wrote.

**Fix:**
- Declare `: Unit` explicitly.
- At the next major, either make the adapters `internal` or make the body accessor public.

### service-utils

<a id="rv-028"></a>
#### RV-028 — A blank Ktor admin path serves that endpoint at `/` instead of disabling it
**Severity:** Medium · **Category:** security / bug · **Verdict:** confirmed (live server)
**Where:**
- `service-utils/src/main/kotlin/com/pambrose/common/service/GenericKtorService.kt:82-87`
- `service-utils/src/main/kotlin/com/pambrose/common/service/HttpServletGroup.kt:49`
- `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt:102`
- `service-utils/README.md:251`

The README says blank paths are ignored. But the Ktor admin paths go through `ensureLeadingSlash()` *before* `HttpServletGroup.addServlet` checks for blank, and `"".ensureLeadingSlash()` is `"/"`, so the blank check never fires. The Jetty variant passes the raw path to `ServletGroup`, which drops it, and normalizes only later (`ServletService.kt:50`).

With `threadDumpPath = ""`, meant to turn thread dumps off:
- The Ktor admin server answered `GET /` with 200 and a full thread dump.
- The Jetty admin server answered 404.

If several paths are blank, the last one assigned, the thread dump, takes `/`.

**Fix:** Drop blank paths before normalizing. The simplest way is to normalize inside `addServlet`, after its blank check, which also fixes [RV-030](#rv-030). Add a blank-path test for both variants.

<a id="rv-029"></a>
#### RV-029 — A failed `KtorServletService` start leaks Ktor's shutdown hook and never destroys the servlets
**Severity:** Medium · **Category:** leak / lifecycle · **Verdict:** confirmed (occupied-port run)
**Follows up:** CR-046
**Where:**
- `service-utils/src/main/kotlin/com/pambrose/common/service/KtorServletService.kt:83-86`
- `AbstractGenericService.kt:208-211`
- `service-utils/README.md:329-331`

In Ktor 3.6, `EmbeddedServer.start()` works in this order:
1. It registers a `KtorShutdownHook` when the application starts.
2. It runs the modules, so `servlet.init` has already happened.
3. It calls `engine.start()`, which throws on a port conflict.

Only `stop()` raises `ApplicationStopping`, which is what removes the hook. Guava never calls `shutDown()` after a failed `startUp()`, and the CR-046 rollback only stops a servlet service that started successfully.

With an occupied port:
- The service ended FAILED.
- The servlets were initialized once and never destroyed.
- `KtorShutdownHook` stayed registered, even after `stopAsync()`.

That hook keeps the embedded server, its application, the servlets and the health-check registry alive until JVM exit, and anything `initKtor` launched keeps running. The failure also surfaces as a `JobCancellationException` wrapping the `BindException`. The Jetty variants clean up correctly.

**Fix:** In `startUp()`:

```kotlin
try {
  ktorServer.start(false)
} catch (t: Throwable) {
  runCatching { ktorServer.stop(0, 0) }.exceptionOrNull()?.let(t::addSuppressed)
  throw t
}
```

Extend the Ktor case of the failed-startup test (`GenericServiceTests.kt:434-450`) to assert that the servlets were destroyed and no Ktor hook remains.

<a id="rv-030"></a>
#### RV-030 — `"ping"` and `"/ping"` collide
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (live server)
**Where:** `ServletGroup.kt:40`, `HttpServletGroup.kt:49`, `ServletService.kt:50`; `service-utils/README.md:251,358`

The groups key servlets by the raw path and normalize it only when registering. So a `"ping"` from config and a `"/ping"` from `servletInit` are both registered:
- Jetty fails to start with "Multiple servlets map to path /ping".
- Ktor keeps the built-in servlet and ignores the override, because routing stops at the first handler.

The README says a later servlet at the same path replaces the earlier one.

**Fix:** Normalize inside `addServlet`, after the blank check. This also fixes [RV-028](#rv-028).

<a id="rv-031"></a>
#### RV-031 — The Jetty admin and metrics servers disclose the Jetty version
**Severity:** Low · **Category:** security · **Verdict:** confirmed (live server)
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/JettyServer.kt:28-39`

Every response carries `Server: Jetty(12.1.13)`, and 404 pages say "Powered by Jetty:// 12.1.13". These servers are unauthenticated and bind every interface by default.

**Fix:** Build the connector with an `HttpConfiguration` that has `sendServerVersion = false`, and install a minimal error handler.

<a id="rv-032"></a>
#### RV-032 — `initMetricsAndHealthChecks()` has no double-init guard
**Severity:** Low · **Category:** bug · **Verdict:** confirmed · **Follows up:** CR-051
**Where:** `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:130-182`

`checkNotInitialized()` is `internal`, and only the two concrete init methods call it. The class KDoc (lines 51-53) invites direct subclasses, as the test `FailingStopService` is. A second call:
- replaced `metricsService` and `zipkinReporterService`, orphaning an `OkHttpSender`;
- grew the services list from 3 to 6;
- then threw `A health check named thread_deadlock already exists`, leaving the object half re-initialized.

**Fix:** Call `checkNotInitialized()` at the top of `initMetricsAndHealthChecks()`.

<a id="rv-033"></a>
#### RV-033 — Lifecycle-critical service properties have public setters
**Severity:** Low · **Category:** API · **Verdict:** confirmed (ABI dump)
**Where:** `AbstractGenericService.kt:105,108,111`, `GenericService.kt:53`, `GenericKtorService.kt:56`; `service-utils.api` lists `setJmxReporter`, `setMetricsService`, `setZipkinReporterService` and `setServletService`

If one of these is reassigned after init, `startUp` and `shutDown` manage a different instance from the one the `ServiceManager` and the `metrics_service` health check hold.

**Fix:** Use `private set` at the next major, then update the ABI dumps.

<a id="rv-034"></a>
#### RV-034 — The README example imports `Compression`, which consumers don't get
**Severity:** Low · **Category:** docs / build · **Verdict:** confirmed
**Where:** `service-utils/README.md:130,145,285`; `service-utils/build.gradle.kts:17`; the `ktor-server-service` bundle in `gradle/libs.versions.toml`

- No main source uses `CallLogging` or `Compression`.
- Both are `implementation` dependencies, so consumers get them at runtime only.
- The `GenericKtorService` example imports `io.ktor.server.plugins.compression.Compression`, which isn't on a consumer's compile classpath.

**Fix:**
- Drop call-logging and compression from the bundle. Removing them changes runtime dependencies, so it belongs in Phase 5.
- Tell readers to add the dependency themselves, or change the example.

<a id="rv-035"></a>
#### RV-035 — Self-referential and duplicated config specs
**Severity:** Low · **Category:** tests · **Verdict:** confirmed
**Where:** `service-utils/src/test/kotlin/com/pambrose/common/service/ZipkinConfigTests.kt:40-50`; `ConfigTests.kt`

- "url can be constructed from config fields" builds the URL inside the test and calls no production code.
- `ConfigTests` repeats the data-class getter tests in `AdminConfigTests`, `MetricsConfigTests` and `ZipkinConfigTests`.
- Nothing tests HEAD, TRACE, blank admin paths, path-spelling collisions, or the Ktor admin server's cleanup after a failed start. Those gaps let RV-023, RV-028, RV-029 and RV-030 through.

**Fix:** Delete the self-referential and duplicate specs, and add the regression tests those items call for.

### prometheus-utils

<a id="rv-036"></a>
#### RV-036 — A labelled `SamplerGaugeCollector` can expose only one series
**Severity:** Low · **Category:** API / docs · **Verdict:** confirmed
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt:39-61`; `prometheus-utils/README.md:91-100`; `prometheus-utils/api/prometheus-utils.api:29`

- **One series per family:**
  - Each instance registers the family name through `describe()`.
  - `CollectorRegistry.register` rejects a second collector with the same name.
  - So a second collector for the README's `queue_depth`, with `labelValues = listOf("inbound")`, throws "already in use". Labels can only be constants on one series.
  - The README's "With labels" example suggests one collector per label value.
- **A Java constructor that always throws:** `@JvmOverloads` generates a public `(String, String, List labelNames, Function0)` constructor with `labelValues` defaulting to empty. It passes the size `require` only when `labelNames` is empty.

**Fix:**
- Document the one-series limit, or accept a `() -> Map<List<String>, Double>` sampler for multi-series families.
- Replace `@JvmOverloads` with explicit overloads so the broken constructor disappears.

<a id="rv-037"></a>
#### RV-037 — `SamplerGaugeCollector` never validates metric or label names
**Severity:** Low · **Category:** bug · **Verdict:** confirmed on the client side
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt:50-57,71`

`SimpleCollector` calls `checkMetricName` and `checkMetricLabelName`, but this custom collector doesn't. `CollectorRegistry.register` doesn't validate names either, and `TextFormat.write004` writes them verbatim.

So the following register cleanly and then emit an invalid exposition:
- `name = "queue-depth"`
- labels `["le"]`, `["a","a"]` or `["__x"]`

Prometheus then rejects the whole scrape, which is the outage the `collect()` guard at `:64-70` was added to prevent.

The comment at `:51-52` is also wrong: `MetricFamilySamples.Sample` doesn't validate sizes.

**Fix:**
- In `init`, call `checkMetricName(name)` and `labelNames.forEach(::checkMetricLabelName)`, and require distinct label names.
- Correct the comment.

<a id="rv-038"></a>
#### RV-038 — `SystemMetrics`'s "duplicates are skipped" is false for a `CollectorRegistry()`
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SystemMetrics.kt:45-49,79-90`; `prometheus-utils/README.md:27,128-131`

Duplicate detection doesn't work on a plain `CollectorRegistry()`:
- The hotspot exporters aren't `Describable`.
- `new CollectorRegistry()` has `autoDescribe = false`, so such a collector registers under no names at all.
- So after `DefaultExports.register(reg)`, calling `SystemMetrics.initialize(..., registry = reg)` adds a second copy of every exporter without any error, and the output has duplicate families.

The README recommends an isolated `CollectorRegistry()` for tests. Every `SystemMetricsTests` case uses `CollectorRegistry(true)`, which hides the difference.

**Fix:** Document that duplicate detection needs an auto-describing registry, and add a test that uses `CollectorRegistry()`.

<a id="rv-039"></a>
#### RV-039 — `InstrumentedThreadFactory` docs give the wrong series names
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `prometheus-utils/src/main/kotlin/com/pambrose/common/concurrent/InstrumentedThreadFactory.kt:33`; `prometheus-utils/README.md:106`

The KDoc says the series are `_threads_created` and `_threads_terminated`. They are counters, so the real series are:
- `X_threads_created_total` and `X_threads_terminated_total`
- `X_threads_created_created` and `X_threads_terminated_created` (creation timestamps)

A PromQL query for `X_threads_created` returns nothing. The README also calls `running` a counter, but it is a gauge.

**Fix:** List the actual series names, and call `running` a gauge.

<a id="rv-040"></a>
#### RV-040 — Tests leak collectors, including a throwing sampler, into the default registry
**Severity:** Low · **Category:** tests · **Verdict:** confirmed
**Where:**
- `prometheus-utils/src/test/kotlin/com/pambrose/common/metrics/SamplerGaugeCollectorTests.kt:32-118` (especially `:96-105`)
- `.../concurrent/InstrumentedThreadFactoryTests.kt:20-156`
- `.../dsl/PrometheusDslTests.kt:31-101`
- `.../metrics/SystemMetricsTests.kt:33-47`

About 20 collectors are registered on `CollectorRegistry.defaultRegistry` and never unregistered. `getSampleValue` collects every registered collector, so the leaked `test_sampler_gauge_throws` logs a WARN with a stack trace on every later default-registry read in the JVM. Running the tests twice in one JVM fails with "already in use". That rules out Kotest `invocations`, and adding the module to `mutationModuleNames`.

**Fix:** Use a fresh `CollectorRegistry()` per test. Keep one default-registry smoke test per API, and have it unregister its collector in `afterTest`.

### jetty-utils

<a id="rv-041"></a>
#### RV-041 — README says non-GET methods return 405
**Severity:** Low · **Category:** docs · **Verdict:** confirmed against the servlet-api 6.1.0 source
**Where:** `jetty-utils/README.md:21-22,152-154`; `jetty-utils/src/main/kotlin/com/pambrose/common/servlet/LambdaServlet.kt:49`

- `HttpServlet.doHead` calls `doGet`, so a HEAD health probe runs the lambda and its side effects.
- `doOptions` answers 200 with an `Allow` header, and `doTrace` echoes headers with 200.

Only POST, PUT, DELETE and PATCH get 405.

**Fix:** Correct the README, and say that HEAD invokes the lambda.

<a id="rv-042"></a>
#### RV-042 — `LambdaServlet` appends a platform line separator
**Severity:** Low · **Category:** bug / tests · **Verdict:** confirmed
**Where:** `jetty-utils/src/main/kotlin/com/pambrose/common/servlet/LambdaServlet.kt:63`; tests `LambdaServletTests.kt:38,47,56`, `ServletEncodingTests.kt:32,38,44`, `LambdaServletDoGetTests.kt`, `VersionServletTests.kt`

`writer.println(body)` writes `System.lineSeparator()`, so `VersionServlet("1.0.0")` serves `1.0.0\n` on Unix and `1.0.0\r\n` on Windows. Every test calls `.trim()`, so none of them can detect a change.

**Fix:** Use `print(body)`, or write an explicit `"\n"` and document it. Assert the exact body bytes in at least one real-Jetty test.

<a id="rv-043"></a>
#### RV-043 — `JettyDsl.server(port)` can't set a bind address
**Severity:** Low · **Category:** API · **Verdict:** confirmed
**Where:** `jetty-utils/src/main/kotlin/com/pambrose/common/dsl/JettyDsl.kt:34-37`; workarounds in `jetty-utils/src/test/kotlin/com/pambrose/common/servlet/JettyTestSupport.kt:42-43` and `service-utils/src/main/kotlin/com/pambrose/common/service/JettyServer.kt:28-45`

`Server(port)` always binds the wildcard address:
- After CR-053, service-utils stopped using `JettyDsl.server` and builds its own connector.
- The module's own test helper casts `connectors.single() as ServerConnector` to set the host.

**Fix:** Add `host: String? = null` to `server(...)` and configure the `ServerConnector` with it, then use it in both places.

### guava-utils

<a id="rv-044"></a>
#### RV-044 — `GenericValueWaiter` waits forever on a zero or negative timeout under an immediate dispatcher
**Severity:** Medium · **Category:** concurrency · **Verdict:** confirmed (reproduced)
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericValueWaiter.kt:130-138`

The timeout job is launched *before* `waiters += waiter` runs, while the reentrant lock is held. When a dispatcher doesn't need to dispatch, `launch` runs the job inline, and then:
1. `delay(ZERO)` or a negative delay returns without suspending.
2. `waiters.remove(waiter)` finds nothing, and the job ends.
3. Only then is the waiter registered, with no timeout left.

These all hung until a later `setValue(true)`:
- `Dispatchers.Unconfined` started `UNDISPATCHED`, with `waitUntilTrue(Duration.ZERO)`;
- the same with `(-5).milliseconds`;
- a custom "run inline when already on the UI thread" dispatcher.

On `Dispatchers.Default` the same call returns `false`. Android's `Main.immediate` is plausibly affected by the same mechanism. A computed `deadline - now` that has gone ≤ 0 therefore becomes an unbounded wait.

**Fix:**
- Register the waiter before launching the timeout job, still under the lock.
- Better, when the predicate fails and `!timeoutDuration.isPositive()`, `resume(false)` directly and launch nothing.
- Document zero and negative timeouts, and add a `Dispatchers.Unconfined` + `UNDISPATCHED` test.

<a id="rv-045"></a>
#### RV-045 — A negative `maxWait` means "forever", but a negative `waitTime` means "check once"
**Severity:** Low · **Category:** API · **Verdict:** confirmed
**Where:** `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericMonitor.kt:44-46,108,269`

`waitWithRetries` maps a negative `maxWait` to `Duration.INFINITE`, while `waitUntilTrue(waitTime)` treats a negative value as a single check. So `waitUntilTrue(timeout = 1.seconds, maxWait = deadline - now, block = null)` blocks forever once the deadline has passed. The behaviour is documented, as the sentinel CR-067 kept, but it is inconsistent within one class, and `INFINITE` already means "no limit".

**Fix:** Treat a negative `maxWait` like `ZERO`. That changes behaviour, so first deprecate the negative sentinel, for example by logging a warning.

<a id="rv-046"></a>
#### RV-046 — `@Throws` on the `Duration` overloads is invisible to Java
**Severity:** Low · **Category:** API / tests · **Verdict:** confirmed · **Follows up:** CR-069
**Where:**
- `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericIdleService.kt:38-39,51-52`
- `GenericExecutionThreadService.kt:38-39,51-52`
- `guava-utils/src/test/kotlin/com/pambrose/common/concurrent/GenericServicesTests.kt:164`
- `guava-utils/README.md:295-296`

`Duration` is a value class, so the JVM names are mangled: `guava-utils.api` lists `startSync-LRDsOJo(J)V`, which Java can't call. The test "declare TimeoutException for Java callers" therefore checks a method Java can't reach. The same applies to `@Throws(InterruptedException)` on the timed `waitUntilTrueWithInterruption` overloads.

**Fix:** Add Java-facing overloads taking `(long, TimeUnit)` or `java.time.Duration`, or drop the Java claim from the test name and the README.

<a id="rv-047"></a>
#### RV-047 — The README's "Choosing a Waiting Primitive" table is stale
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `guava-utils/README.md:345-351` (compare `:82-83`)

- The table sends "value set from non-suspending code" to `BooleanWaiter`, but since CR-072, `ConditionalValue.set` is non-suspending too, as line 82 says.
- It describes `ConditionalValue` as "value observed as a flow", but the flow is private.

**Fix:** Split the rows by the real differences:
- `ConditionalValue` checks only the latest value, and conflates updates.
- `GenericValueWaiter` takes a predicate per waiter, and routes predicate exceptions to that waiter.

### script-utils (common / java / kotlin / python)

<a id="rv-048"></a>
#### RV-048 — `KotlinScript`'s per-variable `_tmp` bindings break evals and very likely cause the Kotlin 2.4.20 hold
**Severity:** High · **Category:** bug / build · **Verdict:** confirmed (harness on kotlin-scripting 2.4.10 and 2.4.20)
**Where:** `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:45,54-57,75,78-81`

`bindVariables` puts every value into ENGINE_SCOPE as `<name>_tmp`, then evaluates `val <name> = bindings["<name>_tmp"] as T`. kotlin-scripting-jsr223 also turns *every* binding into a script property typed by the value's runtime class (`importAllBindings(true)` via `configureProvidedPropertiesFromJsr223Context`). The results:
- **Silent wrong value:** `add("a_tmp", 1); add("a", "s"); eval("a_tmp")` returns `"s"`.
- **Every eval fails once one of these is bound:**
  - A lambda, whose class is hidden: "Cannot access script provided property class 'K2$$Lambda$1/0x…'".
  - `String.CASE_INSENSITIVE_ORDER`: "…'kotlin.String.CaseInsensitiveComparator'".

  The failed binding stays in ENGINE_SCOPE, so the instance is broken until `resetContext`.
- **The name `bindings`:** `add("bindings", 1)` generates `val bindings = bindings[...]`, and every later eval fails with "Type checking has run into a recursive problem". `isReserved` reserves neither `bindings` nor `*_tmp`.
- **The version hold:** on 2.4.20, putting an `ArrayList` straight into the bindings fails with "One type argument expected for class ArrayList". That is exactly the regression CLAUDE.md gives as the reason for the hold.

A single non-generic holder in the bindings worked on both 2.4.10 and 2.4.20. The harness bound a `SimpleScriptContext` holding an `ArrayList`, a `Function1` lambda and `CASE_INSENSITIVE_ORDER`, and generated `val x = (bindings["holder"] as ScriptContext).getAttribute("x") as T`.

**Fix:**
- Bind one library-owned, public, non-generic holder under a reserved key, for example `class ScriptVariables(private val m: Map<String, Any>) { operator fun get(n: String) = m[n] }`.
- Generate `val x = <holderKey>["x"] as T`, and reserve the holder key in `isReserved`.
- Add tests for a `_tmp` collision, a lambda, and the name `bindings`.
- Then re-run `:script-utils-kotlin:test` on 2.4.20. If it passes, lift the hold: the catalog comment, the four Dependabot Kotlin entries, and the CLAUDE.md sections.

<a id="rv-049"></a>
#### RV-049 — Kotlin expression pools are ~8× slower than a lone evaluator
**Severity:** Medium · **Category:** performance · **Verdict:** confirmed (measured on JDK 17) · **Follows up:** CR-086
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluatorPool.kt:36`; `script-utils-kotlin/README.md:151-152,284-286`

`reset = instance.resetContext()` runs on every return, so every pooled eval builds a fresh REPL state:

| Setup                                   | Time per eval |
|-----------------------------------------|---------------|
| Standalone `KotlinExprEvaluator`        | ~25–45 ms     |
| `KotlinExprEvaluatorPool(1).blockingEval` | ~190–270 ms |
| `resetContext` + eval                   | ~376 ms       |

Without resets, the heap grows about 0.77 MB per eval (52 → 360 MB over 400 evals), but the time per eval stays flat. So the memory problem builds up slowly over many evals, yet the pool pays for a reset on every one, which makes it the slow path. The README says pools exist for performance.

**Fix:**
- Reset every N borrows (configurable; `resetEvery = 100` costs about 3–4 ms per eval amortized), or by history size.
- Document that expression-level declarations stay visible to later borrowers until the next reset.
- Do this after [RV-048](#rv-048), which changes what the engine scope holds.

<a id="rv-050"></a>
#### RV-050 — `JavaScript.evalScript` leaves extra public fields in the bindings
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:142-155,169-193`

java-scriptengine copies every public field of the evaluated class back into ENGINE_SCOPE. Before each run, it sets a field for every binding, and throws `NoSuchFieldException` when the class has no such field. For example:
1. `add("count", 41); evalScript("public class Main { public int count; public int extra = 1; … }")` returns 42.
2. After that, `eval("count")` throws `ScriptException: NoSuchFieldException: extra`, and so does any `evalScript` whose class lacks `extra`.

**Fix:** After `evalScript`, remove every binding key that isn't in `valueMap`. Alternatively, run it against a copy of the bindings that holds only the registered variables, then copy those back. Add a test.

<a id="rv-051"></a>
#### RV-051 — `accessibleClass` ignores module exports
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed in both engines · **Follows up:** CR-084, CR-095
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:276-280`

`add("cs", Charset.forName("UTF-8"))` declares the variable as `sun.nio.cs.UTF_8`. That class is public, but its package isn't exported by `java.base`:
- **Java:** "package sun.nio.cs is not visible".
- **Kotlin:** "Symbol is declared in module 'java.base' which does not export package 'sun.nio.cs'".

`TimeZone.getDefault()` (`sun.util.calendar.ZoneInfo`) and the XML factories fail the same way.

**Fix:** In `isPubliclyAccessible`, also require `!module.isNamed || module.isExported(packageName)`, and add tests.

<a id="rv-052"></a>
#### RV-052 — The declared type is the first public supertype, which can lose the value's API
**Severity:** Medium · **Category:** bug / API · **Verdict:** confirmed
**Where:** `AbstractScript.kt:145-148` (rejects type arguments), `AbstractScript.kt:242-255,260-272` (breadth-first supertype search), `KotlinScript.kt:68-70` (star projections)

- **Superclass first:** the search finds the superclass before the interfaces. So `Comparator.naturalOrder()` is declared as `kotlin.Enum<*>` / `java.lang.Enum`, and `cmp.compare(...)` doesn't resolve in either engine.
- **Unusable star projections:** `Comparator.reverseOrder()` becomes `java.util.Comparator<*>`. Calling `compare("a","b")` then fails with "inferred type is String but Nothing! was expected". Lambdas have the same problem, as `kotlin.Function1<*, *>`.
- **No override:** passing `typeOf<String>()` is rejected with "Invalid type parameter", because the runtime class declares no type parameters.

**Fix:** Add an overload that takes the static type: `inline fun <reified T : Any> add(name: String, value: T)`, using `typeOf<T>()`. Otherwise, validate the registered types against the chosen accessible class, not the runtime class. Keep the search as a fallback. Do this with [RV-051](#rv-051).

<a id="rv-053"></a>
#### RV-053 — `JavaScript.import` emits binary names for nested classes
**Severity:** Low · **Category:** bug / API · **Verdict:** confirmed
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:80-81,89-92`

- `import(AbstractMap.SimpleEntry::class.java)` emits `import java.util.AbstractMap$SimpleEntry;`, and javac reports "cannot find symbol". This covers any nested class, including Kotlin classes nested in objects or sealed types.
- `import` is a Java keyword, so `script.import(...)` can't be called from Java.

**Fix:**
- Use `clazz.canonicalName`, and reject classes where it is `null`.
- Add `@JvmName("addImport")` or an alias.
- Add a nested-class test.

<a id="rv-054"></a>
#### RV-054 — The `Isolated` classloader README is wrong, and `NoClassDefFoundError` escapes unwrapped
**Severity:** Low · **Category:** docs / bug · **Verdict:** confirmed
**Where:** `script-utils-java/README.md:170-172`; `JavaScript.kt:197-200`

- The README says Isolated "compiles each script into its own classloader, so a class can be redefined". In fact both modes use a new classloader for each compile. What Isolated really does is hide the host's classes.
- With Isolated, `add("aux", IncClass(5)); eval("aux.getI()")` throws a bare `java.lang.NoClassDefFoundError`. `evaluate` wraps only `RuntimeException`, so the error escapes the documented `@throws ScriptException`. `ExceptionInInitializerError` from static initializers escapes the same way.

**Fix:**
- Correct the README, and warn that bound host types need `CallerClassLoader`.
- Wrap `LinkageError`, or every non-`VirtualMachineError` `Throwable`, in `ScriptException`.

<a id="rv-055"></a>
#### RV-055 — The `JavaScript` null-global-context KDoc is wrong
**Severity:** Low · **Category:** docs / bug · **Verdict:** confirmed
**Where:** `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:34,107`

The KDoc says `nullGlobalContext` is "ignored" and that "Java cannot have a null global context". In fact `resetForReuse(true)` and `resetContext(true)` set GLOBAL_SCOPE to null. `evalScript` then fails with a wrapped `NullPointerException: … globalBindings is null`.

**Fix:** Have `JavaScript` always pass `false`, or reject `true`, and correct the KDoc.

<a id="rv-056"></a>
#### RV-056 — The thread-safety claim doesn't cover the public readers
**Severity:** Low · **Category:** concurrency / docs · **Verdict:** plausible
**Where:** `KotlinScript.kt:51-57`, `JavaScript.kt:58-59,80-81,99-101`, `AbstractScript.kt:101-104`; `script-utils-common/README.md:310-311`

The README says one instance "can be shared". But `varDecls`, `importDecls`, `params` and `assignIsolation` read or write the maps and lists without the lock, so a concurrent `add` or `import` can throw `ConcurrentModificationException`.

**Fix:** Mark these members `@Synchronized`, or narrow the README claim.

<a id="rv-057"></a>
#### RV-057 — The shared `ScriptEngineManager` keeps the first caller's context classloader
**Severity:** Low · **Category:** bug · **Verdict:** plausible (the JDK behaviour is certain; the impact depends on the environment)
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEngine.kt:66-68`

`ScriptEngineManager()` discovers factories through the thread context classloader. It is created lazily, once. If that first construction runs on a thread whose loader can't see the engine jar, every later lookup fails with "Unrecognized script extension".

**Fix:** Use `ScriptEngineManager(AbstractEngine::class.java.classLoader)`, or create one manager per engine.

<a id="rv-058"></a>
#### RV-058 — `withInstance` loses the block's exception when `reset` also throws
**Severity:** Low · **Category:** bug · **Verdict:** confirmed (try/finally semantics)
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEnginePool.kt:83-95`

If `block` throws E1 and then `reset` throws E2, E2 propagates and E1 is lost.

**Fix:** Catch the reset failure and attach it to the pending exception with `addSuppressed`.

<a id="rv-059"></a>
#### RV-059 — Mutable internals are exposed as `protected`
**Severity:** Low · **Category:** API · **Verdict:** confirmed (ABI dump)
**Where:** `script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:57`; `AbstractEnginePool.kt:43`

- `protected val valueMap: MutableMap` lets a subclass drift out of step with `typeMap` and `unboundNames`. A removed name then makes `prepare` throw `NoSuchElementException`.
- `protected val channel` lets a subclass take instances without resetting or returning them.

**Fix:** Expose read-only views and keep the channel private. This changes the ABI, so it belongs in Phase 5.

### email-utils

<a id="rv-060"></a>
#### RV-060 — `isValidEmail` throws `StackOverflowError` on a ~4 KB input
**Severity:** Medium · **Category:** security / bug · **Verdict:** confirmed (re-run on JDK 21 for this doc)
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/EmailUtils.kt:41,44,55`

`java.util.regex` recurses once for each repetition of the domain group `([A-Za-z0-9](...)?\.)+`. Running the exact pattern on a thread with the default stack:
- `"a@" + "a.".repeat(1000) + "com"` (2,005 chars) returns `true`, far over RFC 5321's 254-character limit.
- 2,000 labels (4,005 chars) throws `StackOverflowError`, and so do 5,000.

`StackOverflowError` is an `Error`, so a `catch (e: Exception)` doesn't catch it. The README pairs the validator with `params.getEmail(...)`, which means it runs on untrusted form input.

**Fix:**
- Reject before matching when the address is over 254 characters or the local part is over 64. That caps the label count at about 127.
- Optionally, bound the labels with `{0,61}`.
- Add a 5,000-label regression test.

<a id="rv-061"></a>
#### RV-061 — `ResendService.sendEmail` builds a new OkHttp client for every email
**Severity:** Medium · **Category:** leak / performance · **Verdict:** confirmed (resend-java 4.25.0 source)
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/ResendService.kt:67`; `email-utils/README.md:267`

`Resend.emails()` returns `new Emails(apiKey)`, which calls `BaseService(apiKey)`, which calls `new HttpClient()`, which calls `new OkHttpClient()`. `sendEmail` calls `resend.emails()` on every send, so each email gets:
- its own connection pool;
- a new TLS socket factory;
- a new TCP+TLS handshake.

The idle connection stays in the orphaned pool until OkHttp evicts it about 5 minutes later, so sustained sending piles up open sockets. The README's "reuses it for every send" is therefore wrong, and the mocked-constructor tests can't see the problem.

**Fix:** Keep `private val emails = resend.emails()` from the constructor and reuse it. Add a test that two sends call `emails()` once.

<a id="rv-062"></a>
#### RV-062 — Network failures surface as `RuntimeException`, not `ResendException`
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/email/ResendService.kt:47`; `email-utils/README.md:133,200`

resend-java's `HttpClient.perform` wraps every `IOException` (DNS, connect or read timeout) in a plain `RuntimeException`. Only non-2xx replies become `ResendException`. A caller who follows the docs and catches `ResendException` misses every transport failure. `ResendException` is also a checked exception, and `sendEmail` has no `@Throws`.

**Fix:** Document both exception types, or wrap a `RuntimeException` whose cause is an `IOException` in `ResendException`. Add `@Throws`.

<a id="rv-063"></a>
#### RV-063 — `ResendWebhookMsg.decode` likely fails on non-email events
**Severity:** Low · **Category:** bug / docs · **Verdict:** plausible (resend.com was unreachable; confirm against Resend's current event list)
**Where:** `email-utils/src/main/kotlin/com/pambrose/common/webhook/Data.kt:46-50`; `ResendWebhookMsg.kt:59,65`; `email-utils/README.md:143-147`

`Data.createdAt`, `emailId` and `from` are all required. Resend also sends `contact.*` and `domain.*` events, whose `data` has no `email_id` or `from`, so `decode` would throw `MissingFieldException` on them. The KDoc says any event type "still decodes". An endpoint subscribed to those events would return errors and trigger retries.

**Fix:** Make `emailId` and `from` nullable, or document that only `email.*` events are supported and branch on `type` before decoding.

<a id="rv-064"></a>
#### RV-064 — email-utils README problems
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `email-utils/README.md:124-126,145,260-261,267`

- The `sendEmail` snippet uses `[...]` collection literals. Those compile only with this repo's experimental `-Xcollection-literals` flag, so consumers who copy the snippet get compile errors. No other README does this.
- The webhook example logs `it.ipAddress` at INFO, which the README's own Security section says not to do.
- Line 267's claim about client reuse is wrong (see [RV-061](#rv-061)).

**Fix:**
- Use `listOf(...)` in the snippet.
- Remove the IP address from the example log line.
- Fix line 267 along with RV-061.

### recaptcha-utils

<a id="rv-065"></a>
#### RV-065 — The process-wide client can't be reopened after `close()`
**Severity:** Low · **Category:** API · **Verdict:** plausible
**Where:** `recaptcha-utils/src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:53,59,268`; `recaptcha-utils/README.md:143-147`

Once the client is closed, every later verification in the JVM fails. The README tells users to close it on `ApplicationStopped`, which also fires in these cases:
- Ktor dev-mode auto-reload;
- a second embedded application in the same process;
- successive `testApplication`s in a consumer's test suite.

After the first stop, every reCAPTCHA-protected form rejects everyone.

**Fix:** Recreate the client lazily once it has been closed, or tie its lifecycle to an instance or plugin that follows the `Application`. At minimum, warn about this in the README.

### redis-utils

<a id="rv-066"></a>
#### RV-066 — The `suspend` helpers do blocking Jedis I/O on the caller's dispatcher
**Severity:** Medium · **Category:** concurrency · **Verdict:** confirmed (traced; no dispatcher switch anywhere)
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:306-309,322-325,383-390,403-410`, which reach `ping()` at `:181,194` and client creation at `:163-169`

Client creation, `ping()` and `close()` are blocking Jedis calls made directly inside `suspend` functions. The connect and socket timeouts are 2,000 ms (`:142-143`), so when Redis is unreachable each call parks its thread for at least 2 s, and the pooled variants add the borrow wait on top. Called from `Dispatchers.Default` or a Ktor event-loop thread, as the `suspend` signatures invite, a Redis outage stalls unrelated coroutines.

**Fix:** Run connect, ping and close inside `withContext(Dispatchers.IO)`, and document that Jedis calls inside `block` also block.

<a id="rv-067"></a>
#### RV-067 — Pool idle ordering is still not validated
**Severity:** Low · **Category:** bug · **Verdict:** confirmed · **Follows up:** CR-112
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:228-233`

CR-112 asked for the idle ordering to be validated, but only the signs are checked:
- `minIdleSize = 5, maxIdleSize = 2` is accepted, and commons-pool2 silently clamps it (`getMinIdle()` returns `min(minIdle, maxIdle)`).
- `maxIdleSize = -1`, commons-pool's "unlimited", is rejected, though `-1` is now allowed for `maxPoolSize`.

**Fix:**
- `require(minIdleSize <= maxIdleSize)`.
- When `maxPoolSize != -1`, also `require(maxIdleSize <= maxPoolSize)`.
- Accept `-1` for `maxIdleSize`, or document why not.

<a id="rv-068"></a>
#### RV-068 — `scanKeys` on `UnifiedJedis` fails on cluster clients
**Severity:** Low · **Category:** API / docs · **Verdict:** confirmed (Jedis 8.0.1 source)
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:422-436`

`RedisClusterClient` extends `UnifiedJedis`, and its `ClusterCommandObjects.scan` throws `IllegalArgumentException` unless the MATCH pattern contains a `{hash-tag}`. So the documented `scanKeys("user:*")` throws against a cluster. With a hash tag it scans only one slot's node.

**Fix:** Narrow the receiver to `RedisClient`, or build on `UnifiedJedis.scanIteration(count, pattern)`, which walks every node. Document the behaviour.

<a id="rv-069"></a>
#### RV-069 — Failures are logged without their cause
**Severity:** Low · **Category:** diagnostics · **Verdict:** confirmed
**Where:** `redis-utils/src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:68-76,111`

- With the default `printStackTrace = false`, the log line is just "Failed to connect to redis", with no exception message and no host. An auth failure, an unreachable host and an exhausted pool all look the same.
- A host containing an underscore, such as `redis://redis_cache:6379` (a legal Docker Compose service name), is reported as "Redis URL has no host", because `java.net.URI` returns `host = null` for it.

**Fix:**
- Always log `e.message`; it contains no secrets.
- When the authority is non-null but the host is null, say that the host could not be parsed.

### exposed-utils

<a id="rv-070"></a>
#### RV-070 — `upsert(conflictIndex)` always throws on MySQL and MariaDB
**Severity:** Medium · **Category:** bug / docs · **Verdict:** confirmed (Exposed 1.5.0 source, re-checked for this doc)
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:28-35,61-67`; `exposed-utils/README.md:159-163,235-240`

The wrapper always passes the index columns as `keys`. For MySQL, Exposed passes the keys through unchanged (`UpsertStatement.prepareSQL`: `if (functionProvider is MysqlFunctionProvider) keys.asList()`). `MysqlFunctionProvider.upsert` then throws `UnsupportedByDialectException("MySQL doesn't support specifying conflict keys in UPSERT clause")` whenever `keyColumns.isNotEmpty()`. MariaDB inherits this.

So every call to this overload fails on those databases. Yet the README says it "works on every database Exposed supports that statement for", and the KDoc describes an `ON CONFLICT (...)` clause that MySQL doesn't have. The H2 tests can't catch this, because H2 in MySQL mode deliberately doesn't use the MySQL provider.

**Fix:** Pick one:
- On `MysqlDialect`, validate the index, then call Exposed's `upsert` with no keys, and document that `ON DUPLICATE KEY UPDATE` fires on *any* unique key.
- Fail fast with a clear message, and list MySQL and MariaDB as unsupported.

<a id="rv-071"></a>
#### RV-071 — Functional and partial unique indexes pass validation
**Severity:** Medium · **Category:** bug · **Verdict:** confirmed for functional indexes; plausible for partial indexes on PostgreSQL
**Follows up:** TC-069
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:54-62`

- **Functional index,** e.g. `uniqueIndex("u_lower_email", functions = listOf(email.lowerCase()))`:
  - Its `columns` list is empty, `unique` is true and `table` is right, so validation passes.
  - The wrapper then passes empty `keys`, so Exposed's `getKeyColumns` falls back to the primary key, then to the first unique index.
  - On a table like the module's own fixture (no PK, with an `id` unique index declared first), an upsert "on" `lower(email)` matches on `id` and updates the wrong row, with no error. TC-069 was meant to rule out exactly this fallback.
  - A mixed index (columns plus functions) forwards only its plain columns, which is also the wrong target.
- **Partial index (`filterCondition`):** it passes validation, but PostgreSQL can't infer it from `ON CONFLICT (cols)` without the predicate, so the database rejects it at runtime. The KDoc says validation prevents that.

**Fix:**
- `require(conflictIndex.columns.isNotEmpty() && conflictIndex.functions.isNullOrEmpty())`.
- `require(conflictIndex.filterCondition == null)`, or document the partial-index case.
- Add a test that passes a functional index.

<a id="rv-072"></a>
#### RV-072 — `readonlyTx` and `timed*` don't enforce read-only or isolation when nested
**Severity:** Low · **Category:** docs / API · **Verdict:** confirmed (Exposed source)
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/ExposedUtils.kt:83-145`; `exposed-utils/README.md:18`

When called inside another transaction:
- With the default `useNestedTransactions = false`, `transaction()` returns the outer transaction itself.
- With nesting on, the new transaction inherits the outer transaction's `readOnly` and isolation level.

Either way, `readonlyTx { insert… }` inside `transaction {}` writes. A nested `timedTransaction` also measures a block that doesn't commit. The docs say "Executes a read-only database transaction" without that caveat.

**Fix:** Document the nesting behaviour. If the guarantee matters, use `inTopLevelTransaction`, or `check` for an outer non-read-only transaction.

<a id="rv-073"></a>
#### RV-073 — `KotlinSqlLogger` logs bound parameter values at INFO
**Severity:** Low · **Category:** security · **Verdict:** confirmed
**Where:** `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/ExposedUtils.kt:43-51`; `exposed-utils/README.md:133-134,242-248`

`context.expandArgs(transaction)` inlines every argument into the message, which is logged at `info`. Passwords, tokens and PII written through Exposed therefore reach production logs at the level most deployments keep. Exposed's own `Slf4jSqlDebugLogger` logs at DEBUG. The README's Security section covers only `CustomExpr`.

**Fix:** Log at `debug`, or add a level parameter, and add a line to the Security section.

### grpc-utils

<a id="rv-074"></a>
#### RV-074 — The default channel now disables all retry
**Severity:** Medium · **Category:** bug (regression) · **Verdict:** confirmed (grpc-java 1.84.0 source; `git show 881f18d`)
**Follows up:** CR-116
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:65,100-103`

`enableRetry` defaults to `false`, and since #184 `false` calls `disableRetry()`. Before 4.1.0, a channel built without the flag kept grpc's own default, `retryEnabled = true`.

With retry disabled, `ManagedChannelImpl.ChannelStreamProvider.newStream` skips `RetriableStream` entirely. That also removes the *transparent* retries grpc applies to unconfigured methods, for example on GOAWAY or refused streams during a server restart. Callers who never touched the flag can now see sporadic `UNAVAILABLE` errors.

Meanwhile `enableRetry = true` adds nothing beyond grpc's default unless a retry-policy service config is supplied, and the DSL has no parameter for one. So the README's "TLS channel with retry enabled" example doesn't configure any retry either.

**Fix:**
- Make the flag `enableRetry: Boolean? = null`, where `null` leaves grpc's default and `true`/`false` call the builder.
- Optionally add `retryServiceConfig: Map<String, Any>?`, passed to `defaultServiceConfig`.
- Document that `false` also disables transparent retry.

<a id="rv-075"></a>
#### RV-075 — `GrpcDsl.server` can't bind an address, and its defaults can never work
**Severity:** Low · **Category:** API / tests · **Verdict:** confirmed
**Where:** `grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:63-64,145,167`; `grpc-utils/src/test/kotlin/com/pambrose/common/dsl/GrpcDslTestSupport.kt:79-91`

- `server()` always calls `NettyServerBuilder.forPort(port)`, so a caller can't bind loopback or a specific interface. The tests have to `mockkStatic(NettyServerBuilder::class)` to bind 127.0.0.1.
- The defaults `port = -1` and `hostName = ""` fail at runtime with "port out of range" and "Invalid host or port".

**Fix:** Add a `bindAddress: String? = null` parameter (or a `SocketAddress`) and drop the mock from the tests. Validate the port with a clear message when the Netty transport is chosen.

<a id="rv-076"></a>
#### RV-076 — Unused grpc-protobuf and grpc-services are shipped to every consumer
**Severity:** Low · **Category:** build · **Verdict:** confirmed
**Where:** `grpc-utils/build.gradle.kts:11`; `gradle/libs.versions.toml:164-169`

`implementation(libs.bundles.grpc)` adds grpc-protobuf and grpc-services, but neither main nor test code imports any of their packages. grpc-services also pulls in protobuf-java-util, gson and more at runtime.

**Fix:** Depend on `grpc-inprocess` alone. Consumers that rely on these at runtime would break, so do it in Phase 5 with a CHANGELOG note.

<a id="rv-077"></a>
#### RV-077 — grpc-utils README and KDoc drift
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `grpc-utils/README.md:22,70,176-178`; `grpc-utils/src/main/kotlin/com/pambrose/common/utils/ServerExtensions.kt:62-63`

- **README line 70:** "`channel` requires `tlsContext`" is stale since the CR-119 fix. It now defaults to `PLAINTEXT_CONTEXT`, as the README's own API reference says.
- **README lines 176-178 and the KDoc:** both claim `shutdown()` throws on an already-terminated server. `ServerImpl.shutdown()` in fact returns `this`; only the mocks throw.
- **README line 22:** "supplying a trust collection enables mutual auth" holds only for servers. On the client, mutual auth follows the cert/key pair.

**Fix:** Correct all three statements.

### Cross-module API surface

<a id="rv-078"></a>
#### RV-078 — Public types and empty companions that should be internal or private
**Severity:** Low · **Category:** API · **Verdict:** confirmed (ABI dumps)
**Where:**
- `exposed-utils/src/main/kotlin/com/pambrose/common/exposed/ExposedUtils.kt:33-36`
- `redis-utils/.../RedisUtils.kt:87` (`RedisInfo`)
- `recaptcha-utils/.../RecaptchaService.kt:98` (`RecaptchaResponse`)
- Empty public `Companion` classes in `jetty-utils.api` (`LambdaServlet`, `VersionServlet`), `prometheus-utils.api` (`SamplerGaugeCollector`) and `email-utils.api` (`ResendService`)

- The KDoc calls `object ExposedUtils` an "internal holder", but it is a public, empty object: its only member is `internal`.
- `RedisInfo` holds a password, and only a private function creates it.
- `RecaptchaResponse` is used only internally.
- The companions hold only private loggers.

These widen the ABI (serializers and `copy`/`componentN` included), so any later cleanup becomes a breaking change.

**Fix:**
- Make the types `internal`, and change the companions to `private companion object` or top-level `private val`s.
- Run `make abi-update` on macOS and commit the dumps.
- This belongs in Phase 5.

### Build & publishing

<a id="rv-079"></a>
#### RV-079 — Five modules export core-utils as `api` but use little or none of it
**Severity:** Low · **Category:** build · **Verdict:** confirmed
**Where:** line 4 of `jetty-utils/build.gradle.kts`, `dropwizard-utils/build.gradle.kts`, `prometheus-utils/build.gradle.kts` and `exposed-utils/build.gradle.kts`; `ktor-client-utils/build.gradle.kts:13` with `ktor-client-utils/src/commonMain/kotlin/com/pambrose/common/dsl/KtorDsl.kt:20,68,88`

How much of core-utils each module actually uses:
- **jetty-utils, dropwizard-utils:** no core-utils class, and not even kotlin-logging.
- **prometheus-utils:** kotlin-logging, internally.
- **exposed-utils:** exposes only `KLogger`.
- **ktor-client-utils:** a single `isNull()` call.

Every consumer still gets coroutines, datetime, serialization-json, kotlin-logging and reflect on its compile classpath. ktor-server-utils had the same problem and dropped the dependency in 4.0.0 (CR-127).

**Fix:**
- Drop core-utils from jetty-utils and dropwizard-utils.
- Use `implementation(libs.kotlin.logging)` in prometheus-utils and `api(libs.kotlin.logging)` in exposed-utils.
- Replace `isNull()` with `== null` in ktor-client-utils.
- Update CLAUDE.md, which says all JVM modules depend on core-utils.
- Also: `api(libs.dropwizard.core)` in dropwizard-utils is redundant, because metrics-healthchecks already depends on metrics-core.
- This belongs in Phase 5.

<a id="rv-080"></a>
#### RV-080 — ktor-server-utils' `compileOnlyApi` servlet API is published at Maven `compile` scope
**Severity:** Low · **Category:** publishing · **Verdict:** confirmed (published 4.1.0 POM and `.module`)
**Where:** `ktor-server-utils/build.gradle.kts:14`; `ktor-server-utils/README.md:141,179`

- Gradle consumers get `jakarta.servlet-api` as compile-only: it appears in `apiElements`, not `runtimeElements`.
- The POM lists it at `<scope>compile</scope>`, so Maven consumers get it at runtime, or bundled into a WAR. That contradicts the README's "supply it yourself".

**Fix:** Mark it `<optional>true</optional>` (or `provided`) through `pom.withXml` in `configurePublishing`. Alternatively, model the servlet bridge as a feature variant.

<a id="rv-081"></a>
#### RV-081 — Dependabot's held Kotlin group also takes `kotlin-reflect`
**Severity:** Low · **Category:** deps · **Verdict:** confirmed (config); the drift is a forecast
**Where:** `.github/dependabot.yml:18-23`; `gradle/libs.versions.toml:28-30,78`; `CLAUDE.md:98-101`

- The `kotlin` group's `org.jetbrains.kotlin:*` pattern matches `kotlin-reflect`, so reflect updates ride in the PR that CLAUDE.md says to expect red and not merge.
- The compiler meanwhile moves through `gradlePlugins` in the catch-all group; pambrose-gradle-plugins master pins Kotlin 2.4.20.
- So the next convention-plugin bump would publish stdlib 2.4.x+1 next to reflect 2.4.20. That is the mismatch CR-126 fixed.

**Fix:**
- Add `exclude-patterns: ["org.jetbrains.kotlin:kotlin-reflect"]` to the kotlin group, or declare `api(kotlin("reflect"))` without a version and check that the POM still carries one.
- If [RV-048](#rv-048) lets the hold be lifted, the group can go entirely.

<a id="rv-082"></a>
#### RV-082 — `make coverage-clean` doesn't clear the KMP `jvmTest` results
**Severity:** Low · **Category:** build · **Verdict:** plausible (Gradle/KGP task semantics; not executed)
**Follows up:** CR-129
**Where:** `Makefile:74-78`

`cleanAllTests` deletes only the outputs of the `allTests` aggregate report. It doesn't touch `build/test-results/jvmTest` or the Kover binary report attached to `jvmTest`, so core-utils, json-utils and ktor-client-utils stay UP-TO-DATE after a "clean". The comment at line 74 is also wrong, and the root-only `rm -rf build/kover` doesn't reach the module directories.

**Fix:** Use `./gradlew cleanTest cleanJvmTest` (plus `cleanAllTests` if the HTML report matters), and fix the comment.

<a id="rv-083"></a>
#### RV-083 — The disabled watchOS/tvOS test tasks still link their test binaries
**Severity:** Low · **Category:** build (performance) · **Verdict:** plausible
**Where:** `build.gradle.kts:357-363`

`enabled = false` skips only the test task itself. Its dependencies, `linkDebugTestWatchosSimulatorArm64` and `linkDebugTestTvosSimulatorArm64`, still run under `check` and `allTests` on macOS, for example in `make tests`. That wastes two native links per KMP module.

**Fix:** Also disable the matching `linkDebugTest<Target>` tasks.

<a id="rv-084"></a>
#### RV-084 — No BOM for 19 co-versioned artifacts
**Severity:** Low · **Category:** publishing · **Verdict:** confirmed (removed in 2.7.0, `CHANGELOG.md:1045`)
**Where:** `settings.gradle.kts:59-77`; root `build.gradle.kts`

Consumers that mix modules must align every version by hand; service-utils alone pulls in six siblings. The KMP split between `-jvm` and root artifacts makes this worse for Maven users.

**Fix:** Add a `common-utils-bom` `java-platform` module with constraints on all 19 artifacts, including the `-jvm` coordinates, and publish it through the same vanniktech configuration.

<a id="rv-085"></a>
#### RV-085 — Detekt's `VariableNaming` excludes miss `*Tests.kt`
**Severity:** Low · **Category:** cleanup · **Verdict:** confirmed
**Where:** `config/detekt/detekt.yml:46-47`

`VariableNaming` excludes `**/test/**` and `**/*Test.kt`, but not `**/*Tests.kt`, the project's naming convention (`EmptyFunctionBlock` does exclude it). The rule therefore still applies to the 34 KMP `*Tests.kt` specs under `commonTest`, `jvmTest` and the other test source sets.

**Fix:** Add `'**/*Tests.kt'`.

### CI & supply chain

<a id="rv-086"></a>
#### RV-086 — The wrapper scripts are marked `binary`, and the distribution has no checksum
**Severity:** Medium · **Category:** security · **Verdict:** confirmed (`git check-attr`, re-checked for this doc)
**Where:** `.gitattributes:5,8`; `gradle/wrapper/gradle-wrapper.properties`; `Makefile:113-115`

- **Hidden diffs:** `git check-attr` reports `binary: set`, `text: unset` and `diff: unset` for both `gradlew` and `gradlew.bat`.
  - The trailing `binary` macro (`-text -diff -merge`) overrides `text eol=…`, so the eol rules do nothing.
  - Any change to the two scripts that CI and every developer run shows up as "Binary files differ".
  - Dependabot's wrapper PRs rewrite exactly these files, and setup-gradle validates only `gradle-wrapper.jar`.
- **No checksum:** `gradle-wrapper.properties` has no `distributionSha256Sum`, so the downloaded distribution is never verified.

**Fix:**
- Drop `binary` from lines 5 and 8.
- Add `distributionSha256Sum`.
- Pass `--gradle-distribution-sha256-sum` in `make upgrade-wrapper`, so the next upgrade doesn't drop it.

<a id="rv-087"></a>
#### RV-087 — The Linux job has no Kotlin/Native cache
**Severity:** Low · **Category:** ci · **Verdict:** confirmed
**Where:** `.github/workflows/test.yml:19-48,71-74,94-97`

- The `test` job builds `linuxX64Test` (plus `linuxArm64` and `mingwX64`) through `build`, which downloads the K/N toolchain. It has no `~/.konan` cache, though the file's own comment says setup-gradle doesn't cache it.
- The macOS and Windows caches are keyed on the hash of the whole catalog, with no `restore-keys`, so every weekly Dependabot bump misses the cache.

**Fix:** Add the same cache step to `test`, and add `restore-keys: konan-${{ runner.os }}-` or key on the KGP version only.

<a id="rv-088"></a>
#### RV-088 — CI never exercises publishing
**Severity:** Low · **Category:** ci / publishing · **Verdict:** confirmed (gap); impact plausible
**Where:** `.github/workflows/test.yml:36-40`; `.github/workflows/kdocs.yml:36-37`

CI runs only `build`, `koverXmlReport` and the root `:dokkaGenerate`. It never touches:
- POM and Gradle module metadata generation;
- the per-module javadoc and sources jars;
- the tcnative classifier dependencies added in 4.1.0.

So a broken publication surfaces only at `make publish-maven-central`.

**Fix:** Add `./gradlew publishToMavenLocal` to the Linux job; it needs no signing key. Optionally add a POM sanity check.

<a id="rv-089"></a>
#### RV-089 — Actions are still pinned by mutable tags, and no job has a timeout
**Severity:** Low · **Category:** security / ci · **Verdict:** confirmed · **Follows up:** CR-135
**Where:** `.github/workflows/test.yml:23,28,33,43,58-71,83-94`; `.github/workflows/kdocs.yml:25,29,34,41,60`

- CR-135 recommended SHA pins, but only the concurrency half landed. `codecov/codecov-action@v7` receives `CODECOV_TOKEN`, and `deploy-pages@v5` runs with `pages: write` and `id-token: write`, both on retaggable refs.
- No job sets `timeout-minutes`, so a hang (such as the pool deadlock fixed in 4.1.0) burns the 6-hour default, including on macOS runners.

**Fix:** Pin the third-party actions by SHA (Dependabot keeps SHA pins current), and set `timeout-minutes` on every job.

### Project documentation

<a id="rv-090"></a>
#### RV-090 — llms.txt and the README overstate dropwizard-utils and zipkin-utils
**Severity:** Low · **Category:** docs · **Verdict:** confirmed · **Follows up:** CR-136
**Where:** `llms.txt:31,33`; `README.md:125-126`

- **dropwizard-utils:** its whole API is `MetricsDsl.healthCheck` plus two health-check factories. It has no "metric definitions".
- **zipkin-utils:** its whole API is `ZipkinDsl.tracing`. It has no "span management", and its own README says so.

llms.txt feeds AI tools directly, which was CR-136's concern.

**Fix:**
- dropwizard-utils: "Health-check DSL and ready-made backlog/map-size health checks".
- zipkin-utils: "DSL for building a Brave `Tracing`".

<a id="rv-091"></a>
#### RV-091 — Coverage figures disagree
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `build.gradle.kts:103-104,109`; `codecov.yml:7`; `CLAUDE.md:239-240`

| Source                      | Line  | Branch | Weakest package (`script`) |
|-----------------------------|-------|--------|----------------------------|
| Root build comment          | 98.8% | 89.3%  | 96.1%                      |
| `codecov.yml`               | ~98%  | —      | —                          |
| CLAUDE.md ("as of 4.1.0")   | 99.8% | 97.2%  | 98.5%                      |

**Fix:** Keep the figures in one place, CLAUDE.md, and have the build and codecov comments point to it.

<a id="rv-092"></a>
#### RV-092 — CLAUDE.md drift, and no versioning policy
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `CLAUDE.md:7,117-120,247` (and `scripts/coverage-packages.py:6`); `CHANGELOG.md:27`; `RELEASE_NOTES.md:56`

- **Module count:** CLAUDE.md says "20+ modules", but `settings.gradle.kts` includes 19.
- **`dsl` package count:** it says `com.pambrose.common.dsl` "lives in six" modules; it lives in seven (dropwizard, grpc, guava, jetty, ktor-client, prometheus, zipkin).
- **Compat rationale:** the kotlinx-datetime `-0.6.x-compat` rule is justified by `exposed-kotlin-datetime`, which no module uses. exposed-utils uses `exposed-jodatime`, and core-utils' ABI uses `kotlin.time.Instant`. If the real reason is downstream consumers, the text should say so.
- **Versioning policy:** 4.1.0 made `ScriptUtils.globalBindings` and `bindings(scope)` nullable, which breaks Kotlin source. It also changed exception types. The 4.0.0 release (142e5c0) took a major bump for comparable breaks, and the repo states no policy.

**Fix:**
- Correct the two counts.
- Reword or drop the compat rationale.
- State a versioning policy, e.g. "binary-compatible but source-breaking changes are allowed in minor releases". Phase 5's release target depends on it.

<a id="rv-093"></a>
#### RV-093 — "Adding New Modules" is incomplete, and the CHANGELOG history has gaps
**Severity:** Low · **Category:** docs · **Verdict:** confirmed
**Where:** `README.md:307-313`; `CHANGELOG.md:1031`; `RELEASE_NOTES.md:670,678,778`

- **"Adding New Modules" leaves out:**
  - the `codecov.yml` component entry;
  - generating the `api/` dump with `make abi-update` (without it, `checkKotlinAbi` fails);
  - adding the module to `kmpModuleNames` for KMP modules;
  - updating the llms.txt and README module lists.
- **CHANGELOG and RELEASE_NOTES:**
  - The CHANGELOG dates 2.7.1 to 2026-04-04, but the GitHub release was published 2026-04-17.
  - The CHANGELOG has no 2.6.3 or 2.6.4 entries, although both are tagged and in RELEASE_NOTES.
  - RELEASE_NOTES' `## 2.4.13` lacks the `v` prefix.

**Fix:** Add the missing steps, and correct or backfill the history entries.

---

## Verified non-issues (do not re-report)

- **zipkin-utils** has no findings. Its one-function DSL, its README and its tests (the CR-079/CR-080 fixes) are correct.
- **KMP expect/actual:** consistent across the JVM, JS, wasmJs and Native source sets in core-utils and ktor-client-utils. The `testPlatform` pins cover the known platform differences.
- **Published 4.1.0 POMs:** they map KMP dependencies to `-jvm` coordinates and carry the netty-tcnative classifier jars. Apart from RV-080, the `api`/`implementation` scopes match what the ABI dumps expose.
- **Other config:**
  - The `codecov.yml` component list matches `settings.gradle.kts`.
  - Every `yarnResolutions` pin is present in both lockfiles.
  - The relative links in the READMEs and docs resolve.
  - `check` does run `checkKotlinAbi`.
- **Script engine pools:** returning instances on cancellation, closing, and handling a failed construction (the CR-081/CR-090/CR-091/TC-076 fixes) are correct and tested with the fake engine.
- **guava-utils:** the CR-064…CR-078 fixes hold. The tests use real park detection and hang guards, not sleeps.
- **grpc-utils / recaptcha-utils / redis-utils:** the TLS handshake tests, the URL validation and the cancellation handling from CR-103…CR-123 are correct.
- **service-utils / jetty-utils, Jetty paths:** failed-start rollback, loopback binding and port handling behave correctly. The defects in this area (RV-023, RV-028, RV-029) are on the Ktor side only.
