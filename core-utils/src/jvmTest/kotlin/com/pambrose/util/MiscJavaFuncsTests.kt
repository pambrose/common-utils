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

import com.pambrose.common.util.MiscJavaFuncs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MiscJavaFuncsTests : StringSpec() {
  init {
    "random(int) returns a value in [0, upper) over many iterations" {
      val upper = 100
      repeat(100_000) {
        val v = MiscJavaFuncs.random(upper)
        (v in 0 until upper) shouldBe true
      }
    }

    "random(long) returns a value in [0, upper) over many iterations" {
      val upper = 100L
      repeat(100_000) {
        val v = MiscJavaFuncs.random(upper)
        (v in 0L until upper) shouldBe true
      }
    }

    "random(int) with an upper bound of 1 always returns 0" {
      repeat(1_000) { MiscJavaFuncs.random(1) shouldBe 0 }
    }

    "random(long) with an upper bound of 1 always returns 0" {
      repeat(1_000) { MiscJavaFuncs.random(1L) shouldBe 0L }
    }

    // Bounded RNG rejects non-positive bounds with IllegalArgumentException.
    // Previously random(0) threw ArithmeticException ("/ by zero").
    "random(int) rejects an upper bound of 0" {
      shouldThrow<IllegalArgumentException> { MiscJavaFuncs.random(0) }
    }

    "random(long) rejects an upper bound of 0" {
      shouldThrow<IllegalArgumentException> { MiscJavaFuncs.random(0L) }
    }

    // Previously a negative bound was silently accepted (returned a value in [0, |upper|)).
    "random(int) rejects a negative upper bound" {
      shouldThrow<IllegalArgumentException> { MiscJavaFuncs.random(-10) }
    }

    "random(long) rejects a negative upper bound" {
      shouldThrow<IllegalArgumentException> { MiscJavaFuncs.random(-10L) }
    }

    // When Thread.sleep throws InterruptedException it clears the interrupt flag; sleepMillis
    // must restore it so callers higher up can still observe the interruption.
    "sleepMillis restores the thread interrupt flag when interrupted" {
      Thread.interrupted() // clear any leaked flag first
      // Setting the flag makes the subsequent Thread.sleep throw immediately and clear it.
      Thread.currentThread().interrupt()

      MiscJavaFuncs.sleepMillis(50)

      // Thread.interrupted() reads-and-clears: asserts the flag was restored and cleans up
      // so the state does not leak into other tests on this thread.
      Thread.interrupted() shouldBe true
    }

    "sleepMillis leaves the interrupt flag clear on the normal path" {
      Thread.interrupted() // defensive cleanup
      MiscJavaFuncs.sleepMillis(1)
      Thread.interrupted() shouldBe false
    }
  }
}
