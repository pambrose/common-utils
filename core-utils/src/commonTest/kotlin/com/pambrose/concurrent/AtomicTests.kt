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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "InjectDispatcher")

package com.pambrose.concurrent

import com.pambrose.common.concurrent.Atomic
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class AtomicTests : StringSpec() {
  init {
    "basic atomic value test" {
      val atomic = Atomic(0)
      atomic.value shouldBe 0

      atomic.setWithLock { 42 }
      atomic.value shouldBe 42
    }

    "set with lock test" {
      val atomic = Atomic(0)

      val result = atomic.setWithLock { it + 10 }
      result shouldBe 10
      atomic.value shouldBe 10
    }

    "with lock test" {
      val atomic = Atomic("hello")

      val length = atomic.withLock { length }
      length shouldBe 5
      atomic.value shouldBe "hello"
    }

    // Each update suspends between reading and writing the value, so without the mutex the coroutines interleave
    // and lose updates, on single-threaded JS as well as on the multi-threaded platforms.
    "concurrent setWithLock calls lose no updates" {
      val atomic = Atomic(0)
      withContext(Dispatchers.Default) {
        List(1_000) {
          launch {
            atomic.setWithLock { current ->
              yield()
              current + 1
            }
          }
        }.joinAll()
      }
      atomic.value shouldBe 1_000
    }

    // The writer starts only once the reader is inside withLock, and gets the reader's whole delay to run if
    // withLock does not actually hold the mutex.
    "withLock keeps writers out until its action finishes" {
      val atomic = Atomic(0)
      val readerHoldsLock = CompletableDeferred<Unit>()
      withContext(Dispatchers.Default) {
        val reader =
          launch {
            atomic.withLock {
              val seen = this
              readerHoldsLock.complete(Unit)
              delay(100.milliseconds)
              atomic.value shouldBe seen
            }
          }
        readerHoldsLock.await()
        val writer = launch { atomic.setWithLock { it + 1 } }
        joinAll(reader, writer)
      }
      atomic.value shouldBe 1
    }

    "atomic with complex type test" {
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
