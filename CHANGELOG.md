# Changelog

All notable changes to Common Utils are documented in this file.

## [Unreleased]

### Changed

- service-utils `ServletGroup.addServlet` and `HttpServletGroup.addServlet` add a missing leading slash to the path,
  so `"ping"` and `"/ping"` register one endpoint and the later servlet replaces the earlier one. Before, both were
  registered: the Jetty admin server failed to start with "Multiple servlets map to path /ping", and the Ktor one
  kept serving the first.
- service-utils' Jetty admin and metrics servers no longer send a `Server: Jetty(…)` header or a version on error
  pages, and their error pages no longer include stack traces.
- grpc-utils `GrpcDsl.channel` defaults `enableRetry` to `true`, grpc-java's own default. 4.1.0 made the default
  `false` call `disableRetry()`, which also turned off transparent retries (for example on a refused stream during a
  server restart) on every channel that never mentioned retry.
- json-utils `String.toJsonElement(verbose)` is renamed `parseJson`; the old name is deprecated. It shadowed the
  generic `T.toJsonElement()`, so `"42"` parsed to a number on a `String` but serialized to a string in generic code.
- json-utils `parseJson`, and so `reformatJson`, rejects unquoted tokens that are not JSON literals (`hello`, `007`,
  `+3`, `0x10`) with `SerializationException`. kotlinx's tree parser accepted them, and they were re-encoded
  differently on each platform.
- json-utils `intValue` and `intValueOrNull` accept only JSON integers, as `isNumber` and `doubleValue` already did:
  `007`, `+5` and non-ASCII digits are rejected.
- json-utils: a missing-key error names the key and the keys present instead of quoting the document.
- exposed-utils `upsert(conflictIndex)` throws `IllegalArgumentException` for a functional or partial index, and
  `UnsupportedByDialectException` on MySQL and MariaDB, whose `ON DUPLICATE KEY UPDATE` cannot target one index.
- exposed-utils `KotlinSqlLogger` logs at debug level, like Exposed's own SQL logger, since each statement carries its
  bound values.
- ktor-server-utils `KtorServletResponse` implements the date and int header setters, `flushBuffer`, `resetBuffer` and
  `reset`, ignores `setContentLength`, and accepts the `null` arguments the servlet spec defines. Their
  `Void`-returning variants, which always threw, are gone from the ABI.
- redis-utils `newRedisClient` accepts -1 (unlimited) for `maxIdleSize`, and warns when `minIdleSize` exceeds it.

### Added

- json-utils `longValue`, `longValue(vararg keys)` and `longValueOrNull(vararg keys)`.
- ktor-server-utils `KtorServletRequest` implements `getCookies`, `getDateHeader`, `getIntHeader`, `getRequestURL`,
  `isSecure`, `getCharacterEncoding`, `getContentLength`, `getDispatcherType` and `getServletContext`.

### Bug fixes

- ktor-server-utils `Route.servlet` answers HEAD with GET's headers and no body. Ktor sent the body, so a pipelined
  response after a HEAD was read as part of it; this hit the `GenericKtorService` admin endpoints. TRACE and servlets
  that override `getLastModified` no longer fail with a 500.
- ktor-server-utils `HerokuHttpsRedirect` keeps the query string exactly as sent; it used to reorder and lower-case
  the parameters, breaking signed URLs.
- service-utils `KtorServletService` stops the embedded server when it fails to start, so a port conflict no longer
  leaves Ktor's shutdown hook registered and the servlets undestroyed.
- service-utils `initMetricsAndHealthChecks()` fails fast when called a second time.
- guava-utils `GenericValueWaiter` returns at once for a zero or negative timeout. Under a dispatcher that runs work
  inline, it used to wait until the value next changed.
- redis-utils' suspending helpers connect, ping and close on `Dispatchers.IO` rather than blocking the caller's thread
  for up to the 2-second timeout; connection failures are logged with their cause.
- core-utils `toObjectSecure` bounds array lengths and object references by the size of the payload, and nesting
  at 20 levels (was 32). The old limits let a 35-byte payload allocate 80 MB, a 62-byte `ArrayList` allocate a
  10-million-slot array, and a 1.7 KB nested-`HashSet` payload take about a minute of CPU.
- email-utils `isValidEmail` rejects addresses over 254 characters, local parts over 64 and domain labels over 63
  before running its pattern. An address with a few thousand domain labels threw `StackOverflowError`.
- email-utils `ResendService` creates the Resend emails client once and reuses it. `Resend.emails()` builds a new
  OkHttp client on every call, so each email opened its own connection pool and TLS handshake.
- service-utils `GenericKtorService` treats a blank admin path as disabling that endpoint, as the Jetty variant
  does. It used to normalize `""` to `"/"` first, so a blank `threadDumpPath` served the thread dump at the root.

### Build

- `gradle-wrapper.properties` pins `distributionSha256Sum`, and `make upgrade-wrapper` keeps it up to date.
- `gradlew` and `gradlew.bat` are no longer marked `binary` in `.gitattributes`, so their diffs are visible.
- CI actions are pinned to commit SHAs, and every job has a timeout.

## [4.1.0] - 2026-09-16

### Changed

- core-utils `String.trimEnds(len)` and `String.maxLength(len)` throw `IllegalArgumentException` for a length
  that does not fit. They threw `StringIndexOutOfBoundsException` on the JVM and Native, and returned a wrong
  result on JS, whose native `substring` swaps or clamps bad indices.
- core-utils `MiscFuncs.waitForPortAvailable` throws `IllegalArgumentException` for a port outside `0..65535`,
  instead of retrying it like a busy port and returning `false`. Only I/O failures now count as "busy".
- core-utils `UrlSource` rejects a negative timeout when it is constructed.
- grpc-utils declares the native libraries of `netty-tcnative-boringssl-static` (the `linux-x86_64`,
  `linux-aarch_64`, `osx-x86_64`, `osx-aarch_64` and `windows-x86_64` jars) as runtime dependencies. The main
  tcnative jar holds no native code, and Gradle ignores the POM entries that pull the native jars in, so Gradle
  builds never loaded OpenSSL and TLS silently ran on the JDK provider. Gradle consumers on those platforms now
  get the OpenSSL (BoringSSL) provider, as Maven consumers already did, and about 6 MB of extra runtime jars.
- redis-utils throws `IllegalArgumentException` for a Redis URL that can never work, from `newRedisClient` and
  from the `withRedis` family before the block runs. That covers a malformed URL, a URL with no host, a
  non-numeric database index and an unknown `protocol`. Before:
  - a malformed URL threw `URISyntaxException`, whose message repeated the URL and any password in it. The new
    message leaves the URL out.
  - a URL with no host, such as `localhost:6379` (which parses as the scheme `localhost`), took the `null` path
    like an unreachable server.
- script-utils-common `ScriptUtils.globalBindings` and `ScriptUtils.bindings(scope)` return `Bindings?`. A context
  created with `nullGlobalContext = true` has no global bindings, and reading them threw a `NullPointerException`
  ("getBindings(...) must not be null"). Kotlin callers now need a null check. `engineBindings` is unchanged.

### Bug fixes

- core-utils `String.singleToDoubleQuoted()` drops surrounding whitespace before removing the quotes
  (`"  'x'  "` became `"\" 'x' \""`), and escapes backslashes as well as double quotes.
- core-utils `UrlSource` accepts `Duration.INFINITE` and timeouts longer than `Int.MAX_VALUE` milliseconds
  (about 24.8 days), which it used to convert to a negative `Int` that `URLConnection` rejects. A
  sub-millisecond timeout is rounded up instead of becoming 0, which `URLConnection` treats as no timeout.
- core-utils `Int.lpad` zero-pads a negative number after its sign, as `"%0Nd"` does (`(-1).lpad(4)` is
  `"-001"`, not `"00-1"`). `toMMDDYYYY()` and `toDashedYYYYMMDD()` format negative years accordingly.
- core-utils `String.maxLength` and `String.obfuscate` no longer split a surrogate pair (such as an emoji).
- json-utils `getByPath` returns `null` when the path runs into a value that is not an object, as its KDoc
  promised. `getByPath("a/b")` on `{"a": 1}` or `{"a": null}` threw `IllegalArgumentException`, while
  `getOrNull("a.b")` returned `null` for the same input.
- json-utils `doubleValue`, `doubleValueOrNull` and `isNumber` accept only JSON number syntax, plus `NaN`,
  `Infinity` and `-Infinity`, so they give the same answer on every platform. They used the platform's own
  parser, and each platform accepted different extra text:
  - **JVM and Apple native:** `1.5f`, `1.5d` and hex floats such as `0x1p3`.
  - **JS:** `0x10`, `0b101` and `nan`.
  - **Every platform:** surrounding whitespace, a leading `+`, `.5`, `5.` and `01`.

  For such text, `doubleValue` now throws `NumberFormatException`, `doubleValueOrNull` returns `null` and
  `isNumber` is `false`. A quoted JSON number such as `"2.5"` is still read by `doubleValue`.
- script-utils-kotlin and script-utils-java bind object arrays. An `Array<Int>` registered with `typeOf<Int>()` was
  declared as `kotlin.Any<kotlin.Int>` or `java.lang.Object<java.lang.Integer>`; it is now `kotlin.Array<kotlin.Int>`
  or `java.lang.Integer[]`. Any other value with no public class or interface that takes its registered type
  arguments still falls back to `Any`, but is now declared without them. Before, the generated declaration did not
  compile, so every later evaluation on that instance failed.
- script-utils-java declares a star-projected array type argument (`typeOf<Array<*>>()`) as `java.lang.Object[]`.
  It used to generate `?[]`, which does not compile.
- script-utils-common pools return an instance whose reset throws. Before, the instance was neither returned nor
  closed, so a pool of size 1 then suspended every later borrower forever.
- recaptcha-utils rejects a siteverify reply with a non-2xx status. Before, the status was ignored, so an error
  or redirect whose body parsed as `{"success": true}` passed verification.
- recaptcha-utils `validateRecaptcha` responds `400 reCAPTCHA verification failed` after
  `RecaptchaService.close()`, and logs an error. Before, the closed client's `CancellationException` escaped as
  if the call had been cancelled.
- recaptcha-utils reads each `RecaptchaConfig` key once per call. A config whose getters changed between reads
  could pass the "fully configured" gate and then throw `IllegalArgumentException` from `validateRecaptcha` or
  `recaptchaWidget`.
- redis-utils connects to port 6379 when the URL has no port. The port was passed to Jedis as -1, so every
  connection to `redis://host` failed, and `withRedis` passed `null` to its block even with a server listening.
- prometheus-utils `InstrumentedThreadFactory` registers its three metrics all or nothing. When only a later
  metric name was taken, the constructor threw but left the earlier metrics registered, so building the factory
  kept failing even after the conflict was removed.
- ktor-server-utils `Route.servlet` falls back to `application/octet-stream`, and logs a warning, when a servlet
  sets a content type that does not parse. Before, a servlet that finished normally turned into a 500.
- ktor-server-utils `Route.servlet` drops the servlet's `Content-Length`, `Transfer-Encoding` and `Upgrade`
  headers. Ktor sets `Content-Length` from the body it sends and rejects the other two, so the servlet's values
  were sent twice or failed the call.
- ktor-server-utils `KtorServletResponse` treats a `Content-Type` header as the content type, as a servlet
  container does. `setHeader`/`addHeader` set the content type and character encoding, `getHeader` returns
  `getContentType()`, and the header is no longer listed in `getHeaderNames()`. Before,
  `setHeader("Content-Type", "text/plain; charset=ISO-8859-1")` left the writer on its old charset, so the header
  and the body encoding could disagree.

### Documentation

- core-utils `isFloat`/`isDouble` and `ArrayUtils.asString(FloatArray/DoubleArray)` document where the
  platforms disagree: number parsing (a trailing `f`/`d`, hexadecimal literals) and whole-number formatting
  (`1.0` prints as `1` on JS).
- ktor-client-utils `KtorDsl.newHttpClient` and its README explain that the module declares no client engine.
  js and wasmJs fall back to ktor-client-core's bundled Js engine; JVM and Kotlin/Native consumers must add one
  (CIO, Darwin, Curl or WinHttp), or creating a client fails.

### Build and tests

