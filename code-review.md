# Common-Utils Code-Improvement Report

## Overall Assessment

The library is in good overall health: across 21 reviewed units, 139 of 148 raised findings were confirmed, and the vast majority (113) are low-severity polish rather than functional defects. The handful of genuinely impactful issues cluster in two areas — serialization/protocol contracts (a Resend webhook that cannot decode real payloads, servlet adapters that diverge from the Jakarta/HTTP contract) and the scripting modules, whose `System.exit`/`exit()` "security" guards are brittle, bypassable, and largely untested. A recurring structural theme is duplication — most visibly the ~95% identical `GenericService`/`GenericKtorService` pair — which causes the same bugs and inconsistencies to be fixed twice. None of this blocks use of the library, but several confirmed bugs silently corrupt or reject valid input and warrant prompt attention.

## Top Priorities

1. **[high] Click webhook uses camelCase serial names, breaking real Resend deserialization** — `email-utils / webhook/Click.kt:31-43`
2. **[high] `singleToDoubleQuoted` does not escape inner double quotes despite its KDoc** — `core-utils / util/StringExtensions.kt:63-68`
3. **[high] `KtorServletRequest.getContentType()` returns `"*/*"` instead of null when absent** — `ktor-server-utils / servlet/KtorServletRequest.kt:93`
4. **[medium] UPSERT generates invalid SQL when all inserted columns are conflict columns** — `exposed-utils / exposed/UpsertStatement.kt:96-99`
5. **[medium] Service shutdown hooks added on every `startUp`, never removed (unbounded leak, both service classes)** — `service-utils / GenericService.kt:190-223` and `GenericKtorService.kt:191-205`
6. **[medium] Scripting `System.exit`/`exit()` guards are brittle substring/regex checks that give false security, untested** — `script-utils-kotlin/KotlinScript.kt:74-75`, `script-utils-java/JavaScript.kt:143-144`, `script-utils-python/PythonScript.kt:59-63`
7. **[medium] `JavaScript.eval`/`evalScript` declare non-null `Any` returns but the engine can return null** — `script-utils-java / script/JavaScript.kt:120-167`
8. **[medium] `unzip()` decodes non-zipped bytes with the platform default charset** — `guava-utils / util/ZipExtensions.kt:62-79`
9. **[medium] Email-validation regex rejects valid addresses (plus-addressing, long TLDs, single-char labels)** — `email-utils / email/EmailUtils.kt:36-50`
10. **[medium] `SamplerGaugeCollector` lacks label size validation, failing on every scrape instead of at construction** — `prometheus-utils / metrics/SamplerGaugeCollector.kt:34-48`
11. **[medium] Concurrent waiters are silently lost (single-slot callback) in `GenericValueWaiter`/`BooleanWaiter`** — `guava-utils / concurrent/GenericValueWaiter.kt:91-143`
12. **[medium] Demo `main()` functions and a "hacking" `Example.kt` shipped in the published JAR** — `guava-utils / concurrent/ConditionalValue.kt:103-186`, `script-utils-kotlin / Example.kt:19-54`

## Correctness & Bugs (33)

### Click webhook fields use camelCase serial names, breaking real Resend deserialization
**Where:** `email-utils / src/main/kotlin/com/pambrose/common/webhook/Click.kt:31-43` · **Severity:** high
Every other webhook class in this module maps Resend's snake_case JSON keys (`Data` uses `@SerialName("created_at")`, `@SerialName("email_id")`), but `Click` declares its serial names in camelCase (`@SerialName("ipAddress")`, `@SerialName("linkTags")`, `@SerialName("userAgent")`). Resend's `email.clicked` payload sends `ip_address`, `link_tags`, and `user_agent`, so decoding a real payload throws `MissingFieldException` for the non-nullable `ipAddress`/`userAgent` and silently leaves `linkTags` null. The existing JSON round-trip test only round-trips against the class's own wrong names, so it cannot catch this.
**Suggested change:** Change the serial names to snake_case (`@SerialName("ip_address")`, `@SerialName("link_tags")`, `@SerialName("user_agent")`) and add a test that decodes a representative real Resend `email.clicked` payload to lock the wire contract.

