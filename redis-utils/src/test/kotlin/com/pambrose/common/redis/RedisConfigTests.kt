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

import com.pambrose.common.redis.RedisUtils.withNonNullRedis
import com.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.pambrose.common.redis.RedisUtils.withRedis
import com.pambrose.common.redis.RedisUtils.withRedisPool
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingRedis
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import redis.clients.jedis.RedisClient
import redis.clients.jedis.RedisProtocol
import redis.clients.jedis.exceptions.JedisAccessControlException
import redis.clients.jedis.exceptions.JedisException

/**
 * Pins what a Redis URL turns into, and the connection-failure paths the KDoc promises. Port 1 reliably
 * refuses connections, so nothing here needs a Redis server.
 */
class RedisConfigTests : StringSpec() {
  init {
    val unreachableUrl = "redis://localhost:1"

    // The database index and protocol in the URL were silently dropped, so redis://h:6379/3 used database 0.
    "the client config carries the database index from the url" {
      RedisUtils.clientConfig("redis://localhost:6379/3").database shouldBe 3
    }

    "the client config defaults to database 0 when the url has no index" {
      RedisUtils.clientConfig("redis://localhost:6379").database shouldBe 0
    }

    "the client config carries the protocol from the url" {
      RedisUtils.clientConfig("redis://localhost:6379/0?protocol=3").redisProtocol shouldBe RedisProtocol.RESP3
    }

    // "none" is the placeholder in the default URL; sending it as a real password fails against a server
    // that has no password set.
    "a 'none' placeholder password is not sent" {
      RedisUtils.clientConfig("redis://user:none@localhost:6379").password shouldBe null
    }

    "a real user and password are sent" {
      val config = RedisUtils.clientConfig("redis://alice:secret@localhost:6379")
      config.user shouldBe "alice"
      config.password shouldBe "secret"
    }

    // lowercase() with a Turkish default locale turns REDISS into redıss (dotless i), which silently
    // disabled TLS and sent the password in the clear.
    "an uppercase rediss scheme enables ssl in every locale" {
      val previous = Locale.getDefault()
      Locale.setDefault(Locale.forLanguageTag("tr"))
      try {
        RedisUtils.clientConfig("REDISS://localhost:6379").isSsl shouldBe true
        RedisUtils.clientConfig("redis://localhost:6379").isSsl shouldBe false
      } finally {
        Locale.setDefault(previous)
      }
    }

    // commons-pool2 treats -1 as unlimited and 0 as a pool that can never lend a connection.
    "a pool size of zero is rejected" {
      shouldThrow<IllegalArgumentException> { RedisUtils.newRedisClient(maxPoolSize = 0) }
    }

    "a pool size of -1 means unlimited and is accepted" {
      val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = -1)
      try {
        client.pool.maxTotal shouldBe -1
      } finally {
        client.close()
      }
    }

    // Each pooled command paid for a PING on borrow and another on return.
    "the pool does not test connections on return" {
      val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = 1)
      try {
        client.pool.testOnReturn shouldBe false
        client.pool.testOnBorrow shouldBe true
      } finally {
        client.close()
      }
    }

    // Building a client never connects, so the withRedis family has to check the connection itself.
    "withRedis passes null to the block when the server is unreachable" {
      val callCount = AtomicInt(0)
      val result =
        withRedis(redisUrl = unreachableUrl) { client ->
          callCount.incrementAndFetch()
          client shouldBe null
          "null-branch"
        }
      result shouldBe "null-branch"
      callCount.load() shouldBe 1
    }

    "withNonNullRedis returns null when the server is unreachable" {
      val callCount = AtomicInt(0)
      val result =
        withNonNullRedis(redisUrl = unreachableUrl) { _ ->
          callCount.incrementAndFetch()
          "should-not-reach"
        }
      result shouldBe null
      callCount.load() shouldBe 0
    }

    "withSuspendingRedis passes null to the block when the server is unreachable" {
      val result =
        withSuspendingRedis(redisUrl = unreachableUrl) { client ->
          client shouldBe null
          "suspending-null-branch"
        }
      result shouldBe "suspending-null-branch"
    }

    "withSuspendingNonNullRedis returns null when the server is unreachable" {
      withSuspendingNonNullRedis(redisUrl = unreachableUrl) { "should-not-reach" } shouldBe null
    }

    // Pool exhaustion and borrow-validation failures arrive as a plain JedisException, and auth failures as
    // JedisAccessControlException; both are connection failures from the caller's point of view.
    "pool helpers take the null path when the pool is exhausted" {
      val client = mockk<RedisClient>()
      every { client.ping() } throws JedisException("Could not get a resource from the pool")

      client.withRedisPool {
        it shouldBe null
        "null-branch"
      } shouldBe "null-branch"
      client.withNonNullRedisPool { "should-not-reach" } shouldBe null
    }

    "pool helpers take the null path when authentication fails" {
      val client = mockk<RedisClient>()
      every { client.ping() } throws JedisAccessControlException("WRONGPASS invalid username-password pair")

      client.withRedisPool {
        it shouldBe null
        "null-branch"
      } shouldBe "null-branch"
      client.withNonNullRedisPool { "should-not-reach" } shouldBe null
    }

    "a client for a reachable-looking url is still created" {
      val client = RedisUtils.newRedisClient(redisUrl = "redis://alice:secret@localhost:6379/2", maxPoolSize = 1)
      try {
        client shouldNotBe null
      } finally {
        client.close()
      }
    }
  }
}
