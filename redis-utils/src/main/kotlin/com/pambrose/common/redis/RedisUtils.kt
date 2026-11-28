/*
 *   Copyright © 2026 Paul Ambrose (pambrose@mac.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.pambrose.common.redis

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import kotlin.concurrent.atomics.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol.DEFAULT_DATABASE
import redis.clients.jedis.Protocol.DEFAULT_PORT
import redis.clients.jedis.Protocol.DEFAULT_TIMEOUT
import redis.clients.jedis.RedisClient
import redis.clients.jedis.RedisProtocol
import redis.clients.jedis.SslOptions
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.exceptions.JedisException
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.util.JedisURIHelper

/**
 * Factory methods and extension functions for creating and using Redis connections via Jedis.
 *
 * Pool sizing and wait time can be configured via system properties or method parameters.
 * The default Redis URL is read from the `REDIS_URL` environment variable.
 *
 * Only the `redis` and `rediss` (TLS) schemes are accepted, in any case. Any other scheme, including TLS spellings
 * such as `tls` or `redis+tls`, is rejected rather than connecting without TLS.
 *
 * A URL without a port uses 6379. A URL that can never work (one that is malformed, has no host, has another scheme
 * or a port that is not a number from 1 to 65535, has a path other than a database index such as `/3`, or carries an
 * unknown `protocol`) is a configuration error: every function that takes a URL throws [IllegalArgumentException]
 * for it, rather than treating it as a connection failure. No such message repeats any part of the URL, which may
 * hold a password.
 *
 * Percent-escapes in the user name and password are decoded after the user info is split at its first `:`, so an
 * escaped `%3A` stays in the user name, and a literal `+` stays a `+`. A `/`, `?`, `#` or `@` in either must be
 * percent-encoded (`%2F`, `%3F`, `%23`, `%40`): unescaped, an `@` ends the user info too early, and each of the
 * others ends the authority before the host.
 */
object RedisUtils {
  private val logger = KotlinLogging.logger {}

  /** System property key for the maximum connection pool size. */
  const val REDIS_MAX_POOL_SIZE = "redis.maxPoolSize"

  /** System property key for the maximum number of idle connections. */
  const val REDIS_MAX_IDLE_SIZE = "redis.maxIdleSize"

  /** System property key for the minimum number of idle connections. */
  const val REDIS_MIN_IDLE_SIZE = "redis.minIdleSize"

  /** System property key for the maximum wait time (in seconds) when borrowing a connection. */
  const val REDIS_MAX_WAIT_SECS = "redis.maxWaitSecs"

  /** Pool size meaning "no limit", as defined by commons-pool2. */
  const val UNLIMITED_POOL_SIZE = -1

  // The placeholder used in the default URL for "no credentials", which must not be sent as a real password.
  private const val PLACEHOLDER_PASSWORD = "none"

  private const val FAILED_TO_CONNECT_MSG = "Failed to connect to redis"

  private const val INVALID_PORT_MSG = "Redis URL port must be a number from 1 to 65535"

  private const val INVALID_DATABASE_MSG =
    "Redis URL path must be empty or a database index, such as /3; a '/' in the password must be percent-encoded as %2F"

  // RedisProtocol's two versions, RESP2 and RESP3
  private const val INVALID_PROTOCOL_MSG = "Redis URL protocol must be 2 or 3"

  private val validPorts = 1..65535

  // The exception's message says why (refused, timed out, bad credentials, pool exhausted) and holds no secrets, so
  // it is logged even without the stack trace.
  private fun logConnectionFailure(
    e: JedisException,
    printStackTrace: Boolean,
  ) {
    if (printStackTrace)
      logger.error(e) { "$FAILED_TO_CONNECT_MSG: ${e.message}" }
    else
      logger.error { "$FAILED_TO_CONNECT_MSG: ${e::class.simpleName}: ${e.message}" }
  }

  private val defaultRedisUrl = System.getenv("REDIS_URL") ?: "redis://user:none@localhost:6379"

