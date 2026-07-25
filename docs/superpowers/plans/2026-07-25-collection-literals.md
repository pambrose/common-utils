# Kotlin Collection Literals Adoption — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert eligible `listOf(...)` and `mutableListOf(...)` call sites across all modules (`src` and `test`) to Kotlin 2.4 collection-literal syntax `[...]`, leaving `emptyList()` untouched.

**Architecture:** Enable the experimental `-Xcollection-literals` compiler flag on every compilation (JVM + KMP, main + test) via the root `build.gradle.kts`, then mechanically rewrite factory-function call sites to bracket literals. `mutableListOf(...)` conversions additionally move the element type to an explicit `MutableList<T>` on the variable declaration. Verification is compile-then-test per module, plus a final full-repo `build` + `lint`.

**Tech Stack:** Kotlin 2.4.10, Gradle 9.6.1 (Kotlin DSL), Kotest + MockK, kotlinter 5.5.0, detekt 2.0.0-alpha.5, Kotlin Multiplatform (JVM/JS/wasmJs/native).

## Global Constraints

- Kotlin version: **2.4.10** (flag is experimental in this version; do not bump Kotlin).
- Compiler flag literal: **`-Xcollection-literals`** (exact string).
- `emptyList()` is **never** converted — leave every one of the 21 occurrences as-is.
- `mutableListOf(...)` → `[...]` **requires** an explicit `MutableList<T>` on the variable; without it the literal infers `List` and silently drops mutability.
- The published Maven Central ABI must not change — the flag is source-only and ABI-neutral; do not alter any public signature.
- **Never** convert `listOf`/`mutableListOf` that appears inside a **string literal** (the 6 `eval(...)` lines in `KotlinScriptTests.kt`: 204, 205, 207, 208, 210, 211).
- **Never** convert the 5 `Any`-typed argument-position `mutableListOf` calls: `AbstractScriptAddMessageTests.kt:49,77`, `JavaEquivCharacterizationTests.kt:34,41,55`.
- **Never** edit `*.gradle.kts` (compiled against Gradle's embedded Kotlin; flag does not reach them).
- KMP modules are `core-utils`, `json-utils`, `ktor-client-utils`; they must compile JVM **and** JS/wasmJs/native targets.
- Commit message trailer for every commit:
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`
- Do NOT push or open a PR (repo policy: only on explicit instruction).

## File Structure

- **`build.gradle.kts`** (modify) — add the `collectionLiteralsArg` constant and apply it to all compilations in `configureKotlinJvm()` and `configureKotlinMultiplatform()`. This is the only build-logic change; it gates everything else.
- **Production sources** (modify, per module) — `*/src/main/**`, `*/src/commonMain/**`, `*/src/jvmMain/**`. 9 `listOf` sites + the main-source `mutableListOf` sites.
- **Test sources** (modify, per module) — `*/src/test/**`, `*/src/commonTest/**`, `*/src/jvmTest/**`. The bulk of the `listOf` sites + the test-source `mutableListOf` sites.

No files are created. No files are deleted. No test files are added (the existing test suite is the behavioral guardrail — a mechanical rewrite that preserves compilation and green tests is correct by construction).

---

### Task 1: Enable the `-Xcollection-literals` flag (JVM + KMP) with a canary conversion

**Files:**
- Modify: `build.gradle.kts` (around the `returnValueCheckerArg` declaration ~line 75, `configureKotlinJvm()` ~line 222, `configureKotlinMultiplatform()` ~line 244)
- Modify (canary): `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt:87`
- Modify (canary): `core-utils/src/commonMain/kotlin/com/pambrose/common/util/StringExtensions.kt` — DO NOT touch; `emptyList()` there stays (listed here only to state it is intentionally left alone)

**Interfaces:**
- Consumes: nothing.
- Produces: `val collectionLiteralsArg = "-Xcollection-literals"` at file scope in `build.gradle.kts`; every `KotlinCompile` / KMP compilation task now carries the flag. All later tasks rely on this flag being present.

- [ ] **Step 1: Add the flag constant**

In `build.gradle.kts`, immediately after the existing line:

```kotlin
val returnValueCheckerArg = "-Xreturn-value-checker=check"
```

add:

```kotlin
// Experimental in Kotlin 2.4: enables `[a, b]` collection-literal syntax. Applied to
// every compilation (main and test) since the syntax is used in both.
val collectionLiteralsArg = "-Xcollection-literals"
```

- [ ] **Step 2: Apply the flag to all JVM compilations**

In `configureKotlinJvm()`, the existing block applies the return-value checker to `compileKotlin` only. Leave that block untouched and add a sibling that covers main AND test. After the existing `tasks.named<KotlinCompile>("compileKotlin") { ... }` block (inside the `extensions.configure<KotlinJvmProjectExtension>` body), add:

```kotlin
        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                freeCompilerArgs.add(collectionLiteralsArg)
            }
        }
```

- [ ] **Step 3: Apply the flag to all KMP compilations**

In `configureKotlinMultiplatform()`, find the existing `targets.configureEach { compilations.matching { it.name == "main" } ... }` block (the return-value checker). Extend that same `targets.configureEach` to also cover every compilation:

```kotlin
        targets.configureEach {
            compilations.matching { it.name == "main" }.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.add(returnValueCheckerArg)
                    }
                }
            }
            compilations.configureEach {
                compileTaskProvider.configure {
                    compilerOptions {
                        freeCompilerArgs.add(collectionLiteralsArg)
                    }
                }
            }
        }
```

- [ ] **Step 4: Apply one canary conversion to prove the flag works end-to-end**

In `ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt:87`, change:

```kotlin
    val excludePredicates: MutableList<CallPredicate> = mutableListOf()
```

to:

```kotlin
    val excludePredicates: MutableList<CallPredicate> = []
```

(The variable already has an explicit `MutableList<CallPredicate>` type, so only the RHS changes.)

- [ ] **Step 5: Verify the canary compiles on a JVM module**

Run: `./gradlew :ktor-server-utils:compileKotlin`
Expected: `BUILD SUCCESSFUL`. (If it fails with "unresolved" or a flag error, the wiring in Steps 2–3 is wrong — fix before proceeding.)

- [ ] **Step 6: Verify a KMP module still compiles all targets with the flag on**

Run: `./gradlew :core-utils:compileKotlinJvm :core-utils:compileKotlinJs :core-utils:compileKotlinWasmJs`
Expected: `BUILD SUCCESSFUL`. This proves the flag is accepted on the non-JVM toolchains before any KMP source is touched.

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts ktor-server-utils/src/main/kotlin/com/pambrose/common/features/HerokuHttpsRedirect.kt
git commit -m "Enable -Xcollection-literals flag across JVM and KMP compilations

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Convert all `mutableListOf(...)` sites (main + test)

Concentrated here because these are the only conversions that require judgment — moving the element type to an explicit `MutableList<T>` — and the only ones where a mistake silently changes semantics. The 5 `Any`-arg skip sites and the canary (already done in Task 1) are excluded.

**Files:**
- Modify (main): `service-utils/src/main/kotlin/com/pambrose/common/service/AbstractGenericService.kt:73`
- Modify (main): `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:54`
- Modify (main): `script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:46,55`
- Modify (main): `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericValueWaiter.kt:89,156,157`
- Modify (main): `ktor-server-utils/src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:67,74`
- Modify (test): `script-utils-kotlin/src/test/.../KotlinScriptTests.kt:119,151,175,235,245`
- Modify (test): `script-utils-python/src/test/.../PythonScriptTests.kt:109,147,169`
- Modify (test): `script-utils-java/src/test/.../JavaScriptTests.kt:101,137,161,193,201`
- Modify (test): `guava-utils/src/test/.../ConditionalTests.kt:34,36,68,69,70,92,93,94,116,117,118,119`
- Modify (test): `grpc-utils/src/test/.../StreamObserverHelperTests.kt:70`
- Modify (test): `exposed-utils/src/test/.../KotlinSqlLoggerTests.kt:70`
- Modify (test): `script-utils-common/src/test/.../ScriptUtilsTests.kt:66`
- Modify (test, KMP): `core-utils/src/jvmTest/.../MiscFuncsTests.kt:118`, `core-utils/src/jvmTest/.../BugFixVerificationTests.kt:226`
- **Skip:** `AbstractScriptAddMessageTests.kt:49,77`, `JavaEquivCharacterizationTests.kt:34,41,55`

**Interfaces:**
- Consumes: `collectionLiteralsArg` flag from Task 1.
- Produces: no API changes; concrete runtime type of these locals/fields becomes `ArrayList` (was already `ArrayList` from `mutableListOf`, so no behavioral change).

- [ ] **Step 1: List every `mutableListOf` site to confirm the working set**

Run: `grep -rnE "mutableListOf\s*[<(]" --include="*.kt" .`
Expected: 45 lines. Note the 5 skip sites (`add("list", mutableListOf...`) and the Task-1 canary is already converted (won't appear).

- [ ] **Step 2: Convert the two transformation shapes**

Apply these two rewrites to every site EXCEPT the 5 skip sites:

Shape A — `val`/`var` with inferred type gains an explicit `MutableList<T>`:

```kotlin
// val list = mutableListOf(1)                    ->  val list: MutableList<Int> = [1]
// val results = mutableListOf<Int>()             ->  val results: MutableList<Int> = []
// private val waiters = mutableListOf<Waiter>()  ->  private val waiters: MutableList<Waiter> = []
// protected val services = mutableListOf<Service>() -> protected val services: MutableList<Service> = []
```

The type argument on `mutableListOf<T>()` moves verbatim onto the declaration as `MutableList<T>`; for the inferred-element form (`mutableListOf(1)`) the element type is the literal's element type (`Int` for `1`).

Shape B — no variable (map index-set / lambda return), expected type supplies `MutableList`:

```kotlin
// KtorServletResponse.kt:67  headers[name] = mutableListOf(value)        -> headers[name] = [value]
// KtorServletResponse.kt:74  headers.getOrPut(name) { mutableListOf() }  -> headers.getOrPut(name) { [] }
```

(`headers` is `TreeMap<String, MutableList<String>>`, so both positions have a `MutableList<String>` expected type — no declaration to annotate.)

- [ ] **Step 3: Verify the 5 skip sites were NOT touched**

Run: `grep -rnE "add\(\"list\", mutableListOf" --include="*.kt" .`
Expected: the same 5 lines, still using `mutableListOf`. If any now show `[`, revert that site.

- [ ] **Step 4: Compile every affected module (main + test)**

Run:
```
./gradlew :service-utils:compileKotlin :script-utils-kotlin:compileTestKotlin \
  :script-utils-java:compileTestKotlin :script-utils-python:compileTestKotlin \
  :script-utils-common:compileTestKotlin :guava-utils:compileTestKotlin \
  :grpc-utils:compileTestKotlin :exposed-utils:compileTestKotlin \
  :ktor-server-utils:compileKotlin :core-utils:compileTestKotlinJvm
```
Expected: `BUILD SUCCESSFUL`. A failure naming a specific line means that site's inferred type was wrong — fix the `MutableList<T>` annotation there.

- [ ] **Step 5: Run the tests for the mutated test modules**

Run:
```
./gradlew :guava-utils:test :grpc-utils:test :exposed-utils:test \
  :script-utils-kotlin:test :script-utils-java:test :script-utils-python:test \
  :script-utils-common:test :ktor-server-utils:test :core-utils:jvmTest
```
Expected: `BUILD SUCCESSFUL`, all tests pass. (These exercise the mutable lists — e.g. `ConditionalTests` adds to `results`, and `KtorServletResponseTests` reads back the `headers` map mutated at lines 67/74; a dropped-mutability bug would surface as a compile error in Step 4, but the test run confirms behavior.)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Convert mutableListOf calls to collection literals with explicit MutableList types

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Convert `listOf(...)` in main sources

Small, isolated set (9 sites), all production code — worth its own reviewer gate since it touches the published library surface.

**Files:**
- Modify: `script-utils-common/src/main/kotlin/com/pambrose/common/script/ScriptGuards.kt:35`
- Modify: `core-utils/src/jvmMain/kotlin/com/pambrose/common/util/ContentSource.kt:88,178,206`
- Modify: `prometheus-utils/src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt:61`
- Modify: `script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:46`
- Modify: `guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConditionalValue.kt:115,147,179`

**Interfaces:**
- Consumes: `collectionLiteralsArg` flag.
- Produces: no API changes. (`SamplerGaugeCollector.kt:61` returns `List<MetricFamilySamples>` — an interface return, so the concrete-type change from view to `ArrayList` is not observable by callers.)

- [ ] **Step 1: List the main-source `listOf` sites**

Run: `grep -rnE "listOf\s*[<(]" --include="*.kt" $(find . -type d \( -name main -o -name commonMain -o -name jvmMain \))`
Expected: 9 lines (the files above).

- [ ] **Step 2: Convert each site**

Rewrite `listOf(...)` → `[...]`, preserving arguments verbatim. Examples:

```kotlin
// ContentSource.kt:88  scheme + listOf(domainName, ownerName, repoName).join()
//                   -> scheme + [domainName, ownerName, repoName].join()
// SamplerGaugeCollector.kt:61
//   return listOf(MetricFamilySamples(name, Type.GAUGE, help, listOf(sample)))
//   -> return [MetricFamilySamples(name, Type.GAUGE, help, [sample])]
// KotlinScript.kt:46  private val imports = listOf(System::class.qualifiedName)
//                  -> private val imports = [System::class.qualifiedName]
// ConditionalValue.kt:115  for (s in listOf(false, false, true))
//                      -> for (s in [false, false, true])
```

Nested `listOf` (as in `SamplerGaugeCollector.kt:61`) converts both the outer and inner call.

- [ ] **Step 3: Compile the affected modules**

Run: `./gradlew :script-utils-common:compileKotlin :core-utils:compileKotlinJvm :prometheus-utils:compileKotlin :script-utils-kotlin:compileKotlin :guava-utils:compileKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run tests for these modules**

Run: `./gradlew :script-utils-common:test :core-utils:jvmTest :prometheus-utils:test :script-utils-kotlin:test :guava-utils:test`
Expected: `BUILD SUCCESSFUL`, all pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Convert listOf calls to collection literals in main sources

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Convert `listOf(...)` in JVM-module test sources

The bulk of the work, but purely mechanical `listOf(...)` → `[...]` swaps in test code. KMP test sources are handled separately in Task 5 (they need non-JVM verification).

**Files (JVM modules with test `listOf` — verify with the Step-1 grep, do not rely on this list being exhaustive):**
- `ktor-server-utils/src/test/**` (KtorServletResponseTests, KtorServletRequestTests)
- `redis-utils/src/test/**` (RedisUtilsMockTests — see Step 3 for the Jedis-arg caveat)
- `grpc-utils/src/test/**`, `exposed-utils/src/test/**`, `email-utils/src/test/**`
- `prometheus-utils/src/test/**`, `service-utils/src/test/**`, `guava-utils/src/test/**`
- `jetty-utils/src/test/**`, `recaptcha-utils/src/test/**`, `script-utils-*/src/test/**`
- **Skip:** the 6 `eval(...)` string-literal lines in `KotlinScriptTests.kt` (204, 205, 207, 208, 210, 211)

**Interfaces:**
- Consumes: `collectionLiteralsArg` flag.
- Produces: nothing consumed downstream (test code).

- [ ] **Step 1: Enumerate JVM-test `listOf` sites**

Run:
```
grep -rnE "listOf\s*[<(]" --include="*.kt" \
  $(find . -type d -name test) | grep -v "eval("
```
Expected: the convertible JVM-test sites. The `grep -v "eval("` drops the 6 string-literal lines. Cross-check that exactly those 6 are excluded:
`grep -rn "eval(\"listOf\|eval(\"\"\"listOf" script-utils-kotlin/src/test` → 6 lines.

- [ ] **Step 2: Convert each site**

Rewrite `listOf(...)` → `[...]`, arguments verbatim. Typical cases:

```kotlin
// response.getHeaders("X-Custom").toList() shouldBe listOf("value2")
//                                        -> shouldBe ["value2"]
// headersOf("Accept" to listOf("text/html", "application/json"))
//                    -> headersOf("Accept" to ["text/html", "application/json"])
```

`shouldBe` is infix-generic, so the receiver supplies the expected `List<T>` type — the literal compiles in that position.

- [ ] **Step 3: Handle the redis Jedis-constructor sites (KT-80494 boundary)**

For `redis-utils/src/test/.../RedisUtilsMockTests.kt:64,65,90,91` — `ScanResult(pointer, listOf(...))` passes the literal into a Jedis (Java) constructor. Convert them, then compile just that module:

Run: `./gradlew :redis-utils:compileTestKotlin`
Expected: `BUILD SUCCESSFUL`. **If it fails** with a type/inference error at those lines, revert those 4 `listOf(...)` calls to their factory form (they hit KT-80494) and re-run to confirm green. Record which ones were reverted in the commit message.

- [ ] **Step 4: Compile all JVM test modules**

Run: `./gradlew compileTestKotlin` (aggregates every JVM module's test compilation)
Expected: `BUILD SUCCESSFUL`. A failure names the offending file:line — inspect that site (usually a position with no expected type); if genuinely ineligible, revert just that site.

- [ ] **Step 5: Run the full JVM test suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Verify the 6 string-literal sites are intact**

Run: `grep -rn "eval(\"listOf\|eval(\"\"\"listOf" script-utils-kotlin/src/test`
Expected: the same 6 lines, unchanged (still `listOf` inside the strings).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "Convert listOf calls to collection literals in JVM test sources

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Convert `listOf(...)` in KMP test sources + full-repo verification

KMP test conversions (`commonTest`/`jvmTest` of core-utils, json-utils, ktor-client-utils) are separated because they must be verified on JS/wasmJs/native, not only JVM. This task also runs the final whole-repo gate.

**Files:**
- `core-utils/src/commonTest/**`, `core-utils/src/jvmTest/**`
- `json-utils/src/commonTest/**`, `json-utils/src/jvmTest/**`
- `ktor-client-utils/src/commonTest/**`, `ktor-client-utils/src/jvmTest/**`

**Interfaces:**
- Consumes: `collectionLiteralsArg` flag (applied to all KMP compilations in Task 1).
- Produces: nothing downstream.

- [ ] **Step 1: Enumerate KMP-test `listOf` sites**

Run:
```
grep -rnE "listOf\s*[<(]" --include="*.kt" \
  core-utils/src/commonTest core-utils/src/jvmTest \
  json-utils/src/commonTest json-utils/src/jvmTest \
  ktor-client-utils/src/commonTest ktor-client-utils/src/jvmTest 2>/dev/null
```
Expected: the KMP-test sites (e.g. `StringExtensionTests.kt:201,202`, `BugFixVerificationTests.kt:177`, `JsonIntegrationTest.kt:323`, and others the grep surfaces).

- [ ] **Step 2: Convert each site**

Rewrite `listOf(...)` → `[...]`, arguments verbatim. `commonTest` code must stay zone/platform-neutral — a plain literal is, so no extra care needed beyond the mechanical swap.

- [ ] **Step 3: Compile KMP test sources on JVM first (fast feedback)**

Run: `./gradlew :core-utils:compileTestKotlinJvm :json-utils:compileTestKotlinJvm :ktor-client-utils:compileTestKotlinJvm`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify KMP test sources compile on the non-JVM targets**

Run: `./gradlew :core-utils:compileTestKotlinJs :core-utils:compileTestKotlinWasmJs :json-utils:compileTestKotlinJs :json-utils:compileTestKotlinWasmJs :ktor-client-utils:compileTestKotlinJs :ktor-client-utils:compileTestKotlinWasmJs`
Expected: `BUILD SUCCESSFUL`. This is the check the JVM-only path cannot give — literals must lower correctly on the JS/wasm backends too.

- [ ] **Step 5: Run KMP tests on every host-runnable target**

Run: `./gradlew :core-utils:allTests :json-utils:allTests :ktor-client-utils:allTests`
Expected: `BUILD SUCCESSFUL`. `allTests` covers JVM, Node (js/wasmJs), and the macOS/iOS-simulator native targets.

- [ ] **Step 6: Full-repo build + lint (final gate)**

Run: `./gradlew build && make lint`
Expected: both `BUILD SUCCESSFUL`. `build` runs every module's tests across every target; `make lint` runs kotlinter + detekt repo-wide (proven in the spike to accept `[...]`).

- [ ] **Step 7: Final sanity — confirm no `emptyList()` was converted and no build script was touched**

Run:
```
git diff --stat HEAD~5 | grep -c "gradle.kts"   # expect only build.gradle.kts among *.kts
grep -rn "emptyList()" --include="*.kt" . | wc -l  # expect 21 (unchanged)
```
Expected: the only `.kts` file changed is `build.gradle.kts`; `emptyList()` count is still 21.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Convert listOf calls to collection literals in KMP test sources

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Notes for the Executor

- **Order matters:** Task 1 must land first (nothing else compiles without the flag). Tasks 2–5 are independent of each other and could be reordered, but the numbering keeps the highest-judgment change (Task 2, mutableListOf) early and the widest verification (Task 5) last.
- **The compiler is the arbiter.** Every task compiles before it commits. If a site the plan lists as convertible fails to compile as a literal, revert that single site to its factory form and note it — the plan's counts are diagnostic, not contractual.
- **Do not add tests.** The existing suite is the behavioral guardrail; a mechanical rewrite that keeps compilation and tests green is correct.
- **Do not push or open a PR** — repo policy requires explicit instruction.
