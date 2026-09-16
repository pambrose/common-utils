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

import com.pambrose.common.util.HostInfo
import com.pambrose.common.util.captureStdout
import com.pambrose.common.util.hostInfo
import com.pambrose.common.util.randomId
import com.pambrose.common.util.repeatWithSleep
import com.pambrose.common.util.resolveHostInfo
import com.pambrose.common.util.sleep
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

// The common helpers (isNull, lpad, rpad, capitalizeFirstChar) are covered on every platform by MiscFuncsTests.
class MiscFuncsJvmTests : StringSpec() {
  init {
    "resolveHostInfo reports the looked-up host's name and address from a single lookup" {
      var lookups = 0
      val info =
        resolveHostInfo {
          lookups++
          InetAddress.getByAddress("build-box", byteArrayOf(10, 0, 0, 7))
        }
      info shouldBe HostInfo("build-box", "10.0.0.7")
      lookups shouldBe 1
    }

    // Sandboxed hosts often cannot resolve their own name.
    "resolveHostInfo falls back to Unknown when the local host name does not resolve" {
      resolveHostInfo { throw UnknownHostException("no such host") } shouldBe HostInfo("Unknown", "Unknown")
    }

    // Compared with a second resolution rather than InetAddress.getLocalHost(), which throws on such hosts.
    "hostInfo is the resolved local host" {
      hostInfo shouldBe resolveHostInfo()
    }

    "random id default test" {
      val id = randomId()
      id shouldHaveLength 10
      id shouldMatch Regex("[a-zA-Z0-9]+")
    }

    "random id custom length test" {
      randomId(5) shouldHaveLength 5
      randomId(20) shouldHaveLength 20
      randomId(1) shouldHaveLength 1
    }

    "random id custom char pool test" {
      val numericId = randomId(10, ('0'..'9').toList())
      numericId shouldMatch Regex("[0-9]+")

      val lowercaseId = randomId(10, ('a'..'z').toList())
      lowercaseId shouldMatch Regex("[a-z]+")
    }

    "random id uniqueness test" {
      val ids = (1..100).map { randomId() }.toSet()
      ids.size shouldBeGreaterThan 95 // Should be mostly unique
    }

    "sleep blocks for at least the requested duration" {
      val start = System.currentTimeMillis()
      sleep(50.milliseconds)
      val elapsed = System.currentTimeMillis() - start
      elapsed shouldBeGreaterThanOrEqual 40L
    }

    "repeatWithSleep invokes the block with each iteration index" {
      val seen: MutableList<Int> = []
      repeatWithSleep(iterations = 3, sleepTime = 1.milliseconds) { i, _ -> seen += i }
      seen shouldBe [0, 1, 2]
    }

    "repeatWithSleep with the default sleepTime and zero iterations never invokes the block" {
      // Zero iterations exercises the default sleepTime argument without actually sleeping
      var calls = 0
      repeatWithSleep(iterations = 0) { _, _ -> calls++ }
      calls shouldBe 0
    }

    "repeatWithSleep passes the same startMillis to every iteration" {
      val before = System.currentTimeMillis()
      val startTimes = mutableSetOf<Long>()
      repeatWithSleep(iterations = 3, sleepTime = 1.milliseconds) { _, startMillis -> startTimes += startMillis }
      startTimes.size shouldBe 1
      (startTimes.single() >= before) shouldBe true
    }

    "captureStdout captures println output" {
      val out = captureStdout {
        println("hello captured")
      }
      out shouldContain "hello captured"
    }

    "captureStdout restores System.out even if the block throws" {
      val originalOut = System.out
      runCatching {
        captureStdout { error("boom") }
      }
      System.out shouldBe originalOut
    }

    // Measured from the last iteration rather than as a total, so scheduling delays on a busy runner cannot
    // push a correct run over the bound: a trailing sleep would add a full second here.
    "repeatWithSleep sleeps between iterations but not after the last one" {
      val iterationTimes: MutableList<Long> = []
      repeatWithSleep(iterations = 2, sleepTime = 1.seconds) { _, _ -> iterationTimes += System.nanoTime() }
      val returned = System.nanoTime()

      (iterationTimes[1] - iterationTimes[0]).nanoseconds shouldBeGreaterThanOrEqualTo 1.seconds
      (returned - iterationTimes[1]).nanoseconds shouldBeLessThan 500.milliseconds
    }

    "captureStdout decodes captured output as UTF-8" {
      captureStdout { print("h\u00E9llo \u2713") } shouldBe "h\u00E9llo \u2713"
    }
  }
}