  /**
   * Parsed Redis connection information extracted from a Redis URL.
   *
   * @property uri the parsed [URI]
   * @property user the username extracted from the URL's userinfo
   * @property password the password extracted from the URL's userinfo
   * @property database the database index from the URL's path, or 0 when it has none
   * @property protocol the `protocol` from the URL's query, or `null` to leave the choice to Jedis
   */
  internal class RedisInfo(
    val uri: URI,
    val user: String,
    val password: String,
    val database: Int = DEFAULT_DATABASE,
    val protocol: RedisProtocol? = null,
  ) {
    // The single rule for what counts as a real password: blank and the "none" placeholder are neither sent
    // as a password nor treated as credentials to attach a username to.
    internal val hasRealPassword get() = password.isNotBlank() && password != PLACEHOLDER_PASSWORD

    /** Returns `true` if the user should be included in AUTH commands (i.e., is a real user, not a placeholder). */
    val includeUserInAuth
      get() = user.isNotBlank() && user != "default" && user != "user" && hasRealPassword
  }

  // A URL that can never work is rejected here, rather than becoming a client whose every connection fails. No
  // message repeats any part of the URL, not even the port: an unescaped '/' in a password ends the authority, so
  // what java.net.URI reports as the host or port can be the start of the password.
  private fun urlDetails(redisUrl: String): RedisInfo {
    val uri =
      try {
        URI(redisUrl)
      } catch (e: URISyntaxException) {
        // The URL, and therefore e's message, can hold a password, so neither is passed on.
        throw IllegalArgumentException("Malformed Redis URL: ${e.reason} at index ${e.index}")
      }
    // Without a host, as in localhost:6379 (read as scheme "localhost"), Jedis would silently connect to loopback.
    require(!uri.host.isNullOrBlank()) { hostlessUrlMessage(uri) }
    // clientConfig() enables TLS only for rediss, so any other scheme, such as tls, redis+tls or a misspelled
    // rediss, would send the credentials in plaintext.
    require(JedisURIHelper.isRedisScheme(uri) || JedisURIHelper.isRedisSSLScheme(uri)) {
      "Redis URL scheme must be redis or rediss"
    }
    // URI accepts any port that fits in an Int, and Jedis would fail only on connecting. -1 means no port.
    require(uri.port == -1 || uri.port in validPorts) { INVALID_PORT_MSG }
    // Split before decoding, as JedisURIHelper does: userInfo is already decoded, so an escaped ':' (%3A) in the
    // user name would split it.
    val userInfo = uri.rawUserInfo?.split(":", limit = 2).orEmpty().map(::decodeUserInfo)
    return RedisInfo(
      uri = uri,
      user = userInfo.getOrElse(0) { "" },
      password = userInfo.getOrElse(1) { "" },
      database = uri.databaseIndex(),
      protocol = uri.redisProtocol(),
    )
  }

  // The path is empty, "/" or "/" and the index. JedisURIHelper.getDBIndex would parse it with Integer.parseInt, whose
  // message quotes it, and an unescaped '/' in a password puts the rest of the password, and the host, in the path.
  // Only ASCII digits are accepted: parseInt also takes a sign and other scripts' digits.
  private fun URI.databaseIndex(): Int {
    val index = path.orEmpty().removePrefix("/").ifEmpty { return DEFAULT_DATABASE }
    return requireNotNull(index.takeIf { it.isAsciiDigits() }?.toIntOrNull()) { INVALID_DATABASE_MSG }
  }

  // JedisURIHelper reads the query as it always has, but its message for an unknown protocol quotes the value, which
  // an unescaped '?' in a password can supply, so it is replaced.
  private fun URI.redisProtocol(): RedisProtocol? =
    try {
      JedisURIHelper.getRedisProtocol(this)
    } catch (_: IllegalArgumentException) {
      throw IllegalArgumentException(INVALID_PROTOCOL_MSG)
    }

  // java.net.URI reports no host when there is none, and also when it cannot parse the authority as host[:port]:
  // for a name such as redis_cache (an underscore is legal in a Docker Compose service name but not in a URI host),
  // and for a port that is not a number or does not fit in an Int. Each gets its own message.
  private fun hostlessUrlMessage(uri: URI): String {
    // The authority's host and port: after any userinfo. The host is what precedes the last ':'.
    val hostAndPort = uri.rawAuthority.orEmpty().substringAfterLast('@')
    val port = hostAndPort.substringAfterLast(':', missingDelimiterValue = "")
    return when {
      hostAndPort.substringBeforeLast(':').isBlank() -> "Redis URL has no host"
      port.isNotEmpty() && !port.isValidPort() -> INVALID_PORT_MSG
      else -> "Redis URL host could not be parsed; host names may contain only letters, digits, '-' and '.'"
    }
  }

