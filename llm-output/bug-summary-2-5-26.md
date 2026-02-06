# Bug Summary

## HIGH Severity

### Bug #1 — `toByteArraySecure()` on String uses Java serialization instead of UTF-8 bytes for hashing
**File:** `core-utils/.../StringExtensions.kt:203`

`input.toByteArraySecure()` resolves to `Serializable.toByteArraySecure()` from `IOExtensions.kt` since `String` implements `Serializable`. This wraps the string in Java `ObjectOutputStream` serialization headers instead of producing raw UTF-8 bytes. All MD5/SHA-256 hashes produced by these functions differ from standard implementations and are JVM-version-dependent. Should be `input.toByteArray()`.

### Bug #2 — `Atomic.value` is publicly mutable without synchronization
**File:** `core-utils/.../concurrent/Atomic.kt:26`

`var value: T` is public, allowing callers to read and write the value directly without acquiring the `Mutex`, completely bypassing the synchronization that `setWithLock` and `withLock` provide.

### Bug #3 — NPE when `db=null` and `transactionIsolation` uses its default value
**File:** `exposed-utils/.../ExposedUtils.kt:57, 69, 83`

Three functions (`readonlyTx`, `timedTransaction`, `timedReadOnlyTx`) declare `db: Database? = null` with `transactionIsolation: Int = db.transactionManager.defaultIsolationLevel`. When `db` is null, evaluating the default for `transactionIsolation` throws a `NullPointerException` because it uses `.` instead of `?.` on a nullable receiver.

### Bug #4 — `IndexOutOfBoundsException` if Redis URL userInfo has no colon
**File:** `redis-utils/.../RedisUtils.kt:52-59`

`urlDetails()` calls `it.userInfo?.split(colon, 2)?.get(1)`. If userInfo is present but has no colon (e.g., `redis://username@host`), the split returns a single-element list and `.get(1)` throws `IndexOutOfBoundsException`.

### Bug #5 — Lambda-based logging methods log the lambda reference instead of calling it
**File:** `guava-utils/.../concurrent/BooleanMonitor.kt:58-104`

Four methods (`debug`, `info`, `warn`, `error`) taking `msg: () -> Any?` use `logger.debug { msg }` instead of `logger.debug { msg() }`. They log the lambda object's `toString()` representation, not the actual message.

### Bug #6 — Script pool recycle doesn't clear `valueMap`/`typeMap`/`imports` causing state leaks
**File:** `script-utils-common/.../AbstractScript.kt:48-51`, `AbstractScriptPool.kt:33`

`resetContext()` clears engine bindings and resets the `initialized` flag, but `valueMap`, `typeMap`, and `imports` lists retain entries from the previous pool user. The next borrower inherits stale variables and state from the prior evaluation.

### Bug #7 — Shutdown hook calls `stopAsync()` without waiting for completion
**File:** `service-utils/.../GenericService.kt:230-235`

The JVM shutdown hook calls `stopAsync()` (non-blocking) and immediately prints "shut down complete." The JVM can exit before Jetty, Zipkin, and other services finish cleanup. Should use `stopSync()` or `awaitTerminated()`.

## MEDIUM Severity

### Bug #8 — `SingleSetAtomicReferenceDelegate.setValue` silently drops second writes
**File:** `core-utils/.../AtomicDelegates.kt:93-99`

`compareAndSet` return value is ignored. A "single-set" delegate silently discards reassignment instead of throwing an error like the related `ThreadSafeSingleAssignVar` does.

### Bug #9 — `criticalSection` discards the block's return value
**File:** `core-utils/.../AtomicUtils.kt:23-30`

Declares a type parameter `<T>` for the block's return value but has an implicit `Unit` return type. The generic parameter is misleading and the result of `block()` is silently discarded.

### Bug #10 — `maskUrlCredentials()` corrupts URLs with multiple `@` signs
**File:** `core-utils/.../StringExtensions.kt:226-233`

Uses `split("@")` and takes index `[1]`, dropping everything after the second `@`. For URLs like `https://user@email.com:pass@host.com/path`, the output is corrupted. Should use `substringAfterLast("@")`.

### Bug #11 — `linesBetween()` crashes when patterns are not found
**File:** `core-utils/.../StringExtensions.kt:104-107`

`firstLineNumberOf`/`lastLineNumberOf` return `-1` when the regex is not found. This leads to `subList(0, -1)` which throws `IllegalArgumentException`. No defensive check for the `-1` sentinel value.

### Bug #12 — `JsonElement.isEmpty()` wrong for primitives and crashes on arrays
**File:** `json-utils/.../JsonElementUtils.kt:99`

