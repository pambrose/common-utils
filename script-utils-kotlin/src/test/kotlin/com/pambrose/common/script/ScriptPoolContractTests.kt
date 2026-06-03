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

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import javax.script.ScriptException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Characterization tests for the borrow/recycle and context-reset contracts of [AbstractScriptPool]
 * and [AbstractExprEvaluatorPool], exercised through the concrete [KotlinScriptPool] /
 * [KotlinExprEvaluatorPool]. A pool size of 1 is used deliberately: any failure to recycle (on
 * success OR on exception) drains the pool, which a subsequent borrow would expose.
 *
 * Each body is wrapped in [withTimeout] so that a recycle regression (which would otherwise suspend
 * the next `channel.receive()` forever) fails fast as a [kotlinx.coroutines.TimeoutCancellationException]
 * instead of hanging until the CI wall-clock kills the job.
 */
class ScriptPoolContractTests : StringSpec() {
  init {
    "script pool of size 1 can be reused repeatedly (recycle on success)" {
      runBlocking {
        withTimeout(TIMEOUT_MS) {
          val pool = KotlinScriptPool(size = 1, nullGlobalContext = false)
          repeat(5) { i ->
            pool.eval { eval("$i + 1") } shouldBe i + 1
          }
        }
      }
    }

    "script pool recycles the instance and resets context when the eval block throws" {
      runBlocking {
        withTimeout(TIMEOUT_MS) {
          val pool = KotlinScriptPool(size = 1, nullGlobalContext = false)

          shouldThrow<RuntimeException> {
            pool.eval {
              add("leak", 1) // mutate the context before throwing, to test reset on the exception path
              throw RuntimeException("boom")
            }
          }

          // The single instance was returned (else the next borrow would hang)...
          pool.isEmpty shouldBe false
          // ...and resetContext ran on recycle even though the block threw, so "leak" is unbound.
          shouldThrow<ScriptException> { pool.eval { eval("leak") } }
          pool.eval { eval("40 + 2") } shouldBe 42
        }
      }
    }

    "script pool resets context between borrows (a var added in one eval is gone in the next)" {
      runBlocking {
        withTimeout(TIMEOUT_MS) {
          val pool = KotlinScriptPool(size = 1, nullGlobalContext = false)

          pool.eval {
            add("x", 99)
            eval("x")
          } shouldBe 99

          shouldThrow<ScriptException> {
            pool.eval { eval("x") }
          }
        }
      }
    }

    "script pool isEmpty is true while borrowed and false after recycle" {
      runBlocking {
        withTimeout(TIMEOUT_MS) {
          val pool = KotlinScriptPool(size = 1, nullGlobalContext = false)
          pool.isEmpty shouldBe false

          pool.eval {
            pool.isEmpty shouldBe true // the sole instance is borrowed for the duration of the block
            eval("1 + 1")
          } shouldBe 2

          pool.isEmpty shouldBe false
        }
      }
    }

    "expr pool of size 1 can be reused repeatedly (recycle on success)" {
      runBlocking {
        withTimeout(TIMEOUT_MS) {
          val pool = KotlinExprEvaluatorPool(size = 1)
          repeat(5) {
            pool.eval("1 > 0") shouldBe true
          }
        }
      }
    }

    "expr pool still recycles the evaluator when eval throws" {
      runBlocking {
        withTimeout(TIMEOUT_MS) {
          val pool = KotlinExprEvaluatorPool(size = 1)

          // A non-boolean result -> AbstractExprEvaluator.eval throws IllegalArgumentException.
          shouldThrow<IllegalArgumentException> {
            pool.eval("1 + 2")
          }

          // The evaluator was recycled despite the throw; the pool is not drained.
          pool.isEmpty shouldBe false
          pool.eval("1 > 0") shouldBe true
        }
      }
    }
  }

  companion object {
    // Generous enough for the JSR-223 engine init/compile, but bounded so a broken recycle fails
    // fast instead of hanging.
    private const val TIMEOUT_MS = 30_000L
  }
}
