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
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import redis.clients.jedis.HostAndPort
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
        // sslOptions, not the deprecated ssl flag, is what turns TLS on in jedis 8.
        RedisUtils.clientConfig("REDISS://localhost:6379").sslOptions shouldNotBe null
        RedisUtils.clientConfig("redis://localhost:6379").sslOptions shouldBe null
      } finally {
        Locale.setDefault(previous)
      }
    }

    // URI reports a missing port as -1, which jedis tried to connect to, so every connection failed even with a
    // server listening on 6379.
    "a url without a port connects to 6379" {
      RedisUtils.hostAndPort("redis://cache.example.com") shouldBe HostAndPort("cache.example.com", 6379)
      RedisUtils.hostAndPort("rediss://alice:secret@cache.example.com/2") shouldBe
        HostAndPort("cache.example.com", 6379)
      RedisUtils.hostAndPort("redis://cache.example.com:6380") shouldBe HostAndPort("cache.example.com", 6380)
    }

    // A URL that can never work is a configuration error, not a connection failure: it throws before the block
    // runs, rather than passing it null.
    "an invalid url throws IllegalArgumentException from every function that takes one" {
      mapOf(
        "redis://localhost:6379/abc" to "For input string: \"abc\"",
        "redis://localhost:6379/0?protocol=9" to "Unknown protocol 9",
        // The index in the message varies between JDKs.
        "redis://local host:6379" to "Malformed Redis URL: Illegal character in authority at index ",
        // Read as scheme "localhost" with no host, which jedis would have sent to 127.0.0.1.
        "localhost:6379" to "Redis URL has no host",
        "redis://:6379" to "Redis URL has no host",
      ).forEach { (url, message) ->
        withClue(url) {
          val blockRuns = AtomicInt(0)
          listOf(
            shouldThrow<IllegalArgumentException> { withRedis(url) { blockRuns.incrementAndFetch() } },
            shouldThrow<IllegalArgumentException> { withNonNullRedis(url) { blockRuns.incrementAndFetch() } },
            shouldThrow<IllegalArgumentException> { withSuspendingRedis(url) { blockRuns.incrementAndFetch() } },
            shouldThrow<IllegalArgumentException> {
              withSuspendingNonNullRedis(url) { blockRuns.incrementAndFetch() }
            },
            shouldThrow<IllegalArgumentException> { RedisUtils.newRedisClient(url) },
          ).forEach { it.message.shouldNotBeNull() shouldStartWith message }
          blockRuns.load() shouldBe 0
        }
      }
    }

    // The URL can carry a password, and so can the message of the URISyntaxException that rejects it.
    "a malformed url's exception does not repeat the password" {
      val exception = shouldThrow<IllegalArgumentException> { withRedis("redis://alice:s3cret@bad host:6379") { } }

      exception.message shouldNotContain "s3cret"
      exception.cause shouldBe null
    }

    // commons-pool2 treats -1 as unlimited and 0 as a pool that can never lend a connection.
    "a pool size of zero is rejected" {
      shouldThrow<IllegalArgumentException> { RedisUtils.newRedisClient(maxPoolSize = 0) }
    }

    "a pool size of -1 means unlimited and is accepted" {
      val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = -1)
      client.use { client ->
        client.pool.maxTotal shouldBe -1
      }
    }

    // Building a client does not report an unreachable server, so the withRedis family has to check the connection.
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

    // Pool exhaustion and borrow-validation failures arrive as a plain JedisException, and rejected
    // credentials as JedisAccessControlException; both are connection failures from the caller's point of view.
    "pool helpers take the null path for any Jedis failure" {
      listOf(
        JedisException("Could not get a resource from the pool"),
        JedisAccessControlException("WRONGPASS invalid username-password pair"),
      ).forEach { failure ->
        val client = mockk<RedisClient>()
        every { client.ping() } throws failure

        client.withRedisPool {
          it shouldBe null
          "null-branch"
        } shouldBe "null-branch"
        client.withNonNullRedisPool { "should-not-reach" } shouldBe null
      }
    }
  }
}
