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
  fun newRedisClientValidationNegativePoolSizeTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newRedisClient(maxPoolSize = -1)
    }
  }

  @Test
  fun newRedisClientValidationNegativeIdleSizeTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newRedisClient(maxIdleSize = -1)
    }
  }

  @Test
  fun newRedisClientValidationNegativeMinIdleSizeTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newRedisClient(minIdleSize = -1)
    }
  }

  @Test
  fun newRedisClientValidationNegativeMaxWaitSecsTest() {
    shouldThrow<IllegalArgumentException> {
      RedisUtils.newRedisClient(maxWaitSecs = -1)
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
  fun redisClientCreationWithValidParamsTest() {
    // This will fail to connect but should create the client object
    val client = RedisUtils.newRedisClient(
      redisUrl = "redis://user:pass@localhost:6379",
      maxPoolSize = 5,
      maxIdleSize = 3,
      minIdleSize = 1,
      maxWaitSecs = 2,
    )
    client shouldNotBe null
    client.close()
  }

  @Test
  fun redisClientCreationWithSslUrlTest() {
    // Test that SSL URLs are handled (rediss:// prefix)
    val client = RedisUtils.newRedisClient(
      redisUrl = "rediss://user:pass@localhost:6379",
      maxPoolSize = 1,
    )
    client shouldNotBe null
    client.close()
  }

  @Test
  fun redisClientCreationWithNoPasswordTest() {
    val client = RedisUtils.newRedisClient(
      redisUrl = "redis://localhost:6379",
      maxPoolSize = 1,
    )
    client shouldNotBe null
    client.close()
  }
}