Returns `true` for all `JsonPrimitive` values including non-empty strings and numbers. Crashes with `IllegalArgumentException` on `JsonArray` inputs because it calls `.jsonObject` on an array.

### Bug #13 — `JsonElement.toMap()` crashes on nested arrays
**File:** `json-utils/.../JsonElementUtils.kt:136-163`

Inner `JsonArray` elements hit the `else -> it.toMap()` branch, which only handles `JsonObject`. Nested arrays like `[[1,2],[3,4]]` cause an `IllegalArgumentException`.

### Bug #14 — `servletService` created but never started/stopped when `initServletGroup=true`, `isAdminEnabled=false`
**File:** `service-utils/.../GenericService.kt:76-103, 162-170`

The `servletService` is initialized when `initServletGroup=true`, but `startUp()` and `shutDown()` only start/stop it when `isAdminEnabled=true`. Servlets are registered but Jetty never serves traffic, and the server thread leaks on shutdown.

### Bug #15 — `AbstractExprEvaluator.eval()` unconditionally casts to `Boolean`
**File:** `script-utils-common/.../AbstractExprEvaluator.kt:25`

`engine.eval(expr) as Boolean` throws `ClassCastException` for any non-Boolean expression result, even though the pool's generic `eval<R>` method implies arbitrary return types.

### Bug #16 — Race conditions in `GenericValueWaiter`/`BooleanWaiter`
**File:** `guava-utils/.../GenericValueWaiter.kt:63-103`

There is a window between the initial satisfaction check and callback setup where notifications can be lost. Also, `BooleanWaiter.predicate` is a plain `var` mutated without synchronization, so concurrent waiters can corrupt each other.

### Bug #17 — `LameBooleanWaiter` uses unstructured coroutine scopes
**File:** `guava-utils/.../LameBooleanWaiter.kt:51, 74`

`CoroutineScope(Dispatchers.Default).launch` breaks structured concurrency. The callback may not be installed before `changeValue()` is called, causing missed notifications and hangs.

### Bug #18 — `isZipped()` crashes on byte arrays with fewer than 2 elements
**File:** `guava-utils/.../ZipExtensions.kt:42`

Accesses `this[0]` and `this[1]` without checking that the array has at least 2 elements. Throws `ArrayIndexOutOfBoundsException` on empty or single-byte arrays.

### Bug #19 — Comment/code mismatch in Redis pool config
**File:** `redis-utils/.../RedisUtils.kt:92`

Code sets `minEvictableIdleDuration` to 1 minute, but the comment says "Evict connections idle for 5 minutes." Either the value or the comment is wrong.

## LOW Severity

### Bug #20 — JSON key typo: `"build_time: "` includes trailing colon and space
**File:** `core-utils/.../Version.kt:48`

The JSON key is `"build_time: "` instead of `"build_time"`. Any consumer parsing this JSON must match the trailing colon and space.

### Bug #21 — `Duration.format()` produces garbled output for negative durations
**File:** `core-utils/.../Durations.kt:48-64`

Negative `inWholeMilliseconds` values produce negative intermediate results from `TimeUnit` conversions, and the subtractions compound incorrectly, yielding nonsensical output.

### Bug #22 — `Short.times` infinite loop at `Short.MAX_VALUE`
**File:** `core-utils/.../NumberExtensions.kt:44-47`

When `this` is `Short.MAX_VALUE`, `i++` overflows from 32767 to -32768, and the loop condition `i < this` becomes true again, creating an infinite loop.

### Bug #23 — Debug `println` statements left in library code
**Files:** `guava-utils/.../GenericValueWaiter.kt:83, 90` and `LameBooleanWaiter.kt:69-70`

`println` and `System.out.flush()` calls produce noise on stdout for any consumer of the library.

### Bug #24 — `Long.MAX_VALUE.days` instead of `Duration.INFINITE`
**File:** `guava-utils/.../ConditionalValue.kt:30, 33, 48`

Using `Long.MAX_VALUE.days` as a default timeout overflows on conversion. `Duration.INFINITE` is the correct idiom and is handled specially by `withTimeoutOrNull`.

### Bug #25 — `sendEmail()` silently swallows all failures
**File:** `email-utils/.../ResendService.kt:21-41`

`runCatching` catches all exceptions and only logs them. Callers have no way to detect that email delivery failed.

### Bug #26 — Test `main()` shipped in production source set
**File:** `script-utils-kotlin/.../Test.kt`

Contains a `main()` function with unused variables and resource leaks (`KotlinExprEvaluator` instances never closed). This debug code should not be in the main source set.
