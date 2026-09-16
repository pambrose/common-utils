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

import com.pambrose.common.redis.RedisUtils.scanKeys
import com.pambrose.common.redis.RedisUtils.withNonNullRedis
import com.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.pambrose.common.redis.RedisUtils.withRedis
import com.pambrose.common.redis.RedisUtils.withRedisPool
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedisPool
import com.pambrose.common.redis.RedisUtils.withSuspendingRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.time.Duration
import redis.clients.jedis.RedisClient
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.exceptions.JedisConnectionException
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.params.ScanParams.SCAN_POINTER_START
import redis.clients.jedis.resps.ScanResult

// Hermetic tests for RedisUtils built on MockK. No live Redis server is required:
// Jedis clients are either mocked outright or merely constructed (construction never connects).
class RedisUtilsMockTests : StringSpec() {
  // Stubs the private connect-and-ping step, so a success path runs without a Redis server.
  private fun stubConnectedRedisClient(client: RedisClient) {
    mockkObject(RedisUtils, recordPrivateCalls = true)
    every { RedisUtils["connectOrNull"](any<String>(), any<Boolean>()) } returns client
  }

  init {
    // scanKeys() drives the SCAN command page by page until the cursor returns to "0"

    "scanKeys yields keys from every page until the cursor returns to zero" {
      val jedis = mockk<UnifiedJedis>()
      val paramsSlot = slot<ScanParams>()
      every { jedis.scan(SCAN_POINTER_START, capture(paramsSlot)) } returns
        ScanResult("42", ["user:1", "user:2"])
      every { jedis.scan("42", any<ScanParams>()) } returns ScanResult(SCAN_POINTER_START, ["user:3"])

      val keys = jedis.scanKeys("user:*", count = 25).toList()

      keys shouldBe ["user:1", "user:2", "user:3"]
      // ScanParams implements equals(), so this checks both the match pattern and the count hint
      paramsSlot.captured shouldBe ScanParams().match("user:*").count(25)
      verify(exactly = 1) { jedis.scan(SCAN_POINTER_START, any<ScanParams>()) }
      verify(exactly = 1) { jedis.scan("42", any<ScanParams>()) }
    }

    "scanKeys with the default count returns an empty sequence when nothing matches" {
      val jedis = mockk<UnifiedJedis>()
      val paramsSlot = slot<ScanParams>()
      every { jedis.scan(SCAN_POINTER_START, capture(paramsSlot)) } returns
        ScanResult(SCAN_POINTER_START, emptyList())

      jedis.scanKeys("missing:*").toList() shouldBe emptyList()

      paramsSlot.captured shouldBe ScanParams().match("missing:*").count(100)
      verify(exactly = 1) { jedis.scan(any<String>(), any<ScanParams>()) }
    }

    "scanKeys is lazy and only fetches pages as the sequence is consumed" {
      val jedis = mockk<UnifiedJedis>()
      every { jedis.scan(SCAN_POINTER_START, any<ScanParams>()) } returns ScanResult("7", ["a", "b"])
      every { jedis.scan("7", any<ScanParams>()) } returns ScanResult(SCAN_POINTER_START, ["c"])

      val seq = jedis.scanKeys("*")
      // Building the sequence issues no SCAN calls
      verify(exactly = 0) { jedis.scan(any<String>(), any<ScanParams>()) }

      // Consuming only the first page never requests the second one
      seq.take(2).toList() shouldBe ["a", "b"]
      verify(exactly = 1) { jedis.scan(any<String>(), any<ScanParams>()) }
    }

    // Success paths of the pool extension functions (ping() answers, so the block runs)

    "withRedisPool invokes block with this client when ping succeeds" {
      val client = mockk<RedisClient>()
      every { client.ping() } returns "PONG"
      val result =
        client.withRedisPool { c ->
          c shouldBeSameInstanceAs client
          "pool-ok"
        }
      result shouldBe "pool-ok"
      verify(exactly = 1) { client.ping() }
    }

    "withNonNullRedisPool invokes block with this client when ping succeeds" {
      val client = mockk<RedisClient>()
      every { client.ping() } returns "PONG"
      val result =
        client.withNonNullRedisPool { c ->
          c shouldBeSameInstanceAs client
          "nonnull-pool-ok"
        }
      result shouldBe "nonnull-pool-ok"
      verify(exactly = 1) { client.ping() }
    }

    "withSuspendingRedisPool invokes block with this client when ping succeeds" {
      val client = mockk<RedisClient>()
      every { client.ping() } returns "PONG"
      val result =
        client.withSuspendingRedisPool { c ->
          c shouldBeSameInstanceAs client
          "suspending-pool-ok"
        }
      result shouldBe "suspending-pool-ok"
      verify(exactly = 1) { client.ping() }
    }

    "withSuspendingNonNullRedisPool invokes block with this client when ping succeeds" {
      val client = mockk<RedisClient>()
      every { client.ping() } returns "PONG"
      val result =
        client.withSuspendingNonNullRedisPool { c ->
          c shouldBeSameInstanceAs client
          "suspending-nonnull-pool-ok"
        }
      result shouldBe "suspending-nonnull-pool-ok"
      verify(exactly = 1) { client.ping() }
    }

    "withRedisPool with printStackTrace logs the failure and passes null to block" {
      val client = mockk<RedisClient>()
      every { client.ping() } throws JedisConnectionException("simulated ping failure")
      val result =
        client.withRedisPool(printStackTrace = true) { c ->
          c shouldBe null
          "null-branch"
        }
      result shouldBe "null-branch"
    }

    // The connection-failure paths are exercised against a real unreachable port, an exhausted pool and a
    // rejected password in RedisConfigTests. Forcing the private factory to throw, as this spec used to,
    // tested a path production code cannot reach: building a client never contacts the server.

    // Success paths of the withRedis family. Each function now pings before handing the client over, so a
    // connected client is stubbed in; without one, an absent server would take the null path.

    "withSuspendingNonNullRedis executes block with a non-null client" {
      val client = mockk<RedisClient>(relaxed = true)
      stubConnectedRedisClient(client)
      try {
        val result =
          withSuspendingNonNullRedis(redisUrl = "redis://localhost:6379") { c ->
            c shouldBeSameInstanceAs client
            "suspending-nonnull-ran"
          }
        result shouldBe "suspending-nonnull-ran"
      } finally {
        unmockkObject(RedisUtils)
      }
    }

    "withRedis family uses the default redis url when none is given" {
      val client = mockk<RedisClient>(relaxed = true)
      stubConnectedRedisClient(client)
      try {
        withRedis { c ->
          c shouldNotBe null
          "r"
        } shouldBe "r"
        withNonNullRedis { "n" } shouldBe "n"
        withSuspendingRedis { "sr" } shouldBe "sr"
        withSuspendingNonNullRedis { "sn" } shouldBe "sn"
      } finally {
        unmockkObject(RedisUtils)
      }
    }

    // newRedisClient() pool sizing falls back to system properties before hard-coded defaults

    "newRedisClient reads pool sizing from system properties" {
      System.setProperty(RedisUtils.REDIS_MAX_POOL_SIZE, "7")
      System.setProperty(RedisUtils.REDIS_MAX_IDLE_SIZE, "4")
      System.setProperty(RedisUtils.REDIS_MIN_IDLE_SIZE, "2")
      System.setProperty(RedisUtils.REDIS_MAX_WAIT_SECS, "3")
      try {
        val client = RedisUtils.newRedisClient(redisUrl = "redis://localhost:6379")
        client.use { client ->
          client.pool.maxTotal shouldBe 7
          client.pool.maxIdle shouldBe 4
          client.pool.minIdle shouldBe 2
          client.pool.maxWaitDuration shouldBe Duration.ofSeconds(3)
          client.pool.testOnBorrow shouldBe true
          // testOnReturn would add a second PING to every pooled command; testWhileIdle covers idle ones.
          client.pool.testOnReturn shouldBe false
          client.pool.testWhileIdle shouldBe true
        }
      } finally {
        System.clearProperty(RedisUtils.REDIS_MAX_POOL_SIZE)
        System.clearProperty(RedisUtils.REDIS_MAX_IDLE_SIZE)
        System.clearProperty(RedisUtils.REDIS_MIN_IDLE_SIZE)
        System.clearProperty(RedisUtils.REDIS_MAX_WAIT_SECS)
      }
    }

    "newRedisClient with a real user and password includes the user in auth" {
      // "alice"/"secret" satisfies includeUserInAuth, exercising the builder.user() branch
      val client = RedisUtils.newRedisClient(
        redisUrl = "redis://alice:secret@localhost:6379",
        maxPoolSize = 1,
      )
      client shouldNotBe null
      client.close()
    }
  }
}
