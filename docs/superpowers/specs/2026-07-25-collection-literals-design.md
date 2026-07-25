# Kotlin Collection Literals Adoption — Design

**Date:** 2026-07-25
**Status:** Approved for planning
**Scope:** All modules, `src` and `test`.

## Goal

Adopt Kotlin 2.4's experimental collection-literal syntax (`[a, b, c]`) across the
repository, converting eligible `listOf(...)` and `mutableListOf(...)` call sites to the
bracket form. `emptyList()` is **explicitly out of scope** and stays as-is everywhere.

## Background

Collection literals are experimental in Kotlin 2.4 (the repo is on 2.4.10), gated behind the
`-Xcollection-literals` compiler flag. Semantics established empirically during a spike:

- A literal with a `List<T>` expected type (or no expected type) lowers to `List.of(...)`.
  At runtime `[1, 2, 3]` is a `java.util.ArrayList` — a genuinely mutable list — whereas
  `listOf(1, 2, 3)` returns the fixed-size `java.util.Arrays$ArrayList` view. Invisible
  through the `List<T>` interface, but a real difference in the concrete object.
- A literal with a `MutableList<T>` expected type produces a resizable `ArrayList`
  (`add`/`remove` verified at runtime).
- An empty literal `[]` requires an expected type; with none, it is a compile error.
- `-Xcollection-literals` is **source-only and ABI-neutral** — no change to published
  bytecode signatures, so Maven Central consumers are unaffected by the flag itself.

### Spike results (all passed)

- `compileKotlin` (JVM), `compileKotlinJvm` (KMP), `lintKotlin` (ktlint 5.5.0), and
  `detekt` (2.0.0-alpha.5) all accept `[...]` syntax with the flag enabled.
- Kotest's `shouldBe` is a generic infix function (not `==`), so the receiver supplies the
  expected type: `listOf(1) shouldBe [1]` compiles. Lambda-return (`getOrPut("k") { [] }`)
  and indexed-assignment (`m["x"] = ["y"]`) positions also compile.
- `MutableList<Int> = [1]` yields a real, resizable `ArrayList` at runtime.

## Design

### 1. Build wiring (`build.gradle.kts`)

Add a single flag constant next to the existing `returnValueCheckerArg`:

```kotlin
val collectionLiteralsArg = "-Xcollection-literals"
```

Apply it to **all** compilations — main and test — because literals are used in both
source sets (unlike the return-value checker, which is deliberately main-only).

- **JVM modules** (`configureKotlinJvm`): add
  ```kotlin
  tasks.withType<KotlinCompile>().configureEach {
      compilerOptions { freeCompilerArgs.add(collectionLiteralsArg) }
  }
  ```
  This covers both `compileKotlin` and `compileTestKotlin`. The existing return-value
  checker stays on its `tasks.named("compileKotlin")` block (main-only by design).

- **KMP modules** (`configureKotlinMultiplatform`): add a sibling to the existing
  main-only return-value block:
  ```kotlin
  targets.configureEach {
      compilations.configureEach {
          compileTaskProvider.configure {
              compilerOptions { freeCompilerArgs.add(collectionLiteralsArg) }
          }
      }
  }
  ```
  This covers every target (JVM, JS, wasmJs, native) and both main and test compilations.

### 2. Conversion rules

| From | To | Notes |
|------|----|----|
| `listOf(a, b, c)` | `[a, b, c]` | Direct. |
| `mutableListOf(x)` | `[x]` **+ explicit `MutableList<T>` on the variable** | The type declaration is load-bearing: without it the literal infers `List` and silently drops mutability. |
| `emptyList()` | *(unchanged)* | Out of scope by decision. |
| Empty `listOf()` (no args) | `[]` only where an expected type exists | None currently exist in the repo; rule stated for completeness. |

For `mutableListOf` conversions, the explicit type goes on the *variable declaration*:

```kotlin
// before
val list = mutableListOf(1)
// after
val list: MutableList<Int> = [1]
```

Sites already carrying an explicit `MutableList<T>` type (e.g.
`val excludePredicates: MutableList<CallPredicate> = mutableListOf()`) just swap the RHS to
`[]`.

### 3. Explicit skip list (do NOT convert)

1. **All `emptyList()`** — out of scope. This automatically preserves the immutable
   `EmptyList` singleton on the public-API default parameters that use it
   (`ResendService.cc/bcc`, `SamplerGaugeCollector.labelNames/labelValues`,
   `RecaptchaService.errorCodes`).
