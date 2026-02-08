/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose.common.redis

import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.util.*
import redis.clients.jedis.ConnectionPoolConfig
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol.DEFAULT_TIMEOUT
import redis.clients.jedis.RedisClient
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.exceptions.JedisConnectionException
import redis.clients.jedis.params.ScanParams

object RedisUtils {
  private val logger = KotlinLogging.logger {}

  const val REDIS_MAX_POOL_SIZE = "redis.maxPoolSize"
  const val REDIS_MAX_IDLE_SIZE = "redis.maxIdleSize"
  const val REDIS_MIN_IDLE_SIZE = "redis.minIdleSize"
  const val REDIS_MAX_WAIT_SECS = "redis.maxWaitSecs"

  private const val FAILED_TO_CONNECT_MSG = "Failed to connect to redis"

  private val colon = Regex(":")
  private val defaultRedisUrl = System.getenv("REDIS_URL") ?: "redis://user:none@localhost:6379"

  class RedisInfo(
    val uri: URI,
    val user: String,
    val password: String,
  ) {
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
          setMaxWait(java.time.Duration.ofSeconds(maxWaitSecs))
          testOnBorrow = true
          testOnReturn = true
          testWhileIdle = true

          timeBetweenEvictionRuns = java.time.Duration.ofMinutes(1)
          minEvictableIdleDuration = java.time.Duration.ofMinutes(1)
        }

    val info = urlDetails(redisUrl)
    val clientConfig = buildClientConfig(info, redisUrl.isSsl)

    return RedisClient.builder()
      .hostAndPort(HostAndPort(info.uri.host, info.uri.port))
      .clientConfig(clientConfig)
      .poolConfig(poolConfig)
      .build()
  }

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
