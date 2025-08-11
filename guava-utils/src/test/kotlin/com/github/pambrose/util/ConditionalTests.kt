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

package com.github.pambrose.util

import com.github.pambrose.common.concurrent.ConditionalBoolean
import com.github.pambrose.common.concurrent.ConditionalValue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

class ConditionalTests {
  @Test
  fun simpleBools() {
    val results = mutableListOf<Int>()
    val mutex = Mutex()
    val jobs = mutableListOf<Job>()
    val bool1 = ConditionalBoolean(false)
    val bool2 = ConditionalBoolean(false)
    val bool3 = ConditionalBoolean(false)

    runBlocking {
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

      jobs.forEach { it.join() }

      results shouldBe listOf(3, 1, 2)
    }
  }

  @Test
  fun listBools() {
    val mutex = Mutex()
    val jobs = mutableListOf<Job>()
    val results = mutableListOf<Int>()
    val expected = mutableListOf<Int>()
    val bools = List(1000) { it to ConditionalBoolean(false) }

    runBlocking {
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

      jobs.forEach { it.join() }

      results shouldBe expected
    }
  }

  @Test
  fun multiIntListeners() {
    val mutex = Mutex()
    val jobs = mutableListOf<Job>()
    val results = mutableListOf<Int>()
    val expected = mutableListOf<Int>()
    val vals = List(1000) { it to ConditionalValue(-1) }

    runBlocking {
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

      jobs.forEach { it.join() }

      results shouldBe expected
    }
  }

  @Test
  fun multiListListeners() {
    val mutex = Mutex()
    val jobs = mutableListOf<Job>()
    val results = mutableListOf<Int>()
    val expected = mutableListOf<Int>()
    val listVals = mutableListOf<Int>()
    val vals = List(1000) { it to ConditionalValue(emptyList<Int>()) }

    runBlocking {
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

      jobs.forEach { it.join() }

      results shouldBe expected
    }
  }
}