- Coverage tooling (#187): a per-package Kover line floor, CI jobs for the macOS/iOS and Windows native tests,
  line/branch coverage tables (`make coverage-packages`, `make coverage-modules`), Codecov per-module
  components and a 90% patch target, on-demand PIT mutation testing (`make mutation`), and Kotlin ABI dumps
  checked by `check` (`make abi-update`).
- core-utils specs for common code now run in `commonTest`, so they cover JS, wasmJs and Native as well as the
  JVM, with the results the platforms legitimately disagree on pinned per platform. See
  `docs/TEST_COVERAGE_REVIEW_2026-09-16.md` (TC-008 to TC-021).
- json-utils `JsonIntegrationTest` moves from `jvmTest` to `commonTest`, so it also runs on JS, wasmJs and
  native. It needed the JVM only for `System.currentTimeMillis`, now `kotlin.time.Clock`. Its assertion that the
  lookups finish in under a second is gone: it could flake on a slow runner and checked nothing about the
  results.
- New json-utils specs cover the cases the existing ones missed:
  - `jsonObjectValueOrNull` and `jsonElementListOrNull` returning a value, not just `null`
  - `isString`, `isNumber` and the `OrNull` accessors on an object or array
  - `forEachJsonObject` skipping non-object elements and rejecting a primitive
  - `deepCopy` building new objects and arrays rather than returning the same instances
  - a millisecond timestamp overflowing `intValue`
- script-utils-common has a test-only JSR 223 engine (`fake`), so its base classes and pool contracts are tested
  without starting a compiler. Guard tests in the engine modules keep each termination call in code that never runs,
  and check the guard's own message, so a regressed guard fails a test instead of passing or terminating the test JVM.
  See `docs/TEST_COVERAGE_REVIEW_2026-09-16.md` (TC-073 to TC-083).
- recaptcha-utils tests build their MockEngine client with the production configuration instead of a copy of
  it. New cases cover malformed and non-2xx siteverify replies, v3 fields and a `null` `error-codes`, the site
  key in the widget (and the secret key's absence), a blank `remoteip`, use after `close()`, and how often the
  config is read. Specs that exercised only their own `RecaptchaConfig` literals are removed.
- ktor-client-utils specs that let `KtorDsl` create its own client run on every platform. `jvmTest` and
  `nativeTest` depend on CIO, and js/wasmJs use the bundled Js engine, so the native test binaries no longer
  skip them.
- service-utils and jetty-utils test servers listen on port 0 and bind `127.0.0.1`. service-utils services
  expose the port the OS chose through an `internal` `boundPort` test seam, so parallel test JVMs no longer race
  for a free port, and another local app's loopback listener can no longer take the tests' connections.
- More specs close the remaining gaps in the test coverage review: email-utils payloads and webhooks, grpc-utils
  TLS handshakes and invalid key material, guava-utils monitors, services and value waiters, ktor-server-utils
  redirects and headers, and service-utils lifecycles. See `docs/TEST_COVERAGE_REVIEW_2026-09-16.md`.
- guava-utils `BugFixVerificationTests` wait on conditions instead of fixed delays.

### Dependency bumps

- `ktor` 3.5.2 → 3.6.0
- `resend` 4.24.0 → 4.25.0
- `versions` 0.61.0 → 0.63.1 (build-only)
- Bump project version to 4.1.0

## [4.0.0] - 2026-09-15

### Breaking changes

- grpc-utils publishes `grpc-netty` as `api`, and script-utils-java publishes `java-scriptengine` as `api`.
  Both leaked into the public API — `TlsContext.sslContext`/`TlsContextBuilder.builder` are Netty types, and
  `JavaScript.assignIsolation` takes java-scriptengine's `Isolation` — while the POMs listed them at runtime
  scope, so a consumer touching those members could not compile without adding the dependency itself.
- `kotlin-reflect` has its own catalog version (2.4.20), aligned with the compiler, instead of riding the
  `kotlin` entry held at 2.4.10 for the scripting artifacts. core-utils exports reflect as `api`, so its POM
  previously paired stdlib 2.4.20 with reflect 2.4.10.
- The three script-utils engine modules no longer re-declare `api(project(":core-utils"))`, which
  script-utils-common already exports, and ktor-server-utils drops an `implementation(libs.kotlin.reflect)`
  its sources never used.
- ktor-server-utils no longer publishes `core-utils` as a dependency. It used none of core-utils' own code,
  reaching it only for the `kotlin-logging` facade that core-utils re-exports as `api`; kotlin-logging is now
  declared directly as `implementation`. The POM therefore drops `core-utils-jvm` from `compile` scope and
  lists `kotlin-logging` at runtime scope. Consumers that relied on reaching core-utils transitively through
  ktor-server-utils must now declare `com.pambrose.common-utils:core-utils` themselves. Nothing inside the
  project relied on that path: service-utils, the only dependent, already declares core-utils directly.

- `ByteArray.toObjectSecure` (core-utils) requires an explicit, non-empty `allowedClasses`. The parameter
  used to default to `emptySet()`, which switched the allow-list off entirely and left only a 7-entry
  blocklist between untrusted bytes and known deserialization gadget chains.
  - **Calls that omitted the argument:** they no longer compile. Code compiled against 3.2.3 must be
    recompiled, because the `toObjectSecure$default` synthetic is gone.
  - **An explicit empty set:** it now throws `IllegalArgumentException`.
  - **Java callers:** they always passed both arguments and are unaffected.
- `toObjectSecure` also installs a JEP 290 `ObjectInputFilter`. Streams that nest objects more than 32
  levels deep, or that declare an array longer than the 10 MB payload cap, are rejected with
  `InvalidClassException` before the oversized allocation happens. The filter is merged with any JVM-wide
  `jdk.serialFilter` instead of replacing it.
- `ContentRoot.file(path)` (core-utils) now resolves a relative `path` against the root, as its KDoc and the
  README always described. Before, it ignored the root and used `path` as given.
  - **`FileSystemSource`:** the path resolves against `pathPrefix`, so
    `FileSystemSource("/var/data").file("config.json")` reads `/var/data/config.json`.
  - **Repositories:** the path resolves against `rawSourcePrefix` and begins with the branch name.
  - **Unchanged:** absolute file paths and full URLs pass through as-is. Callers that pass a path already
    prefixed with a *relative* root (for example `FileSystemSource("content").file("content/x")`) must drop
    the prefix. A `"./"` root and absolute roots keep working.
- `GitLabRepo.rawSourcePrefix` and `GitLabFile` URLs now use GitLab's raw-content path
  (`.../-/raw/<branch>/...`). Before, `GitLabFile` pointed at the `/-/blob/` HTML viewer page, so `content`
  returned HTML, and `rawSourcePrefix` was the repository home page.
- `typeParameterCount` (core-utils) now counts the type parameters declared by the object's own class,
  instead of its superclass's type arguments. Generic classes that extend `Object`, such as `Pair`,
  `Triple`, or a user `Box<T>`, report their parameters. Non-generic subclasses of generic classes, such
  as `java.util.Properties`, report 0. The script engines' `add()` validation uses this count: `Pair`
  values now require their type parameters, and `Properties` can be bound without any.
- `String.toPattern` and `asRegex` (core-utils) now escape every regex metacharacter in the glob, not just
  `.`, so only `*` and `?` act as wildcards. Patterns that relied on regex syntax passing through (for
  example a `[abc]` character class) now match those characters literally.
- `AtomicDelegates.singleSetReference` (core-utils) now allows exactly one assignment, and a `null`
  assignment counts as that assignment. `compareValue` defaults to `initValue`, and a `compareValue` that
  differs from `initValue` throws `IllegalArgumentException`, since such a delegate could never be set.
- `AtomicDelegates.atomicInteger()` and `atomicLong()` (core-utils) now default to `0`, not `-1`, so a
  counter declared as `var hits by atomicInteger()` starts at zero.
- `MiscFuncs.waitForPortAvailable` (core-utils) returns `Boolean`: `true` once the port is free, `false` if it
  was still in use after `maxAttempts`. Before, it returned `Unit` and only logged a warning. Call sites that
  ignore the result still compile, but compiled callers must be rebuilt because the JVM signature changed.
- `List<String>.toPath` / `join` / `toRootPath` (core-utils) skip empty elements and strip a whole
  multi-character separator. `["a", "", "b"].join()` is now `a/b` (was `a//b`), and `["a", "::b"].join("::")`
  is `a::b` (was `a:::b`).
- `GitHubRepo.rawSourcePrefix` and `GitHubFile` (core-utils) now build raw-content URLs from their parts.
  - **`github.com`:** a repository name that contains `github.com` is no longer rewritten.
  - **Other domains (GitHub Enterprise Server):** URLs use the `HOSTNAME/raw/OWNER/REPO` path. Before,
    `GitHubFile` always pointed at `raw.githubusercontent.com`, and `rawSourcePrefix` was the repository
    page. Servers with subdomain isolation enabled, which serve raw content from `raw.HOSTNAME`, are not
    detected.
- json-utils accessors reject bad values consistently.
  - **Plain accessors:** `stringValue`, `intValue`, `doubleValue`, and `booleanValue` throw
    `IllegalArgumentException` for a JSON `null`. Before, `stringValue` returned the string `"null"` and
    `booleanValue` returned `false`.
  - **Booleans:** `booleanValue` accepts only `true` and `false` and throws for anything else. It used to
    read `"yes"` or `1` as `false`.
  - **`OrNull` accessors:** they return `null` for a type mismatch (`intValueOrNull` on `"test"`,
    `stringValueOrNull` on an object) instead of throwing.
- json-utils `isNumber` is `false` for a quoted numeric string such as `"42"`, and `isEmpty()` is `true` for
  JSON `null`.
- json-utils `get`, `getOrNull`, and `containsKeys` ignore empty path segments, as `getByPath` already did.
  `get("a..b")` is now the same as `get("a.b")`, and an empty path returns the element itself. Before, the
  lookup was for the key `""`, and it threw.
- `HerokuHttpsRedirect.host` (ktor-server-utils) is now `String?` and defaults to `null`, which keeps the
  host of the incoming request. Before, it defaulted to `"localhost"`, so an install without a `host`
  redirected every visitor to `https://localhost`. Code that reads `host` as a non-null `String` must handle
  `null`.
- service-utils services must call `initServletService()` or `initKtorServletService()` exactly once before
  starting.
  - **Starting without init:** it now fails with a clear `IllegalStateException`. Before, a service with admin,
    metrics, and Zipkin all disabled started anyway, and any other configuration failed with an opaque
    `UninitializedPropertyAccessException`.
  - **Calling init twice:** it throws `IllegalStateException`. Before, it replaced the admin servlet service,
    orphaning the first, and then failed part-way through.
- service-utils registers its Dropwizard exporter with `CollectorRegistry.defaultRegistry` in `startUp()` and
  unregisters it in `shutDown()`. Before, it was registered when the service was built and never removed, so a
  stopped service kept exporting and a second instance added duplicate metric families. Code that reads the
  default registry before the service starts no longer sees those metrics.
- `InstrumentedThreadFactory.newThread` (prometheus-utils) returns `Thread?`. It returns `null` when the delegate
  rejects the thread, as the `ThreadFactory` contract allows, instead of throwing `NullPointerException`. Kotlin
  code that uses the result directly must handle `null`.
- `MetricsUtils` factories and `MetricsDsl.healthCheck` (dropwizard-utils) are `@JvmStatic`, so Java calls them
  without `INSTANCE`. Java and Kotlin source compiles unchanged, but code compiled against earlier versions must
  be recompiled.
- guava-utils `ConditionalValue.set` no longer suspends, so any code can call it. Source compiles unchanged,
  but code compiled against earlier versions must be recompiled.
- guava-utils `GenericIdleService.startSync` and `stopSync` name their parameter `timeout` instead of `maxWait`
  and default to 30 seconds instead of 15, matching `GenericExecutionThreadService`. Calls that name `maxWait`
  must use `timeout`.
- guava-utils `GenericMonitor` retrying waits changed their limits.
  - **`maxWait`:** the two-argument overloads default to `Duration.INFINITE`, and `Duration.ZERO` checks the
    condition once instead of waiting without limit. A negative value still waits without limit.
  - **`timeout`:** a value below 1 ms throws `IllegalArgumentException`. Before, it spun the loop at full CPU.
- guava-utils visibility is tighter.
  - **`GenericValueWaiter`:** `currValue` has a private setter, so subclasses change it only through
    `checkCondition`, and `initValue` is no longer a property.
  - **`EMPTY_BYTE_ARRAY`:** it is private.
  - **`ServiceListenerHelper.starting`:** it no longer accepts `null`.
- The demo `main` functions no longer ship in guava-utils' `ConditionalValueKt`; they moved to test sources.
- script-utils-kotlin no longer has the `com.pambrose.common.script.System` object or `KotlinScript.importDecls`.
  Scripts are no longer prefixed with an import of that object, so `System.currentTimeMillis()`, `System.getenv()`,
  and every other `java.lang.System` member work again. `ScriptGuards` still rejects literal `System.exit(...)`.
- script-utils generated code and messages use fully-qualified type names.
  - **Kotlin casts:** `KotlinScript.varDecls` casts to names such as `java.util.ArrayList<kotlin.Int?>`.
  - **Java fields:** `JavaScript.varDecls` declares types such as `java.util.ArrayList<java.lang.Integer>`, so those
    types no longer need imports.
  - **Messages:** `AbstractScript.params` and the `add` error messages name types the same way.
- script-utils `AbstractEngine.engine` is deprecated. Using the engine directly bypasses variable bindings and context
  resets; use the evaluation and `resetContext` methods instead. Subclasses use the new protected `scriptEngine`.
  `AbstractScript.initialized` is also deprecated, because nothing reads it any more.
- script-utils `add` rejects a variable name that is not a valid identifier or is a reserved word of the script
  language, with a `ScriptException`. Before, such a name produced a confusing compile error, or injected code into
  the generated declarations.
- email-utils `Parameters.getEmail` normalizes the value with `toResendEmail` (lowercase and trim), so an
  address read from a request compares equal to the same address from any other source. Before, the raw
  parameter was wrapped as-is.
- redis-utils `newRedisClient` rejects `maxPoolSize = 0`, which created a pool that could never lend a
  connection, and accepts `UNLIMITED_POOL_SIZE` (-1), which commons-pool2 reads as no limit. Before, `0` was
  accepted and `-1` rejected.
- redis-utils treats the placeholder password `none` as no password, so the default URL
  `redis://user:none@localhost:6379` no longer sends `AUTH none`.
- redis-utils `withRedis`, `withNonNullRedis` and their suspending variants ping the server before running the
  block, so an unreachable server takes the documented null path instead of handing back a client whose first
  command throws.
- recaptcha-utils `validateRecaptcha` lets `CancellationException` propagate instead of reporting a cancelled
  verification as a failed one.
- exposed-utils `upsert(conflictIndex)` throws `IllegalArgumentException` for a non-unique index, or one that
  belongs to another table, instead of failing in the database at runtime.
- exposed-utils `ResultRow.get(index)` returns `null` for a column holding SQL NULL instead of throwing.
- grpc-utils `TlsUtils.serverTlsContext` builds on `GrpcSslContexts.forServer`, so the builder it hands back
  already carries gRPC's ALPN configuration. `NettyServerBuilder.sslContext` rejects a context without ALPN, so
  a context a caller built from that builder was unusable. `buildServerTlsContext` no longer applies
  `GrpcSslContexts.configure` a second time.
- grpc-utils client TLS no longer requires `trustCertCollectionFilePath`. An empty path keeps Netty's default
  trust manager, which verifies against the JVM trust store, so a server with a public-CA certificate works.
  Before, it threw `IllegalArgumentException`, contradicting the README.
- grpc-utils `GrpcDsl.channel` disables retry when `enableRetry = false`. grpc-java enables retry by default
  and the DSL never called `disableRetry()`, so the flag did nothing. The retry, `maxRetryAttempts` and
  `overrideAuthority` options now apply to the in-process transport as well, which ignored them.
- grpc-utils `GrpcDsl.streamObserver` is declared to return `StreamObserver<T>` rather than the
  `StreamObserverHelper<T>` implementation type. Code that named the helper type explicitly must change; DSL
  usage is unaffected.
- grpc-utils `GrpcDsl.channel`'s `tlsContext` defaults to `PLAINTEXT_CONTEXT`, matching `server()`.
- grpc-utils `Server.shutdownWithJvm` rejects a timeout below a millisecond, the resolution it works in, when
  the hook is registered. Before, the check ran inside the hook at JVM exit, where it threw before
  `shutdown()` and left the server running, and a sub-millisecond duration passed registration only to fail
  there.

### Bug fixes

- `koverVerify` now enforces coverage floors instead of passing unconditionally: no verification rules were
  configured, so the `coverage-verify` target and the `check` lifecycle verified nothing. The rule requires
  90% line and 80% branch coverage, set below the weakest package (line 96.0% in `concurrent`, branch 50.0%
  in `response`) rather than at the project's current 98.3% / 89.1%, so a regression such as an untested
  module trips it while ordinary drift does not. `make build` still passes `-x koverVerify`, being documented
  as a build without tests.

- `make build`, documented as "without tests", no longer runs the KMP modules' `jvmTest`: `check` pulls in
  `koverVerify`, which depends on the instrumented test tasks, and `-x test -x allTests` does not cover it.
- `make coverage-clean` cleans the JVM modules' test results too (`cleanTest` alongside `cleanAllTests`,
  which exists only in the KMP modules), and documents that the build cache can restore cleaned results.
- Publishing to Maven Central without a signing key now fails before the upload rather than after Central
  rejects the unsigned artifacts. Signing itself stays conditional on the key being present: making
  `signAllPublications()` unconditional, as first tried, breaks `make publish-local`, which publishes the
  release version with no key and then fails with "No configured signatory".
- The POM's `developerConnection` is `scm:git:ssh://git@github.com/...`, which was missing the `git@` user.
- The `ksp` plugin is declared in the root `plugins {}` block with `apply false`, like every other module
  plugin, so it resolves in one classloader instead of separately in each KMP module.

- grpc-utils `Server.shutdownGracefully` calls `shutdown()` inside its `try`, so `shutdownNow()` in the
  `finally` runs even when `shutdown()` itself throws, as it does on an already-terminated server. Its KDoc
  promised that; the sequence did not. Every caller benefits, and the JVM shutdown hook installed by
  `shutdownWithJvm` now simply delegates to it, swallowing the failure that nothing at JVM exit could observe.
- email-utils webhook models decode the payloads Resend documents. `Data` gained `tags`, `broadcast_id`,
  `message_id` and `template_id`, and `Bounce` gained `subType` and `type`, so a bounce keeps its
  `Permanent`/`Suppressed` classification. `ResendWebhookMsg.decode(body)` ignores unknown fields, so an event
  carrying fields these models do not declare still decodes; a default `Json` threw before. The same
  configured instance is exposed as `ResendWebhookMsg.json`, so a Ktor consumer can register it for
  `call.receive<ResendWebhookMsg>()`.
- email-utils ships the default `css/email.css`, so `email { }` works for a consumer of the published jar. The
  file previously existed only in this module's test resources, and the default threw `IllegalArgumentException`.
- email-utils `sendEmail` logs recipient counts and the Resend message id at info level, never the addresses
  themselves, and no longer logs a failure that it rethrows.
- recaptcha-utils sends `origin.remoteAddress` as `remoteip` instead of `origin.remoteHost`, which can be a
  reverse-DNS hostname and can block on a lookup.
- recaptcha-utils logs a warning the first time reCAPTCHA is enabled with a key missing, instead of passing
  every request silently, and the unreachable inner gate in `verifyRecaptcha` is gone.
- redis-utils reads the database index (`redis://host:6379/3`) and the `?protocol=` setting from the URL. Both
  were dropped, so every connection used database 0.
- redis-utils detects the `rediss://` scheme case-insensitively. Under a Turkish default locale, `REDISS://`
  lowercased to `redıss://`, so TLS was not enabled and the password went out in the clear.
- redis-utils pool helpers treat every `JedisException` as a connection failure, so pool exhaustion, borrow
  validation failures and authentication failures take the documented null path.
- redis-utils pools no longer validate a connection when it is returned, removing a PING round-trip from every
  pooled command.
- exposed-utils `toRowString` renders a row containing SQL NULL instead of throwing, and iterates the row's
  expressions rather than resolving every index through the O(n) lookup.
- exposed-utils `upsert(conflictIndex)` forwards Exposed's `onUpdate`, `onUpdateExclude` and `where` options,
  and its KDoc no longer points at a `PostgresTables.kt` that does not exist.
- script-utils pools no longer lose instances.
  - **Cancelled borrowers:** a borrower cancelled just as an instance was handed to it dropped that instance, shrinking
    the pool until every borrow waited forever. The instance now goes back into the pool.
  - **Sizes:** a size that is not positive throws `IllegalArgumentException`. Before, the pool had no instances and
    every borrow hung.
  - **Closing:** pools are `Closeable`. Closing one closes its instances, closes any borrowed instance when it is
    returned, and makes later borrows throw `ClosedReceiveChannelException`. If creating an instance fails during
    construction, the instances already created are closed. Both pools extend a new `AbstractEnginePool` base, and
    `AbstractExprEvaluatorPool` now uses its `T` type.
- script-utils expression evaluators reject literal JVM-termination calls before evaluating, through a new
  overridable `checkCode`, and their KDoc states that they are not a sandbox. Before,
  `KotlinExprEvaluatorPool(5).blockingEval("kotlin.system.exitProcess(0) == Unit")` terminated the JVM.
  `PythonExprEvaluator` applies the Python checks as well.
- script-utils expression evaluator pools reset each evaluator's context when it is returned, so the Kotlin engine's
  REPL history no longer grows with every expression. Each evaluator also gets its own global bindings instead of
  sharing the `ScriptEngineManager`'s.
- script-utils `KotlinScript` and `JavaScript` bind values whose runtime class cannot be named in code, such as
  `Regex`, `listOf(1, 2)`, `emptyList()`, and nested generic type arguments. They use the nearest public class or
  interface. `JavaScript` also maps `Char`, `Any`, and nested type arguments to valid Java, and reports an engine
  failure such as assigning a variable to an incompatible field as a `ScriptException`.
- script-utils scripts bind a variable added after an evaluation before the next one. Before, `KotlinScript` and
  `PythonScript` failed with an unresolved name, and `JavaScript` silently returned the field's default value. `add`
  and `resetContext` are now synchronized like the evaluation methods.
- script-utils `JavaScript` clears its imports and restores the default isolation when a pool reuses it. Before, one
  borrower's imports accumulated for every later borrower, and its isolation carried over.
- script-utils-python rejects `java.lang.System.exit(...)` and `Runtime.getRuntime().halt(...)`, which terminate the
  JVM from Jython. Methods named `exit` or `quit` can be defined, and the KDoc explains that the checks also match text
  inside string literals and comments.
- script-utils `KotlinScript` and `JavaScript` document that `close()` does nothing, `KotlinScript.eval` and
  `PythonScript.eval` have KDoc, and `JavaScript.evalScript` documents that every variable needs a matching public
  field.
- guava-utils `ConditionalValue` checks its current value before waiting, so a zero or sub-millisecond timeout
  returns `true` when the condition already holds. Before, the timeout was truncated to milliseconds, and a
  non-positive one returned `false` at once.
- guava-utils `ConditionalValue` notifies waiters on every `set`, including one that sets the same object after
  changing it in place. Before, `MutableStateFlow` skipped equal values, so such a waiter blocked until its
  timeout. The KDoc also explains that waiters see only the latest value.
- guava-utils `GenericMonitor` fixes.
  - **Throwing guards:** the untimed waits no longer leave the monitor a second time when the guard throws. The
    guard's exception reaches the caller instead of an `IllegalMonitorStateException`, and a monitor the caller
    holds stays held.
  - **Retry loops:** no attempt waits past `maxWait`, so `timeout = 10s, maxWait = 1s` returns after about
    1 second instead of 10.
  - **State changes:** a new protected `mutate { }` makes a change inside the monitor, and the KDoc explains that
    a change made outside it can strand waiting threads. `BooleanMonitor.set` uses it.
- guava-utils waits no longer truncate sub-millisecond durations: `CountDownLatch.await(Duration)`,
  `VerboseCountDownLatch.await`, and the monitor waits pass nanoseconds. `VerboseCountDownLatch.await` also
  rejects a timeout below 1 ms, which logged in a tight loop.
- guava-utils `startSync` and `stopSync` declare `@Throws(TimeoutException::class)` for Java callers and document
  the `TimeoutException` and `IllegalStateException` they throw.
- guava-utils `GenericValueWaiter.currValue` is `@Volatile`.
- guava-utils `GuavaDsl` listener callbacks can be set again, replacing the earlier one. Before, a second
  assignment threw.
- guava-utils `ByteArray.unzip` takes an optional `maxBytes`, so a gzip bomb can be rejected with
  `IllegalArgumentException` before it exhausts memory. The KDoc also documents the `IOException` thrown for
  corrupt input.
- prometheus-utils factories take an optional `registry`.
  - **Where:** `PrometheusDsl` builders, `SamplerGaugeCollector`, `InstrumentedThreadFactory`, and
    `SystemMetrics.initialize`.
  - **Default:** `CollectorRegistry.defaultRegistry`.
  - **Before:** every metric went to the default registry, so tests could not isolate metrics, and a second
    `InstrumentedThreadFactory` with the same name always threw.
  - **Compatibility:** `@JvmOverloads` keeps the previous signatures.
- `SamplerGaugeCollector` (prometheus-utils) implements `Collector.Describable`, so registering it no longer
  runs the sampler on the constructing thread.
- `SystemMetrics.initialize` (prometheus-utils) survives exporters that are already registered.
  - **Tracking:** it records each exporter per registry, so a repeat call registers exporters requested for the
    first time instead of ignoring them.
  - **Duplicates:** an exporter whose metrics are already registered, for example by
    `DefaultExports.initialize()`, is skipped with a warning.
  - **Before:** the call threw, and after a partial failure every retry failed on the first exporter.
- `InstrumentedThreadFactory` (prometheus-utils) counts a thread as created only when the delegate returns one.
  The comment about the running/terminated invariant is also corrected.
- `newBacklogHealthCheck` (dropwizard-utils) has a `() -> Int` overload that reads the backlog on every check.
  The `Int` form, which captured the size once, is deprecated. Both size checks now report
  `"Large size: N (threshold: T)"`.
- `LambdaServlet` (jetty-utils) runs its lambda before touching the response. Before, it set status `200`
  first, so a throwing lambda produced an empty `200` instead of a `500`.
- `LambdaServlet` and `VersionServlet` (jetty-utils) encode as UTF-8 unless the content type names a charset.
  Before, Jetty assumed ISO-8859-1 for `text/plain` and replaced other characters with `?`.
- `VersionServlet` (jetty-utils) is now a `LambdaServlet`, so the two can no longer drift apart. The
  `JettyDsl.server` and `servletContextHandler` blocks default to `{}`.
- The prometheus-utils README names `io.prometheus:simpleclient_httpserver` as the dependency its `HTTPServer`
  example needs.
- service-utils Jetty admin and metrics paths work with or without a leading slash. Before, a path configured
  as `"/ping"` was registered as `//ping`, so requests for `/ping` got a 404 under `GenericService`, while the
  same configuration worked under `GenericKtorService`.
- service-utils sub-services are released when startup or shutdown fails.
  - **Startup:** when `startUp()` fails, for example because the admin port is taken, it stops the Zipkin,
    metrics, and JMX services that had already started. Before, they kept running, and the metrics server kept
    its port and threads.
  - **Shutdown:** `shutDown()` attempts every step even when one fails, then rethrows the first failure with the
    others suppressed. Before, one failure left the rest running and the shutdown hook registered.
- service-utils `ZipkinReporterService` sends queued spans before closing. Before, spans recorded just before a
  graceful stop were dropped.
- service-utils `ZipkinReporterService` sends span batches after at most 500 ms instead of 1 second. Its flusher
  thread then delivers a batch finished just before a graceful stop within `close()`'s 1-second wait. Before, that
  span could still be dropped, which made `ZipkinReporterServiceTests` flaky.
- service-utils `ZipkinReporterService` has a `defaultServiceName`, taken from `ZipkinConfig.serviceName` and used
  by `newTracing()` when no name is given. Before, `ZipkinConfig.serviceName` was never used.
- service-utils `AdminConfig` and `MetricsConfig` have an optional `host` that binds the admin and metrics
  servers to one interface, such as `"127.0.0.1"`. Before, thread dumps, health details, and `/metrics` were
  always served on every interface. The default, `null`, keeps that behavior.
- service-utils logs a failed service at error level, with its cause. Before, failures were logged at info.
- service-utils builds the Zipkin URL with a single slash when `ZipkinConfig.path` has a leading slash.
- service-utils warns when `addService` is called after the init method. That service never reaches the
  `ServiceManager` or the `all_services_healthy` check, and the KDoc now explains the ordering and that the
  caller starts and stops the services it adds.
- The ktor-server-utils servlet bridge now covers more of the servlet contract.
  - **Responses:** `KtorServletResponse` implements `sendError`, `sendRedirect`, and `isCommitted`. Before,
    they threw `UnsupportedOperationException`, so a request with an unsupported HTTP method returned `500`
    instead of `HttpServlet`'s `405`.
  - **Requests:** `KtorServletRequest.getPathInfo()` returns `null`, and request attributes work.
  - **Lifecycle:** `Route.servlet` calls the servlet's `destroy()` once, when its own application stops.
  - **Character encoding:** a charset set through `setContentType` or `setCharacterEncoding` is used by the
    writer and included in `getContentType()` and the response's `Content-Type`, and it can't change after
    `getWriter()`. Before, the writer ignored it.
- `KtorServletRequest.getParameterMap()` (ktor-server-utils) is case-insensitive, like the other parameter
  accessors, and the documentation states that parameter names are case-insensitive, unlike in a servlet
  container.
- `HerokuHttpsRedirect.excludePrefix`/`excludeSuffix` (ktor-server-utils) match the request path, not the
  URI with its query string. `excludeSuffix(".txt")` no longer fails to exclude `/robots.txt?v=2`.
- `String.toJsonString()` (json-utils) is deprecated in favor of the new `String.reformatJson(prettyPrint)`.
  On a `String`, passing any argument, as in `toJsonString(prettyPrint = true)`, selects the generic overload,
  which serializes the string as a quoted JSON string literal instead of reformatting it.
- json-utils `size` counts array elements as well as object entries; it used to throw for an array.
  `deepCopy()` copies structurally, so it no longer throws on `NaN` or infinite numbers and no longer
  re-parses the whole tree.
- The public `JsonElementUtils.logger` holder (json-utils) is deprecated; the utilities use a private logger.
- `KtorDsl.blockingGet` (ktor-client-utils) accepts `httpClient` and `expectSuccess`, like `withHttpClient`.
  The previous signature is kept as a hidden deprecation, so compiled callers keep linking and calls like
  `blockingGet(url) { … }` still compile.
- `UrlSource` (core-utils) now applies connect and read timeouts, 10 and 30 seconds by default and
  configurable through new constructor parameters. Before, an unresponsive server blocked the reading thread
  forever. The URL is parsed with `URI(source).toURL()` instead of the deprecated `URL(String)` constructor.
  `content` is still fetched on every access, which the KDoc now documents.
- `getBanner` and `ReadResources.readResourceFile` (core-utils) find resources through the thread context
  classloader.
  - **Why:** before, they used kotlin-logging's and core-utils' own classloaders, so an application's
    resources were not found in servlet containers or plugin hosts.
  - **API:** there is a new `getBanner(filename, classLoader)` overload, and `readResourceFile` takes an
    optional `classLoader` argument.
  - **Compatibility:** `getBanner(filename, logger)` still compiles, and it falls back to the logger's
    classloader.
- `readProperties` (core-utils) handles the malformed lines it used to mishandle.
  - **Comments:** comment lines indented with whitespace or starting with `!` are skipped, instead of being
    set as properties.
  - **Empty keys:** a line with an empty key is skipped instead of throwing.
  - **Missing files:** a missing file now leaves the system properties unchanged. Before, the files ahead of
    it had already been applied.
  - **Docs:** the accepted `key=value` format is now documented.
- `repeatWithSleep` (core-utils) no longer sleeps after the last iteration.
- `toByteArray` (core-utils) is no longer deprecated. It is byte-for-byte identical to `toByteArraySecure`,
  which is now an alias, and serializing was never the unsafe side. The deprecations no longer carry
  `ReplaceWith` hints that produced code that would not compile.
- `captureStdout` (core-utils) encodes and decodes captured output as UTF-8 instead of the platform default
  charset. Its KDoc now warns that it swaps the process-wide `System.out`.
- `hostInfo` (core-utils) resolves the local host once instead of twice.
- Clarify in the KDoc that `withChecksum`/`verifyChecksum` detect accidental corruption only (an unkeyed
  SHA-256 is not tamper-proof), and that `toCsv` does not quote or escape elements.
- `toObjectSecure` no longer rejects every exception payload. The `java.lang.Runtime` blocklist entry
  prefix-matched `java.lang.RuntimeException`, the superclass of all unchecked exceptions, and
  `java.lang.RuntimePermission`. Package entries still match by prefix; single classes now match exactly.
- `maskUrlCredentials` only treats an `@` inside the URL authority as the credential separator.
  `https://api.example.com/users?email=bob@corp.com` used to be logged as `https://*****:*****@corp.com`,
  showing the wrong host and credentials that never existed.
- `Long.length` counts digits with integer arithmetic. The `log10` of a converted `Double` rounded up near
  powers of ten, so values such as `999_999_999_999_999` reported one digit too many.
- `DateUtils.toISO8601` always includes seconds. It used to drop them on a round minute (`08:30Z` instead of
  `08:30:00Z`).
- Two-digit years in `toMMDDYY`, `toFullDateString`, and `toLogString` wrap modulo 100 (`1999` → `99`,
  `2100` → `00`) instead of printing `-1` or `100`. `versionDesc` for a class without `@Version` now reports
  `Build Date: Unknown` instead of formatting epoch 0 as `Wed 12/31/-31 16:00:00`.
- `Route.servlet` (ktor-server-utils) now initializes servlets through `init(ServletConfig)`, as a servlet
  container does, instead of the no-arg `init()`.
  - **Why:** servlets that do their setup in `init(ServletConfig)` never got it. Their
    `getServletConfig()` stayed `null`, and `getServletName()`, `getInitParameter()`, and `log()` threw.
    Dropwizard's `HealthCheckServlet` is one such servlet, so `GenericKtorService`'s admin
    `/healthcheck` endpoint returned 500 on every request.
  - **What the servlet gets:** the config is named after the servlet's class and has no init parameters.
    Its internal `ServletContext` supports attributes and logging, and throws
    `UnsupportedOperationException` for container features such as dynamic registration.
- service-utils now publishes `guava-utils`, `dropwizard-utils`, `zipkin-utils`, `jetty-utils`,
  `ktor-server-utils`, and `metrics-jmx` as `api` (POM scope `compile`) instead of `implementation`
  (`runtime`). Their types appear in service-utils' public API: `AbstractGenericService` extends
  `GenericExecutionThreadService`, and the API also exposes `HealthCheckRegistry`, `MetricRegistry`,
  `JmxReporter`, Brave `Tracing`, Ktor `Application`, and Jakarta servlets. Consumers could not compile a
  subclass of `GenericService` or `GenericKtorService` without declaring those modules themselves.
  `prometheus-utils` and the embedded-server internals stay `runtime`.

### Tests

- The build, publishing, CI and documentation fixes in this release carry no new automated specs; they were
  verified by running the real thing: a full `build` (646 tests, lint and detekt, 0 failures), the generated
  POMs for the scope and version changes, the `make build` task graph for the excluded test tasks, a publish
  to the local repository with and without a signing key, and a YAML parse of both workflows.

- Add grpc-utils regression tests that would have caught the fixes above: a hand-built server context passed
  to `GrpcDsl.server`, ALPN assertions on both server paths, retry verification on the Netty and in-process
  transports, timeout validation and forced shutdown in the JVM hook, the declared `streamObserver` return
  type, and the single-assignment callbacks. The TLS and retry specs previously asserted only `isServer` and
  `authority()`, which neither bug would have broken.
- redis-utils drops the null-path specs that forced the private client factory to throw, a path production
  code cannot reach now that building a client never connects. `RedisConfigTests` covers the real failure
  modes: an unreachable port, an exhausted pool and a rejected password.
- ktor-server-utils `ServletRouteTests` was already covering `init(ServletConfig)`, a 405 from a GET-only
  servlet, non-ASCII output and `destroy()` on application stop, added with the earlier servlet fixes; this
  was verified rather than duplicated.
- Add email-utils, recaptcha-utils, redis-utils and exposed-utils regression tests.
  - **Resend payloads:** the documented `email.bounced` and `email.clicked` payloads decode verbatim.
  - **Logging:** logback `ListAppender` assertions pin what `sendEmail` and the misconfiguration warning log.
  - **reCAPTCHA:** cancellation propagation and the `remoteip` value, through a Ktor test application.
  - **Redis:** URL parsing (database, protocol, TLS scheme under a Turkish locale, placeholder password), pool
    validation, and the null paths for an unreachable server, an exhausted pool and an auth failure.
  - **Exposed:** an H2 table with a nullable column, plus upsert index validation and option forwarding.
- Add script-utils regression tests.
  - **Pools:** a borrower cancelled mid-handoff, invalid sizes, closing, instances returned after close, and a failed
    construction.
  - **Evaluators:** termination calls, context resets, and separate global bindings.
  - **Bindings:** hard-to-name values, variables added after an evaluation, invalid names, and Java type mapping.
  - **Guards:** the termination calls sit in functions or lambdas that are never called, so an unguarded engine
    returns instead of exiting the test JVM.
  - **Existing tests:** the Kotlin guard test uses the real `java.lang.System`, and the Java "illegal calls" test uses
    Java syntax instead of passing only because Python syntax does not compile as Java.
- Add guava-utils regression tests.
  - **Waiting:** zero and sub-millisecond timeouts, a same-object `set`, a throwing guard, and a thread blocked in
    a timed wait being woken by `set`.
  - **Retry limits:** elapsed-time bounds for `maxWait`, a zero `maxWait`, and a rejected sub-millisecond timeout.
  - **Services:** `startSync` timeouts, the `TimeoutException` declarations, and `genericServiceListener` logging.
  - **Stale comment:** a `GenericValueWaiterTests` comment described a removed implementation.
- zipkin-utils `ZipkinDsl` tests assert that the configuration block is applied, and close each `Tracing` with
  `use`, so a failed assertion no longer leaks `Tracing.current()`.
- Strengthen the metrics and servlet tests.
  - **Registries:** prometheus-utils tests assert values read back from a registry instead of `shouldNotBe null`
    on non-null types, including isolated registries and `SystemMetrics` exporter registration.
  - **Servlets:** jetty-utils tests serve `LambdaServlet` and `VersionServlet` from a real Jetty server to check
    the encoding.
  - **Coverage gap:** service-utils gains `HttpServletGroupTests`.
- Test the service-utils servers over HTTP.
  - **Endpoints:** both variants are requested on every admin endpoint and on `/metrics`, with paths configured
    with and without a leading slash. Before, no test issued a request to them.
  - **Lifecycle:** tests cover a startup that fails on an occupied port, a shutdown with a sub-service that fails
    to stop, and the log level of a failure.
  - **Zipkin:** a recording sender shows that stopping sends a just-finished span and that `newTracing()` uses the
    default service name.
  - **Binding:** tests check that a configured `host` refuses connections on other interfaces.
  - **Leak:** `ZipkinReporterServiceShutdownTests` closes the `OkHttpSender` it replaces.
- Cover the ktor-server-utils redirect plugin's actual redirect: the `Location` host, path, and query; a
  temporary redirect; a missing `x-forwarded-proto` header; a custom exclude predicate; and exclusions with a
  query string. Before, the tests asserted only the status code.
- Make the json-utils and ktor-client-utils tests able to fail.
  - **Lenient/strict formats:** the lenient-parsing test decodes into a class, so it sees
    `ignoreUnknownKeys`.
  - **Null handling:** a TODO is replaced with real JSON-null assertions.
  - **Error tests:** use `shouldThrow` instead of try/catch.
  - **Deep copy:** it asserts equality instead of identity.
  - **HTTP helpers:** the `blockingGet` setUp test echoes the header back, `newHttpClient` performs a
    request, and `withHttpClient` is verified to close the client it created.
- Replace the core-utils `length` brute-force sweeps, about 40 million assertions that ran on every KMP
  target, with power-of-ten boundary checks. Tighten the weak `withLineNumbers` and `toAdjustedString`
  assertions to exact output.

### Documentation

- CI hardening: `test.yml` declares `permissions: contents: read` (the repository's default token is
  write-scoped), checks out with `persist-credentials: false`, and cancels superseded PR runs through a
  `concurrency` group. Its redundant Lint step is gone, since `build` already runs lintKotlin and detekt
  through `check`. `kdocs.yml` keeps `pages: write` and `id-token: write` on the `deploy` job alone, so the
  job that builds PR code holds only `contents: read`.
- llms.txt, which exists for AI coding tools, no longer misdescribes modules: five modules labelled
  "Standalone" all declare `api(project(":core-utils"))`; script-utils-java compiles Java, not JavaScript;
  dropwizard-utils has no JMX integration; service-utils depends on six sibling modules, not core-utils
  alone; `getByPath` splits on `/` while `get` takes dot-notation; and guava-utils ships GZIP helpers rather
  than ZIP archiving, with its service lifecycle classes named `GenericIdleService` and
  `GenericExecutionThreadService`.
- CLAUDE.md: the format command is `./gradlew formatKotlin` (the per-source-set names mean
  `formatKotlinMain formatKotlinTest` silently skips all three KMP modules); Dokka HTML setup is attributed
  to `configureDokka()` rather than `configurePublishing`; the `-Xreturn-value-checker=check` flag and its
  production-only scope are documented; and the Dependabot section covers the netty-tcnative and Kotlin
  2.4.20 ignore rules.
- README and llms.txt state Kotlin 2.4.20 with the scripting artifacts pinned to 2.4.10, instead of
  advertising 2.4.10 as the project's Kotlin version.
- Removed stale top-level files: two superseded review documents (`code-review.md` and
  `docs/CODE_REVIEW.md`, the latter dated 2026-03-01 against v2.6.3), `.codeclimate.yml` (checkstyle for a
  single Java file, while the badge is Codacy), `system.properties` (a Heroku buildpack file in an
  undeployed library), and the `.wercker/` entry in `.gitignore` for a directory that no longer exists.

- Add READMEs for zipkin-utils and redis-utils, which llms.txt already linked to.
- Correct the guava-utils README.
  - **`genericServiceListener`:** it only builds a listener, so the example now passes it to `addListener`.
  - **Logging actions:** the `debug`/`info`/`warn`/`error` factories belong to `BooleanMonitor`'s companion, not
    to `GenericMonitor`.
- Fix core-utils README snippets that did not compile or misdescribed behavior.
  - **`Atomic.withLock`:** the lambda now uses its receiver instead of `it`.
  - **`criticalSection`:** it only sets a flag while the block runs; it does not exclude other callers.
  - **Checksums:** they are described as a corruption check.
  - **`waitForPortAvailable`:** it is called as `MiscFuncs.waitForPortAvailable`.
- Correct the core-utils README's `toObjectSecure` example, which did not compile, and document the
  required allow-list and stream limits.
- Add `docs/CODE_REVIEW_2026-09-14.md`, a full-repo code review with 139 enumerated, trackable action items.
  CR-001, CR-038, and CR-124 are the three fixes above.
- Bump project version to 4.0.0

## [3.2.3] - 2026-09-07

### Build & tooling

- Hold the Kotlin scripting artifacts at 2.4.10. Kotlin 2.4.20 regressed the JSR-223 K2 REPL that
  `script-utils-kotlin` uses: binding a value whose runtime class is a generic Java class (`ArrayList`,
  `LinkedHashMap`, `HashMap`) via `ScriptEngine.put()` makes every subsequent `eval()` fail to compile —
  including snippets that never reference the binding — because the declaration generated for the binding
  drops its type arguments. Non-generic bindings are unaffected, and there is no caller-side workaround
  since `put()` itself triggers it. Note the pin governs only the `kotlin-scripting-*` artifacts, not the
  compiler: `pambrose-gradle-plugins` 1.1.4 pulls `kotlin-gradle-plugin` 2.4.20, which wins on the
  buildscript classpath.
- Set `maxHeapSize = "2g"` on the `script-utils-kotlin` test task. Those tests run the Kotlin compiler
  in-process and had been relying on Gradle's 512m default; exceeding it surfaces as a misleading
  `Could not read file: ...kotlin-stdlib.jar!/...class` with the `OutOfMemoryError` buried several
  `Caused by` levels down.
- Bump the Gradle wrapper 9.6.1 → 9.7.1.
- Pin `brace-expansion` to 2.1.4 and `js-yaml` to 4.3.1 through `yarnResolutions`, closing two Dependabot
  DoS alerts. Both are requested by the JS/wasm toolchains with ranges yarn will not re-resolve on its own,
  so the explicit pins are required. Build-only — no effect on consumers.
- Streamline the build and dependency sections of `CLAUDE.md`.

### Dependency bumps

- `grpc` 1.83.1 → 1.84.0
- `exposed` 1.3.1 → 1.5.0
- `jedis` (redis) 7.5.3 → 8.0.1
- `jetty` 12.1.11 → 12.1.13
- `resend` 4.13.0 → 4.23.0
- `dropwizard` 4.2.39 → 4.2.40
- `logback` 1.6.1 → 1.6.3 (test runtime only)
- `h2` 2.4.240 → 2.5.250 (exposed-utils test scope only)
- `kotest` 6.2.3 → 6.2.4 (test scope only)
- `gradlePlugins` 1.1.1 → 1.1.4 (build-only)
- `versions` 0.57.0 → 0.60.0 (build-only)
- `detekt` 2.0.0-alpha.5 → 2.0.0-alpha.6 (build-only)
- Bump project version to 3.2.3

## [3.2.2] - 2026-07-31

### Build & tooling

- Replace the remaining `java.util.concurrent.atomic` uses with `kotlin.concurrent.atomics`, matching the
  `load()`/`store()`/`compareAndSet()` idiom already used in `AtomicDelegates`, `AtomicUtils`,
  `BooleanMonitor`, and `AbstractScript`. In core-utils' `SingleAssignVar`, `AtomicReference` now takes an
  explicit `null` initial value (the Kotlin API has no no-arg constructor) and reads via `load()`; the
  redis-utils and service-utils tests move to `AtomicInt`/`AtomicBoolean`. On the JVM these types are
  typealiases to their Java counterparts, so the published API and runtime behavior are unchanged.
- Move the ben-manes dependency-updates plugin id from `com.github.ben-manes.versions` to
  `io.github.ben-manes.versions`, which is where the plugin is now published. Build-only — no effect on
  consumers.

### Dependency bumps

- `grpc` 1.83.0 → 1.83.1
- `ktor` 3.5.1 → 3.5.2
- `gradlePlugins` 1.1.0 → 1.1.1 (build-only)
- `versions` 0.54.0 → 0.57.0 (build-only)
- `logback` 1.5.38 → 1.6.1 (test runtime only)
- `h2` 2.3.232 → 2.4.240 (exposed-utils test scope only)
- `mockk` 1.14.9 → 1.14.11 (test scope only)
- Bump project version to 3.2.2

## [3.2.1] - 2026-07-25

### Build & tooling

- Adopt Kotlin 2.4 collection-literal syntax (`[...]`) across every module (`src` and `test`). Enable the
  experimental `-Xcollection-literals` compiler flag on all JVM and KMP compilations (main and test), and
  convert eligible `listOf(...)` and `mutableListOf(...)` call sites to bracket literals; each
  `mutableListOf` conversion adds an explicit `MutableList<T>` on the variable, and `emptyList()` is left
  unchanged. Source-only and ABI-neutral — no published signature changes.

### Dependency bumps

- `kover` 0.9.8 → 0.9.9
- `grpc` 1.82.2 → 1.83.0
- `kotest` 6.2.2 → 6.2.3
- Bump project version to 3.2.1

## [3.2.0] - 2026-07-14

### Build & tooling

- Silence the deprecated `io.grpc.Attributes.keys()` warning in the grpc-utils test suite with a scoped
  `@Suppress("DEPRECATION")`. gRPC exposes no public replacement for enumerating attribute keys
  (`keysForTest()` is package-private), so the test's exact-key-set assertion is preserved rather than
  dropped. Test-only — no API or behavior change.

### Dependency bumps

- `kotlin` 2.4.0 → 2.4.10
- Bump project version to 3.2.0

## [3.1.0] - 2026-07-10

### New features

- New `DateUtils` object (core-utils, `DateUtils.kt`) collecting multiplatform date/time helpers built on
  `kotlinx-datetime`: ISO parsing (`parseToLocalDate`/`parseToLocalTime`/`parseToLocalDateTime`),
  `instantNow`/`localDateNow`/`localDateTimeNow`, US-style formatters (`toMMDDYYYY`, `toMMDDYY`, `toMMDD`,
  `toDashedYYYYMMDD`, `toFullDateString`, `toLogString`, `toMMDDYYYYHHMM`, `toCreated`, `toISO8601`,
  `toUTCDateTime`), `abbrevDayOfWeek`, and duration/age helpers (`age`, `toAdjustedString`). Every member is
  KDoc-documented.
- `DateUtils` is time-zone-neutral: `localDateNow`/`localDateTimeNow` default to
  `TimeZone.currentSystemDefault()` instead of a hardcoded region, and no named zone is resolved internally,
  so core-utils pulls no IANA time-zone database into JS/wasmJs consumers. Callers needing a fixed zone pass
  one explicitly (which on JS/wasmJs requires the `@js-joda/timezone` npm package on the consumer side).
- **Moved (source-incompatible)**: `toFullDateString` and `abbrevDayOfWeek`, previously top-level functions in
  `com.pambrose.common.util`, are now members of the `DateUtils` object; update call sites to
  `import com.pambrose.common.util.DateUtils.toFullDateString` (and `.abbrevDayOfWeek`).
- `DateUtils.toFullDateString(timeZone)` overload appends the DST-aware UTC offset for the given zone
  (e.g. `"Mon 04/10/26 14:30:00 -04:00"`, `Z` for a zero offset). kotlinx-datetime exposes the numeric
  offset rather than a letter abbreviation such as `EST`/`EDT`, which is ambiguous across regions; the
  no-argument `toFullDateString()` is unchanged. Resolving a named zone on JS/wasmJs still requires the
  consumer to add `@js-joda/timezone`; fixed-offset and UTC zones need no database.

### Bug fixes

- `DateUtils.toFullDateString` no longer appends a hardcoded `"PST"` suffix, which was incorrect during
  Pacific Daylight Time.
- `DateUtils.toLogString` left-pads the millisecond field, so `5 ms` renders as `.005` (previously `.500`).
- `DateUtils.toMMDDYYYYHHMM` zero-pads the hour, so 9 AM renders as `09:05` (previously `9:05`).

### Build & tooling

- KMP module `Test` tasks configure `testLogging` (PASSED/SKIPPED/FAILED events with full exception format).

### Dependency bumps

- `logback` 1.5.32 → 1.5.38
- `grpc` 1.82.1 → 1.82.2
- Bump project version to 3.1.0

## [3.0.0] - 2026-07-09

### Kotlin Multiplatform conversion

- `core-utils`, `json-utils`, and `ktor-client-utils` are now Kotlin Multiplatform modules targeting JVM, JS,
  wasmJs, and Native (iOS/macOS/tvOS/watchOS/Linux/Windows). Portable code moved to `commonMain`; JVM-bound
  declarations moved verbatim to `jvmMain`, so the published JVM API is unchanged. The mixed files
  (`StringExtensions`, `MiscExtensions`, `MiscFuncs`) are split across source sets with `@file:JvmName` +
  `@file:JvmMultifileClass`, keeping the compiled JVM facade classes binary-identical.
- The remaining 16 framework modules stay Kotlin/JVM and consume `core-utils`' jvm variant transparently.
- **Consumer note (Maven only)**: non-Gradle consumers of the three KMP modules must depend on the `-jvm`
  artifact (e.g. `core-utils-jvm`); Gradle consumers resolve the correct variant from the root coordinate
  automatically.
- **Known deviation**: `KtorDsl.blockingGet(...)` is now a JVM-only extension function on `KtorDsl`
  (`runBlocking` does not exist in common code). Qualified call sites compile unchanged, but the member-import
  form becomes `import com.pambrose.common.dsl.blockingGet`, and the compiled symbol moved from `KtorDsl` to
  `KtorDslJvmKt` (binary-incompatible for this one function).
- `kotlin-logging` dependency switched from the `-jvm` artifact to the multiplatform root artifact
  (JVM consumers resolve the same jar as before).
- Tests: portable Kotest specs moved to `commonTest` and now execute on JVM (JUnit Platform), Node.js,
  wasmJs, and native simulators via the Kotest Gradle plugin (`io.kotest` + KSP); JVM-bound specs stay in
  `jvmTest`. watchOS/tvOS simulator test tasks are disabled (no simulator runtimes installed by default).
- The native target set excludes the Intel-based Apple targets (`macosX64`, `tvosX64`, `watchosX64`), which
  Kotlin 2.4 deprecates for removal (https://kotl.in/native-targets-tiers); Apple platforms are covered by the
  Arm64 device/simulator targets.
- Build: `kmpModuleNames` switch in the root `build.gradle.kts` selects KMP vs JVM configuration;
  settings-level ivy repositories serve the Node.js/Yarn/Binaryen toolchain downloads under
  `FAIL_ON_PROJECT_REPOS` (each toolchain env spec's `downloadBaseUrl` is unset so the Kotlin plugin never
  registers project-level repositories); `kotlin-js-store/` lockfiles are now tracked; Gradle heap raised
  to 8g for Kotlin/Native link tasks.

### Testing

- Aggregate JVM instruction coverage raised from 83.5% to 98.7%; the remaining misses are almost entirely
  unreachable defensive branches. Coverage of the core-utils and ktor-client-utils JVM extensions
  (`blockingGet` against a loopback HTTP server, salted hashes with known-answer vectors) closes the
  codecov patch findings from the multiplatform conversion.
- `RecaptchaService`'s verification `HttpClient` is now an internal, swappable property, giving tests a seam
  to fake Google's siteverify endpoint with a MockEngine-backed client; new tests cover the verification
  success/failure response branches, the outgoing form parameters, and `RecaptchaResponse`'s serializer
  write path. Production behavior and the public API are unchanged.
- New hermetic specs across the JVM modules: in-process gRPC round-trip and TLS-context construction from
  committed self-signed PEM fixtures (grpc-utils), MockK-based servlet adapter and Jedis tests
  (ktor-server-utils, redis-utils), H2 in-memory Exposed tests including the custom `upsert`
  (exposed-utils), latch-handshake concurrency tests (guava-utils), webhook serialization branches
  (email-utils), and Jython pool lifecycle (script-utils-python).

### Build & tooling

- Aggregate `detekt`/`detektBaseline` tasks are wired with `tasks.named()` instead of a name-matching live
  spec; the kotlinter lint/format generated-source excludes collapsed onto the shared
  `ConfigurableKtLintTask` supertype with the excluded path derived from `layout.buildDirectory`
  (separator-safe); watchOS/tvOS simulator test disabling selects targets by `konanTarget.family` instead of
  task-name prefixes; duplicated publishing jar arguments and the `-Xreturn-value-checker` flag hoisted to
  shared values.

### Dependency bumps

- `jetty` 12.1.10 → 12.1.11
- `kotest` 6.2.1 → 6.2.2
- Security: Yarn resolution overrides force patched versions of vulnerable transitive npm packages in the
  JS/wasmJs test toolchains — `ws` 8.20.1 → 8.21.0 (memory-exhaustion DoS), `serialize-javascript`
  6.0.2 → 7.0.5 (RCE + CPU DoS), `diff` 7.0.0 → 8.0.3 (parsePatch DoS) — clearing all five Dependabot
  alerts on the `kotlin-js-store/` lockfiles. Dev-time test infrastructure only; nothing ships in
  published artifacts.
- Bump project version to 3.0.0 (major: the two binary/coordinate deviations above for the KMP modules)

## [2.9.3] - 2026-07-03

### New features

- `with(a, b) { ... }` (core-utils, `ScopeFunctions.kt`): a two-receiver variant of the standard `with` that runs a `context(A, B) () -> R` block with both `a` and `b` available as context parameters. The two type parameters keep each receiver's distinct type, so each can satisfy a separate `context(...)` parameter (a `vararg` would collapse them to their common supertype). For more receivers, add further fixed-arity overloads.
- `readProperties(vararg fileNames)` / `readProperties(List<String>)` (core-utils, `PropertyFunctions.kt`): load `key=value` lines from the given files into JVM system properties via `System.setProperty`, skipping `#`-comment and non-`key=value` lines and failing fast when a file is missing.

### Build & tooling

- Enable Kotlin's unused-return-value checker (`-Xreturn-value-checker=check`) on the production `compileKotlin` task only; test source sets are skipped so Kotest's assertion DSL (which returns its receiver) does not emit false positives.
- Replace the `com.pambrose.stable-versions` convention plugin with a direct `com.github.ben-manes.versions` plugin plus an inline `configureVersions()` helper. Its `isNonStable` filter now uses a delimiter-anchored regex that recognizes dot-separated qualifiers (Netty's `.Beta1`, Spring/Hibernate `.RC1`/`.CR1`) while leaving classifier versions such as guava's `-jre` stable, and it rejects a pre-release candidate only when the current version is stable — so dependencies intentionally tracked on a pre-release line (e.g. detekt alphas) still surface updates. The `DependencyUpdatesTask` is now configured lazily via `configureEach`.

### Dependency bumps

- Gradle wrapper 9.5.1 → 9.6.1
- `detekt` 2.0.0-alpha.4 → 2.0.0-alpha.5
- `gradlePlugins` (pambrose convention plugins) 1.0.14 → 1.1.0
- `mavenPublish` 0.36.0 → 0.37.0
- `grpc` 1.82.0 → 1.82.1
- `ktor` 3.5.0 → 3.5.1
- `exposed` 1.3.0 → 1.3.1
- `redis` 7.5.2 → 7.5.3
- Bump project version to 2.9.3

## [2.9.2] - 2026-06-14

### New features

- `ApplicationCall.respondWith`/`redirectTo` and their `RoutingContext` overloads (ktor-server-utils) now accept a `suspend () -> String` block, so the body/redirect-target lambda can call suspending functions. Existing non-suspending lambdas remain valid, since `() -> String` is a subtype of `suspend () -> String`.

### Dependency bumps

- `detekt` 2.0.0-alpha.3 → 2.0.0-alpha.4

## [2.9.1] - 2026-06-11

### Breaking changes

- `Table.upsert` (exposed-utils) is now a thin overload of Exposed's native `upsert` and takes only a unique `Index` as the conflict target (`upsert(conflictIndex = myUniqueIndex) { ... }`). The hand-rolled `UpsertStatement` class and the `conflictColumn` parameter have been removed; pass a single-column unique index instead of a column. The lambda receiver is now Exposed's `org.jetbrains.exposed.v1.core.statements.UpsertStatement<Long>`.

### Refactoring & internals

- Replace the custom `UpsertStatement` and its `prepareSQL` override with a forwarding call to Exposed's native `upsert`, keeping named index definitions as the single source of truth for conflict columns while letting Exposed generate the `ON CONFLICT` SQL

### Dependency bumps

- `grpc` 1.81.0 → 1.82.0
- `netty-tcnative-boringssl-static` pinned to 2.0.75.Final to match gRPC 1.82.0 (catalog alias `netty-ssl` renamed to `netty-tcnative`)

## [2.9.0] - 2026-06-03

### Breaking changes

- `JavaScript.eval`/`evalScript` now return `Any?` instead of `Any`, so a null script result is returned honestly (fixes a `NullPointerException` on `eval("null")`); binary-compatible, source-breaking only for Kotlin callers that bound the result to a non-null type, and aligns `JavaScript` with `KotlinScript`/`PythonScript`
- `KtorServletRequest.getContentType()` now returns `String?` and yields `null` when no `Content-Type` header is present, matching the `HttpServletRequest` contract (previously returned `"*/*"`)
- Remove the always-false `isJava6` public val from `guava-utils`

### Bug fixes

- `ContentSource.GitLabFile` uses `repo.domainName` instead of a hardcoded `gitlab.com`, so self-hosted GitLab instances resolve correctly
- `ListUtils.listPrint` quotes String elements per element instead of casting the whole list, fixing a `ClassCastException` on mixed-type lists
- `InstrumentedThreadFactory` increments terminated before decrementing running, so a concurrent scrape never sees `running + terminated < created`
- `AbstractExprEvaluator.compute()` returns `Any?` and propagates null instead of throwing on a null expression result
- `String.singleToDoubleQuoted()` now escapes inner double quotes per its KDoc (`te"st` → `"te\"st"`)
- `AbstractGenericService` removes its JVM shutdown hook on `shutDown()`, fixing a hook leak that left one hook registered per service instance for the life of the JVM
- `UpsertStatement` emits `DO NOTHING` when every inserted column is part of the conflict key, instead of generating a dangling `DO UPDATE SET` that PostgreSQL rejects
- `SamplerGaugeCollector` validates `labelNames`/`labelValues` sizes at construction (fail-fast) instead of throwing on every Prometheus scrape; `collect()` now guards the user-supplied sampler with `runCatching` and reports `Double.NaN` instead of aborting the whole scrape
- `GenericValueWaiter`/`BooleanWaiter` support concurrent waiters via per-call `(predicate, continuation)` pairs under a lock, fixing waiters clobbering each other; also fixes a liveness bug where a satisfied wait could stall for the full timeout, and isolates a throwing predicate so it no longer starves the other waiters
- `ZipExtensions.unzip()` decodes non-gzipped bytes explicitly as UTF-8 (was the JVM default charset, which corrupted non-ASCII content on a non-UTF-8 JVM)
- `String.obfuscate()` requires `freq > 0` instead of throwing `ArithmeticException` (divide by zero)
- `MiscJavaFuncs.random(int)`/`random(long)` use the unbiased `nextInt`/`nextLong(bound)`, removing modulo bias and rejecting `bound <= 0`
- `MiscJavaFuncs.sleepMillis` restores the thread interrupt flag when `Thread.sleep` is interrupted
- `KtorServletResponse` backs its header map with a case-insensitive `TreeMap` per RFC 9110 §5.1 / the `HttpServletResponse` contract

### New features

- Add a `ByteArray.zip()` GZIP overload mirroring `String.zip()`, avoiding a redundant UTF-8 re-encode for callers that already hold bytes
- Broaden `isValidEmail()` to accept plus-addressing (`user+tag@example.com`), TLDs longer than four characters (`.travel`, `.email`), and single-character domain labels (`user@x.io`), while still rejecting clearly invalid input

### Refactoring & internals

- Extract `AbstractGenericService<T>` to de-duplicate the ~95% identical `GenericService` and `GenericKtorService` base classes; public API unchanged
- Centralize JVM-termination detection in a new `ScriptGuards` (script-utils-common), broadened to cover `System.exit`, `kotlin.system.exitProcess`, and `Runtime.exit`/`halt` across the Kotlin and Java engines; documented everywhere as best-effort guards against accidental termination, **not** a security sandbox
- Remove `LameBooleanWaiter` (a redundant, lower-quality duplicate of `BooleanWaiter`)
- Dead-code sweep: remove unreachable branches, dead fallbacks, a shipped `Example.kt` demo, and stale suppressions across eight modules
- Idiomatic-Kotlin and small performance cleanups (single-pass JSON path walks, a cached default `Json`, `joinToString` hex building, `toXOrNull()` numeric checks, etc.), each behavior-equivalence verified
- Refactor four convoluted-logic targets (`Duration.format`, `BooleanMonitor` init, `JavaScript.javaEquiv`, and `KtorServletRequest` case-insensitive parameter access) with characterization tests

### Documentation

- KDoc accuracy fixes across dropwizard-utils, recaptcha-utils, exposed-utils, json-utils, redis-utils, and script-utils-common so the public docs match actual behavior

### Tests

- Add `grpc-utils` tests: `TlsUtilsTests` (11 cases) and `ServerExtensionsTests` (5 cases) covering trust/cert/key validation, `TlsContext.desc()`, `PLAINTEXT_CONTEXT`, graceful-shutdown ordering, and the `require(timeout > 0)` precondition; add `mockk` to `grpc-utils` test dependencies
- Add tests for open coverage gaps across six modules (script-utils-kotlin, recaptcha-utils, jetty-utils, email-utils, and others)
- Drop redundant `runBlocking { }` wrappers from suspend Kotest test bodies in script-utils-kotlin, script-utils-java, and redis-utils

### Static analysis & coverage

- Wire **Detekt** into every subproject via `configureDetekt()` in the root `build.gradle.kts`, with HTML/checkstyle reports per module and auto-detection of optional `config/detekt/detekt.yml` and `config/detekt/baseline.xml`; `make lint` now also runs Detekt
- Add a `config/detekt/detekt.yml` tailored for a multi-module utility library (disables threshold-style rules, excludes test code from `EmptyFunctionBlock` / `VariableNaming`); activate `LongMethod`, suppress `IgnoredReturnValue`, and resolve the type-resolution findings surfaced by the upgrade across eight modules
- Add `make detekt` and `make detekt-baseline` targets
- Add Kover coverage subcommands: `coverage-html`, `coverage-log`, `coverage-verify`, `coverage-open`, `coverage-packages`, `coverage-clean`; `make coverage` now generates both HTML and XML
- Move the `coverage-packages` inline Python to `scripts/coverage-packages.py` with a friendlier missing-report error

### Build & tooling

- Tidy Gradle config: organize `gradle/libs.versions.toml` into functional categories, alphabetize the scripting module includes, and dedupe the Kover excludes comment
- Centralize the Gradle wrapper version and JVM target (`17`) under a `# Toolchain` section in `gradle/libs.versions.toml`, consumed from `build.gradle.kts` and the `Makefile`'s `upgrade-wrapper` target
- Consolidate duplicated string literals in `build.gradle.kts` (`common-utils`, `"17"`, `config/detekt`) into named vals
- Add `make help` for a self-documenting target list, parsed from `## description` comments
- Add fail-fast guards in the `Makefile` when `VERSION` or `GRADLE_VERSION` cannot be parsed, instead of silently passing empty strings to publish/wrapper commands
- Pass `ORG_GRADLE_PROJECT_signingInMemoryKeyId` in `GPG_ENV` so the in-memory signing plugin has all three required properties
- Drop the unused `compile` alias from the `Makefile`

### Dependencies

- Add **Detekt** `2.0.0-alpha.3` (plugin id `dev.detekt`), with the aggregate `detekt` task depending on the per-source-set `detektMain`/`detektTest` tasks so analysis runs with full type resolution
- Upgrade Gradle wrapper 9.5.0 → 9.5.1
- Bump `kotlin` 2.3.21 → 2.4.0
- Bump `kotlinx-coroutines` 1.10.2 → 1.11.0
- Bump `ktor` 3.4.3 → 3.5.0
- Bump `exposed` 1.2.0 → 1.3.0
- Bump `jetty` 12.1.8 → 12.1.10
- Bump `redis` 7.5.0 → 7.5.2
- Bump `logging` 8.0.1 → 8.0.4
- Bump `dropwizard` 4.2.38 → 4.2.39
- Add `h2` 2.3.232 as a test dependency
- Bump project version to 2.9.0

## [2.8.2] - 2026-05-02

- Add Kotlinx Kover coverage with aggregated HTML/XML reports across all modules and Codecov upload from CI
- Add `codecov.yml`; gate patch coverage at 70% and silence no-change PR comments
- Improve repository line coverage from ~52% to ~62%
- Upgrade Gradle wrapper to 9.5.0; add GPG environment validation in the publish targets; deduplicate Dokka configuration
- Move `group` and `version` from `build.gradle.kts` to `gradle.properties`; preserve `-PoverrideVersion` for snapshot/publish targets; update `Makefile` to read `VERSION` from `gradle.properties`
- Switch `overrideVersion` and `signingInMemoryKey` reads to `providers.gradleProperty(...)`
- Hoist Kover excludes to a shared list reused by the root aggregator and per-project filter
- Derive POM SCM and homepage URLs from a shared `scmHost` constant; wrap `pom.name` in a provider for consistency with `description`
- Enable `org.gradle.parallel=true`; pass `--no-parallel` to `versioncheck` (manes plugin is not parallel-safe)
- Replace deprecated `DefaultJedisClientConfig.ssl(Boolean)` with `sslOptions(SslOptions.defaults())` (Jedis 7.4.2+)
- Fix functional bugs in Redis, Python script handling, Banner, SystemMetrics, and Zipkin
- Add `CountDownLatch.await(Duration)` extension in `guava-utils`
- Bump `kover` 0.9.1 → 0.9.8, `grpc` 1.80.0 → 1.81.0, `netty-tcnative` 2.0.76.Final → 2.0.77.Final
- Bump project version to 2.8.2

## [2.8.1] - 2026-04-24

- Bump Kotlin to 2.3.21
- Tighten root `build.gradle.kts`: move shared configuration into `allprojects`, scope Dokka per-subproject, simplify repository declarations
- Promote inter-module dependencies to the `api` configuration so consumers get correct transitive resolution
- Drop the stale Kover target
- Refresh dependency versions in `gradle/libs.versions.toml` and adopt dependency bundles
- Bump project version to 2.8.1

## [2.8.0] - 2026-04-22

- Upgrade Jetty to 12 (EE11); move servlet imports from `org.eclipse.jetty.servlet.*` to `org.eclipse.jetty.ee11.servlet.*` (breaking)
- Migrate to `com.pambrose` Gradle convention plugins (`pambrose.kotlinter`, `pambrose.testing`, `pambrose.stable-versions`) at version 1.0.14
- Rely on `pambrose.testing` defaults for `kotest-runner-junit5` and `kotlin-test`; remove per-module test dependency duplication across all 19 modules
- Consolidate subproject plugin application in root `build.gradle.kts`
- Consolidate Dokka aggregation at the root project
- Promote several dependencies to the `api` configuration where their types leak into public signatures
- Clean up per-module build scripts and remove unused version catalog entries
- Add `RELEASE_NOTES.md` release history index
- Add `.superset/` to `.gitignore`
- Bump project version to 2.8.0

## [2.7.1] - 2026-04-04

- Consolidate Dokka docs generation in root project with GitHub Actions workflow
- Fix missing POM description by deferring evaluation with provider
- Improve Maven publishing metadata with per-module POM descriptions
- Add Codacy badge and reorder README badges
- Reduce Gradle heap size
- Clean up code across multiple modules (unused imports, redundant suppressions, formatting)

## [2.7.0] - 2026-04-04

- Migrate artifact publishing from JitPack to Maven Central (group: `com.pambrose.common-utils`)
- Rename packages from `com.github.pambrose.common.*` to `com.pambrose.common.*`
- Add Vanniktech Maven Publish plugin for Maven Central publishing with POM metadata and signing
- Remove `common-utils-bom` module and all BOM dependencies in favor of explicit version refs
- Disable root project publishing to prevent stale artifacts
- Remove `jitpack.yml`
- Add ~78 new tests across 9 modules (ktor-client-utils, ktor-server-utils, prometheus-utils, guava-utils, service-utils, email-utils, jetty-utils, recaptcha-utils, script-utils-common)
- Add MockK and ktor-client-mock test dependencies
- Add GitHub Actions CI workflow for build and lint
- Update all documentation to reflect Maven Central coordinates
- Update copyright headers to 2026
- Upgrade Gradle wrapper to 9.4.1
- Update dependencies

## [2.6.2] - 2026-03-16

- Convert all tests from JUnit 5 to Kotest StringSpec (~50 test files across all modules)
- Upgrade Kotlin to 2.3.20
- Upgrade Gradle wrapper to 9.4.0
- Update Redis dependency to 7.4.0
- Refactor import statements for consistency
- Enable Gradle configuration caching
- Rename Makefile tasks (`trigger-build` to `trigger-jitpack`, `view-build` to `view-jitpack`)

## [2.6.1] - 2026-03-03

- Update Ktor dependency to 3.4.1

## [2.6.0] - 2026-02-28

- Upgrade Jetty to 11.0.26 and migrate servlet imports to Jakarta
- Add Ktor servlet support and migrate servlet handling from Jetty
- Update Jakarta Servlet API to 6.1.0
- Add string extension functions (`ensureLeadingSlash`, prefix/suffix helpers)
- Add `GenericKtorService` and refactor servlet handling
- Migrate Exposed from 0.61.0 to 1.1.1 (new v1 API, `JdbcTransaction`, `BlockingExecutable`)
- Extract `VERSION` variable in Makefile from `build.gradle.kts`
- Add agent-optimized `llms.txt` for project discoverability
- Update logback, logging, and redis dependencies

## [2.5.3] - 2026-02-09

- Downgrade Gradle wrapper to 9.2.0

## [2.5.2] - 2026-02-08

- Dependency updates

## [2.5.1] - 2026-02-08

- Dependency updates

## [2.5.0] - 2026-02-08

- Dependency updates

## [2.4.14] - 2026-02-05

- Downgrade Ktor to 3.3.3

## [2.4.13] - 2026-02-05

- Dependency updates

## [2.4.11] - 2026-02-05

- Dependency updates

## [2.4.10] - 2026-01-29

- Update Gradle to 9.3.0
- Bump Logback to 1.5.24
- Remove obsolete Codebeat badge
- Add OpenSpec documentation and commands for spec-driven development
- Update Ktor, Resend, and Serialization dependencies

## [2.4.9] - 2025-12-16

- Dependency version updates

## [2.4.8] - 2025-12-16

- Upgrade to Kotlin 2.3.0
- Dependency updates

## [2.4.7] - 2025-11-07

- Upgrade Gradle wrapper to 9.2.0
- Update library versions

## [2.4.6] - 2025-10-25

- Update to Kotlin 2.2.21
- Dependency updates

## [2.4.5] - 2025-09-10

- Update Jetty to 10.0.26
- Update Dropwizard, Kotest, Kotlin, logging, nettyTcNative, Redis, and Resend versions

## [2.4.4] - 2025-08-26

- Update dependency versions and adjust plugin application in build configuration

## [2.4.3] - 2025-08-20

- Upgrade Gradle to 9.0.0
- Update library dependencies to use BOMs
- Add `common-utils-bom` module with dependency constraints and publishing configuration

## [2.4.2] - 2025-08-18

- Update Kotlin to 2.2.10
- Add `email-utils` module with `EmailUtils` and `ResendService`
- Add data classes for email message handling (Bounce, Click, Data, Header, ResendWebhookMsg)
- Add `recaptcha-utils` module with configuration and verification service
- Add Kotlin serialization plugin to build configuration

## [2.4.1] - 2025-08-15

- Update dependencies
- Change `implementation` to `api` for Java and Python scripting libraries

## [2.4.0] - 2025-08-10

- Update library versions for compatibility and performance
- Remove `corex-utils` module
- Upgrade Kotest to 6.0.0.M14
- Upgrade JVM target to 17
- Update datetime library to 0.6.x

## [2.3.11] - 2025-06-25

- Convert to `build.gradle.kts` (Kotlin DSL)
- Convert to using `libs.versions.toml` version catalog
- Add `json-utils` module
- Refactor build scripts
- Dependency updates

## [2.3.10] - 2025-03-22

- Refactor atomic operations to use `kotlin.concurrent.atomics`
- Update library versions

## [2.3.9] - 2025-03-20

- Refactor dependencies to use `rootProject` libraries
- Update copyright
- Dependency updates

## [2.3.8] - 2024-12-19

- Update coroutines, JUnit, Ktor, and Logback jars

## [2.3.7] - 2024-12-11

- Consolidate Version classes

## [2.3.6] - 2024-12-10

- Add `Version2` with build time value

## [2.3.5] - 2024-12-10

- Update gRPC jars

## [2.3.4] - 2024-12-06

- Dependency updates

## [2.3.3] - 2024-12-02

- Dependency updates

## [2.3.2] - 2024-11-29

- Replace `PipelineCall` with `RoutingContext` in Ktor 3.0.1 code

## [2.3.1] - 2024-11-27

- Change minimum Java version from 17 to 11

## [2.3.0] - 2024-11-27

- Update to Ktor 3.0.1

## [2.2.0] - 2024-11-26

- Update Redis pool options

## [2.1.2] - 2024-11-10

- Add max wait seconds to Redis pool

## [2.1.1] - 2024-11-08

- Revert Redis jar

## [2.1.0] - 2024-10-18

- Update jars
- ktlint cleanup

## [2.0.0] - 2024-06-11

- Update klogger and Kotlin jars

## [1.51.0] - 2024-05-07

- Dependency updates

## [1.50.0] - 2024-03-22

- Dependency updates

## [1.44.2] - 2024-01-06

- Fix issue with evaluating Java script

## [1.44.1] - 2024-01-06

- Add verbose flag for executing Java scripts

## [1.44.0] - 2024-01-06

- Dependency updates

## [1.43.0] - 2023-12-11

- Upgrade Kotlin to 1.9.22
- Upgrade Dropwizard, Exposed, gRPC, and other dependencies
- Refactor gRPC DSL to enhance code readability (channel and server creation abstracted into utility functions)
- Refactor JavaScript utilities code structure (add `evalScriptInternal` synchronized function)
- Kotlinter cleanup

## [1.42.1] - 2023-11-01

- Fix regression in `ContentSource.kt`

## [1.42.0] - 2023-11-01

- Upgrade to Kotlin 1.9.20
- Update gRPC to 1.59.0, Dropwizard to 4.2.21, Exposed to 0.44.1
- Update Guava to 32.0.1-jre, Ktor to 2.3.5, Redis to 5.0.2
- Update to Kotlinter 4.0.0

## [1.41.0] - 2023-08-23

- Update Kotlin, Exposed, gRPC, and Logback jars

## [1.40.0] - 2023-08-02

- Update Exposed, coroutines, Ktor, Redis, and gRPC jars

## [1.39.9] - 2023-07-12

- Fix release issue

## [1.39.0] - 2023-07-06

- Update to Kotlin 1.9.0

## [1.38.0] - 2023-05-16

- Update gRPC to 1.55.1 and coroutines to 1.7.1

## [1.37.0] - 2023-05-07

- Update coroutines to 1.7.0

## [1.36.0] - 2023-05-03

- Update to Kotlin 1.8.21

## [1.35.0] - 2023-04-09

- Dependency updates

## [1.34.1] - 2023-02-01

- Fix logger issue in `Banner.kt`

## [1.34.0] - 2023-02-01

- Dependency updates

## [1.33.0] - 2022-12-31

- Update to Kotlin 1.8.0

## [1.32.0] - 2022-12-14

- Update deprecated call

## [1.31.0] - 2022-11-19

- Dependency updates

## [1.30.0] - 2022-10-11

- Dependency updates

## [1.29.0] - 2022-10-02

- Dependency updates

## [1.28.0] - 2022-07-10

- Update to Kotlin 1.7.10
- Dependency updates

## [1.27.0] - 2022-06-11

- Upgrade to Kotlin 1.7.0

## [1.26.0] - 2022-05-31

- Remove redundant String `lowercase` and `uppercase` functions
- Add `PipelineCall` typealias
- Dependency updates

## [1.25.0] - 2022-04-29

- Update Kotlin to 1.6.21
- Code cleanup
- Dependency updates

## [1.24.0] - 2022-04-11

- Upgrade to Ktor 2.0

## [1.23.0] - 2022-04-01

- Add `String.toLower()` and `String.toUpper()`
- Update to Kotlin 1.6.20
- Dependency updates

## [1.22.0] - 2022-02-22

- Dependency updates

## [1.21.0] - 2022-01-13

- Upgrade to Ktor 2.0
- Dependency updates

## [1.20.0] - 2021-12-14

- Update to Kotlin 1.6.10
- Dependency updates

## [1.19.0] - 2021-11-16

- Update to Kotlin 1.6.0
- Dependency updates

## [1.18.0] - 2021-08-27

- Dependency updates

## [1.17.0] - 2021-08-25

- Dependency updates

## [1.16.0] - 2021-07-16

- Dependency updates

## [1.15.0] - 2021-06-26

- Fix missing sources

## [1.14.0] - 2021-06-24

- Dependency updates

## [1.13.0] - 2021-06-20

- Clean up `build.gradle`

## [1.12.0] - 2021-06-02

- Dependency updates

## [1.11.0] - 2021-05-25

- Upgrade to Kotlin 1.5.10
- Add Kotlinter

## [1.10.0] - 2021-05-20

- Release branch created

## [1.9.0] - 2021-05-01

- Upgrade to Kotlin 1.5.0
- Lambda cleanup
- Code cleanups
- Update copyright

## [1.8.1] - 2021-04-24

- Clean up `Banner.kt`
- Clean up `build.gradle`
- Dependency updates

## [1.7.0] - 2021-02-03

- Add `String.maxLength()`
- Update `HttpClient` calls to include `expectSuccess`
- Add gRPC `enableRetry` support
- Update to Kotlin 1.4.30

## [1.6.0] - 2020-12-15

- Upgrade Kotlin to 1.4.21
- Fix problem with Kotlin bindings requiring a tmp name
- Add `ExprEvaluators` and Pools
- Dependency updates

## [1.5.0] - 2020-12-04

- Exclude default user from Redis `auth()` call
- Add `ContentSource.quotedSource`
- Make `GLOBAL` bindings nullable
- Cast Kotlin engine to `KotlinJsr223JvmLocalScriptEngine`
- Dependency updates

## [1.4.0] - 2020-10-08

- Add config options to Ktor HTTP client
- Convert `withHttp{}` to return value
- Upgrade to Kotlin 1.4.10
- Add non-nullable version of Redis pool requests
- Make stacktrace printing optional in Redis pools
- Add `exposed-utils` module

## [1.3.0] - 2020-08-28

- Add script pools
- Add properties to Redis pools
- Default salt value on digests
- Update to Kotlin 1.4.0
- Update to Ktor 1.4.0
- Add isolation to `JavaScriptEngine`
- Make scripts resettable
- Add support for multiple URLs with Redis pools
- Add timeout support to Ktor client

## [1.2.0] - 2020-08-14

- Upgrade to Kotlin 1.4.0-rc

## [1.1.20] - 2020-08-12

- Add `isNotNull()` and `isNull()`
- Add `String.isNotQuoted()`
- Add `String.isNotSingleQuoted()` and `String.isNotDoubleQuoted()`
- Add `String.isNotBracketed()`
- Add `String.isInt()` and `String.isDouble()`
- Add `OwnerType` to `GitHubRepo`
- Add Histogram support to `prometheus-utils`

## [1.1.19] - 2020-07-02

- Fix jar creation

## [1.1.18] - 2020-07-01

- Dependency updates

## [1.1.17] - 2020-05-31

- Add `GitHubRepo`
- Add `HerokuHttpsRedirect.kt`
- Add SHA256 encoding
- Add `isNotValidEmail()`
- Add `RedisUtils.kt`

## [1.1.16] - 2020-05-04

- Add support for Java and Python script processing

## [1.1.15] - 2020-03-07

- Upgrade Ktor
- Dependency updates

## [1.1.14] - 2019-12-19

- Update gRPC jar

## [1.1.13] - 2019-12-15

- Fix `unzip` dropping newlines

## [1.1.12] - 2019-12-15

- Add `script-utils` module
- Add delegate `atomicInteger()`
- Add `String.zip()` and `ByteArray.unzip()` extensions
- Add gRPC Server extensions

## [1.1.11] - 2019-12-04

- Add hostname to log message

## [1.1.10] - 2019-12-03

- Add times extension
- Add gRPC TLS support

## [1.1.9] - 2019-11-30

- Dependency updates
- Cleanup `build.gradle` files

## [1.1.8] - 2019-11-22

- Convert `genericServiceListener()` to `Service.genericServiceListener()`

## [1.1.7] - 2019-11-20

- Add ability for admin servlet customization

## [1.1.6] - 2019-11-17

- Add `MetricRegistry` to `GenericService`
- Rename `KtorUtils` to `KtorExtensions`

## [1.1.5] - 2019-11-17

- Add service classes

## [1.1.4] - 2019-11-15

- Minor updates

## [1.1.3] - 2019-11-15

- Update Kotlin jar

## [1.1.2] - 2019-11-14

- Version update

## [1.1.1] - 2019-11-14

- Minor updates

## [1.1.0] - 2019-11-13

- Break up jars by dependencies (multi-module restructure)

## [1.0.6] - 2019-11-13

- Convert `Int.length` into a property
- Cleanup lambda invocations

## [1.0.5] - 2019-11-11

- Add `Int.length` and `Long.length`

## [1.0.4] - 2019-11-10

- Minor updates

## [1.0.3] - 2019-11-06

- Update Guava jar to 28.1

## [1.0.2] - 2019-11-03

- Replace Guava with Kotlin objects
- Add `HostInfo` class for returning host information
- Add duration tests

## [1.0.1] - 2019-10-26

- Clean up extensions
- Add host extensions

## [1.0.0] - 2019-10-21

- Initial release
- Add `UndocumentedPublicClass` suppress directives
- Fix sleep overflow problem
- Add `@JvmStatic`
- Add `CountDownLatch.countDown {}`