  // URI accepts only ASCII digits in a port, whereas toIntOrNull() also accepts a sign.
  private fun String.isValidPort() = isAsciiDigits() && toIntOrNull() in validPorts

  private fun String.isAsciiDigits() = all { it in '0'..'9' }

  // Decodes as JedisURIHelper does: a URI keeps a literal '+', which URLDecoder alone would turn into a space. The
  // URI constructor has already rejected a malformed escape, so this cannot throw.
  private fun decodeUserInfo(raw: String): String = URLDecoder.decode(raw.replace("+", "%2B"), UTF_8)

  // URI reports a missing port as -1, which Jedis would try to connect to.
  private val URI.hostAndPort: HostAndPort get() = HostAndPort(host, port.takeUnless { it == -1 } ?: DEFAULT_PORT)

  /** The host and port a client built from [redisUrl] connects to; the port defaults to 6379. */
  internal fun hostAndPort(redisUrl: String): HostAndPort = urlDetails(redisUrl).uri.hostAndPort

  // Compared case-insensitively: lowercase() with a Turkish default locale maps REDISS to redıss.
  private val URI.isSslScheme: Boolean get() = scheme.equals("rediss", ignoreCase = true)

  /**
   * Builds the Jedis client config for [redisUrl].
   *
   * Reads the credentials, TLS scheme, database index (`redis://host:port/3`) and protocol
   * (`?protocol=3`) from the URL, and applies Jedis' default connection and socket timeouts. The
   * placeholder user names `default` and `user` are not sent, and the placeholder password `none` is treated
   * as no password.
   */
  internal fun clientConfig(redisUrl: String): DefaultJedisClientConfig = clientConfig(urlDetails(redisUrl))

  // Jedis' own URI-aware builders are deliberately not used here: DefaultJedisClientConfig.builder(URI) and the
  // deprecated StandaloneClientBuilder.fromURI(URI) both throw IllegalArgumentException for a URL that carries a
  // user but no password, such as redis://username@localhost:6379, which this library accepts. urlDetails() has
  // already read and checked every part of the URL used here, so building the config cannot reject it.
  private fun clientConfig(info: RedisInfo): DefaultJedisClientConfig {
    val builder =
      DefaultJedisClientConfig.builder()
        .connectionTimeoutMillis(DEFAULT_TIMEOUT)
        .socketTimeoutMillis(DEFAULT_TIMEOUT)

    // sslOptions is what enables TLS in jedis 8, and takes precedence over the deprecated ssl(true) flag.
    if (info.uri.isSslScheme)
      builder.sslOptions(SslOptions.defaults())

    if (info.hasRealPassword) {
      if (info.includeUserInAuth)
        builder.user(info.user)
      builder.password(info.password)
    }

    builder.database(info.database)
    info.protocol?.let { builder.protocol(it) }

    return builder.build()
  }

  private fun createRedisClient(redisUrl: String): RedisClient {
    val info = urlDetails(redisUrl)
    return RedisClient.builder()
      .hostAndPort(info.uri.hostAndPort)
      .clientConfig(clientConfig(info))
      .build()
  }

  // Building a client does not report an unreachable server (jedis 8 probes a connection while building, but
  // ignores the failure), so ping() is what actually proves the connection works. Building throws only for an
  // invalid URL, which is a configuration error rather than a connection failure, so it is left to propagate.
  // A client that cannot be used is closed here rather than handed to the caller.
  private fun connectOrNull(
    redisUrl: String,
    printStackTrace: Boolean,
  ): RedisClient? {
    val client = createRedisClient(redisUrl)
    return try {
      client.ping()
      client
    } catch (e: JedisException) {
      runCatching { client.close() }.exceptionOrNull()?.let(e::addSuppressed)
      logConnectionFailure(e, printStackTrace)
      null
    }
  }

  // Whether the server answers. Any Jedis failure counts as a connection failure, including pool exhaustion,
  // borrow validation failures and rejected credentials.
  private fun RedisClient.pingSucceeds(printStackTrace: Boolean): Boolean =
    try {
      ping()
      true
    } catch (e: JedisException) {
      logConnectionFailure(e, printStackTrace)
      false
    }

