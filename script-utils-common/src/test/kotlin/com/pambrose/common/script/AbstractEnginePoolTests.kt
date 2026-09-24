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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "InjectDispatcher")

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import javax.script.ScriptException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.update
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the borrow, recycle, and close contracts that [AbstractEnginePool] gives every pool, run on
 * [FakeEvaluatorPool] so that no compiler starts. Each body that could suspend forever on a broken recycle is wrapped
 * in [withTimeout], so such a regression fails instead of hanging.
 */
class AbstractEnginePoolTests : StringSpec() {
  init {
    "a pool rejects a size that is not positive" {
      shouldThrow<IllegalArgumentException> { FakeEvaluatorPool(size = 0) }.message shouldBe
        "Pool size must be positive, but was 0"
      shouldThrow<IllegalArgumentException> { FakeEvaluatorPool(size = -1) }
    }

    "a pool whose construction fails closes the instances it already created" {
      val created: MutableList<FakeEvaluator> = []
      shouldThrow<IllegalStateException> { FakeEvaluatorPool(size = 3, created = created, failAt = 2) }
      created.map { it.closed } shouldBe [true, true]
    }

    "a failure to close an instance during failed construction is attached to the original exception" {
      val created: MutableList<FakeEvaluator> = []
      val e =
        shouldThrow<IllegalStateException> {
          FakeEvaluatorPool(size = 3, created = created, failAt = 2, failClose = true)
        }
      e.message shouldBe "cannot create evaluator 2"
      // Every instance is still closed, even though closing the first one failed.
      created.map { it.closed } shouldBe [true, true]
      e.suppressed.map { it.message } shouldBe ["cannot close evaluator 0", "cannot close evaluator 1"]
    }

    // A fresh Kotlin REPL costs several times an evaluation, so resetting on every return made pools slower than a lone
    // evaluator. The context is replaced on reset, so its identity shows when resets happen.
    "an evaluator's context is reset every resetEvery returns" {
      withTimeout(TIMEOUT) {
        val pool = FakeEvaluatorPool(size = 1, resetEvery = 3)
        val contexts = List(7) { pool.borrow { evaluator -> evaluator.fakeEngine.context } }
        contexts.map { System.identityHashCode(it) }.distinct().size shouldBe 3
        (contexts[0] === contexts[2]) shouldBe true
        (contexts[2] === contexts[3]) shouldBe false
        (contexts[3] === contexts[5]) shouldBe true
        (contexts[5] === contexts[6]) shouldBe false
      }
    }

    // The reset failure used to replace the block's exception entirely.
    "when the block and the reset both fail, the block's exception propagates with the reset failure suppressed" {
      withTimeout(TIMEOUT) {
        val pool = FakeEvaluatorPool(size = 1)
        pool.onReset = { throw IllegalStateException("reset failed") }
        val thrown =
          shouldThrow<IllegalArgumentException> { pool.borrow { throw IllegalArgumentException("block failed") } }
        thrown.message shouldBe "block failed"
        thrown.suppressed.map { it.message } shouldBe ["reset failed"]
      }
    }

    "resetEvery must be positive" {
      shouldThrow<IllegalArgumentException> { FakeEvaluatorPool(size = 1, resetEvery = 0) }.message shouldBe
        "resetEvery must be positive, but was 0"
    }

    "closing a pool closes its instances and fails later borrows" {
      withTimeout(TIMEOUT) {
        val pool = FakeEvaluatorPool(size = 2)
        pool.close()
        pool.created.map { it.closed } shouldBe [true, true]
        shouldThrow<ClosedReceiveChannelException> { pool.eval("true") }
      }
    }

    "an instance returned after its pool is closed is closed" {
      withTimeout(TIMEOUT) {
        val pool = FakeEvaluatorPool(size = 1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        pool.created.single().onCheck = { holdUntil(entered, release) }
        val borrower = launch(Dispatchers.IO) { pool.eval("true") }
        withContext(Dispatchers.IO) { entered.await() }

        pool.close()
        pool.created.single().closed shouldBe false

        release.countDown()
        borrower.join()
        pool.created.single().closed shouldBe true
      }
    }

    "a borrower waiting when the pool is closed fails instead of waiting forever" {
      withTimeout(TIMEOUT) {
        val pool = FakeEvaluatorPool(size = 1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        pool.created.single().onCheck = { holdUntil(entered, release) }
        val holder = launch(Dispatchers.IO) { pool.eval("true") }
        withContext(Dispatchers.IO) { entered.await() }

        // UNDISPATCHED runs the waiter up to its first suspension, so it is already waiting for the only instance.
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { runCatching { pool.eval("true") } }
        waiter.isActive shouldBe true

        pool.close()
        waiter.await().exceptionOrNull().shouldBeInstanceOf<ClosedReceiveChannelException>()

        release.countDown()
        holder.join()
        pool.created.single().closed shouldBe true
      }
    }

    "an instance whose reset throws is still returned to the pool" {
      withTimeout(TIMEOUT) {
        val pool = FakeEvaluatorPool(size = 1)
        pool.onReset = { throw IllegalStateException("reset failed") }
        shouldThrow<IllegalStateException> { pool.eval("true") }.message shouldBe "reset failed"

        // A size-1 pool that lost its instance would suspend here until the timeout.
        pool.onReset = {}
        pool.eval("true") shouldBe true
        pool.created.single().closed shouldBe false
        pool.isEmpty shouldBe false
      }
    }

    "an instance is reset and returned when the borrower's block throws" {
      withTimeout(TIMEOUT) {
        val pool = FakeEvaluatorPool(size = 1)
        val resets = AtomicInt(0)
        pool.onReset = { resets.incrementAndFetch() }
        shouldThrow<ScriptException> { pool.eval("unknown") }
        resets.load() shouldBe 1
        pool.borrow { it.index } shouldBe 0
        resets.load() shouldBe 2
      }
    }

    "a pool never lends more instances at once than its size" {
      withTimeout(TIMEOUT) {
        val size = 4
        val pool = FakeEvaluatorPool(size)
        val active = AtomicInt(0)
        val peak = AtomicInt(0)
        val finished = AtomicInt(0)
        // Each borrower waits until size borrowers hold an instance at once. A pool that caps concurrency at size
        // therefore reaches exactly size, and all the borrowers finish because their number is a multiple of size.
        val barrier = CyclicBarrier(size)
        coroutineScope {
          repeat(size * 5) {
            launch(Dispatchers.IO) {
              pool.borrow {
                val now = active.incrementAndFetch()
                peak.update { highest -> maxOf(highest, now) }
                barrier.await(BARRIER_TIMEOUT_SECS, TimeUnit.SECONDS)
                active.decrementAndFetch()
              }
              finished.incrementAndFetch()
            }
          }
        }
        peak.load() shouldBe size
        finished.load() shouldBe size * 5
        pool.isEmpty shouldBe false
      }
    }
  }

  companion object {
    private val TIMEOUT = 30.seconds
    private const val BARRIER_TIMEOUT_SECS = 10L

    // Signals that the evaluator is borrowed, then keeps it borrowed until released.
    private fun holdUntil(
      entered: CountDownLatch,
      release: CountDownLatch,
    ) {
      entered.countDown()
      release.await(BARRIER_TIMEOUT_SECS, TimeUnit.SECONDS)
    }
  }
}
