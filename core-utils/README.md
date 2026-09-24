# Core Utils

Foundational Kotlin extensions and utilities used by every other module in this repository: string,
number, collection and date helpers, atomics and delegates, plus JVM-only I/O, hashing, reflection and
content-source support.

**Multiplatform** — portable APIs live in `commonMain` and target JVM, JS, wasmJs and Native. JVM-bound
APIs (hashing, URL codecs, serialization, classpath resources, reflection) live in `jvmMain` and remain
available to JVM consumers unchanged.

## Features

### commonMain

- **Strings**: quoting, bracketing, padding, path building, line search, masking and obfuscation
- **Numbers**: `random()`, `length`
- **Collections**: `toCsv()`, `listPrint`, `ArrayUtils`
- **Dates**: parsing, formatting and age helpers built on `kotlinx-datetime`
- **Atomics**: `Atomic<T>`, `AtomicDelegates`, `AtomicBoolean.criticalSection`
- **Exceptions**: cancellation-aware `runCatching` variants
- **Scope functions**: a two-receiver `with`

### jvmMain

- **Hashing**: MD5 and SHA-256 with salts, plus secure salt generation
- **I/O**: `Serializable` ↔ `ByteArray` with a hardened, allow-listed variant and checksums
- **Content sources**: read files from the local filesystem, GitHub, GitLab or a URL
- **Reflection**: `typeParameterCount`
- **Version metadata**: the `@Version` annotation and accessors
- **Misc**: host info, banners, properties loading, stdout capture, port waiting

## Usage Examples

### String Extensions

```kotlin
import com.pambrose.common.util.*

"hello".toDoubleQuoted()          // "\"hello\""
"\"hello\"".isDoubleQuoted()      // true
"[a]".isBracketed()               // true, defaults are '[' and ']'
"a".asBracketed('(', ')')         // "(a)"

"item".pluralize(3)               // "items"
"  ".nullIfBlank()                // null
"path".ensurePrefix("/")          // "/path"
"hello world".capitalizeFirstChar()

"42".isInt()                      // true
"3.14".isDouble()                 // true

pathOf("a", "b", "c")             // "a/b/c"
listOf("a", "b").toPath()

// Redacts credentials in a URL before logging
"https://user:secret@host/db".maskUrlCredentials()

"sensitive".obfuscate()           // partially masked
"a very long string".maxLength(10)
" [x] ".trimEnds()                // "x"
```

`obfuscate()` counts positions in code points, and `maxLength` drops a surrogate pair it would otherwise cut in half,
so neither splits an emoji; `maxLength` can therefore return one character fewer than asked. `maxLength` and
`trimEnds` throw `IllegalArgumentException` for a negative length, and `trimEnds` also throws when the trimmed string
is shorter than twice the length it removes.

`maskUrlCredentials()` masks only credentials inside the authority — an `@` between `://` and the first `/`, `?`
or `#` — so an `@` in a path, query or fragment is left alone. Credentials must be percent-encoded as RFC 3986
requires; an unencoded `/`, `?` or `#` in a password ends the authority early.

`pathOf(...)` and `List<String>.toPath()` skip empty elements and strip a leading separator from every element
after the first, so they never produce doubled separators.

`toPattern` and `asRegex()` treat their receiver as a **glob**, not a regex: `*` becomes `.*`, `?` becomes `.`,
and every regex metacharacter (`\ ^ $ . | + ( ) [ ] { }`) is escaped so it matches itself. The result is anchored
with `^` and `$`.

```kotlin
"*.kt".toPattern                  // "^.*\.kt$"
"a+b".asRegex().matches("a+b")    // true — the '+' is literal
```

Line helpers work on both `String` and `List<String>`:

```kotlin
val text = "alpha\nbeta\ngamma"
text.firstLineNumberOf("beta".asRegex())
text.linesBetween("alpha".asRegex(), "gamma".asRegex())
text.withLineNumbers()
```

### Number Extensions

```kotlin
import com.pambrose.common.util.length
import com.pambrose.common.util.random

100.random()      // random Int in 0 until 100
1000L.random()
12345.length      // 5 — digit count
```

### Collections

```kotlin
import com.pambrose.common.util.ListUtils.listPrint
import com.pambrose.common.util.toCsv

listOf("a", "b", "c").toCsv()   // "a, b, c"
listPrint(listOf(1, 2, 3))
```

`toCsv()` is a display helper, not a CSV encoder: elements are neither quoted nor escaped, so one containing a
comma, quote or newline will not round-trip through a CSV parser.

### Date Utilities

Built on `kotlinx-datetime`. Note that core-utils bundles **no IANA time-zone database**: only
`TimeZone.currentSystemDefault()` and UTC resolve everywhere. Named zones need the `@js-joda/timezone`
package on JS/wasm, so they are left to consumers.

