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

// DEPRECATION: the evaluator probe reads the deprecated public engine to observe context resets.
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "DEPRECATION", "InjectDispatcher")

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.script.ScriptException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

// A pool of one evaluator that the test can inspect after it is returned.
private class ProbeEvaluatorPool : AbstractExprEvaluatorPool<KotlinExprEvaluator>(1) {
  lateinit var evaluator: KotlinExprEvaluator

  init {
    populate { KotlinExprEvaluator().also { evaluator = it } }
  }
}

/**
 * Characterization tests for the borrow/recycle and context-reset contracts of [AbstractScriptPool]
 * and [AbstractExprEvaluatorPool], exercised through the concrete [KotlinScriptPool] /
 * [KotlinExprEvaluatorPool]. A pool size of 1 is used deliberately: any failure to recycle (on
 * success OR on exception) drains the pool, which a subsequent borrow would expose. The pool contracts that need no
 * compiler are tested in script-utils-common's `AbstractEnginePoolTests`.
 *
 * Kotest test bodies are already suspending, so the pool's `suspend` APIs are called directly. Each
 * body is wrapped in [withTimeout] so that a recycle regression (which would otherwise suspend the
 * next `channel.receive()` forever) fails fast as a [kotlinx.coroutines.TimeoutCancellationException]
 * instead of hanging until the CI wall-clock kills the job.
 */
class ScriptPoolContractTests : StringSpec() {
  init {
    "script pool of size 1 can be reused repeatedly (recycle on success)" {
      withTimeout(TIMEOUT_MS.milliseconds) {
        val pool = KotlinScriptPool(size = 1, nullGlobalContext = false)
        repeat(5) { i ->
          pool.eval { eval("$i + 1") } shouldBe i + 1
        }
      }
    }

    "script pool recycles the instance and resets context when the eval block throws" {
      withTimeout(TIMEOUT_MS.milliseconds) {
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

    "script pool resets context between borrows (a var added in one eval is gone in the next)" {
      withTimeout(TIMEOUT_MS.milliseconds) {
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

    "script pool isEmpty is true while borrowed and false after recycle" {
      withTimeout(TIMEOUT_MS.milliseconds) {
        val pool = KotlinScriptPool(size = 1, nullGlobalContext = false)
        pool.isEmpty shouldBe false

        pool.eval {
          pool.isEmpty shouldBe true // the sole instance is borrowed for the duration of the block
          eval("1 + 1")
        } shouldBe 2

        pool.isEmpty shouldBe false
      }
    }

    "expr pool of size 1 can be reused repeatedly (recycle on success)" {
      withTimeout(TIMEOUT_MS.milliseconds) {
        val pool = KotlinExprEvaluatorPool(size = 1)
        repeat(5) {
          pool.eval("1 > 0") shouldBe true
        }
      }
    }

    "expr pool still recycles the evaluator when eval throws" {
      withTimeout(TIMEOUT_MS.milliseconds) {
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

    "a borrower cancelled just as an instance is recycled does not shrink the pool" {
      val waiterThread = Executors.newSingleThreadExecutor()
      val waiterDispatcher = waiterThread.asCoroutineDispatcher()
      waiterDispatcher.use { waiterDispatcher ->
        withTimeout(TIMEOUT_MS.milliseconds) {
          val pool = KotlinScriptPool(size = 1, nullGlobalContext = false)
          val borrowed = CountDownLatch(1)
          val release = CountDownLatch(1)
          val holder =
            launch(Dispatchers.IO) {
              pool.eval {
                borrowed.countDown()
                release.await()
              }
            }
          withContext(Dispatchers.IO) { borrowed.await() }

          // UNDISPATCHED runs the waiter up to its first suspension, so it is already waiting for the only instance
          // when launch returns; it resumes on its own thread. A delay could not guarantee that.
          val waiterRan = AtomicBoolean(false)
          val waiter =
            launch(waiterDispatcher, start = CoroutineStart.UNDISPATCHED) {
              pool.eval { waiterRan.store(true) }
            }
          waiter.isActive shouldBe true

          // Occupy the waiter's only thread, so its resumption is queued instead of run.
          val busy = CountDownLatch(1)
          waiterThread.execute { busy.await() }
          release.countDown()
          holder.join() // the instance has been handed to the suspended waiter
          waiter.cancel() // cancelled before its queued resumption runs
          busy.countDown()
          waiter.join()

          // The waiter was cancelled without running its block, so the instance it was handed was never used.
          waiter.isCancelled shouldBe true
          waiterRan.load() shouldBe false
          pool.eval { eval("1 + 1") } shouldBe 2
        }
      }
    }

    "pools reject a size that is not positive" {
      shouldThrow<IllegalArgumentException> { KotlinScriptPool(size = 0, nullGlobalContext = false) }
      shouldThrow<IllegalArgumentException> { KotlinExprEvaluatorPool(size = -1) }
    }

    "expr pool resets an evaluator's context when it is returned" {
      withTimeout(TIMEOUT_MS.milliseconds) {
        val pool = ProbeEvaluatorPool()
        val before = pool.evaluator.engine.context
        pool.eval("1 > 0") shouldBe true
        (pool.evaluator.engine.context === before) shouldBe false
      }
    }

    "closing a pool fails later borrows" {
      withTimeout(TIMEOUT_MS.milliseconds) {
        val scripts = KotlinScriptPool(size = 1, nullGlobalContext = false)
        scripts.close()
        shouldThrow<ClosedReceiveChannelException> { scripts.eval { eval("1") } }

        val evaluators = KotlinExprEvaluatorPool(size = 1)
        evaluators.close()
        shouldThrow<ClosedReceiveChannelException> { evaluators.eval("1 > 0") }
      }
    }
  }

  companion object {
    // Generous enough for the JSR-223 engine init/compile, but bounded so a broken recycle fails
    // fast instead of hanging.
    private const val TIMEOUT_MS = 30_000L
  }
}
