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

package com.pambrose.common.concurrent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.URLClassLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ConcurrentExtensionsTests : StringSpec() {
  init {
    "count down latch is finished" {
      val latch = CountDownLatch(2)
      latch.isFinished shouldBe false

      latch.countDown()
      latch.isFinished shouldBe false

      latch.countDown()
      latch.isFinished shouldBe true
    }

    "count down latch count down with block" {
      val latch = CountDownLatch(1)
      var blockExecuted = false

      latch.countDown {
        blockExecuted = true
      }

      blockExecuted shouldBe true
      latch.isFinished shouldBe true
    }

    "count down latch count down with exception" {
      val latch = CountDownLatch(1)

      shouldThrow<RuntimeException> {
        latch.countDown {
          throw RuntimeException("Test exception")
        }
      }

      latch.isFinished shouldBe true
    }

    "semaphore with lock" {
      val semaphore = Semaphore(1)
      var blockExecuted = false

      val result = semaphore.withLock {
        blockExecuted = true
        42
      }

      blockExecuted shouldBe true
      result shouldBe 42
      semaphore.availablePermits() shouldBe 1
    }

    "semaphore with lock exception" {
      val semaphore = Semaphore(1)

      shouldThrow<RuntimeException> {
        semaphore.withLock {
          throw RuntimeException("Test exception")
        }
      }

      semaphore.availablePermits() shouldBe 1
    }

    "count down latch await with duration returns true when reached" {
      val latch = CountDownLatch(1)
      latch.countDown()

      latch.await(1.seconds) shouldBe true
    }

    "count down latch await with duration returns false on timeout" {
      val latch = CountDownLatch(1)

      latch.await(50.milliseconds) shouldBe false
      latch.isFinished shouldBe false
    }

    "thread with latch" {
      val latch = CountDownLatch(1)
      var threadExecuted = false

      val t = thread(latch, start = true) {
        threadExecuted = true
      }

      t.join()
      latch.await()

      threadExecuted shouldBe true
      latch.isFinished shouldBe true
    }

    "thread with latch starts the thread by default, as a non-daemon" {
      val latch = CountDownLatch(1)
      val ran = AtomicBoolean(false)

      val t = thread(latch) { ran.store(true) }

      t.isDaemon shouldBe false
      latch.await(HANG_GUARD_SECONDS, TimeUnit.SECONDS) shouldBe true
      ran.load() shouldBe true
    }

    "thread with latch applies its options and waits for start when start is false" {
      val latch = CountDownLatch(1)
      val loader = URLClassLoader(arrayOf(), null)

      val t =
        thread(
          latch,
          start = false,
          isDaemon = true,
          contextClassLoader = loader,
          name = "latched-worker",
          priority = Thread.MIN_PRIORITY,
        ) {}

      t.state shouldBe Thread.State.NEW
      t.name shouldBe "latched-worker"
      t.isDaemon shouldBe true
      t.contextClassLoader shouldBe loader
      t.priority shouldBe Thread.MIN_PRIORITY
      latch.count shouldBe 1L

      t.start()
      latch.await(HANG_GUARD_SECONDS, TimeUnit.SECONDS) shouldBe true
    }

    "thread with latch counts down even when the block throws" {
      val latch = CountDownLatch(1)
      val uncaught = CompletableFuture<Throwable>()

      val t = thread(latch, start = false) { error("boom") }
      t.setUncaughtExceptionHandler { _, e -> uncaught.complete(e) }
      t.start()

      latch.await(HANG_GUARD_SECONDS, TimeUnit.SECONDS) shouldBe true
      uncaught.getGuarded().message shouldBe "boom"
    }

    // An interrupted acquire throws before the permit is taken, so withLock must not release one it never held.
    "semaphore with lock leaves the permits alone when the acquire is interrupted" {
      val semaphore = Semaphore(1)
      val blockRan = AtomicBoolean(false)

      val (_, outcome) =
        inDaemonThread {
          Thread.currentThread().interrupt()
          runCatching { semaphore.withLock { blockRan.store(true) } }
        }

      outcome.getGuarded().exceptionOrNull().shouldBeInstanceOf<InterruptedException>()
      blockRan.load() shouldBe false
      semaphore.availablePermits() shouldBe 1
    }

    "count down latch await with a sub-millisecond duration waits for it instead of returning at once" {
      val latch = CountDownLatch(1)
      val mark = TimeSource.Monotonic.markNow()
      latch.await(800.microseconds) shouldBe false
      (mark.elapsedNow() >= 800.microseconds) shouldBe true
    }
  }
}