```kotlin
// DateUtils is an object, so its members are imported one by one (a star import from an object does not compile)
import com.pambrose.common.util.DateUtils.age
import com.pambrose.common.util.DateUtils.instantNow
import com.pambrose.common.util.DateUtils.localDateNow
import com.pambrose.common.util.DateUtils.localDateTimeNow
import com.pambrose.common.util.DateUtils.parseToLocalDate
import com.pambrose.common.util.DateUtils.parseToLocalDateTime
import com.pambrose.common.util.DateUtils.toDashedYYYYMMDD
import com.pambrose.common.util.DateUtils.toFullDateString
import com.pambrose.common.util.DateUtils.toISO8601
import com.pambrose.common.util.DateUtils.toLogString
import com.pambrose.common.util.DateUtils.toMMDDYY
import kotlinx.datetime.TimeZone

// In UTC, so toISO8601()'s trailing Z and age(TimeZone.UTC) below describe it correctly
val now = localDateTimeNow(TimeZone.UTC)
val today = localDateNow()
val instant = instantNow()

"2026-09-07".parseToLocalDate()
"2026-09-07T14:30:00".parseToLocalDateTime()

now.toFullDateString()      // "Mon 09/07/26 14:30:00"
now.toISO8601()
now.toLogString()
today.toMMDDYY()
today.toDashedYYYYMMDD()

instant.age                 // Duration since that instant
now.age(TimeZone.UTC)
```

`toISO8601()` always emits seconds and drops any fractional-seconds component, so `2024-03-15T08:30` formats as
`"2024-03-15T08:30:00Z"`. The trailing `Z` labels the value as UTC; no zone conversion is performed.

### Atomics and Delegates

Atomics come from `kotlin.concurrent.atomics`, so use `load()` / `store()` rather than the Java
`get()` / `set()` names.

```kotlin
import com.pambrose.common.concurrent.Atomic
import com.pambrose.common.delegate.AtomicDelegates
import com.pambrose.common.util.AtomicUtils.criticalSection
import kotlin.concurrent.atomics.AtomicBoolean

// Property delegates
var counter: Int by AtomicDelegates.atomicInteger(0)
var flag: Boolean by AtomicDelegates.atomicBoolean(false)
var name: String by AtomicDelegates.nonNullableReference("initial")
var once: String? by AtomicDelegates.singleSetReference()

// Mutex-guarded value (setWithLock and withLock suspend, so call them from a coroutine)
val shared = Atomic("initial")
val current = shared.value
shared.setWithLock { "updated" }
val result = shared.withLock { length }   // the current value is the receiver

// Sets the flag to true while the block runs and restores its previous value afterward, so nested sections keep it
// set; it does not exclude other callers, and overlapping threads can clear it while another is still inside
val started = AtomicBoolean(false)
started.criticalSection { println("started is true while this runs") }
```

`atomicInteger` and `atomicLong` default to `0` and `0L`. `singleSetReference` allows exactly one assignment —
including an assignment of `null`, which still counts as set — and throws `IllegalStateException` on any later
one. Its `compareValue` parameter defaults to `initValue` and must equal it; a differing value could never match
and would leave the property permanently unsettable, so it is rejected with `IllegalArgumentException`.

On the JVM, `singleAssign()` provides a write-once property:

```kotlin
import com.pambrose.common.delegate.SingleAssignVar.singleAssign

var config: String? by singleAssign()
```

### Exception Utilities

These preserve coroutine cancellation, which plain `runCatching` swallows.

```kotlin
import com.pambrose.common.util.onFailureOrRethrow
import com.pambrose.common.util.onFailureRethrowCancellation
import com.pambrose.common.util.runCatchingCancellable

runCatchingCancellable { riskyCall() }
  .onFailureRethrowCancellation { e -> logger.warn { "failed: ${e.message}" } }

// Handle one exception type, rethrow everything else
runCatchingCancellable { parse() }
  .onFailureOrRethrow<NumberFormatException, _> { e -> logger.warn { "bad number" } }
```

### Hashing (JVM)

MD5 and SHA-256 with optional salts. These are **not** password-hashing functions — use a dedicated
password hash for credentials.

```kotlin
import com.pambrose.common.util.md5
import com.pambrose.common.util.newByteArraySalt
import com.pambrose.common.util.newStringSalt
import com.pambrose.common.util.sha256

val salt = newStringSalt()            // 16 chars by default
val byteSalt = newByteArraySalt(32)   // SecureRandom bytes

"password".sha256(salt)
"password".md5(byteSalt)
```

### URL Encoding (JVM)

```kotlin
import com.pambrose.common.util.decode
import com.pambrose.common.util.encode

"hello world".encode()   // "hello+world"
"hello+world".decode()   // "hello world"
```

### Serialization and Checksums (JVM)

