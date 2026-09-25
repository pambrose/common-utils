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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.redis

import ch.qos.logback.classic.Level
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.net.URI
import java.time.Duration

class RedisUtilsTests : StringSpec() {
  init {
    // -1 is commons-pool2's "unlimited"; anything below that is meaningless.
    "new redis client validation negative pool size" {
      shouldThrow<IllegalArgumentException> {
        RedisUtils.newRedisClient(maxPoolSize = -2)
      }
    }

    "new redis client validation negative idle size" {
      shouldThrow<IllegalArgumentException> {
        RedisUtils.newRedisClient(maxIdleSize = -2)
      }
    }

    // -1 is commons-pool2's "no limit", accepted for maxIdleSize as it is for maxPoolSize.
    "a max idle size of -1 means unlimited and is accepted" {
      RedisUtils.newRedisClient(redisUrl = "redis://localhost:1", maxIdleSize = -1).use { client ->
        client.pool.maxIdle shouldBe -1
      }
    }

    // commons-pool2 silently lowers minIdle to maxIdle, so the mismatch is at least reported.
    "a min idle size above the max idle size is reported" {
      capturingRedisLogs { logs ->
        RedisUtils.newRedisClient(redisUrl = "redis://localhost:1", maxIdleSize = 2, minIdleSize = 5).use { client ->
          client.pool.minIdle shouldBe 2
        }
        logs().single { it.level == Level.WARN }.formattedMessage shouldContain
          "min idle size 5 exceeds max idle size 2"
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

    // Port 1 refuses connections, and jedis only reports that on the first command, so these clients are built
    // without contacting any server.
    "redis client creation applies the given pool settings" {
      RedisUtils.newRedisClient(
        redisUrl = "redis://user:pass@localhost:1",
        maxPoolSize = 5,
        maxIdleSize = 3,
        minIdleSize = 2,
        maxWaitSecs = 4,
      ).use { client ->
        client.pool.maxTotal shouldBe 5
        client.pool.maxIdle shouldBe 3
        client.pool.minIdle shouldBe 2
        client.pool.maxWaitDuration shouldBe Duration.ofSeconds(4)
      }
    }

    // "user" is a placeholder name, so only the password is sent.
    "an ssl url with a placeholder user enables ssl and sends only the password" {
      RedisUtils.clientConfig("rediss://user:pass@localhost:6379").apply {
        sslOptions shouldNotBe null
        user shouldBe null
        password shouldBe "pass"
      }
      RedisUtils.newRedisClient(redisUrl = "rediss://user:pass@localhost:1", maxPoolSize = 1).use { client ->
        client.pool.maxTotal shouldBe 1
      }
    }

    // RedisInfo.includeUserInAuth decides whether to send the username in AUTH: only when the
    // user is a real (non-placeholder) name and the password is not the "none" placeholder.
    "includeUserInAuth is true for a real user with a real password" {
      RedisUtils.RedisInfo(URI("redis://h"), "alice", "secret").includeUserInAuth shouldBe true
    }

    "includeUserInAuth is false for the 'default' placeholder user" {
      RedisUtils.RedisInfo(URI("redis://h"), "default", "secret").includeUserInAuth shouldBe false
    }

    "includeUserInAuth is false for the 'user' placeholder user" {
      RedisUtils.RedisInfo(URI("redis://h"), "user", "secret").includeUserInAuth shouldBe false
    }

    "includeUserInAuth is false when the password is the 'none' placeholder" {
      RedisUtils.RedisInfo(URI("redis://h"), "alice", "none").includeUserInAuth shouldBe false
    }

    "includeUserInAuth is false when the user is blank" {
      RedisUtils.RedisInfo(URI("redis://h"), "", "secret").includeUserInAuth shouldBe false
    }

    "RedisInfo exposes the parsed uri, user, and password" {
      val info = RedisUtils.RedisInfo(URI("redis://h:6379"), "alice", "secret")
      info.user shouldBe "alice"
      info.password shouldBe "secret"
      info.uri.host shouldBe "h"
    }
  }
}
