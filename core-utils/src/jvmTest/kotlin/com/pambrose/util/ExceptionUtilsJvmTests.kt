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

import com.pambrose.common.util.runCatchingInterruptible
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import java.util.concurrent.CountDownLatch

class ExceptionUtilsJvmTests : StringSpec() {
  init {
    resultTests()
    rethrowTests()
  }

  private fun resultTests() {
    "runCatchingInterruptible returns success" {
      val result = runCatchingInterruptible { 42 }
      result.getOrNull() shouldBe 42
    }

    "runCatchingInterruptible wraps other exceptions" {
      val result = runCatchingInterruptible { error("boom") }
      result.exceptionOrNull()!!.message shouldBe "boom"
    }
  }

  private fun rethrowTests() {
    "runCatchingInterruptible rethrows InterruptedException" {
      shouldThrow<InterruptedException> {
        runCatchingInterruptible { throw InterruptedException("interrupt me") }
      }.message shouldBe "interrupt me"
    }

    "runCatchingInterruptible rethrows CancellationException" {
      shouldThrow<CancellationException> {
        runCatchingInterruptible { throw CancellationException("cancel me") }
      }.message shouldBe "cancel me"
    }

    "runCatchingInterruptible propagates an interruption of the current thread" {
      try {
        Thread.currentThread().interrupt()
        shouldThrow<InterruptedException> {
          runCatchingInterruptible { CountDownLatch(1).await() }
        }
      } finally {
        // await clears the flag when it throws; clear it here too in case it never ran.
        Thread.interrupted()
      }
    }
  }
}