Writing is the same either way: `toByteArraySecure()` delegates to `toByteArray()` and exists only for source
compatibility. All of the hardening is on the read side. `toObjectSecure` deserializes only classes you
allow-list, which avoids the deserialization gadget risk that the deprecated `toObject()` carries.

The allow-list is required and must be non-empty, and it has to name every class in the stream, including
superclasses (an `Integer` also needs `Number`). A blocklist is checked first and rejects `java.lang.Runtime`,
`java.lang.Process` and `java.lang.ProcessBuilder`, plus anything under `java.rmi.`, `javax.management.` or the
Commons Collections `functors` packages, even when allow-listed. A JEP 290 `ObjectInputFilter` bounds the stream
as well — at most 20 levels of nesting, and no array length or object-reference count larger than the payload's
size in bytes — and is merged with any JVM-wide `jdk.serialFilter` rather than replacing it. The serialized input
itself is capped at 10 MB. Allow-list hash-based collections (`HashSet`, `HashMap`, `Hashtable`) sparingly for
untrusted input: nested sets cost hashing time that grows exponentially with depth, which the depth limit only
bounds.

```kotlin
import com.pambrose.common.util.*

val bytes = myValue.toByteArray()

// Hardened read; the allow-list argument is required
val restored = bytes.toObjectSecure(MyType::class.java, setOf(MyType::class.java))

// Corruption check: an unkeyed SHA-256 detects accidental damage, not tampering
val withSum = bytes.withChecksum()
val verified = withSum.verifyChecksum()
```

`toObjectSecure` throws `IllegalArgumentException` for an empty allow-list, `SecurityException` for an oversized
payload or a blocklisted or non-allow-listed class, and `InvalidClassException` when the depth or array-length
limits are exceeded.

### Content Sources (JVM)

A `ContentRoot` resolves a relative path against its location and returns a `ContentSource`, which exposes
`content`. Absolute file paths and full URLs (paths that start with a scheme such as `https://`) are used unchanged.
No containment is applied: `..` and absolute paths escape a `FileSystemSource`'s directory, and a repository reads any
full URL, `file:` URLs and internal hosts included, so validate paths that come from user input. Repository files take
the same optional `connectTimeout` and `readTimeout` as `UrlSource` (`repo.file(path, connect, read)`,
`GitHubFile(..., connectTimeout = ...)`), and Java callers can pass `java.time.Duration` timeouts to `UrlSource`:

```kotlin
import com.pambrose.common.util.FileSource
import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubFile
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType
import com.pambrose.common.util.UrlSource
import kotlin.time.Duration.Companion.seconds

// Local filesystem
val local = FileSystemSource("/var/data")
val text = local.file("config.json").content

// Direct sources
FileSource("/etc/hosts").content
UrlSource("https://example.com/data.json").content                     // fetched on every access
UrlSource("https://example.com/data.json", readTimeout = 5.seconds).content   // 10s connect, 30s read by default

// GitHub
val repo = GitHubRepo(OwnerType.Organization, "pambrose", "common-utils")
val readme = GitHubFile(repo, branchName = "master", srcPath = "", fileName = "README.md")
println(readme.content)

// Repository paths are relative to the raw-content prefix and start with the branch name
val sameReadme = repo.file("master/README.md")
```

`UrlSource` applies its timeouts in whole milliseconds, as `URLConnection` requires: a positive timeout is rounded
up and capped at `Int.MAX_VALUE` milliseconds, and `Duration.INFINITE` or `Duration.ZERO` means no timeout. A
negative timeout throws `IllegalArgumentException` when the source is constructed.

`GitLabRepo` / `GitLabFile` mirror the GitHub pair and read raw content from GitLab's `/-/raw/` path.
Every `ContentSource` reports `remote`, and repository sources resolve paths against the raw-content prefix
(`rawSourcePrefix`).

`GitHubRepo` uses `raw.githubusercontent.com` for `github.com`. For any other `domainName` (GitHub Enterprise
Server) it uses that host's `/raw/` path, the form served when subdomain isolation is disabled; a server with
subdomain isolation enabled serves raw content from `raw.HOSTNAME`, which this class does not detect.

### Version Metadata (JVM)

The annotation is named `Version`:

```kotlin
import com.pambrose.common.util.Version
import com.pambrose.common.util.Version.Companion.buildString
import com.pambrose.common.util.Version.Companion.version
import com.pambrose.common.util.Version.Companion.versionDesc

@Version(version = "2.2.6", releaseDate = "2026-09-07", buildTime = 1788739200000L)  // 2026-09-07T00:00:00Z
object MyApp

MyApp::class.version()          // "2.2.6", or "Unknown" if unannotated
MyApp::class.buildString()      // formatted build timestamp
MyApp::class.versionDesc()      // plain-text summary
MyApp::class.versionDesc(true)  // JSON summary
```