2. **`listOf` / `mutableListOf` inside string literals** — the `eval("listOf(1,2,3) ...")`
   sites in `script-utils-kotlin/.../KotlinScriptTests.kt` (exactly 6 occurrences, lines
   204, 205, 207, 208, 210, 211). These are source code passed *as strings* to a script
   evaluator; they are test data, not Kotlin the build compiles. Converting them would
   corrupt the tests. (Lines that merely contain a quoted *argument*, e.g.
   `shouldBe listOf("value2")`, are NOT in this category and DO convert.)
3. **`mutableListOf` in `Any`-typed argument position** — the 5 `script.add("list", …)` /
   `it.add("list", …)` sites: `AbstractScriptAddMessageTests.kt:49,77` and
   `JavaEquivCharacterizationTests.kt:34,41,55`. The parameter is `Any`, so a literal there
   infers `List` rather than `MutableList`, defeating what the test asserts.
4. **Java-defined collection constructors** — e.g. `ScanResult(SCAN_POINTER_START, ...)` in
   redis-utils (Jedis). [KT-80494](https://youtrack.jetbrains.com/issue/KT-80494): literals
   cannot construct Java-defined collections. This one uses `emptyList()` anyway (already
   skipped); the rule stands for any non-empty case. Attempt, skip if it won't compile.
5. **Gradle build scripts** (`*.gradle.kts`) — compiled against Gradle's embedded Kotlin,
   which the flag does not reach. The `listOf(...)` calls there stay as-is.
6. **Public-API `listOf(...)` default parameter values**, if any are discovered — kept
   immutable for the same reason as (1). *Inventory finding: none currently exist.*

### 4. Inventory (as of 2026-07-25)

Counts are **typed-aware** — found with `listOf\s*[<(]` / `mutableListOf\s*[<(]`, so the
`listOf<T>(…)` / `mutableListOf<T>()` variants are included (a plain `listOf(` grep misses
them and undercounts `mutableListOf` roughly threefold).

- `listOf` — 9 in main sources, 124 in test sources (133 total). Exactly 6 are inside
  string literals (the `eval("listOf(…)")` lines in `KotlinScriptTests.kt`, skip per
  rule 2). The other ~127 are convertible. Note: many lines contain a quoted *argument*
  (`shouldBe listOf("value2")`) — those are real calls and DO convert; only the 6 `eval(…)`
  lines are code-as-string.
- `mutableListOf` — 45 total (main and test). 5 are `Any`-typed argument-position calls
  (skip per rule 3): `AbstractScriptAddMessageTests.kt:49,77`,
  `JavaEquivCharacterizationTests.kt:34,41,55`. The other 40 are convertible.
- `emptyList()` — 21 occurrences, all skipped.
- No bare empty `listOf()` and no public-API `listOf` default parameters exist.
- The redis `ScanResult(pointer, listOf(…))` sites (`RedisUtilsMockTests.kt:64,65,90,91`)
  pass a literal into a Jedis (Java) constructor — the KT-80494 boundary. Attempt; if the
  literal will not compile there, leave those `listOf(…)` as-is.

Counts are diagnostic, not contractual — the compiler is the arbiter of eligibility. Any
site that fails to compile as a literal reverts to its factory-function form.

### 5. Sequencing

Module by module. Compile each module (`:MODULE:compileKotlin` + `:MODULE:compileTestKotlin`,
or `:MODULE:compileKotlinJvm` etc. for KMP) immediately after converting it, so a bad type
inference surfaces local to one module rather than as a pile of errors at the end.

KMP modules (core-utils, json-utils, ktor-client-utils) must additionally compile their
**JS, wasmJs, and native** targets — not just JVM — since the flag and syntax apply to
every target.

### 6. Verification

- Per-module compile as each module is converted.
- Final full `./gradlew build` (all tests, all targets) plus `make lint` (kotlinter +
  detekt) across the whole repo.
- Explicitly confirm the KMP non-JVM targets build (`allTests` or the per-target compile
  tasks), not only the JVM path.

## Risks

- **Experimental syntax churn.** `-Xcollection-literals` is experimental in Kotlin 2.4. A
  future Kotlin bump could change the syntax or semantics, causing source churn in this
  repo. No mitigation — accepted as a known cost of opting in. Documented so a future
  Kotlin upgrade anticipates it.
- **Concrete-type change on `List` literals.** `[a, b]` is a mutable `ArrayList` where
  `listOf(a, b)` was a fixed-size view. Not observable through the `List<T>` interface and
  ABI-neutral, but noted for anyone reasoning about the concrete runtime object.

## Out of Scope

- Converting `emptyList()`.
- `setOf` / `mapOf` / `arrayOf` and other factory functions.
- Any behavioral refactoring beyond the mechanical literal substitution.
- Bumping the Kotlin version or changing the experimental-feature posture elsewhere.
