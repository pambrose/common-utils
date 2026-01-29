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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.redis

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class RedisUtilsTests {
  @Test
  fun newJedisPoolValidationNegativePoolSizeTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newJedisPool(maxPoolSize = -1)
    }
  }

  @Test
  fun newJedisPoolValidationNegativeIdleSizeTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newJedisPool(maxIdleSize = -1)
    }
  }

  @Test
  fun newJedisPoolValidationNegativeMinIdleSizeTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newJedisPool(minIdleSize = -1)
    }
  }

  @Test
  fun newJedisPoolValidationNegativeMaxWaitSecsTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newJedisPool(maxWaitSecs = -1)
    }
  }

  @Test
  fun redisConstantsTest() {
    RedisUtils.REDIS_MAX_POOL_SIZE shouldBe "redis.maxPoolSize"
    RedisUtils.REDIS_MAX_IDLE_SIZE shouldBe "redis.maxIdleSize"
    RedisUtils.REDIS_MIN_IDLE_SIZE shouldBe "redis.minIdleSize"
    RedisUtils.REDIS_MAX_WAIT_SECS shouldBe "redis.maxWaitSecs"
  }

  @Test
  fun jedisPoolCreationWithValidParamsTest() {
    // This will fail to connect but should create the pool object
    val pool = RedisUtils.newJedisPool(
      redisUrl = "redis://user:pass@localhost:6379",
      maxPoolSize = 5,
      maxIdleSize = 3,
      minIdleSize = 1,
      maxWaitSecs = 2,
    )
    pool shouldNotBe null
    pool.close()
  }

  @Test
  fun jedisPoolCreationWithSslUrlTest() {
    // Test that SSL URLs are handled (rediss:// prefix)
    val pool = RedisUtils.newJedisPool(
      redisUrl = "rediss://user:pass@localhost:6379",
      maxPoolSize = 1,
    )
    pool shouldNotBe null
    pool.close()
  }

  @Test
  fun jedisPoolCreationWithNoPasswordTest() {
    val pool = RedisUtils.newJedisPool(
      redisUrl = "redis://localhost:6379",
      maxPoolSize = 1,
    )
    pool shouldNotBe null
    pool.close()
  }
}
