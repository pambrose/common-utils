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

package com.pambrose.util

import com.pambrose.common.concurrent.ConditionalBoolean
import com.pambrose.common.concurrent.ConditionalValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class ConditionalTests : StringSpec() {
  init {
    "simple bools" {
      val results: MutableList<Int> = []
      val mutex = Mutex()
      val jobs: MutableList<Job> = []
      val bool1 = ConditionalBoolean(false)
      val bool2 = ConditionalBoolean(false)
      val bool3 = ConditionalBoolean(false)

      jobs += launch {
        bool1.waitUntilTrue()
        mutex.withLock { results.add(1) }
      }

      jobs += launch {
        bool2.waitUntilTrue()
        mutex.withLock { results.add(2) }
      }

      jobs += launch {
        bool3.waitUntilTrue()
        mutex.withLock { results.add(3) }
      }

      yield()
      bool3.set(true)
      bool1.set(true)
      bool2.set(true)

      jobs.joinAll()

      results shouldContainExactlyInAnyOrder [1, 2, 3]
    }

    "list bools" {
      val mutex = Mutex()
      val jobs: MutableList<Job> = []
      val results: MutableList<Int> = []
      val expected: MutableList<Int> = []
      val bools = List(1000) { it to ConditionalBoolean(false) }

      for ((id, bool) in bools) {
        jobs +=
          launch {
            bool.waitUntilTrue()
            mutex.withLock { results.add(id) }
          }
      }
      for ((id, bool) in bools.shuffled()) {
        bool.set(true)
        expected += id
      }

      jobs.joinAll()

      results shouldContainExactlyInAnyOrder expected
    }

    "multi int listeners" {
      val mutex = Mutex()
      val jobs: MutableList<Job> = []
      val results: MutableList<Int> = []
      val expected: MutableList<Int> = []
      val vals = List(1000) { it to ConditionalValue(-1) }

      for ((id, cv) in vals.shuffled()) {
        jobs +=
          launch {
            cv.waitUntil { it == id }
            mutex.withLock { results.add(id) }
          }
      }
      for ((id, cv) in vals.shuffled()) {
        cv.set(id)
        expected += id
      }

      jobs.joinAll()

      results shouldContainExactlyInAnyOrder expected
    }

    "multi list listeners" {
      val mutex = Mutex()
      val jobs: MutableList<Job> = []
      val results: MutableList<Int> = []
      val expected: MutableList<Int> = []
      val listVals: MutableList<Int> = []
      val vals = List(1000) { it to ConditionalValue(emptyList<Int>()) }

      for ((id, cv) in vals.shuffled()) {
        jobs +=
          launch {
            cv.waitUntil { id in it }
            mutex.withLock { results.add(id) }
          }
      }
      for ((id, cv) in vals.shuffled()) {
        listVals += id
        cv.set(listVals)
        expected += id
      }

      jobs.joinAll()

      results shouldContainExactlyInAnyOrder expected
    }

    "wait until false returns immediately when already false" {
      ConditionalBoolean(false).waitUntilFalse() shouldBe true
    }

    "wait until false resumes when value becomes false" {
      val bool = ConditionalBoolean(true)
      var result: Boolean? = null

      val job = launch {
        result = bool.waitUntilFalse(5.seconds)
      }

      yield()
      bool.set(false)
      job.join()

      result shouldBe true
    }

    "get returns the current value" {
      val cv = ConditionalValue(1)
      cv.get() shouldBe 1

      cv.set(5)
      cv.get() shouldBe 5
    }

    "a zero timeout still reports a condition that already holds" {
      ConditionalBoolean(true).waitUntilTrue(Duration.ZERO) shouldBe true
      ConditionalBoolean(false).waitUntilFalse(Duration.ZERO) shouldBe true
      ConditionalValue(5).waitUntil(Duration.ZERO) { it == 5 } shouldBe true
      ConditionalValue(5).waitUntil(Duration.ZERO) { it == 6 } shouldBe false
    }

    "a sub-millisecond timeout still reports a condition that already holds" {
      ConditionalBoolean(true).waitUntilTrue(500.microseconds) shouldBe true
      ConditionalValue("ready").waitUntil(1.nanoseconds) { it == "ready" } shouldBe true
    }

    "setting the same mutable list again after changing it wakes a waiter" {
      val list: MutableList<Int> = [1]
      val cv = ConditionalValue<List<Int>>(list)
      var result: Boolean? = null
      val job = launch { result = cv.waitUntil(2.seconds) { 5 in it } }
      delay(50.milliseconds)

      list += 5
      cv.set(list)
      job.join()

      result shouldBe true
    }

    "the demo main functions are not shipped with ConditionalValue" {
      runCatching { Class.forName("com.pambrose.common.concurrent.ConditionalValueKt") }.isFailure shouldBe true
    }

    "set can be called from code that is not suspending" {
      val ready = ConditionalBoolean(false)
      var result: Boolean? = null
      val job = launch { result = ready.waitUntilTrue(2.seconds) }
      delay(50.milliseconds)

      thread { ready.set(true) }.join()
      job.join()

      result shouldBe true
    }
  }
}
