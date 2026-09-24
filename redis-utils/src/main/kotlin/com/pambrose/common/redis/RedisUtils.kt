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
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol.DEFAULT_PORT
import redis.clients.jedis.Protocol.DEFAULT_TIMEOUT
import redis.clients.jedis.RedisClient
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
 * A URL without a port uses 6379. A URL that can never work (one that is malformed, has no host, or carries a
 * non-numeric database index or an unknown `protocol`) is a configuration error: every function that takes a URL
 * throws [IllegalArgumentException] for it, rather than treating it as a connection failure.
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
   */
  class RedisInfo(
    val uri: URI,
    val user: String,
    val password: String,
  ) {
    // The single rule for what counts as a real password: blank and the "none" placeholder are neither sent
    // as a password nor treated as credentials to attach a username to.
    internal val hasRealPassword get() = password.isNotBlank() && password != PLACEHOLDER_PASSWORD

    /** Returns `true` if the user should be included in AUTH commands (i.e., is a real user, not a placeholder). */
    val includeUserInAuth
      get() = user.isNotBlank() && user != "default" && user != "user" && hasRealPassword
  }

  // A URL that can never work is rejected here, rather than becoming a client whose every connection fails.
  private fun urlDetails(redisUrl: String): RedisInfo {
    val uri =
      try {
        URI(redisUrl)
      } catch (e: URISyntaxException) {
        // The URL, and therefore e's message, can hold a password, so neither is passed on.
        throw IllegalArgumentException("Malformed Redis URL: ${e.reason} at index ${e.index}")
      }
    // Without a host, as in localhost:6379 (read as scheme "localhost"), Jedis would silently connect to loopback.
    // java.net.URI also reports no host for a name it cannot parse as one, such as redis_cache (an underscore is
    // legal in a Docker Compose service name but not in a URI host), which gets its own message.
    require(!uri.host.isNullOrBlank()) {
      // The authority's host part: after any userinfo, before any port.
      if (uri.rawAuthority.orEmpty().substringAfterLast('@').substringBeforeLast(':').isBlank())
        "Redis URL has no host"
      else
        "Redis URL host could not be parsed; host names may contain only letters, digits, '-' and '.'"
    }
    val userInfo = uri.userInfo?.split(":", limit = 2).orEmpty()
    return RedisInfo(uri, userInfo.getOrElse(0) { "" }, userInfo.getOrElse(1) { "" })
  }

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
  // user but no password, such as redis://username@localhost:6379, which this library accepts. Only the parts
  // with no such trap (database index, protocol) are delegated to JedisURIHelper.
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

    if (JedisURIHelper.hasDbIndex(info.uri))
      builder.database(JedisURIHelper.getDBIndex(info.uri))

    JedisURIHelper.getRedisProtocol(info.uri)?.let { builder.protocol(it) }

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
   * [block] runs in the caller's context, so Jedis calls made in it still block their thread.
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
   * [Dispatchers.IO]; [block] runs in the caller's context.
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
  private suspend fun RedisClient.pingSucceedsOnIo(printStackTrace: Boolean) =
    withContext(Dispatchers.IO) { pingSucceeds(printStackTrace) }

  private suspend fun connectOrNullOnIo(
    redisUrl: String,
    printStackTrace: Boolean,
  ) = withContext(Dispatchers.IO) { connectOrNull(redisUrl, printStackTrace) }

  private suspend fun <T> RedisClient.useOnIo(block: suspend (RedisClient) -> T): T {
    var failure: Throwable? = null
    try {
      return block(this)
    } catch (e: Throwable) {
      failure = e
      throw e
    } finally {
      // NonCancellable, so the client is closed even when the block was cancelled.
      withContext(NonCancellable + Dispatchers.IO) {
        runCatching { close() }.exceptionOrNull()?.let { closeFailure ->
          failure?.addSuppressed(closeFailure) ?: throw closeFailure
        }
      }
    }
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
