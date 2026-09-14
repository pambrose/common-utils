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

### Date Utilities

Built on `kotlinx-datetime`. Note that core-utils bundles **no IANA time-zone database**: only
`TimeZone.currentSystemDefault()` and UTC resolve everywhere. Named zones need the `@js-joda/timezone`
package on JS/wasm, so they are left to consumers.

```kotlin
import com.pambrose.common.util.DateUtils.*
import kotlinx.datetime.TimeZone

val now = localDateTimeNow()
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

// Mutex-guarded value
val shared = Atomic("initial")
val current = shared.value
shared.setWithLock { "updated" }
val result = shared.withLock { it.length }

// Run a block only if the flag flips false -> true
val started = AtomicBoolean(false)
started.criticalSection { println("runs once") }
```

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

`toObjectSecure` deserializes only classes you allow-list, which avoids the deserialization gadget risk
that plain `toObject` carries. The allow-list is required and must name every class in the stream,
including superclasses (an `Integer` also needs `Number`). A blocklist of known gadget packages applies
even to allow-listed classes. The stream is also limited to a nesting depth of 32, and to array lengths
within the 10 MB payload cap.

```kotlin
import com.pambrose.common.util.*

val bytes = myValue.toByteArray()
val back = bytes.toObject()

// Hardened round-trip
val secureBytes = myValue.toByteArraySecure()
val restored = secureBytes.toObjectSecure(MyType::class.java, setOf(MyType::class.java))

// Tamper detection
val withSum = bytes.withChecksum()
val verified = withSum.verifyChecksum()
```

### Content Sources (JVM)

A `ContentRoot` resolves a relative path against its location and returns a `ContentSource`, which exposes
`content`. Absolute file paths and full URLs are used unchanged:

```kotlin
import com.pambrose.common.util.FileSource
import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubFile
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType
import com.pambrose.common.util.UrlSource

// Local filesystem
val local = FileSystemSource("/var/data")
val text = local.file("config.json").content

// Direct sources
FileSource("/etc/hosts").content
UrlSource("https://example.com/data.json").content

// GitHub
val repo = GitHubRepo(OwnerType.Organization, "pambrose", "common-utils")
val readme = GitHubFile(repo, branchName = "master", srcPath = "", fileName = "README.md")
println(readme.content)

// Repository paths are relative to the raw-content prefix and start with the branch name
val sameReadme = repo.file("master/README.md")
```

`GitLabRepo` / `GitLabFile` mirror the GitHub pair and read raw content from GitLab's `/-/raw/` path.
Every `ContentSource` reports `remote`, and repository sources resolve paths against the raw-content prefix
(`rawSourcePrefix`).

### Version Metadata (JVM)

The annotation is named `Version`:

```kotlin
import com.pambrose.common.util.Version
import com.pambrose.common.util.Version.Companion.buildString
import com.pambrose.common.util.Version.Companion.version
import com.pambrose.common.util.Version.Companion.versionDesc

@Version(version = "3.2.3", releaseDate = "2026-09-07", buildTime = 1757260800000L)
object MyApp

MyApp::class.version()          // "3.2.3", or "Unknown" if unannotated
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
waitForPortAvailable(port = 8080)

readProperties("app.properties", "override.properties")
ReadResources.readResourceFile("banner.txt")
println(getBanner("banner.txt"))

throwable.stackTraceAsString
myList.typeParameterCount
```

## API Reference

Grouped by file; see the KDoc for full signatures.

### commonMain

| Area | Entry points |
|------|--------------|
| Strings | `StringExtensions.kt` — quoting, bracketing, paths, line search, masking |
| Numbers | `NumberExtensions.kt` — `random()`, `length` |
| Collections | `ListUtils`, `ArrayUtils`, `Iterable.toCsv()` |
| Dates | `DateUtils` |
| Atomics | `Atomic<T>`, `AtomicDelegates`, `AtomicUtils.criticalSection` |
| Exceptions | `runCatchingCancellable`, `onFailureRethrowCancellation`, `onFailureOrRethrow` |
| Misc | `simpleClassName`, `isNull()`, `isNotNull()`, `lpad`, `rpad`, `capitalizeFirstChar` |

### jvmMain

| Area | Entry points |
|------|--------------|
| Hashing | `md5`, `sha256`, `newStringSalt`, `newByteArraySalt`, `md5Of` |
| Encoding | `encode()`, `decode()` |
| I/O | `toByteArray`, `toObject`, `toByteArraySecure`, `toObjectSecure`, `withChecksum`, `verifyChecksum` |
| Content | `ContentRoot`, `ContentSource`, `FileSystemSource`, `GitHubRepo`, `GitLabRepo`, `GitHubFile`, `GitLabFile`, `UrlSource`, `FileSource`, `OwnerType` |
| Version | `@Version`, `version()`, `buildString()`, `buildDateTime()`, `versionDesc()` |
| Durations | `timeUnitToDuration`, `Duration.format` |
| Misc | `hostInfo`, `sleep`, `randomId`, `captureStdout`, `waitForPortAvailable`, `readProperties`, `ReadResources`, `getBanner`, `stackTraceAsString`, `typeParameterCount`, `singleAssign` |

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
- Prefer `toObjectSecure` over `toObject` for any data you did not produce yourself, and keep its
  allow-list to exactly the classes you expect. Java deserialization of untrusted bytes is a known
  remote-code-execution vector.
- `maskUrlCredentials()` exists so connection strings can be logged without leaking passwords

## License

Licensed under the Apache License, Version 2.0.
