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
import com.pambrose.common.redis.RedisUtils.withRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingRedis
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import redis.clients.jedis.HostAndPort

private const val SCHEME_MSG = "Redis URL scheme must be redis or rediss"
private const val PORT_MSG = "Redis URL port must be a number from 1 to 65535"
private const val HOST_MSG =
  "Redis URL host could not be parsed; host names may contain only letters, digits, '-' and '.'"

/**
 * Pins which Redis URLs are accepted, how the credentials in one are read, and that the message rejecting one
 * repeats none of it. The parser is reached through the internal seams, and the one URL that could connect is
 * pointed at [FakeRedisServer], so nothing here needs a Redis server.
 */
class RedisUrlTests : StringSpec() {
  init {
    schemeTests()
    portTests()
    credentialTests()
  }

  // Every function that takes a URL, each of which has to throw before it builds a client or runs a block.
  private suspend fun rejectionsOf(url: String): List<IllegalArgumentException> {
    val blockRuns = AtomicInt(0)
    val rejections =
      listOf(
        shouldThrow<IllegalArgumentException> { withRedis(url) { blockRuns.incrementAndFetch() } },
        shouldThrow<IllegalArgumentException> { withNonNullRedis(url) { blockRuns.incrementAndFetch() } },
        shouldThrow<IllegalArgumentException> { withSuspendingRedis(url) { blockRuns.incrementAndFetch() } },
        shouldThrow<IllegalArgumentException> { withSuspendingNonNullRedis(url) { blockRuns.incrementAndFetch() } },
        shouldThrow<IllegalArgumentException> { RedisUtils.newRedisClient(url) },
        shouldThrow<IllegalArgumentException> { RedisUtils.clientConfig(url) },
        shouldThrow<IllegalArgumentException> { RedisUtils.hostAndPort(url) },
      )
    blockRuns.load() shouldBe 0
    return rejections
  }

  // Every function rejects [url] with [message], and no exception in the cause chain holds any of [secrets]. The
  // suspending variants' exceptions can carry a copy of the original as their cause (coroutine stack trace recovery).
  private suspend fun shouldReject(
    url: String,
    message: String,
    vararg secrets: String,
  ) = withClue(url) {
    rejectionsOf(url).forEach { e ->
      e.message shouldBe message
      generateSequence<Throwable>(e) { it.cause }.forEach { t ->
        secrets.forEach { t.message.shouldNotBeNull() shouldNotContain it }
      }
    }
  }

  private fun schemeTests() {
    // clientConfig() enables TLS only for rediss, so each of these connected in plaintext and sent the password
    // unencrypted, instead of failing.
    "a scheme other than redis or rediss is rejected by every function that takes a url" {
      listOf(
        "tls://alice:s3cret@localhost:6379",
        "redis+tls://alice:s3cret@localhost:6379",
        "http://alice:s3cret@localhost:6379",
        "rediss2://alice:s3cret@localhost:6379",
        // No scheme at all.
        "//alice:s3cret@localhost:6379",
      ).forEach { url -> shouldReject(url, SCHEME_MSG, "alice", "s3cret") }
    }

    "the redis and rediss schemes are accepted in any case, and only rediss enables tls" {
      mapOf(
        "redis" to false,
        "REDIS" to false,
        "Redis" to false,
        "rediss" to true,
        "REDISS" to true,
        "rEdIsS" to true,
      ).forEach { (scheme, tls) ->
        withClue(scheme) {
          val url = "$scheme://alice:s3cret@cache.example.com:6380"
          RedisUtils.hostAndPort(url) shouldBe HostAndPort("cache.example.com", 6380)
          (RedisUtils.clientConfig(url).sslOptions != null) shouldBe tls
        }
      }
    }

    // The client used to connect, and send AUTH alice s3cret, in plaintext.
    "a tls url never opens a connection" {
      FakeRedisServer().use { server ->
        shouldThrow<IllegalArgumentException> { withRedis("tls://alice:s3cret@$LOOPBACK:${server.port}") { "ran" } }
          .message shouldBe SCHEME_MSG
        server.accepted.load() shouldBe 0
      }
    }
  }

