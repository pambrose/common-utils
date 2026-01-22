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

package com.github.pambrose.concurrent

import com.github.pambrose.common.concurrent.Atomic
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class AtomicTests {
  @Test
  fun basicAtomicValueTest() {
    val atomic = Atomic(0)
    atomic.value shouldBe 0
    atomic.value = 42
    atomic.value shouldBe 42
  }

  @Test
  fun setWithLockTest() {
    runBlocking {
      val atomic = Atomic(0)

      val result = atomic.setWithLock { it + 10 }
      result shouldBe 10
      atomic.value shouldBe 10
    }
  }

  @Test
  fun withLockTest() {
    runBlocking {
      val atomic = Atomic("hello")

      val length = atomic.withLock { length }
      length shouldBe 5
      atomic.value shouldBe "hello"
    }
  }

  @Test
  fun concurrentAccessTest() {
    runBlocking {
      val atomic = Atomic(0)
      val iterations = 1000

      // Launch multiple coroutines that increment the value
      val jobs =
        (1..iterations).map {
          launch {
            atomic.setWithLock { it + 1 }
          }
        }

      jobs.forEach { it.join() }

      atomic.value shouldBe iterations
    }
  }

  @Test
  fun atomicWithComplexTypeTest() {
    runBlocking {
      data class Counter(
        val count: Int,
        val name: String,
      )

      val atomic = Atomic(Counter(0, "test"))

      atomic.setWithLock { Counter(it.count + 1, it.name) }
      atomic.value.count shouldBe 1
      atomic.value.name shouldBe "test"

      val name = atomic.withLock { name.uppercase() }
      name shouldBe "TEST"
    }
  }
}