  /**
   * Creates a new pooled [RedisClient] with the given configuration.
   *
   * Each pool parameter defaults to its system property (see [REDIS_MAX_POOL_SIZE], etc.) if set,
   * otherwise to the concrete fallback noted below. The connection and socket timeouts both use Jedis'
   * `DEFAULT_TIMEOUT`.
   *
   * Connections are validated when they are borrowed and while they sit idle, but not when they are
   * returned, which would add a second round-trip to every command.
   *
   * @param redisUrl the Redis connection URL (defaults to the `REDIS_URL` environment variable)
   * @param maxPoolSize maximum connections in the pool, or [UNLIMITED_POOL_SIZE] for no limit; defaults to the
   *   [REDIS_MAX_POOL_SIZE] property or 10
   * @param maxIdleSize maximum idle connections, or [UNLIMITED_POOL_SIZE] for no limit; defaults to the
   *   [REDIS_MAX_IDLE_SIZE] property or 5
   * @param minIdleSize minimum idle connections; defaults to the [REDIS_MIN_IDLE_SIZE] property or 1. A value above
   *   [maxIdleSize] is lowered to it (commons-pool2 does so), and a warning is logged
   * @param maxWaitSecs seconds to wait when borrowing a connection; defaults to the [REDIS_MAX_WAIT_SECS] property or 1
   * @return a configured [RedisClient] with connection pooling
   * @throws IllegalArgumentException if a pool setting is negative (other than [UNLIMITED_POOL_SIZE] for
   *   [maxPoolSize] and [maxIdleSize]), if [maxPoolSize] is 0, which would
   *   create a pool that can never lend a connection, or if [redisUrl] is invalid (see [RedisUtils])
   */
  fun newRedisClient(
    redisUrl: String = defaultRedisUrl,
    maxPoolSize: Int = System.getProperty(REDIS_MAX_POOL_SIZE)?.toInt() ?: 10,
    maxIdleSize: Int = System.getProperty(REDIS_MAX_IDLE_SIZE)?.toInt() ?: 5,
    minIdleSize: Int = System.getProperty(REDIS_MIN_IDLE_SIZE)?.toInt() ?: 1,
    maxWaitSecs: Long = System.getProperty(REDIS_MAX_WAIT_SECS)?.toLong() ?: 1L,
  ): RedisClient {
    require(maxPoolSize > 0 || maxPoolSize == UNLIMITED_POOL_SIZE) {
      "Max pool size must be positive, or $UNLIMITED_POOL_SIZE for unlimited, but was $maxPoolSize"
    }
    require(maxIdleSize >= 0 || maxIdleSize == UNLIMITED_POOL_SIZE) {
      "Max idle size cannot be negative, other than $UNLIMITED_POOL_SIZE for unlimited, but was $maxIdleSize"
    }
    require(minIdleSize >= 0) { "Min idle size cannot be a negative number" }
    require(maxWaitSecs >= 0) { "Max wait secs cannot be a negative number" }
    if (maxIdleSize != UNLIMITED_POOL_SIZE && minIdleSize > maxIdleSize)
      logger.warn { "Redis min idle size $minIdleSize exceeds max idle size $maxIdleSize; using $maxIdleSize" }

    logger.info { "Redis max pool size: $maxPoolSize" }
    logger.info { "Redis max idle size: $maxIdleSize" }
    logger.info { "Redis min idle size: $minIdleSize" }
    logger.info { "Redis max wait secs: $maxWaitSecs" }

    val poolConfig =
      ConnectionPoolConfig()
        .apply {
          maxTotal = maxPoolSize
          maxIdle = maxIdleSize
          minIdle = minIdleSize
          setMaxWait(Duration.ofSeconds(maxWaitSecs))
          testOnBorrow = true
          testWhileIdle = true

          timeBetweenEvictionRuns = Duration.ofMinutes(1)
          minEvictableIdleDuration = Duration.ofMinutes(1)
        }

    val info = urlDetails(redisUrl)

    return RedisClient.builder()
      .hostAndPort(info.uri.hostAndPort)
      .clientConfig(clientConfig(info))
      .poolConfig(poolConfig)
      .build()
  }

  /**
   * Executes [block] with this [RedisClient], passing `null` if the connection fails.
   *
   * Extension function on [RedisClient]. Pings the server first to verify connectivity. A pool that cannot
   * lend a connection, and an authentication failure, both count as a connection failure.
   *
   * @param T the return type of the block
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the operation to execute; receives `null` on connection failure
   * @return the result of [block]
   */
  fun <T> RedisClient.withRedisPool(
    printStackTrace: Boolean = false,
    block: (RedisClient?) -> T,
  ): T = block.invoke(if (pingSucceeds(printStackTrace)) this else null)

