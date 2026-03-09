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

package com.github.pambrose.common.concurrent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore

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
  }
}
