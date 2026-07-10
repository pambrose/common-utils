@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "InjectDispatcher")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// Test-only subclass exposing the protected waitForCondition with an arbitrary predicate.
private class IntWaiter(
  init: Int,
) : GenericValueWaiter<Int>(init) {
  val current get() = currValue

  suspend fun awaitValue(
    timeout: Duration = Duration.INFINITE,
    predicate: () -> Boolean,
  ) = waitForCondition(predicate, timeout)
}

class GenericValueWaiterTests : StringSpec() {
  init {
    "a satisfied waiter resumes promptly rather than stalling for the full timeout" {
      // Races registering a waiter against satisfying it, with a long timeout. If checkCondition cannot
      // see (and cancel) the timeout job when it removes the waiter, the satisfied wait stalls until the
      // timeout elapses because the structured coroutineScope waits for the orphaned delay.
      withContext(Dispatchers.Default) {
        repeat(200) {
          val waiter = BooleanWaiter(false)
          val job = launch { waiter.waitUntilTrue(5.seconds) }
          launch { waiter.setValue(true) }
          val mark = TimeSource.Monotonic.markNow()
          job.join()
          (mark.elapsedNow() < 2.seconds) shouldBe true
        }
      }
    }

    "a throwing predicate fails only its own waiter, not the others" {
      val waiter = IntWaiter(0)
      var goodResult: Boolean? = null
      var badError: Throwable? = null

      val good = launch { goodResult = waiter.awaitValue { waiter.current == 1 } }
      val bad =
        launch {
          runCatching { waiter.awaitValue { if (waiter.current == 1) error("boom") else false } }
            .onFailure { badError = it }
        }
      delay(50.milliseconds)

      waiter.checkCondition(1)
      good.join()
      bad.join()

      goodResult shouldBe true
      badError!!.message shouldContain "boom"
    }

    "multiple coroutines waiting on the same condition are all resumed" {
      val waiter = BooleanWaiter(false)
      val results = CopyOnWriteArrayList<Boolean>()

      val jobs =
        (1..3).map {
          launch { results += waiter.waitUntilTrue(1.seconds) }
        }

      delay(100.milliseconds)
      waiter.setValue(true)
      jobs.forEach { it.join() }

      // Every waiter must be resumed with true; the old single-slot callback resumed only the last.
      results.size shouldBe 3
      results.all { it } shouldBe true
    }

    "a waiting waitUntilTrue is not clobbered by a concurrent waitUntilFalse" {
      val waiter = BooleanWaiter(false)
      var aResult: Boolean? = null

      val a = launch { aResult = waiter.waitUntilTrue(1.seconds) }
      delay(50.milliseconds)

      // The value is already false, so this returns immediately. On the old code it overwrote the
      // single shared predicate, so the still-waiting waitUntilTrue then missed the value becoming true.
      waiter.waitUntilFalse(1.seconds) shouldBe true

      waiter.setValue(true)
      a.join()
      aResult shouldBe true
    }

    "BooleanWaiter setValue and waitUntilTrue work correctly" {
      val waiter = BooleanWaiter(false)
      var completed = false

      val job = launch {
        val result = waiter.waitUntilTrue(1.seconds)
        result shouldBe true
        completed = true
      }

      delay(50.milliseconds)
      completed shouldBe false

      waiter.setValue(true)
      job.join()
      completed shouldBe true
    }

    "BooleanWaiter waitUntilFalse returns when value becomes false" {
      val waiter = BooleanWaiter(true)
      var completed = false

      val job = launch {
        val result = waiter.waitUntilFalse(1.seconds)
        result shouldBe true
        completed = true
      }

      delay(50.milliseconds)
      completed shouldBe false

      waiter.setValue(false)
      job.join()
      completed shouldBe true
    }

    "BooleanWaiter waitUntilTrue times out if value never matches" {
      val waiter = BooleanWaiter(false)

      val result = waiter.waitUntilTrue(100.milliseconds)
      result shouldBe false
    }

    "BooleanWaiter waitUntilFalse times out if value never matches" {
      val waiter = BooleanWaiter(true)

      val result = waiter.waitUntilFalse(100.milliseconds)
      result shouldBe false
    }

    "BooleanWaiter waitUntilTrue returns immediately if already true" {
      val waiter = BooleanWaiter(false)
      waiter.setValue(true)

      // checkCondition has been called; the value changed from initValue
      // so monitorSatisfied (predicate: currValue != initValue) is true
      val result = waiter.waitUntilTrue(100.milliseconds)
      result shouldBe true
    }

    "BooleanWaiter checkCondition updates value" {
      val waiter = BooleanWaiter(false)
      waiter.checkCondition(true)

      val result = waiter.waitUntilTrue(100.milliseconds)
      result shouldBe true
    }

    "BooleanWaiter waitUntilTrue with the default timeout returns immediately when already true" {
      BooleanWaiter(true).waitUntilTrue() shouldBe true
    }

    "BooleanWaiter waitUntilFalse with the default timeout returns immediately when already false" {
      BooleanWaiter(false).waitUntilFalse() shouldBe true
    }

    "a cancelled waiter is deregistered and later updates remain safe" {
      val waiter = BooleanWaiter(false)

      val job = launch { waiter.waitUntilTrue() }
      delay(100.milliseconds) // let the waiter register and suspend
      job.cancelAndJoin()
      job.isCancelled shouldBe true

      // The cancelled waiter was removed on cancellation, so signaling now resumes no one
      // and must not fail, and new waits observe the updated value.
      waiter.setValue(true)
      waiter.waitUntilTrue(1.seconds) shouldBe true
    }

    "a throwing predicate with a finite timeout still fails its waiter promptly" {
      val waiter = IntWaiter(0)
      var badError: Throwable? = null

      val mark = TimeSource.Monotonic.markNow()
      val bad =
        launch {
          runCatching { waiter.awaitValue(5.seconds) { if (waiter.current == 1) error("kaboom") else false } }
            .onFailure { badError = it }
        }
      delay(50.milliseconds)

      waiter.checkCondition(1)
      bad.join()

      badError!!.message shouldContain "kaboom"
      // The armed timeout job was cancelled, so the waiter failed well before the 5s timeout.
      (mark.elapsedNow() < 2.seconds) shouldBe true
    }
  }
}
