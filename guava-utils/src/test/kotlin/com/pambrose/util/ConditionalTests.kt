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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

class ConditionalTests : StringSpec() {
  init {
    "simple bools" {
      val results = mutableListOf<Int>()
      val mutex = Mutex()
      val jobs = mutableListOf<Job>()
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

      jobs.forEach { it.join() }

      results shouldBe listOf(3, 1, 2)
    }

    "list bools" {
      val mutex = Mutex()
      val jobs = mutableListOf<Job>()
      val results = mutableListOf<Int>()
      val expected = mutableListOf<Int>()
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

      jobs.forEach { it.join() }

      results shouldBe expected
    }

    "multi int listeners" {
      val mutex = Mutex()
      val jobs = mutableListOf<Job>()
      val results = mutableListOf<Int>()
      val expected = mutableListOf<Int>()
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

      jobs.forEach { it.join() }

      results shouldBe expected
    }

    "multi list listeners" {
      val mutex = Mutex()
      val jobs = mutableListOf<Job>()
      val results = mutableListOf<Int>()
      val expected = mutableListOf<Int>()
      val listVals = mutableListOf<Int>()
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

      jobs.forEach { it.join() }

      results shouldBe expected
    }
  }
}
