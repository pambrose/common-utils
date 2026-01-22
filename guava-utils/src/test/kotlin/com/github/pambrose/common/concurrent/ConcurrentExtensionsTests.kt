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

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore

class ConcurrentExtensionsTests {
  @Test
  fun countDownLatchIsFinishedTest() {
    val latch = CountDownLatch(2)
    latch.isFinished shouldBe false

    latch.countDown()
    latch.isFinished shouldBe false

    latch.countDown()
    latch.isFinished shouldBe true
  }

  @Test
  fun countDownLatchCountDownWithBlockTest() {
    val latch = CountDownLatch(1)
    var blockExecuted = false

    latch.countDown {
      blockExecuted = true
    }

    blockExecuted shouldBe true
    latch.isFinished shouldBe true
  }

  @Test
  fun countDownLatchCountDownWithExceptionTest() {
    val latch = CountDownLatch(1)
    var exceptionThrown = false

    try {
      latch.countDown {
        throw RuntimeException("Test exception")
      }
    } catch (e: RuntimeException) {
      exceptionThrown = true
    }

    exceptionThrown shouldBe true
    latch.isFinished shouldBe true
  }

  @Test
  fun semaphoreWithLockTest() {
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

  @Test
  fun semaphoreWithLockExceptionTest() {
    val semaphore = Semaphore(1)
    var exceptionThrown = false

    try {
      semaphore.withLock {
        throw RuntimeException("Test exception")
      }
    } catch (e: RuntimeException) {
      exceptionThrown = true
    }

    exceptionThrown shouldBe true
    semaphore.availablePermits() shouldBe 1
  }

  @Test
  fun threadWithLatchTest() {
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
