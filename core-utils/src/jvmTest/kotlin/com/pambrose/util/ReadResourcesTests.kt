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

import com.pambrose.common.util.MiscFuncs
import com.pambrose.common.util.ReadResources.readResourceFile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import java.net.ServerSocket

class ReadResourcesTests : StringSpec() {
  init {
    "readResourceFile returns content of an existing classpath resource" {
      readResourceFile("test-banner.txt").shouldNotBeEmpty()
    }

    "readResourceFile throws IllegalArgumentException for missing resource" {
      shouldThrow<IllegalArgumentException> {
        readResourceFile("definitely-not-on-the-classpath.bogus")
      }
    }

    "MiscFuncs.waitForPortAvailable returns immediately for a free port" {
      // Bind to find a free port, then close it so the port is unbound,
      // then verify waitForPortAvailable returns without retrying.
      val freePort = ServerSocket(0).use { it.localPort }
      val start = System.currentTimeMillis()
      MiscFuncs.waitForPortAvailable(port = freePort, maxAttempts = 3, delayMs = 1000)
      val elapsed = System.currentTimeMillis() - start
      // Should be far below maxAttempts * delayMs (3000ms) since the port is already free.
      (elapsed < 1000) shouldBe true
    }

    "MiscFuncs.waitForPortAvailable gives up after maxAttempts when port stays bound" {
      ServerSocket(0).use { occupied ->
        val start = System.currentTimeMillis()
        MiscFuncs.waitForPortAvailable(port = occupied.localPort, maxAttempts = 2, delayMs = 10)
        val elapsed = System.currentTimeMillis() - start
        // Should at least sleep maxAttempts * delayMs and then return (logging a warning).
        (elapsed >= 20) shouldBe true
      }
    }
  }
}