### Misc JVM Helpers

```kotlin
import com.pambrose.common.util.*
import kotlin.time.Duration.Companion.seconds

hostInfo.hostName
hostInfo.ipAddress

sleep(2.seconds)
randomId()
captureStdout { println("captured") }
MiscFuncs.waitForPortAvailable(port = 8080)   // true once the port is free

readProperties("app.properties", "override.properties")
ReadResources.readResourceFile("banner.txt")
println(getBanner("banner.txt"))              // found through the thread context classloader

throwable.stackTraceAsString
myList.typeParameterCount
```

`waitForPortAvailable` polls up to `maxAttempts` times (default 50), `delayMs` apart (default 200), and returns
`false` if the port is still in use. A port outside `0..65535` throws `IllegalArgumentException` rather than being
retried like a busy one.

`getBanner` and `ReadResources.readResourceFile` both take an optional `ClassLoader` that defaults to the thread
context classloader, falling back to core-utils' own; the `getBanner(filename, logger)` overload falls back to the
logger's classloader instead.

`readProperties` accepts a simple `key=value` subset of `.properties`: one pair per line, split on the first `=`,
with key and value trimmed. Blank lines, `#` and `!` comment lines, lines without `=` and lines with an empty key
are skipped; escapes, line continuations and `:` separators are not supported. Every file is read and parsed
before any property is set, so a missing file throws `IllegalStateException` and leaves the system properties
untouched. Later files override earlier ones.

`captureStdout` is not thread-safe: it replaces the process-wide `System.out` while the block runs, so output from
other threads is captured too and concurrent calls interfere with each other. Output is encoded and decoded as
UTF-8.

`typeParameterCount` counts the type parameters the runtime class itself declares. `Array<T>` reports 1, while
primitive arrays such as `IntArray` and non-generic classes report 0 — as does a non-generic subclass of a generic
class, such as `java.util.Properties`, which extends `Hashtable<Object, Object>`.

## API Reference

Grouped by file; see the KDoc for full signatures.

### commonMain

| Area        | Entry points                                                                        |
|-------------|-------------------------------------------------------------------------------------|
| Strings     | `StringExtensions.kt` — quoting, bracketing, paths, line search, masking            |
| Numbers     | `NumberExtensions.kt` — `random()`, `length`                                        |
| Collections | `ListUtils`, `ArrayUtils`, `Iterable.toCsv()`                                       |
| Dates       | `DateUtils`                                                                         |
| Atomics     | `Atomic<T>`, `AtomicDelegates`, `AtomicUtils.criticalSection`                       |
| Exceptions  | `runCatchingCancellable`, `onFailureRethrowCancellation`, `onFailureOrRethrow`      |
| Misc        | `simpleClassName`, `isNull()`, `isNotNull()`, `lpad`, `rpad`, `capitalizeFirstChar` |

### jvmMain

| Area      | Entry points                                                                                                                                                                                            |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Hashing   | `md5`, `sha256`, `newStringSalt`, `newByteArraySalt`, `md5Of`                                                                                                                                           |
| Encoding  | `encode()`, `decode()`                                                                                                                                                                                  |
| I/O       | `toByteArray`, `toByteArraySecure` (an alias for it), `toObjectSecure`, `withChecksum`, `verifyChecksum`, and the deprecated `toObject`                                                                 |
| Content   | `ContentRoot`, `ContentSource`, `FileSystemSource`, `GitHubRepo`, `GitLabRepo`, `GitHubFile`, `GitLabFile`, `UrlSource`, `FileSource`, `OwnerType`                                                      |
| Version   | `@Version`, `version()`, `buildString()`, `buildDateTime()`, `versionDesc()`                                                                                                                            |
| Durations | `timeUnitToDuration`, `Duration.format`                                                                                                                                                                 |
| Misc      | `hostInfo`, `sleep`, `randomId`, `repeatWithSleep`, `captureStdout`, `waitForPortAvailable`, `readProperties`, `ReadResources`, `getBanner`, `stackTraceAsString`, `typeParameterCount`, `singleAssign` |

## Dependencies

This module depends on:

- Kotlin Standard Library
- Kotlinx Coroutines
- Kotlinx DateTime
- Kotlinx Serialization JSON
- kotlin-logging

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/core-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/core-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:core-utils:LATEST_VERSION")
}
```

### Maven

Maven consumers must depend on the `-jvm` artifact, since this is a multiplatform module:

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>core-utils-jvm</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Security Notes

- `md5` and `sha256` are general-purpose digests, not password hashes
- `toObject` is deprecated; use `toObjectSecure` and keep its allow-list to exactly the classes you
  expect. Java deserialization of untrusted bytes is a known remote-code-execution vector.
- `maskUrlCredentials()` exists so connection strings can be logged without leaking passwords

## License

Licensed under the Apache License, Version 2.0.
