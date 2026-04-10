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
import java.time.Duration
import java.util.*
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol.DEFAULT_TIMEOUT
import redis.clients.jedis.RedisClient
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.exceptions.JedisConnectionException
import redis.clients.jedis.params.ScanParams

/**
 * Factory methods and extension functions for creating and using Redis connections via Jedis.
 *
 * Pool sizing and wait time can be configured via system properties or method parameters.
 * The default Redis URL is read from the `REDIS_URL` environment variable.
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

  private const val FAILED_TO_CONNECT_MSG = "Failed to connect to redis"

  private val colon = Regex(":")
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
    /** Returns `true` if the user should be included in AUTH commands (i.e., is a real user, not a placeholder). */
    val includeUserInAuth
      get() = user.isNotBlank() && user != "default" && user != "user" && password != "none"
  }

  private fun urlDetails(redisUrl: String) =
    URI(redisUrl).let {
      RedisInfo(
        it,
        (it.userInfo?.split(colon, 2)?.getOrElse(0) { "" }.orEmpty()),
        (it.userInfo?.split(colon, 2)?.getOrElse(1) { "" }.orEmpty()),
      )
    }

  private val String.isSsl: Boolean get() = lowercase(Locale.getDefault()).startsWith("rediss://")

  private fun buildClientConfig(
    info: RedisInfo,
    isSsl: Boolean,
  ): DefaultJedisClientConfig {
    val builder =
      DefaultJedisClientConfig.builder()
        .connectionTimeoutMillis(DEFAULT_TIMEOUT)
        .socketTimeoutMillis(DEFAULT_TIMEOUT)
        .ssl(isSsl)

    if (info.password.isNotBlank()) {
      if (info.includeUserInAuth)
        builder.user(info.user)
      builder.password(info.password)
    }

    return builder.build()
  }

  private fun createRedisClient(redisUrl: String): RedisClient {
    val info = urlDetails(redisUrl)
    val clientConfig = buildClientConfig(info, redisUrl.isSsl)
    return RedisClient.builder()
      .hostAndPort(HostAndPort(info.uri.host, info.uri.port))
      .clientConfig(clientConfig)
      .build()
  }

  /**
   * Creates a new pooled [RedisClient] with the given configuration.
   *
   * Pool sizes default to values from system properties (see [REDIS_MAX_POOL_SIZE], etc.)
   * or sensible defaults if those properties are not set.
   *
   * @param redisUrl the Redis connection URL (defaults to the `REDIS_URL` environment variable)
   * @param maxPoolSize maximum number of connections in the pool
   * @param maxIdleSize maximum number of idle connections
   * @param minIdleSize minimum number of idle connections
   * @param maxWaitSecs maximum seconds to wait when borrowing a connection
   * @return a configured [RedisClient] with connection pooling
   */
  fun newRedisClient(
    redisUrl: String = defaultRedisUrl,
    maxPoolSize: Int = System.getProperty(REDIS_MAX_POOL_SIZE)?.toInt() ?: 10,
    maxIdleSize: Int = System.getProperty(REDIS_MAX_IDLE_SIZE)?.toInt() ?: 5,
    minIdleSize: Int = System.getProperty(REDIS_MIN_IDLE_SIZE)?.toInt() ?: 1,
    maxWaitSecs: Long = System.getProperty(REDIS_MAX_WAIT_SECS)?.toLong() ?: 1L,
  ): RedisClient {
    require(maxPoolSize >= 0) { "Max pool size cannot be a negative number" }
    require(maxIdleSize >= 0) { "Max idle size cannot be a negative number" }
    require(minIdleSize >= 0) { "Min idle size cannot be a negative number" }
    require(maxWaitSecs >= 0) { "Max wait secs cannot be a negative number" }

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
          testOnReturn = true
          testWhileIdle = true

          timeBetweenEvictionRuns = Duration.ofMinutes(1)
          minEvictableIdleDuration = Duration.ofMinutes(1)
        }

    val info = urlDetails(redisUrl)
    val clientConfig = buildClientConfig(info, redisUrl.isSsl)

    return RedisClient.builder()
      .hostAndPort(HostAndPort(info.uri.host, info.uri.port))
      .clientConfig(clientConfig)
      .poolConfig(poolConfig)
      .build()
  }

  /**
   * Executes [block] with this [RedisClient], passing `null` if the connection fails.
   *
   * Extension function on [RedisClient]. Pings the server first to verify connectivity.
   *
   * @param T the return type of the block
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the operation to execute; receives `null` on connection failure
   * @return the result of [block]
   */
  fun <T> RedisClient.withRedisPool(
    printStackTrace: Boolean = false,
    block: (RedisClient?) -> T,
  ): T =
    try {
      ping()
      block.invoke(this)
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      block.invoke(null)
    }

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
  ): T? =
    try {
      ping()
      block.invoke(this)
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      null
    }

  /**
   * Suspending variant of [withRedisPool]. Executes a suspending [block] with this [RedisClient],
   * passing `null` if the connection fails.
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
  ): T =
    try {
      ping()
      block.invoke(this)
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      block.invoke(null)
    }

  /**
   * Suspending variant of [withNonNullRedisPool]. Executes a suspending [block] with this [RedisClient],
   * returning `null` if the connection fails.
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
  ): T? =
    try {
      ping()
      block.invoke(this)
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      null
    }

  /**
   * Creates a short-lived [RedisClient] connection, executes [block], and closes the client.
   *
   * Passes `null` to [block] if the connection fails.
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the operation to execute; receives `null` on connection failure
   * @return the result of [block]
   */
  fun <T> withRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: (RedisClient?) -> T,
  ): T =
    try {
      createRedisClient(redisUrl)
        .use { client ->
          block.invoke(client)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      block.invoke(null)
    }

  /**
   * Creates a short-lived [RedisClient] connection, executes [block] with a non-null client, and closes it.
   *
   * Returns `null` if the connection fails.
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the operation to execute with a guaranteed non-null client
   * @return the result of [block], or `null` on connection failure
   */
  fun <T> withNonNullRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: (RedisClient) -> T,
  ): T? =
    try {
      createRedisClient(redisUrl)
        .use { client ->
          block.invoke(client)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      null
    }

  /**
   * Suspending variant of [withRedis]. Creates a short-lived connection, executes a suspending [block], and closes it.
   *
   * Passes `null` to [block] if the connection fails.
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the suspending operation to execute; receives `null` on connection failure
   * @return the result of [block]
   */
  suspend fun <T> withSuspendingRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: suspend (RedisClient?) -> T,
  ): T =
    try {
      createRedisClient(redisUrl)
        .use { client ->
          block.invoke(client)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      block.invoke(null)
    }

  /**
   * Suspending variant of [withNonNullRedis]. Creates a short-lived connection, executes a suspending [block],
   * and closes it. Returns `null` if the connection fails.
   *
   * @param T the return type of the block
   * @param redisUrl the Redis connection URL
   * @param printStackTrace if `true`, logs the full stack trace on connection failure
   * @param block the suspending operation to execute with a guaranteed non-null client
   * @return the result of [block], or `null` on connection failure
   */
  suspend fun <T> withSuspendingNonNullRedis(
    redisUrl: String = defaultRedisUrl,
    printStackTrace: Boolean = false,
    block: suspend (RedisClient) -> T,
  ): T? =
    try {
      createRedisClient(redisUrl)
        .use { client ->
          block.invoke(client)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      null
    }

  /**
   * Lazily scans Redis keys matching the given [pattern] using the SCAN command.
   *
   * Extension function on [UnifiedJedis]. Returns a [Sequence] that iterates through all matching keys
   * without loading them all into memory at once.
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
      var cursorVal = "0"
      while (true) {
        cursorVal =
          scan(cursorVal, scanParams).run {
            result.forEach { yield(it) }
            cursor
          }
        if (cursorVal == "0")
          break
      }
    }
}