  /**
   * Executes [block] with this [RedisClient], returning `null` if the connection fails.
   *
   * Extension function on [RedisClient]. Unlike [withRedisPool], the block receives a non-null client,
   * and the entire function returns `null` on connection failure.
   *
   * @param T the return type of the block
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the operation to execute with a guaranteed non-null client
   * @return the result of [block], or `null` on connection failure
   */
  fun <T> RedisClient.withNonNullRedisPool(
    printStackTrace: Boolean = false,
    block: (RedisClient) -> T,
  ): T? = if (pingSucceeds(printStackTrace)) block.invoke(this) else null

  /**
   * Suspending variant of [withRedisPool]. Executes a suspending [block] with this [RedisClient],
   * passing `null` if the connection fails.
   *
   * The connectivity ping runs on [Dispatchers.IO]. [block] runs in the caller's context, so Jedis calls made in
   * it still block their thread; wrap them in `withContext(Dispatchers.IO)` when calling from a limited dispatcher.
   *
   * Extension function on [RedisClient].
   *
   * @param T the return type of the block
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the suspending operation to execute; receives `null` on connection failure
   * @return the result of [block]
   */
  suspend fun <T> RedisClient.withSuspendingRedisPool(
    printStackTrace: Boolean = false,
    block: suspend (RedisClient?) -> T,
  ): T = block.invoke(if (pingSucceedsOnIo(printStackTrace)) this else null)

  /**
   * Suspending variant of [withNonNullRedisPool]. Executes a suspending [block] with this [RedisClient],
   * returning `null` if the connection fails.
   *
   * The connectivity ping runs on [Dispatchers.IO]; [block] runs in the caller's context, as in
   * [withSuspendingRedisPool].
   *
   * Extension function on [RedisClient].
   *
   * @param T the return type of the block
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the suspending operation to execute with a guaranteed non-null client
   * @return the result of [block], or `null` on connection failure
   */
  suspend fun <T> RedisClient.withSuspendingNonNullRedisPool(
    printStackTrace: Boolean = false,
    block: suspend (RedisClient) -> T,
  ): T? = if (pingSucceedsOnIo(printStackTrace)) block.invoke(this) else null