### `singleToDoubleQuoted` does not escape inner double quotes despite its KDoc
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/util/StringExtensions.kt:63-68` · **Severity:** high
The KDoc says this converts a single-quoted string to double-quoted while "escaping inner double quotes", but the regex replacement `\"` is interpreted as an escaped literal quote, so no backslash is ever emitted. The existing test at `StringExtensionTests.kt:129` confirms it: `'te"st'` becomes `"te"st"` — an unescaped quote inside a double-quoted string, which is malformed. Either the behavior or the doc is wrong.
**Suggested change:** If escaping is intended, use the plain `String.replace` overload (which does not treat `\` specially) to insert a real backslash, and update the test to expect the escaped form. If escaping is not intended, drop the `replace` and fix the KDoc.

### `KtorServletRequest.getContentType()` returns `"*/*"` instead of null when absent
**Where:** `ktor-server-utils / src/main/kotlin/com/pambrose/common/servlet/KtorServletRequest.kt:93` · **Severity:** high
The Jakarta servlet contract requires `getContentType()` to return null when no Content-Type header is present, but Ktor's `request.contentType()` never returns null — for an absent header it returns `ContentType.Any`, whose `toString()` is `"*/*"`. This method therefore returns `"*/*"` (or the literal `"null"` if `contentType()` ever yielded null) for a request with no Content-Type, so servlet code checking `if (req.contentType == null)` misbehaves.
**Suggested change:** Read the raw header to preserve exact value and null-ness, e.g. `override fun getContentType(): String? = request.headers[HttpHeaders.ContentType]`, and change the declared return type to `String?` to match `HttpServletRequest`.

### UPSERT generates invalid SQL when all inserted columns are conflict columns
**Where:** `exposed-utils / src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:96-99` · **Severity:** medium
After appending ` DO UPDATE SET `, the code joins only the non-conflict columns. When every inserted column is part of the conflict key (e.g. a table whose only columns are its unique key, or an insert that sets only key columns), the filtered list is empty and nothing is appended, leaving a dangling `ON CONFLICT (...) DO UPDATE SET ` that PostgreSQL rejects with a syntax error. The correct behavior there is `DO NOTHING`.
**Suggested change:** Compute the update columns first and branch — emit `DO NOTHING` when the update-column list is empty, otherwise `DO UPDATE SET <col=EXCLUDED.col, ...>`.

### Service shutdown hooks are added on every `startUp` and never removed
**Where:** `service-utils / src/main/kotlin/com/pambrose/common/service/GenericService.kt:190-223,274-280` and `service-utils / src/main/kotlin/com/pambrose/common/service/GenericKtorService.kt:191-205,275-281` · **Severity:** medium
`startUp()` unconditionally calls `Runtime.getRuntime().addShutdownHook(...)`, but neither `shutDown()` nor `close()` ever removes it. For a `Closeable` service with an `isTestMode` flag designed to start/stop repeatedly, each cycle leaks a hook; at JVM exit every accumulated hook runs `shutDownHookAction` against an already-`TERMINATED` service, calling `stopAsync()`/`awaitTerminated()` redundantly. This is an unbounded leak plus wasted work at exit, duplicated verbatim across both service classes.
**Suggested change:** Store the hook in a field, register it in `startUp()`, and remove it in `shutDown()` wrapped in `runCatching` (since `removeShutdownHook` throws if the JVM is already shutting down). Apply identically to `GenericKtorService`.

### System.exit guards are brittle substring checks that give false security
**Where:** `script-utils-kotlin / src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:74-75` and `script-utils-java / src/main/kotlin/com/pambrose/common/script/JavaScript.kt:143-144` · **Severity:** medium
Both scripting modules attempt to block JVM termination with a literal substring match on `System.exit`. This is trivially bypassable: Kotlin scripts can call `kotlin.system.exitProcess(0)`, `Runtime.getRuntime().exit(0)`/`halt(0)`, or whitespace variants (`System . exit(1)`); Java scripts likewise reach `Runtime.getRuntime().exit/halt`, which is never caught. The Java check also only scans `expr`, not the `action` statement block, so `System.exit` placed in `action` bypasses it entirely. The Kotlin check is effectively dead — the real protection there comes from the auto-imported `com.pambrose.common.script.System` shadow whose `exit()` throws, which the substring match never exercises.
**Suggested change:** At minimum, scan both `expr` and `action` and broaden the check to a regex covering `System.exit`, `kotlin.system.exitProcess`, and `Runtime.getRuntime().(exit|halt)`. Better, drop the brittle substring approach in favor of real isolation and add a KDoc note that these classes are NOT a security sandbox.

### `JavaScript.eval`/`evalScript` declare non-null returns but the engine can return null
**Where:** `script-utils-java / src/main/kotlin/com/pambrose/common/script/JavaScript.kt:120-167` · **Severity:** medium
Both `evalScript` (line 123) and `eval` (line 142) declare a return type of `Any` and return `engine.eval(code)` directly. `javax.script.ScriptEngine.eval` is platform-nullable and can legitimately return null (arbitrary Java evaluating to null, or `getValue()` returning Object), so a null flows through a type the compiler believes is non-null, producing NPEs or contract violations downstream. The sibling `KotlinScript.eval` correctly declares `Any?`, making this an API inconsistency as well.
**Suggested change:** Declare these return types as `Any?` to match the engine's real nullability and the Kotlin sibling. If a non-null contract is genuinely intended, enforce it with `requireNotNull(engine.eval(code)) { "Script returned null" }` and document it rather than relying on the silent platform type.

### Python `exit`/`quit` guards reject legitimate method calls like `obj.exit()`
**Where:** `script-utils-python / src/main/kotlin/com/pambrose/common/script/PythonScript.kt:59-63,78-80` · **Severity:** medium
The `EXIT_PATTERN` `(?<!\w)exit\s*\(` and `QUIT_PATTERN` `(?<!\w)quit\s*\(` use a negative lookbehind for a word char, but `.` is not a word char. So `queue.quit()`, `myObj.exit()`, and `widget.exit(0)` all match and are rejected even though they invoke a user object's own method, not Python's builtin. Given the test suite already binds user objects with methods, an object exposing `exit()`/`quit()` is plausible and would be silently un-runnable. The guards also fire inside string literals and comments.
**Suggested change:** Exclude a preceding `.` as well as word chars: `(?<![\w.])exit\s*\(` and `(?<![\w.])quit\s*\(`. This still catches bare `exit(1)`/`quit(1)` while allowing `obj.exit(...)`.

### `unzip()` decodes non-zipped bytes with the platform default charset
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/util/ZipExtensions.kt:62-79` · **Severity:** medium
`zip()` always encodes with `StandardCharsets.UTF_8` and the GZIP branch decodes with UTF-8, but the non-zipped fallback uses `String(this)`, which decodes with the JVM default charset. On a JVM whose default is not UTF-8 (some Windows configs, or any `-Dfile.encoding` override), passing raw UTF-8 bytes through `unzip()` corrupts non-ASCII content and breaks round-tripping with bytes produced via `toByteArray(UTF_8)` elsewhere.
**Suggested change:** Decode explicitly to match the encoding side: `!isZipped() -> String(this, StandardCharsets.UTF_8)`.

### Email-validation regex rejects many valid addresses
**Where:** `email-utils / src/main/kotlin/com/pambrose/common/email/EmailUtils.kt:36-50` · **Severity:** medium
The hand-rolled regex behind `isValidEmail()`/`isNotValidEmail()` produces false negatives on legitimate input (verified empirically): plus-addressing (`user+tag@example.com`) is rejected though Resend supports it; TLDs longer than 4 chars (`.travel`, `.email`, `.photography`) fail because the final label is capped at `[a-zA-Z]{2,4}`; and single-character domain labels (`user@x.io`) fail because each label requires `>= 2` chars. For a published library whose sole validation entry point this is, that rejects real user addresses.
**Suggested change:** Use a more permissive, well-tested pattern (allow `+` and common local-part specials, drop the `{2,4}` TLD cap in favor of `{2,63}`, allow single-char labels) or delegate to a vetted validator; at minimum support `+` and long TLDs and document remaining limits in the public KDoc.

### `BooleanWaiter` shares a single mutable predicate / callback across all waiters
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/concurrent/GenericValueWaiter.kt:41-75` and `guava-utils / src/main/kotlin/com/pambrose/common/concurrent/LameBooleanWaiter.kt:46-104` · **Severity:** medium
`BooleanWaiter` stores its wait condition in a single `@Volatile predicate` that `waitUntilTrue`/`waitUntilFalse` overwrite before waiting; with a single callback slot, a second concurrent caller clobbers the first's predicate, so `monitorSatisfied()` evaluates the wrong condition for the original waiter. `LameBooleanWaiter` has the analogous single-callback limitation (a second `waitForChangeInValue` silently clobbers the first) plus an always-true `if (newValue == !oldValue)` clear-guard that makes the "continue to wait" comment dead logic.
**Suggested change:** Pass the predicate down per-wait (stored alongside each registered continuation) rather than via a shared field, and support multiple waiters via a list (or explicitly document the single-waiter constraint). In `LameBooleanWaiter`, drop the always-true guard and just clear the callback after invoking it.

### `SamplerGaugeCollector` does not validate label name/value size match
**Where:** `prometheus-utils / src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt:34-48` · **Severity:** medium
`labelNames` and `labelValues` are independent constructor params with no size check. A mismatch is not caught at construction; instead `MetricFamilySamples.Sample` throws `IllegalArgumentException` on every `collect()` — i.e. on every Prometheus scrape — surfacing far from the construction site and taking down the whole scrape rather than failing eagerly.
**Suggested change:** Validate in `init` before registering: `require(labelNames.size == labelValues.size) { "labelNames (${labelNames.size}) and labelValues (${labelValues.size}) must have the same size" }`.

### `obfuscate` throws ArithmeticException for `freq <= 0`
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/util/StringExtensions.kt:462` · **Severity:** medium
`obfuscate` computes `i % freq`; for `freq == 0` this throws `ArithmeticException` (divide by zero), and negative `freq` yields surprising modulo semantics. This is a public API with no validation on the parameter.
**Suggested change:** Add `require(freq > 0) { "freq must be positive" }` at the start (or document the constraint), and add a test for the `freq = 1` (all-masked) boundary.

### `booleanValue` silently coerces any non-`"true"` content to false
**Where:** `json-utils / src/main/kotlin/com/pambrose/common/json/JsonElementUtils.kt:47-48` · **Severity:** low
`booleanValue` uses `String.toBoolean()`, which returns true only for `"true"` and false for everything else (`"yes"`, `"1"`, garbage, a typo like `"ture"`). Unlike the int/double accessors, which throw on non-numeric content, the boolean accessor never signals an error, so `booleanValueOrNull` can never report malformed data and masks bad input.
**Suggested change:** Use the strict parser to mirror the fail-loud int/double accessors, e.g. `jsonPrimitive.boolean` (throws on non-boolean content) or at minimum `content.toBooleanStrict()`; if lenient coercion is intentional, document it in the KDoc.

### Empty-string key makes `get()` throw while `getOrNull()`/`containsKeys()` return null/false
**Where:** `json-utils / src/main/kotlin/com/pambrose/common/json/JsonElementUtils.kt:94-96,159-169` · **Severity:** low
`get(vararg keys)` does `keys.flatMap { it.split(".") }` with no empty filter, so `get("")` yields `[""]` and `element("")` throws `IllegalArgumentException`. But `containsKeys("")` returns false and `getOrNull("")` returns null, violating the "getOrNull is like get but returns null when missing" contract. Meanwhile `getByPath` deliberately filters empty segments, so the three navigation entry points disagree on empty path components.
**Suggested change:** Pick one policy and apply it across `get`, `getOrNull`, and `containsKeys` — either filter empty segments (`.filter { it.isNotEmpty() }`, mirroring `getByPath`) or `require(keys.none { it.isBlank() })` — and add a test covering `get("")`/`getOrNull("")`.

### InterruptedException swallowed without restoring the interrupt flag
**Where:** `core-utils / src/main/java/com/pambrose/common/util/MiscJavaFuncs.java:36-42` and `grpc-utils / src/main/kotlin/com/pambrose/common/utils/ServerExtensions.kt:33-37` · **Severity:** low
`MiscJavaFuncs.sleepMillis` calls `e.printStackTrace()` and continues on `InterruptedException`, while `shutdownWithJvm`'s shutdown-hook catch is an empty `// do nothing` body. Both discard the interrupt status, so callers higher up lose the ability to observe cancellation; the library also prints to stderr instead of restoring/propagating the signal.
**Suggested change:** Restore the flag in the catch block with `Thread.currentThread().interrupt()` (and drop the stderr print in the Java helper). In `shutdownWithJvm`, `shutdownGracefully` already calls `shutdownNow()` in its finally, so just restoring the interrupt flag is sufficient.

### `SamplerGaugeCollector` sampler exception breaks the entire scrape
**Where:** `prometheus-utils / src/main/kotlin/com/pambrose/common/metrics/SamplerGaugeCollector.kt:45-48` · **Severity:** low
`data()` is invoked synchronously inside `collect()`, which Prometheus calls on every scrape. If the user-supplied sampler throws (e.g. transient I/O), the exception propagates out of `collect()` and aborts the whole registry scrape, taking down all other metrics, with no metric-name context attached.
**Suggested change:** Adopt a documented failure policy — guard the call, log it with context, and emit `Double.NaN` so the rest of the scrape survives: `val value = runCatching { data() }.getOrElse { logger.warn(it) { "Sampler for $name threw" }; Double.NaN }`.

### `InstrumentedThreadFactory.newThread` counts a thread even when the delegate returns null
**Where:** `prometheus-utils / src/main/kotlin/com/pambrose/common/concurrent/InstrumentedThreadFactory.kt:53-58` · **Severity:** low
`ThreadFactory.newThread` is allowed to return null when creation is rejected, but `created.inc()` runs unconditionally afterward, so a rejected creation still increments `threads_created`. The `InstrumentedRunnable.run()` running/terminated counters then never fire for that phantom thread, permanently skewing the created-vs-running+terminated invariant the code explicitly maintains.
**Suggested change:** Only increment when a thread was produced: `val thread = delegate.newThread(InstrumentedRunnable(runnable)); if (thread != null) created.inc(); return thread`, and make the return type `Thread?` to reflect the platform nullability callers can already observe.

### `MiscJavaFuncs.random` can return negative values and is modulo-biased
**Where:** `core-utils / src/main/java/com/pambrose/common/util/MiscJavaFuncs.java:24-30` · **Severity:** low
`random(int)` returns `Math.abs(random.nextInt() % upper)`. When `nextInt()` is `Integer.MIN_VALUE` and `upper` is a power of two, `MIN_VALUE % upper` can be `MIN_VALUE`, and `Math.abs(Integer.MIN_VALUE)` is still negative — violating the implicit `0..upper` contract. `random(long)` has the same flaw with `Long.MIN_VALUE`, and both suffer modulo bias. The Kotlin `Int.random()`/`Long.random()` already do this correctly.
**Suggested change:** Use the bounded RNG APIs, which are non-negative and unbiased: `return random.nextInt(upper);` and `return random.nextLong(upper);` (available on JDK 17), guaranteeing a value in `[0, upper)`.

### Service-utils minor correctness gaps: lateinit flags, failure log level, Zipkin URL
**Where:** `service-utils / GenericService.kt:178-184,195-218,242-243,163` and `service-utils / GenericKtorService.kt:163-166,179-183,196-217,243-244` · **Severity:** low
Three smaller issues duplicated across both service classes: (1) `metricsService`/`jmxReporter`/`zipkinReporterService` are gated only by captured boolean flags rather than the `::property.isInitialized` check used (robustly) for `servletService`, so if `initServletService()` is skipped, access throws an opaque `UninitializedPropertyAccessException` instead of being safely no-op'd. (2) The `ServiceManager` failure listener logs a service *failure* at `logger.info`, making genuine failures easy to miss in warn+-filtered logs. (3) The Zipkin URL is built with raw interpolation `"http://${hostname}:${port}/${path}"`, so a conventional leading-slash path (`/api/v2/spans`) yields a double slash.
**Suggested change:** Guard the lateinit metrics/zipkin properties with their own `isInitialized` checks for consistency; log the failure case at `error` (or `warn`) level; and normalize the Zipkin path, e.g. `"http://${hostname}:${port}/${path.removePrefix("/")}"` (or validate that `path` has no leading slash).

### `LambdaServlet` evaluates the body lambda after acquiring the writer
**Where:** `jetty-utils / src/main/kotlin/com/pambrose/common/servlet/LambdaServlet.kt:50-55` · **Severity:** low
`writer.use { it.println(block()) }` invokes the arbitrary user `block()` after status/headers are set and the writer is open. If `block()` throws, the response state is already partially mutated and, depending on buffering, may be committed, producing a 200 with an empty/partial body instead of a clean 500.
**Suggested change:** Evaluate the body before touching the response: `val body = block()` first, then set status/headers and `writer.use { it.println(body) }`, so a failing lambda surfaces before any response state is mutated and the container can emit a proper error.

### `KtorServletResponse` header storage is case-sensitive
**Where:** `ktor-server-utils / src/main/kotlin/com/pambrose/common/servlet/KtorServletResponse.kt:39,60-80` · **Severity:** low
HTTP header names are case-insensitive and the Servlet API requires `containsHeader`/`getHeader`/`getHeaders` to treat them so, but a plain `mutableMapOf<String, MutableList<String>>` makes `setHeader("Content-Type", ...)` and `getHeader("content-type")` miss each other, producing duplicate or missing headers when set and read with different casing.
**Suggested change:** Use a case-insensitive map, e.g. `sortedMapOf<String, MutableList<String>>(String.CASE_INSENSITIVE_ORDER)`.

### `ServletRoute` drops the servlet's character encoding when forwarding the response
**Where:** `ktor-server-utils / src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:56-59` · **Severity:** low
`KtorServletResponse` tracks `setCharacterEncoding()`/`getCharacterEncoding()` (default UTF-8), but the route reconstructs the `ContentType` solely from `getContentType()` and never applies the charset, and creates `PrintWriter(buffer, true)` with the platform default charset. A servlet setting a non-UTF-8 encoding, or relying on the charset being advertised, loses that information silently.
**Suggested change:** Apply the response character encoding to the `ContentType` when the parsed type carries no charset (`ct.withCharset(charset(response.characterEncoding))`), and construct `getWriter()`'s `PrintWriter` with `charEncodingValue` rather than the platform default.

### `PythonScript` thread-safety and late-binding foot-guns
**Where:** `script-utils-python / src/main/kotlin/com/pambrose/common/script/PythonScript.kt:46-55,65-68` · **Severity:** low
Two related issues: (1) `eval()` is `@Synchronized` and iterates `valueMap`, but the overridden `add()` writes the non-thread-safe `valueMap` without synchronization, so concurrent use can race (`ConcurrentModificationException` or lost writes). (2) Bindings are pushed to the engine only on the first `eval()` (guarded by `!initialized`), so any variable `add()`ed after the first `eval()` is never bound — `add("a",1); eval("a"); add("b",2); eval("b")` fails with a `NameError` on `b`.
**Suggested change:** Mark `add()` `@Synchronized` to match `eval()` (or remove `@Synchronized` and document single-thread-per-instance use, which the pool already guarantees); and in `add()`, when `initialized` is already true, also call `engine.put(name, value)` so late additions take effect (or document that all `add()` calls must precede the first `eval()`).

### `JavaScript.varDecls` emits unqualified simpleName, risking unresolved types / collisions
**Where:** `script-utils-java / src/main/kotlin/com/pambrose/common/script/JavaScript.kt:52-64` · **Severity:** low
`varDecls` uses `javaClass.simpleName` for the field type, which is unqualified. A bound value whose class is not `java.lang.*` (or not explicitly `import()`ed) generates a field referencing a type the generated `Main` class cannot resolve, forcing callers to remember a matching `import()`; two classes with the same simpleName in different packages also collide.
**Suggested change:** Prefer the qualified name when no primitive equivalent exists, e.g. `javaClazz.javaPrimitiveType?.name ?: (javaClazz.canonicalName ?: javaClazz.name)`, so declarations compile without a matching `import()`. If unqualified names are intentional, document the per-type `import()` requirement.

### `gRPC` `maxRetryAttempts` applied even when retry is disabled
**Where:** `grpc-utils / src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:113-117` · **Severity:** low
In `createNettyChannel`, `enableRetry()` is gated on `enableRetry==true` but `builder.maxRetryAttempts(...)` is called whenever `maxRetryAttempts > -1`. With the default `enableRetry=false, maxRetryAttempts=5`, `maxRetryAttempts(5)` is applied to every channel, where it is a silent no-op in gRPC. This contradicts the KDoc and means callers who set `maxRetryAttempts` but forget `enableRetry` get no retries and no error.
**Suggested change:** Gate `maxRetryAttempts` on `enableRetry` so the two cannot diverge: call `builder.maxRetryAttempts(...)` only inside the `if (enableRetry)` block (or document that it requires `enableRetry=true` and is otherwise ignored).

### `hostInfo` resolves localhost twice and uses `!!` under a narrow catch
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/util/MiscFuncs.kt:48-57` · **Severity:** low
`hostInfo` calls `InetAddress.getLocalHost()` twice (two lookups that could observe different state) and applies `!!` to its results inside a try that only catches `UnknownHostException`. The `hostName`/`hostAddress` properties are declared non-null in Java, so the `!!` is unnecessary, and the double resolution is wasteful.
**Suggested change:** Resolve once — `val localHost = InetAddress.getLocalHost(); HostInfo(localHost.hostName, localHost.hostAddress)` — drop the `!!`, and remove the commented-out `logger.debug` dead code at line 52.

## Design & Architecture (26)

### Health check captures a static snapshot and never reflects live state
**Where:** `dropwizard-utils / src/main/kotlin/com/pambrose/common/util/MetricsUtils.kt:34-42`  ·  **Severity:** medium
`newBacklogHealthCheck` takes `backlogSize` as a plain `Int`, which is captured by value when the closure is created. A Dropwizard `HealthCheck` is registered once and executed repeatedly, but every `execute()` here re-evaluates the same captured number and always returns the same result, so the check provides no ongoing monitoring value. This is inconsistent with the sibling `newMapHealthCheck`, which holds a `Map` reference and re-reads `map.size` on each run; the KDoc even calls `backlogSize` "the current backlog size", confirming it is only a point-in-time snapshot.
**Suggested change:** Accept a supplier so the check re-evaluates each execution, mirroring `newMapHealthCheck`: `fun newBacklogHealthCheck(backlogSize: () -> Int, size: Int) = ...`. If the snapshot form must be retained for source compatibility, add the supplier-based overload as the preferred path and document that the old form is constant.

### Admin servlet path normalization diverges between Jetty and Ktor variants
**Where:** `service-utils / src/main/kotlin/com/pambrose/common/service/GenericService.kt:125-128` (with `ServletService.kt:53,69` and `KtorServletService.kt:54-56,76`)  ·  **Severity:** medium
The two otherwise-parallel base classes treat servlet paths inconsistently. `GenericKtorService` normalizes each admin path with `.ensureLeadingSlash()` before storing it and registers routes verbatim, while `GenericService` stores the raw path and `ServletService` prepends `/` at registration. The net effect: a config value of `"/ping"` yields route `"/ping"` under Ktor but `"//ping"` under Jetty, and `"ping"` yields `"/ping"` under Jetty but `"ping"` (no leading slash) under Ktor. User-supplied servlets added via `servletInit` diverge the same way, and the two `toString()` implementations reflect the inconsistency too. A library offering two interchangeable variants should normalize identically.
**Suggested change:** Pick one normalization strategy and apply it in both variants — either normalize in the `Generic` classes (add `.ensureLeadingSlash()` and stop prepending `/` in `ServletService`), or normalize centrally inside `ServletGroup.addServlet` / `HttpServletGroup.addServlet` so both classes and their `toString()` agree.

### Concurrent waiters are silently lost (single-slot callback)
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/concurrent/GenericValueWaiter.kt:91-143`  ·  **Severity:** medium
`onConditionChanged` is a single nullable lambda. If two coroutines call `waitForCondition` concurrently, the second registration overwrites the first, so when the condition is satisfied `checkCondition` invokes only the last-registered callback and the earlier waiter is silently lost (it can only ever return via timeout/`false`). The class is presented as a general-purpose waiter, so this is a real correctness limitation, not just a doc gap.
**Suggested change:** Replace the single slot with a collection of pending continuations/callbacks (e.g. a `MutableList` that `checkCondition` drains and resumes), keeping the API the same while making concurrent waits correct. If single-waiter is intended, document and guard against concurrent waiters.

### `includeUserInAuth` conflates user identity with the password placeholder
**Where:** `redis-utils / src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:81-84`  ·  **Severity:** medium
The property is named and documented as deciding whether the user should be included in `AUTH`, but the predicate also returns `false` when `password == "none"`. A URL like `redis://realuser:none@host:6379` parses a legitimate username, yet the user is silently dropped from `AUTH` purely because the password equals the sentinel `"none"`. The password gate belongs in `buildClientConfig` (which already guards on `info.password.isNotBlank()`), not in a property describing the user, and the `"none"` placeholder is undocumented magic.
**Suggested change:** Make the predicate solely about user identity (e.g. `user.isNotBlank() && user != "default" && user != "user"`) and have `buildClientConfig` own password gating by normalizing `password == "none"` to empty before its `isNotBlank()` check. Document the `"none"`/`"user"`/`"default"` placeholder conventions in the KDoc.

### Near-total duplication between `GenericService` and `GenericKtorService`
**Where:** `service-utils / src/main/kotlin/com/pambrose/common/service/GenericService.kt:66-282`  ·  **Severity:** low
The two classes are ~95% identical: same constructor parameters, fields, `startUp`/`shutDown`/`close`/`addService(s)`/`registerHealthChecks` bodies, and the same `shutDownHookAction` companion. The only real differences are the servlet-hosting backend (`ServletService`+`ServletGroup` vs `KtorServletService`+`HttpServletGroup`) and the extra `initKtor` parameter. This duplication is the root cause of the same bugs and inconsistencies (including the path-normalization issue above) needing to be fixed twice.
**Suggested change:** Extract the shared lifecycle/metrics/zipkin/health-check machinery into an `abstract class AbstractGenericService<T>` holding the registries, enabled flags, `ServiceManager` wiring, lifecycle methods, and the companion hook. Have both classes extend it and override only the servlet-group construction.

### Servlet lifecycle: `init()` called but `destroy()` never invoked
**Where:** `ktor-server-utils / src/main/kotlin/com/pambrose/common/servlet/ServletRoute.kt:44-45`  ·  **Severity:** low
`servlet.init()` runs when the route is registered, but there is no corresponding `servlet.destroy()` wired to application shutdown, so servlets that allocate resources in `init()` (thread pools, connections) leak them on shutdown/reload. `init()` also runs on the registering thread rather than `Dispatchers.IO`, so a blocking init runs on the caller's (often coroutine) thread.
**Suggested change:** Register a shutdown hook inside `route(path) { }`, e.g. `application.monitor.subscribe(ApplicationStopping) { servlet.destroy() }`. At minimum document that calling `destroy()` is the caller's responsibility.

### `Closeable.close()` is an empty placeholder that does not release the engine
**Where:** `script-utils-kotlin / src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:89-91`  ·  **Severity:** low
`KotlinScript` implements `Closeable` specifically so callers can write `KotlinScript().use { ... }` (which all tests do), but `close()` is annotated `// Placeholder` and does nothing. This is misleading: callers reasonably assume `use {}` frees engine resources/bindings, so a held reference leaks script state.
**Suggested change:** Implement real cleanup (e.g. `resetContext(...)` to clear bindings/`valueMap`/`typeMap`), or replace `// Placeholder` with a KDoc explaining the deliberate no-op contract.

### `blockingGet` cannot reuse an injected client
**Where:** `ktor-client-utils / src/main/kotlin/com/pambrose/common/dsl/KtorDsl.kt:121-131`  ·  **Severity:** low
Unlike `withHttpClient`/`httpClient`, `blockingGet` offers no `httpClient` parameter, so it always spins up and tears down a fresh `HttpClient` per call. That makes it impossible to inject a `MockEngine`-backed client for unit tests and prevents connection/client reuse across many blocking GETs, an asymmetry with the rest of the API.
**Suggested change:** Accept an optional `httpClient: HttpClient? = null` and forward it to `withHttpClient`, matching the other helpers.

### Inconsistent `tlsContext` defaulting between `channel()` and `server()`
**Where:** `grpc-utils / src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:61-69, 133-137`  ·  **Severity:** low
`server()` defaults `tlsContext` to `PLAINTEXT_CONTEXT`, but `channel()` makes it a required parameter positioned after several defaulted parameters, forcing every `channel()` caller to pass `tlsContext` explicitly (often `TlsContext.PLAINTEXT_CONTEXT`) even for in-process channels where TLS is ignored.
**Suggested change:** Add `tlsContext: TlsContext = PLAINTEXT_CONTEXT` to `channel()` for symmetry with `server()` (the import is already present).

### `Parameters.getEmail` does not normalize like `String.toResendEmail`
**Where:** `email-utils / src/main/kotlin/com/pambrose/common/email/Email.kt:59-72`  ·  **Severity:** low
`toResendEmail()` lowercases and trims to the project's canonical form, but `getEmail(name)` builds `Email(it)` directly from the raw parameter value. These are the two entry points for turning external input into an `Email`, yet they disagree — an address arriving via a Ktor form parameter keeps its original case and whitespace — making equality comparisons and downstream lookups against normalized emails unreliable depending on which path was used.
**Suggested change:** Have `getEmail` reuse the normalization: `fun Parameters.getEmail(name: String) = this[name]?.toResendEmail() ?: EMPTY_EMAIL`. If raw values are intentionally wanted somewhere, document the difference.

### `eval()` throws `IllegalArgumentException`, inconsistent with the module's `ScriptException` convention
**Where:** `script-utils-common / src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluator.kt:25-31`  ·  **Severity:** low
Every other failure path in this module signals problems via `javax.script.ScriptException`, but a non-`Boolean` evaluation result here throws `IllegalArgumentException`. Callers who catch `ScriptException` to handle all script failures will let this one slip past, and the exception type is part of the public contract.
**Suggested change:** Throw `ScriptException` for consistency, e.g. `?: throw ScriptException("Expression did not evaluate to Boolean, got ${result?.javaClass?.simpleName ?: "null"}")`. If `IllegalArgumentException` is intentional, document it in the (currently missing) KDoc.

### `evalScript` has no `System.exit` guard while `eval` does
**Where:** `script-utils-java / src/main/kotlin/com/pambrose/common/script/JavaScript.kt:119-135`  ·  **Severity:** low
`eval` rejects scripts containing `System.exit`, but `evalScript` — which evaluates an even more arbitrary raw Java string — performs no such check. A caller who believes the wrapper blocks `System.exit` (because `eval` does) gets no protection from `evalScript`. The two public entry points should have a consistent policy.
**Suggested change:** Apply the same guard at the top of `evalScript`, or explicitly document in its KDoc that it performs no `System.exit` filtering and is intended for trusted input only.

### `isNumber` reports true for quoted JSON strings
**Where:** `json-utils / src/main/kotlin/com/pambrose/common/json/JsonElementUtils.kt:67-68`  ·  **Severity:** low
`isNumber` tests `jsonPrimitive.content.toDoubleOrNull() != null`, which only inspects content and not whether the primitive was quoted. A JSON string value `"42"` (where `isString == true`) therefore also reports `isNumber == true`, so both predicates are simultaneously true — surprising for a type-discrimination helper.
**Suggested change:** Exclude quoted strings so the predicate reflects the JSON type: `val JsonElement.isNumber get() = this is JsonPrimitive && !isString && content.toDoubleOrNull() != null`, and document that it reflects an unquoted numeric primitive.

### `ConditionalValue.set` relies on `yield()` to notify waiters
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/concurrent/ConditionalValue.kt:97-100`  ·  **Severity:** low
`set()` updates the `MutableStateFlow` then calls `yield()` "to allow waiting coroutines to observe the change". `StateFlow` already notifies collectors; the `yield()` is a fragile attempt to force scheduling and does not guarantee a waiting `waitUntil` coroutine runs before `set()` returns. Worse, `StateFlow` is conflated, so `set(false)` immediately followed by `set(true)` may never expose `false`, and the KDoc overstates the guarantee.
**Suggested change:** Drop the `yield()` (`StateFlow.first` already suspends/resumes correctly) and update the doc to note `StateFlow` is conflated, so transient overwritten values are not guaranteed to be observed. If every transition matters, document that `ConditionalValue` is unsuitable.

### `AtomicUtils.criticalSection` provides no mutual exclusion despite its name
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/util/AtomicUtils.kt:32-39`  ·  **Severity:** low
`criticalSection` sets the flag to `true` on entry and `false` in `finally` regardless of prior value. The name and doc imply mutual-exclusion semantics, but it provides none: concurrent or nested invocations clobber each other, and a nested call resets the flag to `false` while the outer call is still running. As written it is just a try/finally set-true/set-false wrapper that can mislead callers into assuming re-entrancy is guarded.
**Suggested change:** Either document explicitly that it does not provide mutual exclusion (nested/concurrent use corrupts the flag), or implement `compareAndSet(false, true)` at entry and restore the prior value in `finally` so re-entry is detectable: `val acquired = compareAndSet(false, true); try { ... } finally { if (acquired) store(false) }`.

### DSL callback setters throw on second assignment, an undocumented sharp edge
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/dsl/GuavaDsl.kt:72-74, 96-116, 132-136, 171-209`  ·  **Severity:** low
Every callback field in `ServiceManagerListenerHelper` and `ServiceListenerHelper` is backed by `SingleAssignVar.singleAssign()`, which throws `IllegalStateException` on a second assignment (even setting to `null` counts). In a configuration DSL it is natural to write a block like `running { ... }` twice while editing or composing, and that throws at runtime. This single-assignment semantics leaks into the DSL, is undocumented, and is surprising for a builder where last-write-wins is conventional.
**Suggested change:** Replace the delegates with plain nullable vars for last-write-wins semantics, or keep single-assignment intentionally but document it in each setter's KDoc. Add a test covering the double-assignment case to lock in the chosen behavior.

### `GenericMonitor` retry loop is copy-pasted three times with a negative-sentinel timeout
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/concurrent/GenericMonitor.kt:181-306`  ·  **Severity:** low
`waitUntilTrue`, `waitUntilTrueWithInterruption`, and `waitUntilFalse` duplicate the same loop structure with the magic `(-1).seconds` default for `maxWait` and the `maxWait > 0.seconds` "no limit" sentinel. Using a negative `Duration` as "no limit" is non-obvious, and the loop body is duplicated three times, so any timing fix must be applied in three places.
**Suggested change:** Extract the shared loop into one private helper that the three public methods delegate to, and express "no limit" as `Duration.INFINITE` (or a nullable `maxWait`) instead of a negative sentinel.

### `ZipkinConfig.serviceName` is dropped, forcing it to be re-passed to `newTracing`
**Where:** `service-utils / src/main/kotlin/com/pambrose/common/service/GenericService.kt:162-168` (with `ZipkinConfig.kt:35`)  ·  **Severity:** low
`ZipkinConfig` carries a `serviceName` documented as "the name used to identify this service in Zipkin traces", but when constructing `ZipkinReporterService` only the URL is passed and `serviceName` is dropped. `newTracing(serviceName)` then forces the caller to supply the name again, making the config field effectively dead at this wiring point.
**Suggested change:** Thread `zipkinConfig.serviceName` into `ZipkinReporterService` and use it as the default for `newTracing`, e.g. `fun newTracing(serviceName: String = defaultServiceName)`, so the config field is honored. Otherwise document it as informational only.

### `RecaptchaService` "configured?" gate is duplicated and partly unreachable
**Where:** `recaptcha-utils / src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:86-89, 142-154`  ·  **Severity:** low
The private `verifyRecaptcha` is only called by `validateRecaptcha`, which already wraps the call in `if (isRecaptchaConfigured(config))`. `verifyRecaptcha` then re-checks the same condition, so its "return true when not configured" branch is unreachable in practice and the two checks can drift.
**Suggested change:** Keep the gate only in the public `validateRecaptcha` and let `verifyRecaptcha` assume it is called only when configured, documenting that precondition; remove the duplicated/unreachable branch.

### `sendEmail` logs then rethrows, producing duplicate error noise
**Where:** `email-utils / src/main/kotlin/com/pambrose/common/email/ResendService.kt:53-73`  ·  **Severity:** low
The `runCatching { ... }.onFailure { logger.error(...) }.getOrThrow()` pattern logs at ERROR and then rethrows, so every failure is recorded twice (here with a full stack trace, and again at the call site). The library decides logging policy on the caller's behalf, and the `runCatching` wrapper adds no value since the result is immediately rethrown.
**Suggested change:** Let the exception propagate without logging (caller decides logging), or if logging-then-rethrow is intentional, drop `runCatching` for a direct `try { ... } catch (e) { logger.error(e) { ... }; throw e }` for clarity.

### Duplicated bindings-initialization block in `eval` and `evalScript`
**Where:** `script-utils-java / src/main/kotlin/com/pambrose/common/script/JavaScript.kt:124-127, 158-161`  ·  **Severity:** low
The lazy block that copies `valueMap` into `engineBindings` and flips the `initialized` flag is duplicated verbatim across both entry points. This is the kind of duplication that drifts: a change to one path's init logic (e.g. the `System.exit` guard or null handling) can be missed in the other.
**Suggested change:** Extract `private fun ensureBindings()` and call it from both `eval` and `evalScript`.

### `VersionServlet` duplicates `LambdaServlet` response logic (incl. magic Cache-Control literal)
**Where:** `jetty-utils / src/main/kotlin/com/pambrose/common/servlet/VersionServlet.kt:33-52` and `LambdaServlet.kt:52`  ·  **Severity:** low
`VersionServlet.doGet` is byte-for-byte identical to `LambdaServlet.doGet` (same `SC_OK`, same `Cache-Control` header, content type, and `println`), differing only in a cosmetic `serialVersionUID`. The `"Cache-Control"` name and its `"must-revalidate,no-cache,no-store"` value are also repeated verbatim across both files. If the response semantics change, both must be edited in lockstep.
**Suggested change:** Define `VersionServlet` as a thin subclass, e.g. `class VersionServlet(version: String) : LambdaServlet({ version })` (which already defaults content type to `text/plain`), and extract the no-cache value to a shared `private const val NO_CACHE` referenced by both, defining the policy once.

### Vague unhealthy message omits the threshold
**Where:** `dropwizard-utils / src/main/kotlin/com/pambrose/common/util/MetricsUtils.kt:41, 58`  ·  **Severity:** low
The unhealthy result message `"Large size: $backlogSize"` (and `"Large size: ${map.size}"`) is unclear and never reports the threshold that was exceeded. A health-check message surfaced on a Dropwizard admin page is most useful when it states both the observed value and the limit it crossed.
**Suggested change:** Include both the observed value and the threshold, e.g. `"Backlog size $backlogSize exceeds threshold $size"` and `"Map size ${map.size} exceeds threshold $size"`.

### Checksum length hardcoded as magic `32`, decoupled from the algorithm
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/util/IOExtensions.kt:160-172`  ·  **Severity:** low
`verifyChecksum` hardcodes `32` (and ranges `0..31` / `32 until size`) for the SHA-256 digest length, so the relationship to the algorithm string is implicit and fragile, and `withChecksum`/`verifyChecksum` independently recreate the digest.
**Suggested change:** Introduce a `private const val SHA256_LEN = 32` (or derive it from `MessageDigest.getInstance("SHA-256").digestLength`) and use it in both functions to keep the checksum length and algorithm in sync.

### `captureStdout` mutates process-wide `System.out` without warning
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/util/MiscFuncs.kt:189-199`  ·  **Severity:** low
`captureStdout` swaps the JVM-global `System.out`, which is process-wide and not thread-safe, so concurrent callers clobber each other's redirection. The `finally` correctly restores `out`, but for a published library this global-state hazard is an undocumented sharp edge.
**Suggested change:** Document in the KDoc that this mutates process-wide `System.out` and is not safe to call concurrently. Optionally use `PrintStream(baos, true, Charsets.UTF_8)` and `baos.toString(Charsets.UTF_8)` for deterministic charset handling.

## Idiomatic Kotlin (9)

The findings in this category are all low-severity polish items. They are grouped below, with the two most broadly-reaching patterns (builder-lambda invocation and exception-based parsing) first since they recur across multiple modules.

### `apply { block(this) }` / `run { block(this); build() }` instead of `apply(block)`
**Where:** `jetty-utils / JettyDsl.kt:37,45` · `zipkin-utils / ZipkinDsl.kt:33-38` · **Severity:** low
Several DSL builders manually invoke a receiver-typed lambda inside `apply`/`run` (e.g. `Server(port).apply { block(this) }` and `Tracing.newBuilder().run { block(this); build() }`). Since `block` already carries the correct receiver type, the explicit `block(this)` call is redundant noise and obscures the standard Kotlin builder idiom. Note: `GrpcDsl.attributes` uses the same `run` pattern, so leaving that one untouched (or converting all three) is a consistency call.
**Suggested change:** Use `Server(port).apply(block)` and `ServletContextHandler().apply(block)` in JettyDsl; use `Tracing.newBuilder().apply(block).build()` in ZipkinDsl.

### Exception-based parsing for `isInt`/`isFloat`/`isDouble`
**Where:** `core-utils:strings-numbers-collections / StringExtensions.kt:247-281` · **Severity:** low
The three numeric predicates parse-and-catch a broad `Exception` to decide validity. Catching `Exception` masks unexpected errors, relies on exception control flow, and is wordier than the standard library equivalent. `toInt()`/`toFloat()`/`toDouble()` only throw `NumberFormatException`, so the `*OrNull` variants are exactly equivalent and clearer.
**Suggested change:** `fun String.isInt() = toIntOrNull() != null`, `fun String.isFloat() = toFloatOrNull() != null`, `fun String.isDouble() = toDoubleOrNull() != null`.

### SCAN loop hardcodes the `"0"` cursor sentinel
**Where:** `redis-utils / RedisUtils.kt:392-404` · **Severity:** low
`scanKeys` seeds the cursor with the literal `"0"` and terminates on `cursorVal == "0"`, duplicating Jedis internals. Jedis exposes `ScanParams.SCAN_POINTER_START` and `ScanResult.isCompleteIteration()` (defined as `SCAN_POINTER_START.equals(getCursor())`) for exactly this, which states intent and removes the magic string.
**Suggested change:** Seed with `ScanParams.SCAN_POINTER_START` and break on `res.isCompleteIteration`, e.g. `var cursorVal = ScanParams.SCAN_POINTER_START; while (true) { val res = scan(cursorVal, scanParams); res.result.forEach { yield(it) }; if (res.isCompleteIteration) break; cursorVal = res.cursor }`.

### Compiled `Regex(":")` to split on a literal character
**Where:** `redis-utils / RedisUtils.kt:66,90-91` · **Severity:** low
A top-level `colon = Regex(":")` field is used only to split `userInfo` on a literal colon. `String.split` has a vararg-of-`String` overload that splits on literal delimiters with no regex compilation, which is cheaper and removes a field whose purpose is non-obvious at the call site. The current code also splits `userInfo` twice.
**Suggested change:** Drop the `colon` field, parse `userInfo` once into a local, and use the literal overload: `it.userInfo?.split(":", limit = 2)` then `getOrElse(0)/getOrElse(1)` with `.orEmpty()`.

### `ResultRow.get(index)` uses an O(n) filter/map/firstOrNull chain
**Where:** `exposed-utils / ExposedUtils.kt:63-65` · **Severity:** low
The `get(index)` operator builds an intermediate filtered list and a mapped list just to take the first element, costing two allocations and reading less clearly than a single pass over `fieldIndex.entries`.
**Suggested change:** `operator fun ResultRow.get(index: Int) = fieldIndex.entries.firstOrNull { it.value == index }?.let { this[it.key] } ?: throw IllegalArgumentException("No value at index $index")`. (Whether a null value at a valid index should be returned vs. treated as "not found" is a separate semantic question worth confirming.)

### Banner blank-line detection reimplements `isNotBlank()`
**Where:** `core-utils:concurrency-time-version / Banner.kt:45-46` · **Severity:** low
`line.trim { arg -> arg <= ' ' }.isNotEmpty()` is a hand-rolled, allocating reimplementation of `isNotBlank()`. The custom `char <= ' '` predicate also differs subtly from Kotlin's whitespace definition, and it allocates a trimmed string only to test emptiness.
**Suggested change:** Use `if (line.isNotBlank()) { ... }` inside the `forEachIndexed` loop — no allocation, clearer intent.

### Redundant `.let` around `ifBlank` in recipient logging
**Where:** `email-utils / ResendService.kt:66-68` · **Severity:** low
`to.joinToString(", ").let { it.ifBlank { "None" } }` wraps `ifBlank` in an unnecessary `.let`; `ifBlank` already operates on the receiver string.
**Suggested change:** `val toStr = to.joinToString(", ").ifBlank { "None" }` (and likewise for `ccStr`/`bccStr`).

### `ArrayList()` instead of `mutableListOf()`
**Where:** `ktor-server-utils / HerokuHttpsRedirect.kt:87` · **Severity:** low
`excludePredicates` is initialized with the Java `ArrayList()` constructor. `mutableListOf()` returns the same `ArrayList`-backed `MutableList` while reading as idiomatic Kotlin and matching the rest of the codebase.
**Suggested change:** `val excludePredicates: MutableList<CallPredicate> = mutableListOf()`.

## Performance (5)

### `getOrNull` traverses the JSON tree twice on every lookup
**Where:** `json-utils / json-utils/src/main/kotlin/com/pambrose/common/json/JsonElementUtils.kt:106-107`  ·  **Severity:** low
`getOrNull` calls `containsKeys(*keys)` to walk the full key path, then — if present — calls `get(*keys)` to walk the identical path a second time, re-splitting every key on `.` both times. Because `getOrNull` is the foundation of every `*OrNull` accessor in the file (`stringValueOrNull`, `intValueOrNull`, `jsonObjectValueOrNull`, etc.), the doubled traversal multiplies across the whole nullable-accessor API and grows with path depth.
**Suggested change:** Walk the path once and short-circuit on the first missing key:
```kotlin
fun JsonElement.getOrNull(vararg keys: String): JsonElement? {
  var curr: JsonElement? = this
  for (k in keys.flatMap { it.split(".") }) {
    curr = (curr as? JsonObject)?.get(k) ?: return null
  }
  return curr?.takeIf { it != JsonNull }
}
```

### `toFormattedString` builds a fresh `Json` instance on every call
**Where:** `json-utils / json-utils/src/main/kotlin/com/pambrose/common/json/JsonElementUtils.kt:209-223`  ·  **Severity:** low
`prettyPrint(indent)` constructs a brand-new `Json` configuration (with its `JsonConfiguration` and serializers module) every time `toFormattedString` is invoked. For the common default-indent case this allocation is pure waste, and callers formatting many elements in a loop pay it on each iteration. It is also inconsistent with the rest of the file, which caches `Json` instances via `lazy`/`object` (`JsonContentUtils.prettyFormat`, `JsonDefaults`).
**Suggested change:** Reuse the already-cached two-space `JsonContentUtils.prettyFormat` for the default-indent path and only build a custom `Json` when a non-default indent is supplied:
```kotlin
fun JsonElement.toFormattedString(indent: String = "  "): String =
  if (indent == "  ") JsonContentUtils.prettyFormat.encodeToString(this)
  else Json { prettyPrint = true; prettyPrintIndent = indent }.encodeToString(this)
```

### `importDecls` recomputed and re-prepended on every `eval`
**Where:** `script-utils-kotlin / script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:69-70, 85-86`  ·  **Severity:** low
`imports` is effectively fixed (initialized with `System::class.qualifiedName` and never mutated), yet the `importDecls` property re-joins the import list into a string on every read, and `eval()` prepends that string to every script. For a pooled, repeatedly-invoked evaluator this is needless per-call allocation and string concatenation directly in the hot path.
**Suggested change:** Compute the joined imports once and reuse it:
```kotlin
private val imports = listOf(System::class.qualifiedName)
val importDecls: String by lazy { imports.joinToString("\n") { "import $it" } }
```
If `imports` must remain mutable, at minimum cache `importDecls` and invalidate it on mutation.

### `asText` builds the hex string via `fold` string concatenation
**Where:** `core-utils:strings-numbers-collections / core-utils/src/main/kotlin/com/pambrose/common/util/StringExtensions.kt:383-384`  ·  **Severity:** low
`ByteArray.asText` folds with string concatenation (`str + "%02x".format(byte)`), allocating a new `String` per byte (O(n²) in allocations) and re-parsing the `"%02x"` format spec on every byte. It runs for every `md5`/`sha256` call. Hashes are short, so the absolute cost is small, but the pattern is needlessly wasteful.
**Suggested change:** Avoid repeated reallocation and format parsing — `joinToString("") { "%02x".format(it) }`, or build into a `StringBuilder` of known capacity, or use Kotlin stdlib hex (`ByteArray.toHexString()` via `HexFormat`) on recent Kotlin.

### `GenericValueWaiter` launches a `delay(Duration.INFINITE)` timeout coroutine even when no timeout is wanted
**Where:** `guava-utils:concurrent / guava-utils/src/main/kotlin/com/pambrose/common/concurrent/GenericValueWaiter.kt:100-130`  ·  **Severity:** low
When `timeoutDuration` is the default `Duration.INFINITE`, `waitForCondition` still launches a timeout coroutine that calls `delay(Duration.INFINITE)`. It is not a hard leak (the coroutine is cancelled when the condition is met or the scope ends), but every wait pays for an extra coroutine plus a mutex acquisition inside the timeout body even when no timeout is desired; an infinite-timeout waiter that is satisfied must still wait for that coroutine to be cancelled.
**Suggested change:** Short-circuit the infinite case — only launch the timeout coroutine when `timeoutDuration.isFinite()`, and on the infinite path skip the timeout job entirely, resuming solely from `checkCondition` / immediate satisfaction.

## Public API Surface (19)

### Demo `main()` functions shipped in the published library
**Where:** `guava-utils:concurrent / guava-utils/src/main/kotlin/com/pambrose/common/concurrent/ConditionalValue.kt:103-186` (also `GenericValueWaiter.kt` and `LameBooleanWaiter.kt`, each with their own `main`)  ·  **Severity:** medium
`ConditionalValue.kt` declares public top-level `main()`, `main2()`, `main3()`, `main4()`, and two sibling files each add another public `main()`. These are scratch/demo drivers that print to stdout and run multi-second `runBlocking` loops, yet they compile into the published JAR. As public top-level functions they show up on the module's public API surface (and in each file's JVM default class), are visible to consumers, bloat the artifact, and represent untested dead production code.
**Suggested change:** Move these demos to the test source set (as Kotest specs or a `samples` directory) or delete them. Top-level mains cannot be hidden with `private`, so relocate them under `src/test` or a dedicated examples module so they leave the published API.

### Prometheus factories hard-code the default `CollectorRegistry`
**Where:** `prometheus-utils / prometheus-utils/src/main/kotlin/com/pambrose/common/dsl/PrometheusDsl.kt:38-78` (and `SamplerGaugeCollector`'s `init`)  ·  **Severity:** medium
Every factory (`counter`/`summary`/`gauge`/`histogram`) and `SamplerGaugeCollector` call `register()` with no argument, always binding to `CollectorRegistry.defaultRegistry`. Callers cannot target a private/isolated registry, which blocks multi-tenant setups and test isolation — the current tests must invent globally-unique metric names precisely because everything shares the default registry. The Prometheus builders already provide `register(registry)` overloads.
**Suggested change:** Add an optional `registry: CollectorRegistry = CollectorRegistry.defaultRegistry` parameter to each factory and forward it to `register(registry)`; do the same for `SamplerGaugeCollector`'s constructor.

### `upsert()` silently ignores `conflictColumn` when both arbiters are passed
**Where:** `exposed-utils / exposed-utils/src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:41-46, 77-82`  ·  **Severity:** low
The KDoc states `conflictColumn` and `conflictIndex` are mutually exclusive ("exactly one must be provided"), but the `when` block only throws when both are null. If a caller passes both, the index branch wins and `conflictColumn` is silently dropped — producing SQL that targets a different arbiter than requested, with no error to debug from.
**Suggested change:** Add a branch that rejects the both-provided case (`throw IllegalArgumentException("Provide only one of conflictColumn or conflictIndex, not both")`) so the documented contract is enforced. (Plain `!= null` also reads more idiomatically here than the imported `isNotNull()` helper, since no smart-cast is gained.)

### `blockingGet` cannot opt into `expectSuccess`
**Where:** `ktor-client-utils / ktor-client-utils/src/main/kotlin/com/pambrose/common/dsl/KtorDsl.kt:121-131`  ·  **Severity:** low
`newHttpClient`, `withHttpClient`, and `httpClient` all expose an `expectSuccess` parameter so callers can make non-2xx responses throw, but `blockingGet` calls `withHttpClient {}` with no arguments and hard-codes the default. A blocking caller therefore has no way to get exception-on-error behavior — an inconsistency and a silent feature gap.
**Suggested change:** Add `expectSuccess: Boolean = false` to `blockingGet` and forward it into `withHttpClient(expectSuccess = expectSuccess) { get(url, setUp, block) }`.

### `streamObserver` leaks the concrete helper type and its single-assign setters
**Where:** `grpc-utils / grpc-utils/src/main/kotlin/com/pambrose/common/dsl/GrpcDsl.kt:189, 197-225`  ·  **Severity:** low
`streamObserver` infers a return type of `StreamObserverHelper<T>` rather than the `StreamObserver<T>` interface, so callers see the internal `onNext`/`onError`/`onCompleted` setters and their semantics, and the published signature is tied to an implementation class. Those setters are backed by `singleAssign()`, so calling any of them twice inside the DSL block throws `IllegalStateException` at configuration time — surprising for a builder DSL (where last-wins is expected) and undocumented; the existing tests never register a callback twice, so this is untested.
**Suggested change:** Declare the return type as `StreamObserver<T>` (`StreamObserverHelper<T>().apply { init() }`). For the setters, either document on each that it may be called only once (throws otherwise), or replace `singleAssign` with plain nullable `var`s so re-registration overwrites the prior block.

### Internal helpers exposed as public API (Version formatters, JSON logger, EMPTY_BYTE_ARRAY)
**Where:** `core-utils:concurrency-time-version / core-utils/src/main/kotlin/com/pambrose/common/util/Version.kt:55-67`; `json-utils / json-utils/src/main/kotlin/com/pambrose/common/json/JsonElementUtils.kt:308-311`; `guava-utils:util-dsl / guava-utils/src/main/kotlin/com/pambrose/common/util/ZipExtensions.kt:28-29`  ·  **Severity:** low
Several implementation details leak onto the stable, Maven-Central-published surface and can't be removed later without a breaking change. `Version.jsonStr`/`plainStr` are public companion lambda *vals* (callable/capturable, awkward for Java, and their parameter is named `buildDate` while callers pass `releaseDate`). `JsonElementUtils.logger` is a public object/`val` whose own KDoc calls it an "internal logger holder," used only by `toJsonElement`'s verbose branch. `EMPTY_BYTE_ARRAY` is a public top-level `ByteArray` (a mutable type) that is only an implementation detail of `zip()`.
**Suggested change:** Convert `jsonStr`/`plainStr` to `private fun`s with accurate parameter names (`releaseDate`); call sites in `versionDesc` are unchanged. Replace the `JsonElementUtils.logger` object with a file-level `private val logger = KotlinLogging.logger {}` (also removing the awkward self-import). Make `EMPTY_BYTE_ARRAY` `private`/`internal` (or just return `ByteArray(0)` from `zip()`), since callers never reference it by name.

### Script-engine abstractions over-expose internals (`engine`, `toTempName`)
**Where:** `script-utils-common / script-utils-common/src/main/kotlin/com/pambrose/common/script/AbstractEngine.kt:35-38`; `script-utils-kotlin / script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:64`  ·  **Severity:** low
`AbstractEngine.engine` is a public `val` handing external callers the raw JSR-223 `ScriptEngine`, letting them mutate its context/bindings directly and defeat the `resetContext`/`valueMap` bookkeeping the abstraction maintains. Separately, `String.toTempName()` is an `internal` member extension callable on any `String` in the module (tests already do `"list".toTempName()`); it has no KDoc and appends a magic `_tmp` suffix that can collide with a user variable literally named `foo_tmp`.
**Suggested change:** Mark `engine` as `protected` (subclasses keep access) or document that external mutation is unsupported. Make `toTempName()` `private`, use a namespaced suffix (e.g. `"${this}__script_tmp"`) to avoid collisions, document the scheme, and have tests exercise temp-name behavior indirectly via `varDecls`.

### Inconsistent failure signaling: `error()`/`IllegalStateException` instead of `ScriptException`
**Where:** `script-utils-kotlin / script-utils-kotlin/src/main/kotlin/com/pambrose/common/script/KotlinScript.kt:55`  ·  **Severity:** low
`varDecls` calls `error("No qualified name for ...")`, throwing `IllegalStateException`, while `add()`/`eval()` consistently signal failure with `ScriptException` (the documented failure type — see `AbstractScript.add`'s `@throws ScriptException`). A caller wrapping `add`/`eval` in a `catch (ScriptException)` won't catch this; it's reachable when building `varDecls` for a value whose Kotlin class has no `qualifiedName`.
**Suggested change:** Throw the consistent type: `kotlinClazz.qualifiedName ?: throw ScriptException("No qualified name for $kotlinClazz")`.

### `Closeable` scripts with no-op `close()`, and undocumented primary `eval()`
**Where:** `script-utils-java / script-utils-java/src/main/kotlin/com/pambrose/common/script/JavaScript.kt:42-44, 169-171` (KotlinScript has the identical placeholder); `script-utils-python / script-utils-python/src/main/kotlin/com/pambrose/common/script/PythonScript.kt:54-71`  ·  **Severity:** low
`JavaScript` implements `Closeable` but `close()` is empty, which misleads callers using `JavaScript().use { ... }` (as the tests do) into thinking resources are released — a real leak risk if the underlying engine holds a child classloader for compiled classes. Separately, `PythonScript.eval()` is the primary public entry point yet carries no KDoc, unlike its `JavaScript`/`KotlinScript` siblings and the documented `add()` directly above it.
**Suggested change:** Either implement `close()` to release engine state (reset context / drop compiled-class state) or add KDoc on `close()` explaining it intentionally holds no closeable resources and exists for `use {}` ergonomics — and hoist that decision into the shared abstraction since `KotlinScript` shares the placeholder. Add KDoc to `PythonScript.eval()` covering the first-call variable flush, the `ScriptException` on guarded/invalid calls, and Jython's surprising numeric coercion (`Long`→`BigInteger`, `Float`→`Double`) already exercised by the tests.

### Surprising negative defaults for atomic numeric delegates
**Where:** `core-utils:concurrency-time-version / core-utils/src/main/kotlin/com/pambrose/common/delegate/AtomicDelegates.kt:67-81`  ·  **Severity:** low
`atomicInteger` defaults to `-1` and `atomicLong` to `-1L`. For a general-purpose thread-safe numeric delegate often used as a counter, this is a footgun: `var n by AtomicDelegates.atomicInteger()` then incremented starts at `-1`. The `-1` choice is also undocumented.
**Suggested change:** Default to `0`/`0L` (matching `AtomicBoolean`'s `false` default and typical counter usage). If `-1` is an intentional uninitialized sentinel, state that explicitly in the KDoc.

### `starting()` setter is needlessly nullable and the "or null to clear" doc is false
**Where:** `guava-utils:util-dsl / guava-utils/src/main/kotlin/com/pambrose/common/dsl/GuavaDsl.kt:171-173`  ·  **Severity:** low
In `ServiceListenerHelper`, `starting(block: (() -> Unit)?)` takes a nullable block while every sibling setter (`running`, `stopping`, `terminated`, `failed`) and all of `ServiceManagerListenerHelper`'s setters take non-null. The KDoc claims "or null to clear," but the field uses `singleAssign()`, so a null assignment clears nothing and permanently consumes the single-assignment slot.
**Suggested change:** Make the parameter non-null to match the siblings and drop the misleading "or null to clear" note: `fun starting(block: () -> Unit) { startingBlock = block }`.

### `Iterable.toCsv` name implies CSV but performs no escaping
**Where:** `core-utils:strings-numbers-collections / core-utils/src/main/kotlin/com/pambrose/common/util/MiscExtensions.kt:45-52`  ·  **Severity:** low
`toCsv` joins with `", "` and does no quoting/escaping of elements containing commas, quotes, or newlines, so its output is not valid CSV. The name invites callers to feed it to a CSV parser unsafely; the KDoc describes the real behavior but the name still misleads on a public API.
**Suggested change:** Rename to something like `toCommaString()`/`joinComma()`, or implement real CSV escaping (quote fields containing the delimiter/quote/newline and double interior quotes). At minimum, expand the KDoc to warn it does not escape.

### Missing `@JvmStatic` on Dropwizard factory methods
**Where:** `dropwizard-utils / dropwizard-utils/src/main/kotlin/com/pambrose/common/util/MetricsUtils.kt:26-59` (and `MetricsDsl`)  ·  **Severity:** low
`MetricsUtils` and `MetricsDsl` are Kotlin `object`s exposing public factory methods, but Dropwizard is a Java framework so these are likely called from Java/mixed codebases. Without `@JvmStatic`, Java callers must write `MetricsUtils.INSTANCE.newBacklogHealthCheck(...)` instead of `MetricsUtils.newBacklogHealthCheck(...)`.
**Suggested change:** Annotate the public factory methods (`MetricsUtils.newBacklogHealthCheck`, `MetricsUtils.newMapHealthCheck`, `MetricsDsl.healthCheck`) with `@JvmStatic`; no cost to Kotlin callers.

### `JettyDsl` builders require an empty `{}` for the no-config case
**Where:** `jetty-utils / jetty-utils/src/main/kotlin/com/pambrose/common/dsl/JettyDsl.kt:34-45`  ·  **Severity:** low
Both DSL functions require a mandatory `block` lambda, so creating a server or handler with no inline configuration forces callers to pass `{}` — counter to the conciseness a DSL is meant to provide.
**Suggested change:** Default the lambdas to no-op: `fun server(port: Int, block: Server.() -> Unit = {})` and `fun servletContextHandler(block: ServletContextHandler.() -> Unit = {})`, each as `Server(port).apply(block)` / `ServletContextHandler().apply(block)`. (`apply(block)` is also more idiomatic than `apply { block(this) }`.)

## Simplification & Dead Code (15)

### Dead/shipped "hacking" file in a published artifact
**Where:** `script-utils-kotlin / src/main/kotlin/com/pambrose/common/script/Example.kt:19-54`  ·  **Severity:** medium
`Example.kt` lives under `src/main`, so it is compiled into and published with the Maven Central artifact. It carries a second, duplicate Apache license block (lines 19-34) on top of the real header, an unused local variable, and a `private object ScriptExample` with a `main2()` that is never invoked and explicitly marked `// Just for hacking`. The `main2` name is not a real entry point, so the code has zero runtime purpose and only enlarges the published surface. Its intent is already covered by `KotlinScriptTests`.
**Suggested change:** Delete `Example.kt` entirely, or move it under `src/test` if it must be kept as a manual harness; in either case remove the duplicated license block.

### Unreachable secret/site-key guards behind `isRecaptchaConfigured`
**Where:** `recaptcha-utils / src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:86-95` and `recaptcha-utils / RecaptchaService.kt:192-199`  ·  **Severity:** low
`isRecaptchaConfigured(config)` already requires `!recaptchaSecretKey.isNullOrBlank()` (and a non-blank site key). In `verifyRecaptcha`, the early `isRecaptchaConfigured` gate (86-89) means the later `secretKey.isNullOrBlank()` check (92-95) and its warn-and-return-false path can never execute. The same pattern recurs in `recaptchaWidget`, where the `val siteKey = config.recaptchaSiteKey ?: return` Elvis branch (194) is unreachable inside the surrounding guard. Carrying both the gate and the local re-checks is dead code that misleadingly implies these methods are defensively self-contained when they actually depend on the prior gate.
**Suggested change:** Drop the unreachable null/blank branches and read each key through a non-null accessor that makes the invariant explicit, e.g. `requireNotNull(config.recaptchaSecretKey)` / `requireNotNull(config.recaptchaSiteKey)`. Keep exactly one source of the guarantee — the `isRecaptchaConfigured` gate — not both.

### Dead inner `if (isAdminEnabled)` inside an already-guarded admin branch
**Where:** `service-utils / src/main/kotlin/com/pambrose/common/service/GenericService.kt:119-141` and `service-utils / src/main/kotlin/com/pambrose/common/service/GenericKtorService.kt:122-135`  ·  **Severity:** low
The outer `if (isAdminEnabled)` already guarantees admin is enabled, yet an inner `if (isAdminEnabled)` re-tests the same condition with an `else` that logs "Admin service disabled." That else branch can never run because the outer guard short-circuited it, so it is dead code duplicated across both service classes.
**Suggested change:** Register the servlets unconditionally inside the outer guard and move the single "Admin service disabled" log to an `else` on the outer `if (isAdminEnabled)`, so the disabled case is still logged exactly once.

### Redundant "blank or empty" checks (blank already subsumes empty)
**Where:** `service-utils / src/main/kotlin/com/pambrose/common/service/ServletGroup.kt:40-41` and `service-utils / HttpServletGroup.kt:49`; `email-utils / src/main/kotlin/com/pambrose/common/email/Email.kt:38-45`  ·  **Severity:** low
`isNotBlank()` is already false for the empty string, so the compound `path.isNotEmpty() && path.isNotBlank()` in the servlet groups reduces to `path.isNotBlank()`. The same equivalence makes `Email.isBlankOrEmpty() = value.isBlank() || value.isEmpty()` exactly `value.isBlank()`, and `isNotBlankOrEmpty()` exactly `value.isNotBlank()` — two public methods that duplicate `isBlank()`/`isNotBlank()` under names that misleadingly imply they catch an extra case, expanding the value class's surface with no added behavior.
**Suggested change:** Collapse the servlet conditions to `if (path.isNotBlank())`. Remove `Email.isBlankOrEmpty()`/`isNotBlankOrEmpty()` (or, if kept for API stability, delegate them to `isBlank()`/`isNotBlank()` and document that "or empty" is redundant).

### Dead commented-out `upsert()` implementation left in source
**Where:** `exposed-utils / src/main/kotlin/com/pambrose/common/exposed/UpsertStatement.kt:51-58`  ·  **Severity:** low
An 8-line commented-out alternative implementation, marked `** DO NOT DELETE **`, sits after the `upsert()` body and references `InsertBlockingExecutable`, which is no longer imported or used. It is stale, cannot compile, and adds noise that will confuse maintainers; version control already preserves the old approach.
**Suggested change:** Remove lines 51-58. If the alternative is worth preserving, capture the rationale in a commit message or a short KDoc note rather than a non-compiling code block.

### Unreachable `isAssigned()` on a single-assign delegate
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/delegate/SingleAssignVar.kt:32-62`  ·  **Severity:** low
`ThreadSafeSingleAssignVar` is private and `singleAssign()` returns it typed as `ReadWriteProperty<Any?, T?>`, so callers cannot reach `isAssigned()` through the public API — it is dead code.
**Suggested change:** Either expose it intentionally via a public interface (e.g. `interface SingleAssignProperty<T> : ReadWriteProperty<Any?, T?> { fun isAssigned(): Boolean }` returned from `singleAssign()`), or delete `isAssigned()`.

### Obsolete `isJava6` detection on a JVM-17 library
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/util/GuavaFuncs.kt:28-29`  ·  **Severity:** low
The library targets JVM 17, so `isJava6` is always false on any supported runtime (the matching test even asserts `isJava6.shouldBeFalse()`), making it effectively dead public API. The underlying `startsWith("1.6")` check is also vestigial since `1.6` can never appear on a JVM-17 target.
**Suggested change:** Remove `isJava6` and its test assertion, or mark it `@Deprecated("Java 6 is not supported; this library targets JVM 17")` if removal would break source compatibility.

### Dead elvis fallbacks on `String.decode()` / `String.encode()`
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/util/StringExtensions.kt:93-96`  ·  **Severity:** low
`URLDecoder.decode(String, String)` and `URLEncoder.encode(String, String)` are non-null in their Java signatures and never return null, so the `?: this` fallbacks are unreachable and falsely suggest null is possible.
**Suggested change:** Drop the elvis and switch to the JDK 17 `Charset` overloads, e.g. `fun String.decode() = URLDecoder.decode(this, UTF_8)` and `fun String.encode() = URLEncoder.encode(this, UTF_8)`, which also avoid the `UTF_8.toString()` conversion.

### Stale `@file:Suppress` for now-documented public members
**Where:** `zipkin-utils / src/main/kotlin/com/pambrose/common/dsl/ZipkinDsl.kt:16`  ·  **Severity:** low
The file-level `@Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")` no longer applies: the only public members — `object ZipkinDsl` (lines 22-24) and `fun tracing` (lines 26-32) — are now fully documented. The suppression hides the existing docs and would mask a genuine future regression if an undocumented public member were added.
**Suggested change:** Remove the `@file:Suppress` line; Detekt will pass since both members are documented.

### `Duration.format` uses identity conversions and over-complex arithmetic
**Where:** `core-utils / src/main/kotlin/com/pambrose/common/time/Durations.kt:70-88`  ·  **Severity:** low
The per-component breakdown wraps the remainder in `MILLISECONDS.toMillis(...)` (line 81), an identity conversion that returns its argument unchanged, and reimplements the day/hour/min/sec split via a cascading subtract-and-reconvert chain that `kotlin.time.Duration` already exposes directly. It is harder to read and easier to get wrong than the stdlib alternative.
**Suggested change:** Compute each field with modulo arithmetic (or `Duration.toComponents`), dropping the identity conversion and the intermediate `*Millis` vars — e.g. `day = diff / 86_400_000`, `hr = (diff / 3_600_000) % 24`, `min = (diff / 60_000) % 60`, `sec = (diff / 1_000) % 60`, `ms = diff % 1_000`. The existing `DurationFormatTests` validate the refactor.

### `BooleanMonitor` discards `initValue` at field init and locks needlessly
**Where:** `guava-utils / src/main/kotlin/com/pambrose/common/concurrent/BooleanMonitor.kt:35-41`  ·  **Severity:** low
`monVal` is constructed as `AtomicBoolean(false)` and the `init` block then calls `set(initValue)`, entering/leaving the Guava `Monitor` purely to store a value no other thread can yet observe. The hardcoded `false` is misleading (the real initial value is `initValue`), and the construction-time monitor enter/leave is unnecessary.
**Suggested change:** Initialize directly with the parameter (`private val monVal = AtomicBoolean(initValue)`) and drop the init-block monitor dance. Keep `set()` entering the monitor only so guards re-evaluate — the one case where `monitor.enter()/leave()` is actually required.

### `getParameter` bypasses the lazy `params` map (two sources of truth)
**Where:** `ktor-server-utils / src/main/kotlin/com/pambrose/common/servlet/KtorServletRequest.kt:58-62, 70`  ·  **Severity:** low
`getParameter` reads `request.queryParameters[name]` directly while `getParameterValues`/`getParameterMap`/`getParameterNames` all read the lazily built `params` map. The two paths agree today but maintain two sources of truth, inviting divergence (e.g. if `params` later also includes form parameters) and re-querying Ktor on every `getParameter` call.
**Suggested change:** Route the accessor through the cached map: `override fun getParameter(name: String): String? = params[name]?.firstOrNull()`.

### `javaEquiv` special-cases only `Int` with fragile, inconsistent mapping
**Where:** `script-utils-java / src/main/kotlin/com/pambrose/common/script/JavaScript.kt:92-100`  ·  **Severity:** low
`javaEquiv` special-cases `typeOf<Int>()` and `typeOf<Int?>()` to `Integer`'s simple name via two redundant branches that produce identical results, then falls through to string-mangling (`removePrefix("kotlin.").replace("?", "")`) for everything else. `Long`/`Double`/`Boolean`/etc. take the fallback and emit names like `"Long"`/`"Double"` rather than the boxed Java type names, so the `Int` handling is inconsistent with the rest, and the leading comment describes the mapping without explaining why only `Int` is handled.
**Suggested change:** Collapse the two `Int` branches into `typeOf<Int>(), typeOf<Int?>() -> ...`, then either extend the mapping consistently to the other boxed primitives (`Long`/`Double`/`Float`/`Boolean`/`Char`/`Short`/`Byte` -> `javaObjectType.simpleName`) or drop the special-case and document that callers must pass JVM-resolvable type names. Update the comment to explain the intent.

## Test Coverage Gaps (20)

### Script exit/security guards have no tests that actually fire the guard
**Where:** `script-utils-kotlin / KotlinScript.kt:74-75` · `script-utils-java / JavaScript.kt:119-135` (illegal-calls test at 214-222) · `script-utils-python / PythonScriptPool.kt:31-40` · `core-utils / IOExtensions.kt:84-146` · **Severity:** medium
The security-critical guards across the scripting modules are either untested or only passing by accident. In Java, the "illegal calls" assertions on `sys.exit(1)`/`exit(1)`/`quit(1)` pass because the snippet fails to compile, not because the `System.exit` guard fired — no test exercises a string actually containing `System.exit`. In Kotlin, `kotlin.system.exitProcess(...)` and `Runtime.getRuntime().exit/halt(...)` bypass routes are never tested (and are currently not blocked). In Python, the exit-guard regexes are never tested against a bound object method literally named `exit()`/`quit()`, the exact false-positive case. In core-utils, `toObjectSecure` has zero coverage for its `MAX_SERIALIZED_SIZE` (10 MB) DoS guard, the `DANGEROUS_CLASSES` blocklist in `SecureObjectInputStream.resolveClass`, and the whitelist-rejection path. These are precisely the behaviors a security wrapper must preserve across refactors.
**Suggested change:** Add explicit guard tests: Java `shouldThrow<ScriptException> { eval("System.exit(0)") }`; Kotlin cases for `exitProcess`/`Runtime` bypass routes (pinning current behavior, then flipping to reject once the gap is closed); Python tests binding an object with an `exit()`/`quit()` method to pin desired guard behavior; and core-utils Kotest cases asserting `SecurityException` for an oversized `ByteArray`, a blocklisted-class-prefix payload, and a non-whitelisted class when `allowedClasses` is non-empty (with the allowed-class round trip succeeding).

### AbstractScript.add validation branches are entirely untested
**Where:** `script-utils-common / AbstractScript.kt:94-129` · **Severity:** medium
`add()` is the most non-trivial logic in the module — four distinct validation branches (local/anonymous-class rejection, missing type params, unexpected type params, count mismatch), each producing a specific `ScriptException` message, plus the success branch that populates `valueMap`/`typeMap`. None is exercised by any test (existing tests only cover `AbstractEngine` extension lookup and `ScriptUtils` bindings), so a regression in message text or branch conditions would go unnoticed.
**Suggested change:** Add a Kotest `StringSpec` that subclasses `AbstractScript` and asserts each branch: `paramCnt>0 && types.isEmpty()`, `paramCnt==0 && types.isNotEmpty()`, `paramCnt != types.size`, the local/anonymous-class case, and the happy path. Verify exact message substrings including pluralization ("parameter" vs "parameters").

### RecaptchaService verification and routing logic has zero coverage
**Where:** `recaptcha-utils / RecaptchaService.kt:81-166` · **Severity:** medium
The only behavioral logic in the module — `verifyRecaptcha` (HTTP token verification) and the `validateRecaptcha` Ktor route extension — is untested; existing tests cover only the config interface, widget/script rendering gating, response deserialization, and `close()`. Untested behaviors include: returning true without calling Google when reCAPTCHA is unconfigured; HTTP 400 + false on a missing/blank `g-recaptcha-response`; true on a successful Google response; HTTP 400 + false on `success=false`; and fail-closed (false) when verification throws.
**Suggested change:** Add Kotest `StringSpec` tests using Ktor's `MockEngine` to drive `verifyRecaptcha`/`validateRecaptcha` (or `testApplication` for the route), covering configured/not-configured, missing-token, success, failure, and exception (fail-closed). Extract the response-parsing/decision into an internal function taking the `HttpClient` so the mock engine can be injected.

### LambdaServlet.doGet response behavior is untested
**Where:** `jetty-utils / LambdaServlet.kt:45-56` · **Severity:** medium
`LambdaServletTests` only asserts that constructed servlets are non-null and never invokes `doGet`/`service`. By contrast `VersionServletTests` fully verifies content type, `Cache-Control`, and body. The distinguishing behavior of the more general `LambdaServlet` — writing the lambda result as the body, honoring a custom `contentType`, setting `SC_OK` and the no-cache header — is entirely uncovered.
**Suggested change:** Add MockK tests mirroring `VersionServletTests` that call `servlet.service(request, response)` and assert: the body equals the lambda output; `contentType` matches the custom value passed to the two-arg constructor (e.g. `application/json`); the `Cache-Control` header is set; and the default constructor yields `text/plain`.

### Adapter and DSL entry points exercised only indirectly (or not at all)
**Where:** `ktor-server-utils / KtorServletRequest.kt:55-99` · `ktor-client-utils / KtorDsl.kt:121-131` · `grpc-utils / GrpcDsl.kt:61-165` · `zipkin-utils / ZipkinDsl.kt:33-38` · **Severity:** low
Several public adapters and DSL entry points have no direct tests, so their core contracts are unverified. `KtorServletRequest`'s accessors (`getParameterMap`/`getParameterValues` for repeated keys, `getQueryString` null-on-empty, `getHeaders` enumeration, `getContentType`) are only hit through the end-to-end route. `KtorDsl.blockingGet` — the sole blocking entry point — is never actually invoked; the test labelled "blockingGet performs a GET request" really calls `KtorDsl.get`, leaving the `runBlocking`/new-client path untested. `GrpcDsl.channel`/`server` have no coverage of their in-process-vs-Netty, `overrideAuthority`, plaintext-vs-TLS, and `enableRetry`/`maxRetryAttempts` branches. `ZipkinDsl` tests only assert non-null and never verify the user-supplied `block(this)` is applied to the builder — dropping it would still pass every current test.
**Suggested change:** Add focused specs: `KtorServletRequestTests` for repeated params (`?a=1&a=2`), null query string, null content type, and multi-value headers (MockK or `testApplication`); a direct `KtorDsl.blockingGet` test (injectable `MockEngine` or a local embedded server), also asserting the temporary client is closed; a `GrpcDslTests` building an in-process server+channel (`InProcessServerBuilder`/`InProcessChannelBuilder`, no network) and verifying `maxRetryAttempts` only applies when `enableRetry` is true; and a `ZipkinDsl` test that configures a setting inside the block and asserts it on the returned `Tracing` (or `verify` the builder method is invoked once).

### Untested mapping and propagation logic in ResendService and RedisUtils
**Where:** `email-utils / ResendService.kt:45-73` · `redis-utils / RedisUtils.kt:76-95` · **Severity:** low
Both contain mapping/parsing logic likely to regress silently with no observing test. `ResendService.sendEmail` has no test file at all: it maps `List<Email>` via `.map { it.value }`, formats recipient/cc/bcc log strings (including the "None" fallback for empty lists), and rethrows on failure. `RedisUtils`'s `RedisInfo.includeUserInAuth` and the `userInfo`-splitting in `urlDetails` are the only nontrivial branching in the file, yet existing tests only confirm clients construct without throwing and cannot observe the parsed user/password (both private) — leaving user-only userInfo, the `default`/`user` placeholders, the `none` password placeholder, and `rediss://` SSL detection untested (already the source of a prior bug).
**Suggested change:** Add `ResendServiceTests` (Kotest + MockK) mocking `Resend`/`resend.emails()` to verify from/to/cc/bcc/subject/html are forwarded as `.value` strings, empty cc/bcc still build a valid request, and an exception from `send()` propagates. For redis, expose `RedisInfo`/`urlDetails` as `internal` (or add an internal helper) and assert parsed user/password and `includeUserInAuth` across `redis://user:pass@h`, `redis://default:pass@h`, `redis://name@h`, `redis://h`, `redis://x:none@h`, plus `isSsl` true for `REDISS://` (case-insensitive) and false for `redis://`.

### Pool recycle-on-exception and context-reset contracts are untested
**Where:** `script-utils-common / AbstractExprEvaluatorPool.kt:51-64` (and `AbstractScriptPool.kt:50-54`) · `script-utils-python / PythonScriptPool.kt:31-40` · **Severity:** low
The pool's key correctness property — borrow, run, and always recycle even when the block throws (the `try/finally`) — is untested at the common level, so a thrown evaluation that permanently shrinks the pool would slip through. `PythonScriptPool` is never exercised at all (no borrow/recycle/`resetContext` round trip), nor is `PythonExprEvaluator.compute()`/`resetContext()` or the `nullGlobalContext=true` path.
**Suggested change:** Add a minimal concrete pool subclass test that verifies a successful `eval` returns the evaluator to the pool, and that when the borrowed evaluator throws the instance is still recycled (a subsequent borrow succeeds). Mirror the same for `AbstractScriptPool.eval`'s `resetContext`-on-recycle, and add a `PythonScriptPool` test running several `eval` round-trips that confirms recycling and context reset between borrows.

### UpsertStatement validation and DO NOTHING fallback are untested
**Where:** `exposed-utils / UpsertStatement.kt:77-100` · **Severity:** low
`BugFixVerificationTests` covers the `ON CONFLICT (cols)` happy path for both `conflictColumn` and `conflictIndex`, but not constructing `UpsertStatement` with neither argument (should throw `IllegalArgumentException`), nor the all-conflict-columns case that currently produces a dangling `DO UPDATE SET`. Both are assertable against `prepareSQL` output with the existing in-memory H2 setup.
**Suggested change:** Add `shouldThrow<IllegalArgumentException> { UpsertStatement<Number>(table) }`, and a test that sets only the conflict-key columns and asserts the generated SQL ends with `DO NOTHING` rather than a trailing `DO UPDATE SET`.

### Coroutine waiter concurrency contract is untested
**Where:** `guava-utils / GenericValueWaiterTests.kt:12-82` · **Severity:** low
Existing tests only exercise a single waiter at a time on `BooleanWaiter`. The high-risk concurrency behaviors — two concurrent waiters on the same instance, or one `waitUntilTrue` plus one `waitUntilFalse` on the same instance — have no coverage, so a silent-lost-waiter or shared-predicate bug would not be caught.
**Suggested change:** Add tests that launch two concurrent waiters on a single `BooleanWaiter`/`GenericValueWaiter` and assert both resume when the condition is met, plus a test mixing `waitUntilTrue` and `waitUntilFalse` on the same instance to document and guard the intended concurrency contract.

### ZipExtensions passthrough/detection and HttpServletGroup batch logic untested
**Where:** `guava-utils / ZipExtensions.kt:47-79` · `service-utils / HttpServletGroup.kt:27-52` · **Severity:** low
Small branching logic that regresses silently. `ZipExtensionTests` only covers the zip→unzip round trip and the empty case; the `!isZipped()` passthrough branch of `unzip()` (raw bytes returned as a string, where a charset bug would bite) and `isZipped()` itself are never directly tested. Separately, `ServletGroup` is covered but `HttpServletGroup` (the Ktor-side equivalent) has no test at all, and its `addServlets(vararg)` batch method and blank/empty-path filtering are untested in either class.
**Suggested change:** For zip, add `raw.isZipped() shouldBe false` and `raw.unzip()` round-tripping a non-ASCII UTF-8 string (`"héllo wörld"`), plus `"hello world".zip().isZipped() shouldBe true`. For service-utils, add `HttpServletGroupTests` mirroring `ServletGroupTests`: empty map initially, `addServlet` maps a path, `addServlets` registers multiple pairs, and blank/empty paths are silently dropped.

### Missing boundary and misuse-path tests in metrics health/sampler checks
**Where:** `dropwizard-utils / MetricsUtilsTests.kt:47-67` · `prometheus-utils / SamplerGaugeCollector.kt:34-48` · **Severity:** low
Threshold and misuse edge cases — the subtle parts of this code — lack coverage. The dropwizard backlog check has an explicit boundary test (`value == threshold` is unhealthy) but the map check has no symmetric `map.size == size` boundary case. The Prometheus `SamplerGaugeCollector` tests cover only the happy path and never exercise a `labelNames`/`labelValues` size mismatch or a sampler lambda that throws, both realistic misuse paths whose current behavior is a cryptic exception at scrape time.
**Suggested change:** Add a dropwizard test where map size equals the threshold and assert unhealthy, e.g. `newMapHealthCheck(mapOf("a" to 1, "b" to 2), size = 2).execute().isHealthy shouldBe false`. For Prometheus, add a test asserting `IllegalArgumentException` at construction when `labelNames.size != labelValues.size`, and a test that `collect()` does not throw (or returns `NaN`) when the sampler throws.

## Documentation (12)

All findings in this category are KDoc/API-documentation defects on a published library surface: docs that contradict the code, understate real limitations, or omit sharp edges. None are severe individually, but several actively mislead callers who read only the public docs.

### KDoc contradicts actual threshold semantics (off-by-one in `@param`)
**Where:** `dropwizard-utils / src/main/kotlin/com/pambrose/common/util/MetricsUtils.kt:28-32, 45-49`  ·  **Severity:** low
Both `newBacklogHealthCheck` and `newMapHealthCheck` summarize the check as unhealthy when the size "meets or exceeds" the threshold (matching the code: healthy only when `value < size`). But the `@param size` line says "the threshold **above which** the check is considered unhealthy," which is wrong at the boundary — at `value == size` the check is already unhealthy, and a `backlog health check boundary` test confirms this. The summary and the `@param` thus contradict each other.
**Suggested change:** Reword the `@param` to the inclusive semantics in both functions, e.g. `@param size the threshold at or above which the check is considered unhealthy.`

### KDoc understates reCAPTCHA gating (both keys required, not just the site key)
**Where:** `recaptcha-utils / src/main/kotlin/com/pambrose/common/recaptcha/RecaptchaService.kt:169, 186`  ·  **Severity:** low
`loadRecaptchaScript` and `recaptchaWidget` both document that they render "if reCAPTCHA is enabled and a site key is configured," but the real gate (`isRecaptchaConfigured`, lines 201-207) additionally requires a non-blank secret key by deliberate lockstep design. A caller reading only these doc strings expects rendering with `enabled + siteKey`, which no longer holds.
**Suggested change:** Update both KDoc lines to "if reCAPTCHA is enabled and both the site key and secret key are configured," and cross-reference `isRecaptchaConfigured` so the rendering/validation lockstep is discoverable.

### `toRowString` KDoc says "non-empty values" but renders literal `"null"`
**Where:** `exposed-utils / src/main/kotlin/com/pambrose/common/exposed/ExposedUtils.kt:67-75`  ·  **Severity:** low
The doc claims the function joins "all non-empty column values," but the implementation calls `this[it].toString()` then filters `isNotEmpty()`. A null column becomes the 4-char string `"null"`, which is non-empty, so null columns are not dropped and instead appear as the literal text `null` in the human-readable output. Only genuinely empty strings are filtered. Code and doc disagree, and the `null` rendering is almost certainly unintended.
**Suggested change:** Align code and doc. To skip both nulls and empty strings:
```kotlin
fun ResultRow.toRowString() =
  fieldIndex.values
    .mapNotNull { this[it]?.toString() }
    .filter { it.isNotEmpty() }
    .joinToString(" - ")
```
and update the KDoc to state that null and empty values are omitted.

### Python exit guards documented as "blocked" but are trivially bypassable
**Where:** `script-utils-python / src/main/kotlin/com/pambrose/common/script/PythonScript.kt:28-31, 54-63`  ·  **Severity:** low
The KDoc states "Calls to `sys.exit()`, `exit()`, and `quit()` are blocked," implying a safety guarantee. The regex guards are easily defeated — `import os; os._exit(0)`, `getattr(sys, 'ex'+'it')(1)`, line-continuation splits, or `e = sys.exit; e(1)` — and Jython exposes the full JVM, so a regex blocklist cannot be a security control. Calling these "blocked" in published docs is misleading.
**Suggested change:** Reword to make clear it is a best-effort guard against common literal forms and is NOT a sandbox, e.g. "Common literal calls to `sys.exit()`, `exit()`, and `quit()` are rejected as a convenience; this is not a security sandbox and can be bypassed (e.g. via `os._exit`, `getattr`, or reflection). Run untrusted scripts in an isolated process/JVM."

### `isValidEmail` KDoc omits its surprising rejections
**Where:** `email-utils / src/main/kotlin/com/pambrose/common/email/EmailUtils.kt:47-55`  ·  **Severity:** low
The KDoc says only "Returns true if this String matches a valid email address pattern," but the regex rejects valid addresses in non-obvious ways: no `+` in the local part (no plus-addressing), TLDs longer than 4 chars, and single-character domain labels. The only acknowledgement of any of this lives in a test comment, not the public docs, so callers cannot know these addresses will be reported invalid.
**Suggested change:** Preferably fix the regex; otherwise document the known limitations on `isValidEmail` (no plus-addressing, TLDs limited to 2–4 chars, domain labels must be ≥ 2 chars) so callers can judge adequacy.

### `toJsonElement(verbose)` KDoc omits that it logs the full raw input at WARN
**Where:** `json-utils / src/main/kotlin/com/pambrose/common/json/JsonElementUtils.kt:256-271`  ·  **Severity:** low
The function logs a warning then rethrows via `getOrThrow()`. The KDoc documents the throw but not that, when `verbose=true`, the entire raw input string is emitted at WARN level — a quiet data-exposure footgun, since the input may carry PII or secrets.
**Suggested change:** Add a KDoc line warning that `verbose=true` logs the full raw input at WARN (avoid for sensitive payloads), and consider truncating the logged input (e.g. `this.take(N)`), mirroring `element()`'s `take(100)`.

### Redis pool/timeout defaults are undocumented magic values
**Where:** `redis-utils / src/main/kotlin/com/pambrose/common/redis/RedisUtils.kt:138-143`  ·  **Severity:** low
`newRedisClient`'s KDoc says pool sizes "default to values from system properties ... or sensible defaults," but the concrete fallbacks (10/5/1/1s, with `DEFAULT_TIMEOUT` applied to both connection and socket timeouts) and the call-time system-property precedence are only visible by reading the signature. These public defaults materially affect behavior.
**Suggested change:** State the concrete fallback in each `@param`, e.g. `@param maxPoolSize maximum connections in the pool; defaults to the redis.maxPoolSize system property or 10`. Optionally hoist 10/5/1/1 into named `private const` ints so docs and code share one source of truth.

### Pool `isEmpty` KDoc overstates accuracy of a racy `Channel.isEmpty`
**Where:** `script-utils-common / src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluatorPool.kt:40-41` and `.../AbstractScriptPool.kt:42-43`  ·  **Severity:** low
Both `isEmpty` properties delegate to kotlinx.coroutines `Channel.isEmpty`, which is explicitly documented as an approximate, racy snapshot. The local KDoc ("Returns true if there are no evaluator instances currently available") reads as authoritative and gives no hint that concurrent borrow/recycle can produce false positives/negatives.
**Suggested change:** Soften both KDocs to note this is an approximate, point-in-time check that may be racy under concurrent borrow/recycle, mirroring kotlinx.coroutines' own caveat.

### `blockingEval`/`eval` on the evaluator pool lack docs for blocking and deadlock behavior
**Where:** `script-utils-common / src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluatorPool.kt:45-64`  ·  **Severity:** low
`blockingEval` has KDoc but the suspend `eval()` (56-64) has none, and crucially the bounded-`Channel` back-pressure is documented nowhere. `borrow()` calls `channel.receive()`; if all evaluators are borrowed, `blockingEval` blocks the calling thread inside `runBlocking` until one is recycled. Calling it more times concurrently than `size`, or from a context that itself owns the only evaluator, can deadlock — a sharp edge for a public API.
**Suggested change:** Add KDoc to `eval()`, and note on `blockingEval` that it blocks the current thread until an evaluator becomes available and must not be called when no further evaluator can be recycled. Document the pool's blocking/back-pressure contract at the class level.

### `params()` default path can throw, but KDoc reads as always-safe
**Where:** `script-utils-common / src/main/kotlin/com/pambrose/common/script/AbstractScript.kt:76-82`  ·  **Severity:** low
The KDoc says `types ... defaults to those previously registered for [name]` and `@return an empty string if there are no parameters`, but the default expression is `typeMap[name] ?: error("No type parameters registered for $name")`. So `params(name)` for an unregistered name throws `IllegalStateException` rather than returning `""`. This is a public `open` function intended for subclass use (e.g. `KotlinScript.varDecls`).
**Suggested change:** Document the throw, e.g. `@throws IllegalStateException if [types] is omitted and no types were registered for [name]`. Optionally return `""` for an unregistered name if that is the intended contract.

### Public `eval()` on `AbstractExprEvaluator` is undocumented while its siblings are not
**Where:** `script-utils-common / src/main/kotlin/com/pambrose/common/script/AbstractExprEvaluator.kt:25-31`  ·  **Severity:** low
`compute()` (33-39) and `resetContext()` (41-46) carry full KDoc, but the primary, most-used `eval()` has none — and its contract (returns `Boolean`, throws on a non-Boolean result) is non-obvious. Detekt's `UndocumentedPublicFunction` would normally flag this; it likely passes only due to a baseline.
**Suggested change:** Add KDoc covering the parameter, the Boolean return, and the throw, e.g. `/** Evaluates [expr] and returns its Boolean result. @throws ScriptException if the result is not a Boolean. */`.

### Duplicated "null global context" note in `JavaScript` class KDoc
**Where:** `script-utils-java / src/main/kotlin/com/pambrose/common/script/JavaScript.kt:35-37`  ·  **Severity:** low
The class KDoc states "Note that Java cannot have a null global context." (line 35) and repeats it as "Note: Java cannot have a null global context." (line 37) — a copy/paste artifact.
**Suggested change:** Remove one of the two identical notes, keeping a single sentence.

---

*Stats: 21 units reviewed · 148 findings raised, 139 confirmed / 9 rejected · severity breakdown: 3 high, 23 medium, 113 low.*