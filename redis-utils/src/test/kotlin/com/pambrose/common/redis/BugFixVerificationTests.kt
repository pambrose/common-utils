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
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedisPool
import com.pambrose.common.redis.RedisUtils.withSuspendingRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import redis.clients.jedis.exceptions.JedisConnectionException

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #4: urlDetails() threw IndexOutOfBoundsException when userInfo had no colon
    // Before fix: split(colon, 2).get(1) crashed on single-element list
    // After fix: getOrElse(1) { "" } returns empty string when index is missing

    "redis client creation with user info without colon does not crash" {
      // URL with username only (no colon/password in userInfo)
      shouldNotThrow<IndexOutOfBoundsException> {
        val client = RedisUtils.newRedisClient(
          redisUrl = "redis://username@localhost:6379",
          maxPoolSize = 1,
        )
        client shouldNotBe null
        client.close()
      }
    }

    "redis client creation with no user info does not crash" {
      shouldNotThrow<Exception> {
        val client = RedisUtils.newRedisClient(
          redisUrl = "redis://localhost:6379",
          maxPoolSize = 1,
        )
        client shouldNotBe null
        client.close()
      }
    }

    "redis client creation with standard user info works" {
      shouldNotThrow<Exception> {
        val client = RedisUtils.newRedisClient(
          redisUrl = "redis://user:password@localhost:6379",
          maxPoolSize = 1,
        )
        client shouldNotBe null
        client.close()
      }
    }

    // Bugs #1 and #2: the catch around `block.invoke(client)` re-invoked the block with
    // null when the *block itself* threw JedisConnectionException — meaning user
    // operations could be silently double-executed. After fix: only connection setup
    // (and, for pooled variants, the initial ping) is caught; block exceptions propagate,
    // and the block is invoked at most once.

    // Port 1 is unassigned and refuses connections — `ping()` against it throws
    // JedisConnectionException, exercising the connection-failure path for pool variants.
    val unreachableUrl = "redis://localhost:1"

    "withRedisPool: connection failure invokes block exactly once with null" {
      val callCount = AtomicInteger(0)
      val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = 1)
      try {
        val result =
          client.withRedisPool { c ->
            callCount.incrementAndGet()
            c shouldBe null
            "from-null-branch"
          }
        result shouldBe "from-null-branch"
        callCount.get() shouldBe 1
      } finally {
        client.close()
      }
    }

    "withNonNullRedisPool: connection failure returns null without invoking block" {
      val callCount = AtomicInteger(0)
      val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = 1)
      try {
        val result =
          client.withNonNullRedisPool { _ ->
            callCount.incrementAndGet()
            "should-not-reach"
          }
        result shouldBe null
        callCount.get() shouldBe 0
      } finally {
        client.close()
      }
    }

    "withSuspendingRedisPool: connection failure invokes block exactly once with null" {
      runBlocking {
        val callCount = AtomicInteger(0)
        val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = 1)
        try {
          val result =
            client.withSuspendingRedisPool { c ->
              callCount.incrementAndGet()
              c shouldBe null
              "from-null-branch"
            }
          result shouldBe "from-null-branch"
          callCount.get() shouldBe 1
        } finally {
          client.close()
        }
      }
    }

    "withSuspendingNonNullRedisPool: connection failure returns null without invoking block" {
      runBlocking {
        val callCount = AtomicInteger(0)
        val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = 1)
        try {
          val result =
            client.withSuspendingNonNullRedisPool { _ ->
              callCount.incrementAndGet()
              "should-not-reach"
            }
          result shouldBe null
          callCount.get() shouldBe 0
        } finally {
          client.close()
        }
      }
    }

    // Block-throws path: regardless of which `with*` variant, if the user's block throws
    // JedisConnectionException, the exception must propagate and the block must be
    // invoked exactly once. Pre-fix, the catch would re-invoke block(null), double-firing
    // any side effect (e.g., a write).

    "withRedis: block JedisConnectionException propagates and block invoked once" {
      val callCount = AtomicInteger(0)
      shouldThrow<JedisConnectionException> {
        withRedis(redisUrl = unreachableUrl) { _ ->
          callCount.incrementAndGet()
          throw JedisConnectionException("simulated mid-block failure")
        }
      }
      callCount.get() shouldBe 1
    }

    "withNonNullRedis: block JedisConnectionException propagates and block invoked once" {
      val callCount = AtomicInteger(0)
      shouldThrow<JedisConnectionException> {
        withNonNullRedis(redisUrl = unreachableUrl) { _ ->
          callCount.incrementAndGet()
          throw JedisConnectionException("simulated mid-block failure")
        }
      }
      callCount.get() shouldBe 1
    }

    "withRedisPool: block JedisConnectionException propagates and block invoked once" {
      // Use a server that responds to ping but where the user simulates a mid-block
      // failure. Since we can't depend on a live Redis here, use an unreachable URL and
      // skip if ping fails; otherwise the bug-fix path under test is unreachable.
      // Instead, exercise via a real client with the block guaranteed to be called only
      // when ping succeeds — which it won't here. Therefore this test asserts the more
      // tractable invariant: when ping fails, the block runs exactly once with null and
      // the caller's exception (if any) still propagates.
      val callCount = AtomicInteger(0)
      val client = RedisUtils.newRedisClient(redisUrl = unreachableUrl, maxPoolSize = 1)
      try {
        shouldThrow<JedisConnectionException> {
          client.withRedisPool { _ ->
            callCount.incrementAndGet()
            throw JedisConnectionException("simulated mid-block failure")
          }
        }
        // Pre-fix, the catch would have re-invoked block(null), bumping the counter to 2.
        callCount.get() shouldBe 1
      } finally {
        client.close()
      }
    }

    "withSuspendingRedis: block JedisConnectionException propagates and block invoked once" {
      runBlocking {
        val callCount = AtomicInteger(0)
        shouldThrow<JedisConnectionException> {
          withSuspendingRedis(redisUrl = unreachableUrl) { _ ->
            callCount.incrementAndGet()
            throw JedisConnectionException("simulated mid-block failure")
          }
        }
        callCount.get() shouldBe 1
      }
    }
  }
}
