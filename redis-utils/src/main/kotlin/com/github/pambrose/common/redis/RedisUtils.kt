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
  private val redisURI by lazy { URI(System.getenv("REDIS_URL") ?: "redis://user:none@localhost:6379") }
  private val password by lazy { redisURI.userInfo.split(colon, 2)[1] }

  private fun newJedisPool(): JedisPool {
    val maxPoolSize = System.getProperty(REDIS_MAX_POOL_SIZE)?.toInt() ?: 10
    val maxIdleSize = System.getProperty(REDIS_MAX_IDLE_SIZE)?.toInt() ?: 5
    val minIdleSize = System.getProperty(REDIS_MIN_IDLE_SIZE)?.toInt() ?: 1

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
    return JedisPool(poolConfig, redisURI.host, redisURI.port, Protocol.DEFAULT_TIMEOUT, password)
  }

  private val pool by lazy { newJedisPool() }

  fun <T> withRedisPool(block: (Jedis?) -> T): T {
    try {
      pool.resource
        .use { redis ->
          redis.ping("")
          return block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      return block.invoke(null)
    }
  }

  suspend fun <T> withSuspendingRedisPool(block: suspend (Jedis?) -> T): T {
    try {
      pool.resource
        .use { redis ->
          redis.ping("")
          return block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      return block.invoke(null)
    }
  }

  fun <T> withRedis(block: (Jedis?) -> T): T {
    try {
      Jedis(redisURI.host, redisURI.port, Protocol.DEFAULT_TIMEOUT)
        .use { redis ->
          redis.auth(password)
          return block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      logger.info(e) { "" }
      return block.invoke(null)
    }
  }

  suspend fun <T> withSuspendingRedis(block: suspend (Jedis?) -> T): T {
    try {
      Jedis(redisURI.host, redisURI.port, Protocol.DEFAULT_TIMEOUT)
        .use { redis ->
          redis.auth(password)
          return block.invoke(redis)
        }
    }
    catch (e: JedisConnectionException) {
      logger.info(e) { "" }
      return block.invoke(null)
    }
  }
}

