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

import com.pambrose.common.util.onFailureOrRethrow
import com.pambrose.common.util.onFailureRethrowCancellation
import com.pambrose.common.util.runCatchingCancellable
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException

class ExceptionUtilsTests : StringSpec() {
  init {
    "runCatchingCancellable returns success" {
      val result = runCatchingCancellable { 42 }
      result.isSuccess shouldBe true
      result.getOrNull() shouldBe 42
    }

    "runCatchingCancellable wraps non-cancellation exceptions" {
      val result = runCatchingCancellable { error("boom") }
      result.isFailure shouldBe true
      result.exceptionOrNull()!!.message shouldBe "boom"
    }

    "runCatchingCancellable rethrows CancellationException" {
      shouldThrow<CancellationException> {
        runCatchingCancellable { throw CancellationException("cancel me") }
      }
    }

    "onFailureRethrowCancellation invokes action for non-cancellation failures" {
      var seen: Throwable? = null
      val result = runCatching { error("regular failure") }
        .onFailureRethrowCancellation { seen = it }
      seen!!.message shouldBe "regular failure"
      result.isFailure shouldBe true
    }

    "onFailureRethrowCancellation rethrows CancellationException" {
      val r = runCatching<Unit> { throw CancellationException("cancelled") }
      shouldThrow<CancellationException> {
        r.onFailureRethrowCancellation { /* never called */ }
      }
    }

    "onFailureRethrowCancellation does nothing on success" {
      var called = false
      val r = runCatching { 1 }.onFailureRethrowCancellation { called = true }
      r.getOrNull() shouldBe 1
      called shouldBe false
    }

    "onFailureOrRethrow rethrows the specified type" {
      val r = runCatching<Unit> { throw IllegalStateException("ise") }
      shouldThrow<IllegalStateException> {
        r.onFailureOrRethrow<IllegalStateException, Unit> { /* never called */ }
      }
    }

    "onFailureOrRethrow invokes action for other types" {
      var seen: Throwable? = null
      val r = runCatching<Unit> { throw IllegalArgumentException("iae") }
        .onFailureOrRethrow<IllegalStateException, Unit> { seen = it }
      seen!!.message shouldBe "iae"
      r.isFailure shouldBe true
    }
  }
}
