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

import com.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs the real client path (build the client, ping over the wire, run the block, close the client) against
 * [FakeRedisServer], so the connection that is opened and closed is a real one and no Redis server is needed.
 */
class RedisConnectionTests : StringSpec() {
  init {
    connectionTests()
    cancellationTests()
  }

  private fun connectionTests() {
    "each withRedis variant connects to the url, pings, runs the block and closes the connection" {
      withRedisVariants.forEach { (name, call) ->
        withClue(name) {
          FakeRedisServer().use { server ->
            val result =
              call(server.url) { client ->
                client.ping() shouldBe "PONG"
                server.open.load() shouldBeGreaterThan 0
                "ran"
              }

            result shouldBe "ran"
            // One ping checks the connection before the block runs; the other is the block's own.
            server.pings.load() shouldBe 2
            eventually(5.seconds) { server.open.load() shouldBe 0 }
          }
        }
      }
    }

    "each withRedis variant closes the connection when the block throws" {
      withRedisVariants.forEach { (name, call) ->
        withClue(name) {
          FakeRedisServer().use { server ->
            shouldThrow<IllegalStateException> {
              call(server.url) { error("simulated block failure") }
            }

            server.accepted.load() shouldBeGreaterThan 0
            eventually(5.seconds) { server.open.load() shouldBe 0 }
          }
        }
      }
    }

    "a pooled client connects to the url's host and port and closes its connections" {
      FakeRedisServer().use { server ->
        RedisUtils.newRedisClient(redisUrl = server.url, maxPoolSize = 1).use { client ->
          client.withNonNullRedisPool { it.ping() } shouldBe "PONG"
          server.accepted.load() shouldBeGreaterThan 0
        }

        eventually(5.seconds) { server.open.load() shouldBe 0 }
      }
    }
  }

  private fun cancellationTests() {
    // Cancellation cannot interrupt the blocking connection check, so it finished and connected, and then
    // withContext threw instead of returning the client: nothing closed it, and its connection stayed open.
    "cancelling a suspending withRedis variant while it connects closes the connection" {
      suspendingWithRedisVariants.forEach { (name, call) ->
        withClue(name) {
          val pingGate = CountDownLatch(1)
          FakeRedisServer(pingGate).use { server ->
            val blockRuns = AtomicInt(0)
            coroutineScope {
              val connecting =
                launch {
                  call(server.url) {
                    blockRuns.incrementAndFetch()
                    "ran"
                  }
                }
              withTimeout(10.seconds) { server.pingArrived.await() }
              connecting.cancel()
              pingGate.countDown()
              connecting.join()
            }

            blockRuns.load() shouldBe 0
            server.pings.load() shouldBe 1
            eventually(5.seconds) { server.open.load() shouldBe 0 }
          }
        }
      }
    }
  }
}
