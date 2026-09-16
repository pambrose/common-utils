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
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import java.net.ServerSocket
import java.net.URLClassLoader
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

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

    "readResourceFile falls back to core-utils' classloader when there is no context classloader" {
      withoutContextClassLoader {
        readResourceFile("test-banner.txt").shouldNotBeEmpty()
      }
    }

    "MiscFuncs.waitForPortAvailable returns true immediately for a free port" {
      // Bind to find a free port, then close it so the port is unbound,
      // then verify waitForPortAvailable returns without retrying.
      val freePort = ServerSocket(0).use { it.localPort }
      val start = System.currentTimeMillis()
      MiscFuncs.waitForPortAvailable(port = freePort, maxAttempts = 3, delayMs = 60_000) shouldBe true
      val elapsed = System.currentTimeMillis() - start
      // A single retry would sleep for a minute, so half that is a generous bound even on a slow runner.
      (elapsed < 30_000) shouldBe true
    }

    "MiscFuncs.waitForPortAvailable keeps retrying until the port is released" {
      val occupied = ServerSocket(0)
      val port = occupied.localPort
      val releasing = CountDownLatch(1)
      thread(isDaemon = true) {
        Thread.sleep(100)
        // Counted down first, so the latch is already open by the time the port can be bound.
        releasing.countDown()
        occupied.close()
      }
      MiscFuncs.waitForPortAvailable(port = port, maxAttempts = 500, delayMs = 20) shouldBe true
      // The port was bound when the wait started, so it succeeded only after retrying past the release.
      releasing.count shouldBe 0L
    }

    // A port outside 0..65535 can never be bound. It used to be retried like a busy port and reported as
    // "still in use" once maxAttempts ran out.
    "MiscFuncs.waitForPortAvailable rejects an invalid port" {
      shouldThrow<IllegalArgumentException> {
        MiscFuncs.waitForPortAvailable(port = 70_000, maxAttempts = 2, delayMs = 1)
      }
      shouldThrow<IllegalArgumentException> { MiscFuncs.waitForPortAvailable(port = -1, maxAttempts = 2, delayMs = 1) }
    }

    "MiscFuncs.waitForPortAvailable with default arguments returns for a free port" {
      // Bind to find a free port, then close it so the port is unbound. With the port
      // already free, the first attempt succeeds and the defaults never trigger a retry.
      val freePort = ServerSocket(0).use { it.localPort }
      MiscFuncs.waitForPortAvailable(freePort) shouldBe true
      // The port is still bindable after the call returns
      ServerSocket(freePort).use { it.localPort shouldBe freePort }
    }

    "MiscFuncs.waitForPortAvailable returns false after maxAttempts when the port stays bound" {
      ServerSocket(0).use { occupied ->
        val start = System.currentTimeMillis()
        MiscFuncs.waitForPortAvailable(port = occupied.localPort, maxAttempts = 2, delayMs = 10) shouldBe false
        val elapsed = System.currentTimeMillis() - start
        // Should at least sleep maxAttempts * delayMs before reporting that the port never freed up.
        (elapsed >= 20) shouldBe true
      }
    }

    "readResourceFile finds a resource through the thread context classloader" {
      val dir = tempdir("resource-loader")
      dir.resolve("context-only.txt").writeText("from the context loader")

      withIsolatedContextClassLoader(dir) {
        readResourceFile("context-only.txt") shouldBe "from the context loader"
      }
    }

    "readResourceFile accepts an explicit classloader" {
      val dir = tempdir("resource-explicit")
      dir.resolve("explicit.txt").writeText("explicit")

      URLClassLoader(arrayOf(dir.toURI().toURL()), null).use { loader ->
        readResourceFile("explicit.txt", loader) shouldBe "explicit"
      }
    }
  }
}
