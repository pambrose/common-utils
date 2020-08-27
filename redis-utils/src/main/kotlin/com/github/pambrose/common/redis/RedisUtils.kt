/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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
import redis.clients.jedis.Protocol
import redis.clients.jedis.exceptions.JedisConnectionException
import java.net.URI

object RedisUtils : KLogging() {
  const val REDIS_MAX_POOL_SIZE = "redis.maxPoolSize"
  const val REDIS_MAX_IDLE_SIZE = "redis.maxIdleSize"
  const val REDIS_MIN_IDLE_SIZE = "redis.minIdleSize"

  private val colon = Regex(":")
  private val defaultRedisUrl = System.getenv("REDIS_URL") ?: "redis://user:none@localhost:6379"
  private fun urlDetails(redisUrl: String): Pair<URI, String> {
    val redisUri = URI(redisUrl)
    return redisUri to redisUri.userInfo.split(colon, 2)[1]
  }

  fun newJedisPool(redisUrl: String = defaultRedisUrl,
                   maxPoolSize: Int = System.getProperty(REDIS_MAX_POOL_SIZE)?.toInt() ?: 10,
                   maxIdleSize: Int = System.getProperty(REDIS_MAX_IDLE_SIZE)?.toInt() ?: 5,
                   minIdleSize: Int = System.getProperty(REDIS_MIN_IDLE_SIZE)?.toInt() ?: 1): JedisPool {

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

    val (redisUri, password) = urlDetails(redisUrl)
    return JedisPool(poolConfig,
                     redisUri.host,
                     redisUri.port,
                     Protocol.DEFAULT_TIMEOUT,
                     password,
                     redisUrl.toLowerCase().startsWith("rediss://"))
  }

  fun <T> JedisPool.withRedisPool(block: (Jedis?) -> T): T =
    try {
      resource
        .use { redis ->
          redis.ping("")
          block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      logger.info(e) { "Failed to connect to redis" }
      block.invoke(null)
    }

  suspend fun <T> JedisPool.withSuspendingRedisPool(block: suspend (Jedis?) -> T): T =
    try {
      resource
        .use { redis ->
          redis.ping("")
          block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      logger.info(e) { "Failed to connect to redis" }
      block.invoke(null)
    }

  fun <T> withRedis(redisUrl: String = defaultRedisUrl, block: (Jedis?) -> T): T =
    try {
      val (redisUri, password) = urlDetails(redisUrl)
      Jedis(redisUri.host, redisUri.port, Protocol.DEFAULT_TIMEOUT)
        .use { redis ->
          redis.auth(password)
          block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      logger.info(e) { "Failed to connect to redis" }
      block.invoke(null)
    }

  suspend fun <T> withSuspendingRedis(redisUrl: String = defaultRedisUrl, block: suspend (Jedis?) -> T): T =
    try {
      val (redisUri, password) = urlDetails(redisUrl)
      Jedis(redisUri.host, redisUri.port, Protocol.DEFAULT_TIMEOUT)
        .use { redis ->
          redis.auth(password)
          block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      logger.info(e) { "Failed to connect to redis" }
      block.invoke(null)
    }
}