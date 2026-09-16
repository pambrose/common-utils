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
import ch.qos.logback.classic.spi.ThrowableProxy
import com.pambrose.common.redis.RedisUtils.scanKeys
import com.pambrose.common.redis.RedisUtils.withNonNullRedis
import com.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.pambrose.common.redis.RedisUtils.withRedis
import com.pambrose.common.redis.RedisUtils.withRedisPool
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedisPool
import com.pambrose.common.redis.RedisUtils.withSuspendingRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Duration
import redis.clients.jedis.RedisClient
import redis.clients.jedis.UnifiedJedis
import redis.clients.jedis.exceptions.JedisConnectionException
import redis.clients.jedis.exceptions.JedisException
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.params.ScanParams.SCAN_POINTER_START
import redis.clients.jedis.resps.ScanResult

// Hermetic tests for RedisUtils built on MockK. No live Redis server is required: Jedis clients are either mocked
// outright or built against an unreachable port, which jedis tolerates until the first command.
class RedisUtilsMockTests : StringSpec() {
  // A client whose ping() answers, and whose close() can be verified.
  private fun connectedClient(): RedisClient =
    mockk {
      every { ping() } returns "PONG"
      every { close() } just Runs
    }

  // Stubs only the private client factory, so the rest of the connect step (the ping, and closing a client that
  // fails it) runs for real against [client]. Every URL the factory is asked for is added to [urls].
  private inline fun <T> withClientFactoryReturning(
    client: RedisClient,
    urls: MutableList<String> = [],
    block: () -> T,
  ): T {
    mockkObject(RedisUtils, recordPrivateCalls = true)
    try {
      every { RedisUtils["createRedisClient"](capture(urls)) } returns client
      return block()
    } finally {
      unmockkObject(RedisUtils)
    }
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

    "a pool helper logs a failure with its stack trace only when printStackTrace is true" {
      val failure = JedisConnectionException("simulated ping failure")
      val client = mockk<RedisClient>()
      every { client.ping() } throws failure

      capturingRedisLogs { logs ->
        client.withRedisPool(printStackTrace = true) { it } shouldBe null
        client.withRedisPool(printStackTrace = false) { it } shouldBe null

        val (withTrace, withoutTrace) = logs().filter { it.level == Level.ERROR }
        withTrace.formattedMessage shouldBe "Failed to connect to redis"
        withTrace.throwableProxy.shouldBeInstanceOf<ThrowableProxy>().throwable shouldBeSameInstanceAs failure
        withoutTrace.formattedMessage shouldBe "Failed to connect to redis"
        withoutTrace.throwableProxy shouldBe null
      }
    }

    // The connection-failure paths are exercised against a real unreachable port, an exhausted pool and a
    // rejected password in RedisConfigTests, and the real connect-and-close path against a fake server in
    // RedisConnectionTests. The tests below stub only the private client factory, so the rest of that path runs.

    "each withRedis variant pings the new client once, hands it to the block and then closes it" {
      withRedisVariants.forEach { (name, call) ->
        withClue(name) {
          val client = connectedClient()
          withClientFactoryReturning(client) {
            call("redis://cache.example.com:6379") { c ->
              c shouldBeSameInstanceAs client
              verify(exactly = 0) { client.close() }
              "ran"
            } shouldBe "ran"
          }

          verifyOrder {
            client.ping()
            client.close()
          }
          verify(exactly = 1) { client.ping() }
          verify(exactly = 1) { client.close() }
        }
      }
    }

    "each withRedis variant closes the client exactly once when the block throws" {
      withRedisVariants.forEach { (name, call) ->
        withClue(name) {
          val client = connectedClient()
          withClientFactoryReturning(client) {
            shouldThrow<IllegalStateException> {
              call("redis://cache.example.com:6379") { error("simulated block failure") }
            }
          }

          verify(exactly = 1) { client.close() }
        }
      }
    }

    // A REDIS_URL in the environment replaces the built-in default, so there is nothing to check then.
    "the withRedis family connects to the default url when none is given".config(
      enabled = System.getenv("REDIS_URL") == null,
    ) {
      val urls: MutableList<String> = []
      withClientFactoryReturning(connectedClient(), urls) {
        withRedis { "r" } shouldBe "r"
        withNonNullRedis { "n" } shouldBe "n"
        withSuspendingRedis { "sr" } shouldBe "sr"
        withSuspendingNonNullRedis { "sn" } shouldBe "sn"
      }

      urls shouldBe List(4) { "redis://user:none@localhost:6379" }
    }

    // A client that fails its ping is closed before the block runs. If closing fails too, that failure is kept as
    // a suppressed exception of the ping failure, which is the one logged.
    "a client that fails its ping is closed, and a failure to close it is logged as suppressed" {
      val pingFailure = JedisConnectionException("simulated ping failure")
      val closeFailure = JedisException("simulated close failure")
      val client = mockk<RedisClient>()
      every { client.ping() } throws pingFailure
      every { client.close() } throws closeFailure

      capturingRedisLogs { logs ->
        withClientFactoryReturning(client) {
          withRedis(redisUrl = "redis://cache.example.com:6379", printStackTrace = true) { c ->
            c shouldBe null
            "null-branch"
          } shouldBe "null-branch"
        }

        val event = logs().filter { it.level == Level.ERROR }.shouldHaveSize(1).single()
        val logged = event.throwableProxy.shouldBeInstanceOf<ThrowableProxy>().throwable
        logged shouldBeSameInstanceAs pingFailure
        logged.suppressed.toList() shouldBe [closeFailure]
      }
      verify(exactly = 1) { client.close() }
    }

    "withNonNullRedis closes a client that fails its ping and skips the block" {
      val client = mockk<RedisClient>()
      every { client.ping() } throws JedisConnectionException("simulated ping failure")
      every { client.close() } just Runs

      withClientFactoryReturning(client) {
        withNonNullRedis(redisUrl = "redis://cache.example.com:6379") { "should-not-run" } shouldBe null
      }

      verify(exactly = 1) { client.close() }
    }

    // newRedisClient() pool sizing falls back to system properties before hard-coded defaults

    "newRedisClient reads pool sizing from system properties" {
      System.setProperty(RedisUtils.REDIS_MAX_POOL_SIZE, "7")
      System.setProperty(RedisUtils.REDIS_MAX_IDLE_SIZE, "4")
      System.setProperty(RedisUtils.REDIS_MIN_IDLE_SIZE, "2")
      System.setProperty(RedisUtils.REDIS_MAX_WAIT_SECS, "3")
      try {
        val client = RedisUtils.newRedisClient(redisUrl = "redis://localhost:1")
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

    // With no properties set, each pool setting falls back to its documented default.
    "newRedisClient uses the documented pool defaults" {
      listOf(
        RedisUtils.REDIS_MAX_POOL_SIZE,
        RedisUtils.REDIS_MAX_IDLE_SIZE,
        RedisUtils.REDIS_MIN_IDLE_SIZE,
        RedisUtils.REDIS_MAX_WAIT_SECS,
      ).forEach { System.getProperty(it) shouldBe null }

      RedisUtils.newRedisClient(redisUrl = "redis://localhost:1").use { client ->
        client.pool.maxTotal shouldBe 10
        client.pool.maxIdle shouldBe 5
        client.pool.minIdle shouldBe 1
        client.pool.maxWaitDuration shouldBe Duration.ofSeconds(1)
      }
    }
  }
}
