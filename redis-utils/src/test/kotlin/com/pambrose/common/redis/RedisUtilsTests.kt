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

package com.pambrose.common.redis

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RedisUtilsTests : StringSpec() {
  init {
    "new redis client validation negative pool size" {
      shouldThrow<IllegalArgumentException> {
        RedisUtils.newRedisClient(maxPoolSize = -1)
      }
    }

    "new redis client validation negative idle size" {
      shouldThrow<IllegalArgumentException> {
        RedisUtils.newRedisClient(maxIdleSize = -1)
      }
    }

    "new redis client validation negative min idle size" {
      shouldThrow<IllegalArgumentException> {
        RedisUtils.newRedisClient(minIdleSize = -1)
      }
    }

    "new redis client validation negative max wait secs" {
      shouldThrow<IllegalArgumentException> {
        RedisUtils.newRedisClient(maxWaitSecs = -1)
      }
    }

    "redis constants" {
      RedisUtils.REDIS_MAX_POOL_SIZE shouldBe "redis.maxPoolSize"
      RedisUtils.REDIS_MAX_IDLE_SIZE shouldBe "redis.maxIdleSize"
      RedisUtils.REDIS_MIN_IDLE_SIZE shouldBe "redis.minIdleSize"
      RedisUtils.REDIS_MAX_WAIT_SECS shouldBe "redis.maxWaitSecs"
    }

    "redis client creation with valid params" {
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

    "redis client creation with ssl url" {
      // Test that SSL URLs are handled (rediss:// prefix)
      val client = RedisUtils.newRedisClient(
        redisUrl = "rediss://user:pass@localhost:6379",
        maxPoolSize = 1,
      )
      client shouldNotBe null
      client.close()
    }

    "redis client creation with no password" {
      val client = RedisUtils.newRedisClient(
        redisUrl = "redis://localhost:6379",
        maxPoolSize = 1,
      )
      client shouldNotBe null
      client.close()
    }
  }
}