  /**
   * Creates a short-lived [RedisClient] connection, executes [block], and closes the client.
   *
   * The connection is verified with a ping before [block] runs, so an unreachable server, a pool that cannot
   * lend a connection, or an authentication failure all pass `null` to [block] instead of failing on the
   * first command.
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the operation to execute; receives `null` on connection failure
   * @return the result of [block]
   * @throws IllegalArgumentException if [redisUrl] is invalid (see [RedisUtils]); [block] is not run
   */
  fun <T> withRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: (RedisClient?) -> T,
  ): T {
    val client = connectOrNull(redisUrl, printStackTrace) ?: return block.invoke(null)
    return client.use { block.invoke(it) }
  }

  /**
   * Creates a short-lived [RedisClient] connection, executes [block] with a non-null client, and closes it.
   *
   * Returns `null` if the connection fails, verified with a ping as in [withRedis].
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the operation to execute with a guaranteed non-null client
   * @return the result of [block], or `null` on connection failure
   * @throws IllegalArgumentException if [redisUrl] is invalid (see [RedisUtils]); [block] is not run
   */
  fun <T> withNonNullRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: (RedisClient) -> T,
  ): T? {
    val client = connectOrNull(redisUrl, printStackTrace) ?: return null
    return client.use { block.invoke(it) }
  }

  /**
   * Suspending variant of [withRedis]. Creates a short-lived connection, executes a suspending [block], and closes it.
   *
   * Passes `null` to [block] if the connection fails. Connecting, pinging and closing run on [Dispatchers.IO];
   * [block] runs in the caller's context, so Jedis calls made in it still block their thread. A caller cancelled
   * while connecting waits for the attempt to finish, which cancellation cannot interrupt, and the client is closed
   * without running [block].
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the suspending operation to execute; receives `null` on connection failure
   * @return the result of [block]
   * @throws IllegalArgumentException if [redisUrl] is invalid (see [RedisUtils]); [block] is not run
   */
  suspend fun <T> withSuspendingRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: suspend (RedisClient?) -> T,
  ): T {
    val client = connectOrNullOnIo(redisUrl, printStackTrace) ?: return block.invoke(null)
    return client.useOnIo { block.invoke(it) }
  }

  /**
   * Suspending variant of [withNonNullRedis]. Creates a short-lived connection, executes a suspending [block],
   * and closes it. Returns `null` if the connection fails. Connecting, pinging and closing run on
   * [Dispatchers.IO]; [block] runs in the caller's context. Cancellation while connecting closes the client without
   * running [block], as in [withSuspendingRedis].
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the suspending operation to execute with a guaranteed non-null client
   * @return the result of [block], or `null` on connection failure
   * @throws IllegalArgumentException if [redisUrl] is invalid (see [RedisUtils]); [block] is not run
   */
  suspend fun <T> withSuspendingNonNullRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: suspend (RedisClient) -> T,
  ): T? {
    val client = connectOrNullOnIo(redisUrl, printStackTrace) ?: return null
    return client.useOnIo { block.invoke(it) }
  }

  // The suspending helpers connect, ping and close on Dispatchers.IO: each is a blocking Jedis call that can take the
  // full 2 s connect or socket timeout when Redis is down, which would otherwise stall the caller's dispatcher thread.
  @Suppress("InjectDispatcher")
  private val ioDispatcher = Dispatchers.IO

  private suspend fun RedisClient.pingSucceedsOnIo(printStackTrace: Boolean) =
    withContext(ioDispatcher) { pingSucceeds(printStackTrace) }

  // Cancellation cannot interrupt connectOrNull, whose ping is blocking socket I/O, so a caller cancelled meanwhile
  // still gets a connected client back on the IO thread. withContext then throws instead of returning it, so the
  // client is recorded as soon as it exists and closed here, rather than leaking its pool and socket. Once this
  // returns, the caller hands the client to useOnIo with no suspension point in between.
  private suspend fun connectOrNullOnIo(
    redisUrl: String,
    printStackTrace: Boolean,
  ): RedisClient? {
    val connected = AtomicReference<RedisClient?>(null)
    try {
      return withContext(ioDispatcher) { connectOrNull(redisUrl, printStackTrace).also { connected.store(it) } }
    } catch (e: Throwable) {
      connected.load()?.closeOnIo()?.let(e::addSuppressed)
      throw e
    }
  }

  // Closes on the IO dispatcher, under NonCancellable so that a cancelled caller still closes the client, and returns
  // what closing threw, if anything. The two are nested because a withContext that changes the dispatcher throws a
  // CancellationException on returning to a cancelled caller, even under NonCancellable, which would discard both
  // the close failure and the exception the caller is about to rethrow; one that keeps the dispatcher does not.
  private suspend fun RedisClient.closeOnIo(): Throwable? =
    withContext(NonCancellable) { withContext(ioDispatcher) { runCatching { close() }.exceptionOrNull() } }

  // Like use(), but closes on the IO dispatcher, and under NonCancellable so a cancelled block still closes it.
  private suspend fun <T> RedisClient.useOnIo(block: suspend (RedisClient) -> T): T {
    val result = runCatching { block(this) }
    val closeFailure = closeOnIo()
    result.exceptionOrNull()?.let { failure ->
      closeFailure?.let(failure::addSuppressed)
      throw failure
    }
    closeFailure?.let { throw it }
    return result.getOrThrow()
  }

  /**
   * Lazily scans Redis keys matching the given [pattern] using the SCAN command.
   *
   * Extension function on [UnifiedJedis]. Returns a [Sequence] that iterates through all matching keys
   * without loading them all into memory at once.
   *
   * This covers a standalone server ([RedisClient]). A cluster client (`RedisClusterClient`) sends SCAN to a
   * single node: Jedis rejects a [pattern] without a `{hash-tag}` with [IllegalArgumentException], and with one it
   * returns only the keys in that tag's slot. To scan every node of a cluster, use Jedis' own `scanIteration`.
   *
   * @param pattern the glob-style pattern to match keys against (e.g., `"user:*"`)
   * @param count a hint to Redis for how many keys to return per SCAN iteration
   * @return a [Sequence] of matching key names
   */
  fun UnifiedJedis.scanKeys(
    pattern: String,
    count: Int = 100,
  ): Sequence<String> =
    sequence {
      val scanParams = ScanParams().match(pattern).count(count)
      var cursorVal = ScanParams.SCAN_POINTER_START
      while (true) {
        val res = scan(cursorVal, scanParams)
        res.result.forEach { yield(it) }
        if (res.isCompleteIteration)
          break
        cursorVal = res.cursor
      }
    }
}
