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

package com.pambrose.util

import com.pambrose.common.util.random
import com.pambrose.common.util.times
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class NumberExtensionTests : StringSpec() {
  init {
    "short test" {
      val cnt: Short = 1000.toShort()
      var runs: Short = 0
      var index: Short = 0
      cnt times {
        index shouldBe it
        index++
        runs++
      }
      cnt shouldBe runs
    }

    "int test" {
      val cnt = 100000
      var runs = 0
      var index = 0
      cnt times {
        index shouldBe it
        index++
        runs++
      }
      cnt shouldBe runs
    }

    "long test" {
      val cnt = 100000L
      var runs = 0L
      var index = 0L
      cnt times {
        index shouldBe it
        index++
        runs++
      }
      cnt shouldBe runs
    }

    "int random test" {
      repeat(1_000) {
        val v = 10.random()
        (v in 0 until 10) shouldBe true
      }
      // An upper bound of 1 leaves only one possible value
      repeat(100) { 1.random() shouldBe 0 }
    }

    "long random test" {
      repeat(1_000) {
        val v = 10L.random()
        (v in 0L until 10L) shouldBe true
      }
      repeat(100) { 1L.random() shouldBe 0L }
    }
  }
}