  private fun portTests() {
    // URI accepts these, and jedis failed only on connecting, which withRedis reported as a connection failure.
    "a port outside 1 to 65535 is rejected by every function that takes a url" {
      listOf(
        "redis://localhost:0",
        "redis://localhost:65536",
        "redis://localhost:99999",
        // Too large for an Int, so URI reports no host at all.
        "redis://localhost:99999999999",
      ).forEach { url -> shouldReject(url, PORT_MSG) }
    }

    // URI reports no host when the port is not a number, which got the message for a host it cannot parse.
    "a port that is not a number gets a port message rather than a host message" {
      listOf(
        "redis://localhost:abc",
        "redis://localhost:+80",
        "redis://localhost:-1",
        "rediss://alice:s3cret@localhost:abc",
      ).forEach { url -> shouldReject(url, PORT_MSG, "alice", "s3cret") }
    }

    // An unescaped '/' ends the authority, so URI reads the start of the password as the port.
    "a port message does not repeat a password that URI read as the port" {
      shouldReject("redis://alice:123456/x@localhost:6379", PORT_MSG, "123456")
      shouldReject("redis://alice:s3cret/x@localhost:6379", PORT_MSG, "s3cret")
    }

    "the boundary ports are accepted, and a missing port means 6379" {
      RedisUtils.hostAndPort("redis://localhost:1") shouldBe HostAndPort("localhost", 1)
      RedisUtils.hostAndPort("redis://localhost:65535") shouldBe HostAndPort("localhost", 65535)
      RedisUtils.hostAndPort("redis://localhost:6380") shouldBe HostAndPort("localhost", 6380)
      RedisUtils.hostAndPort("redis://localhost") shouldBe HostAndPort("localhost", 6379)
      RedisUtils.hostAndPort("redis://localhost:") shouldBe HostAndPort("localhost", 6379)
    }

    "a host that URI cannot parse keeps its own message when the port is valid" {
      shouldReject("redis://redis_cache:6379", HOST_MSG)
      shouldReject("redis://alice:s3cret@redis_cache", HOST_MSG, "alice", "s3cret")
    }
  }

  private fun credentialTests() {
    // URI.getUserInfo() decodes before the split, so an escaped ':' in the user name split it in two.
    "an escaped colon in the user name stays in the user name" {
      RedisUtils.clientConfig("redis://tenant%3Aalice:s3cret@localhost:6379").apply {
        user shouldBe "tenant:alice"
        password shouldBe "s3cret"
      }
    }

    // The text after the escaped ':' became a password, and the start of the user name was sent along with it.
    "an escaped colon in a user name with no password does not make up a password" {
      RedisUtils.clientConfig("redis://tenant%3Aalice@localhost:6379").apply {
        user shouldBe null
        password shouldBe null
      }
    }

    "escapes in the user name and password are decoded once" {
      mapOf(
        "alice%40corp:p%40ss" to ("alice@corp" to "p@ss"),
        "alice:p%3Ass%3Aword" to ("alice" to "p:ss:word"),
        "alice%2B1:p%2Bss" to ("alice+1" to "p+ss"),
        "al%20ice:p%20ss" to ("al ice" to "p ss"),
        "alice%2540:p%2540" to ("alice%40" to "p%40"),
      ).forEach { (userInfo, credentials) ->
        withClue(userInfo) {
          RedisUtils.clientConfig("rediss://$userInfo@localhost:6379").apply {
            user shouldBe credentials.first
            password shouldBe credentials.second
          }
        }
      }
    }

    // URLDecoder on its own would turn a literal '+' into a space; a URI keeps it.
    "a literal plus in the user name and password is kept" {
      RedisUtils.clientConfig("redis://al+ice:p+ss@localhost:6379").apply {
        user shouldBe "al+ice"
        password shouldBe "p+ss"
      }
    }
  }
}
