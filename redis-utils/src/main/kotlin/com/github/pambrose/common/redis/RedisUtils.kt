/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol.DEFAULT_TIMEOUT
import redis.clients.jedis.exceptions.JedisConnectionException
import redis.clients.jedis.params.ScanParams
import java.net.URI
import java.util.*

object RedisUtils : KLogging() {
  const val REDIS_MAX_POOL_SIZE = "redis.maxPoolSize"
  const val REDIS_MAX_IDLE_SIZE = "redis.maxIdleSize"
  const val REDIS_MIN_IDLE_SIZE = "redis.minIdleSize"

  private const val FAILED_TO_CONNECT_MSG = "Failed to connect to redis"

  private val colon = Regex(":")
  private val defaultRedisUrl = System.getenv("REDIS_URL") ?: "redis://user:none@localhost:6379"

  class RedisInfo(val uri: URI, val user: String, val password: String) {
    val includeUserInAuth get() = user.isNotBlank() && user != "default" && user != "user" && password != "none"
  }

  private fun urlDetails(redisUrl: String) =
    URI(redisUrl)
      .let {
        RedisInfo(
          it,
          (it.userInfo?.split(colon, 2)?.get(0) ?: ""),
          (it.userInfo?.split(colon, 2)?.get(1) ?: "")
        )
      }

  private val String.isSsl: Boolean get() = lowercase(Locale.getDefault()).startsWith("rediss://")

  fun newJedisPool(
    redisUrl: String = defaultRedisUrl,
    maxPoolSize: Int = System.getProperty(REDIS_MAX_POOL_SIZE)?.toInt() ?: 10,
    maxIdleSize: Int = System.getProperty(REDIS_MAX_IDLE_SIZE)?.toInt() ?: 5,
    minIdleSize: Int = System.getProperty(REDIS_MIN_IDLE_SIZE)?.toInt() ?: 1
  ): JedisPool {
    require(maxPoolSize > 0) { "Max pool size must be a positive number" }
    require(maxIdleSize > 0) { "Max idle size must be a positive number" }
    require(minIdleSize >= 0) { "Min idle size canot be a negative number" }

    logger.info { "Redis max pool size: $maxPoolSize" }
    logger.info { "Redis max idle size: $maxIdleSize" }
    logger.info { "Redis min idle size: $minIdleSize" }

    val poolConfig =
      JedisPoolConfig()
        .apply {
          maxTotal = maxPoolSize
          maxIdle = maxIdleSize
          minIdle = minIdleSize
          testOnBorrow = true
          testOnReturn = true
          testWhileIdle = true
        }

    val info = urlDetails(redisUrl)
    val host = info.uri.host
    val port = info.uri.port

    return if (info.password.isNotBlank()) {
      if (info.includeUserInAuth)
        JedisPool(poolConfig, host, port, DEFAULT_TIMEOUT, info.user, info.password, redisUrl.isSsl)
      else
        JedisPool(poolConfig, host, port, DEFAULT_TIMEOUT, info.password, redisUrl.isSsl)
    } else
      JedisPool(poolConfig, host, port, DEFAULT_TIMEOUT, redisUrl.isSsl)
  }

  fun <T> JedisPool.withRedisPool(printStackTrace: Boolean = false, block: (Jedis?) -> T): T =
    try {
      resource
        .use { redis ->
          redis.ping("")
          block.invoke(redis)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      block.invoke(null)
    }

  fun <T> JedisPool.withNonNullRedisPool(printStackTrace: Boolean = false, block: (Jedis) -> T): T? =
    try {
      resource
        .use { redis ->
          redis.ping("")
          block.invoke(redis)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      null
    }

  suspend fun <T> JedisPool.withSuspendingRedisPool(printStackTrace: Boolean = false, block: suspend (Jedis?) -> T): T =
    try {
      resource
        .use { redis ->
          redis.ping("")
          block.invoke(redis)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      block.invoke(null)
    }

  suspend fun <T> JedisPool.withSuspendingNonNullRedisPool(
    printStackTrace: Boolean = false,
    block: suspend (Jedis) -> T
  ): T? =
    try {
      resource
        .use { redis ->
          redis.ping("")
          block.invoke(redis)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      null
    }

  fun <T> withRedis(redisUrl: String = defaultRedisUrl, printStackTrace: Boolean = false, block: (Jedis?) -> T): T =
    try {
      val info = urlDetails(redisUrl)
      Jedis(info.uri.host, info.uri.port, DEFAULT_TIMEOUT, redisUrl.isSsl)
        .use { redis ->
          if (info.password.isNotBlank()) {
            if (info.includeUserInAuth)
              redis.auth(info.user, info.password)
            else
              redis.auth(info.password)
          }
          block.invoke(redis)
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
    block: (Jedis) -> T
  ): T? =
    try {
      val info = urlDetails(redisUrl)
      Jedis(info.uri.host, info.uri.port, DEFAULT_TIMEOUT, redisUrl.isSsl)
        .use { redis ->
          if (info.password.isNotBlank()) {
            if (info.includeUserInAuth)
              redis.auth(info.user, info.password)
            else
              redis.auth(info.password)
          }
          block.invoke(redis)
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
    block: suspend (Jedis?) -> T
  ): T =
    try {
      val info = urlDetails(redisUrl)
      Jedis(info.uri.host, info.uri.port, DEFAULT_TIMEOUT, redisUrl.isSsl)
        .use { redis ->
          if (info.password.isNotBlank()) {
            if (info.includeUserInAuth)
              redis.auth(info.user, info.password)
            else
              redis.auth(info.password)
          }
          block.invoke(redis)
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
    block: suspend (Jedis) -> T
  ): T? =
    try {
      val info = urlDetails(redisUrl)
      Jedis(info.uri.host, info.uri.port, DEFAULT_TIMEOUT, redisUrl.isSsl)
        .use { redis ->
          if (info.password.isNotBlank()) {
            if (info.includeUserInAuth)
              redis.auth(info.user, info.password)
            else
              redis.auth(info.password)
          }
          block.invoke(redis)
        }
    } catch (e: JedisConnectionException) {
      if (printStackTrace)
        logger.error(e) { FAILED_TO_CONNECT_MSG }
      else
        logger.error { FAILED_TO_CONNECT_MSG }
      null
    }

  fun Jedis.scanKeys(pattern: String, count: Int = 100): Sequence<String> =
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