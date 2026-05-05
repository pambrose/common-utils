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

package com.pambrose.common.utils

import io.grpc.Server
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ServerExtensionsTests : StringSpec() {
  init {
    "shutdownGracefully invokes shutdown, awaitTermination, and shutdownNow in order" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } returns true

      server.shutdownGracefully(500L, TimeUnit.MILLISECONDS)

      verifyOrder {
        server.shutdown()
        server.awaitTermination(500L, TimeUnit.MILLISECONDS)
        server.shutdownNow()
      }
    }

    "shutdownGracefully always calls shutdownNow even when awaitTermination throws" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } throws InterruptedException("boom")

      shouldThrow<InterruptedException> {
        server.shutdownGracefully(100L, TimeUnit.MILLISECONDS)
      }

      verify(exactly = 1) { server.shutdown() }
      verify(exactly = 1) { server.shutdownNow() }
    }

    "shutdownGracefully(Duration) converts to milliseconds" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } returns true

      server.shutdownGracefully(2.seconds)

      verify(exactly = 1) { server.awaitTermination(2_000L, TimeUnit.MILLISECONDS) }
    }

    "shutdownGracefully rejects non-positive timeout" {
      val server = mockk<Server>(relaxed = true)
      shouldThrow<IllegalArgumentException> {
        server.shutdownGracefully(0L, TimeUnit.MILLISECONDS)
      }
      shouldThrow<IllegalArgumentException> {
        server.shutdownGracefully(-1L, TimeUnit.MILLISECONDS)
      }
    }

    "shutdownGracefully(Duration) of zero throws IllegalArgumentException" {
      val server = mockk<Server>(relaxed = true)
      shouldThrow<IllegalArgumentException> {
        server.shutdownGracefully(0.milliseconds)
      }
    }
  }
}
