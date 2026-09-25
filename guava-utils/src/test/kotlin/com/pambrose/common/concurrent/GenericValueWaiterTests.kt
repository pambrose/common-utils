@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "InjectDispatcher")

package com.pambrose.common.concurrent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.lang.reflect.Modifier
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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

// A waiter's timeout is an hour in the tests below that check it is cancelled, so a wait that stalls until the timeout
// fails this guard instead, however slow the machine.
private val hangGuard = HANG_GUARD_SECONDS.seconds

// Runs block with a scope of its own, cancelled afterwards. A waiter launched in it that fails or wrongly hangs then
// fails the assertion on it, instead of cancelling the test or holding it open.
private suspend fun <T> withDetachedScope(block: suspend (CoroutineScope) -> T): T {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  return try {
    block(scope)
  } finally {
    scope.cancel()
  }
}

// Waiters that must be registered before the test changes the value start with UNDISPATCHED: waitForCondition
// registers the waiter before it first suspends, so it is registered by the time launch or async returns, and no
// delay is needed to let it get there.
class GenericValueWaiterTests : StringSpec() {
  init {
    // Under a dispatcher that runs launch inline, the timeout job used to be launched before the waiter was
    // registered: a zero or negative delay fired, found nothing to remove, and the waiter then waited forever.
    "a zero or negative timeout returns false at once, even under a dispatcher that runs work inline" {
      listOf(Duration.ZERO, (-5).milliseconds).forEach { timeout ->
        withTimeout(hangGuard) {
          CoroutineScope(Dispatchers.Unconfined)
            .async(start = UNDISPATCHED) { BooleanWaiter(false).waitUntilTrue(timeout) }
            .await() shouldBe false
        }
        withTimeout(hangGuard) {
          CoroutineScope(Dispatchers.Unconfined)
            .async(start = UNDISPATCHED) { BooleanWaiter(true).waitUntilTrue(timeout) }
            .await() shouldBe true
        }
      }
    }

    "a satisfied waiter resumes promptly rather than stalling for the full timeout" {
      // Races registering a waiter against satisfying it, with a long timeout. If checkCondition cannot
      // see (and cancel) the timeout job when it removes the waiter, the satisfied wait stalls until the
      // timeout elapses because the structured coroutineScope waits for the orphaned delay.
      withDetachedScope { scope ->
        repeat(200) {
          val waiter = BooleanWaiter(false)
          val result = scope.async { waiter.waitUntilTrue(1.hours) }
          scope.launch { waiter.setValue(true) }
          withTimeout(hangGuard) { result.await() } shouldBe true
        }
      }
    }

    "a throwing predicate fails only its own waiter, not the others" {
      val waiter = IntWaiter(0)

      withDetachedScope { scope ->
        val good = scope.async(start = UNDISPATCHED) { waiter.awaitValue { waiter.current == 1 } }
        val bad =
          scope.async(start = UNDISPATCHED) {
            waiter.awaitValue { if (waiter.current == 1) error("boom") else false }
          }

        waiter.checkCondition(1)

        good.await() shouldBe true
        shouldThrow<IllegalStateException> { bad.await() }.message shouldContain "boom"
      }
    }

    "multiple coroutines waiting on the same condition are all resumed" {
      val waiter = BooleanWaiter(false)

      val results = (1..3).map { async(start = UNDISPATCHED) { waiter.waitUntilTrue() } }
      results.none { it.isCompleted } shouldBe true

      waiter.setValue(true)

      // Every waiter must be resumed with true; the old single-slot callback resumed only the last.
      results.awaitAll() shouldBe [true, true, true]
    }

    "a waiting waitUntilTrue is not clobbered by a concurrent waitUntilFalse" {
      val waiter = BooleanWaiter(false)

      val a = async(start = UNDISPATCHED) { waiter.waitUntilTrue() }

      // The value is already false, so this returns immediately. On the old code it overwrote the
      // single shared predicate, so the still-waiting waitUntilTrue then missed the value becoming true.
      waiter.waitUntilFalse() shouldBe true

      waiter.setValue(true)
      a.await() shouldBe true
    }

    "BooleanWaiter setValue and waitUntilTrue work correctly" {
      val waiter = BooleanWaiter(false)

      val result = async(start = UNDISPATCHED) { waiter.waitUntilTrue() }
      result.isActive shouldBe true

      waiter.setValue(true)
      result.await() shouldBe true
    }

    "BooleanWaiter waitUntilFalse returns when value becomes false" {
      val waiter = BooleanWaiter(true)

      val result = async(start = UNDISPATCHED) { waiter.waitUntilFalse() }
      result.isActive shouldBe true

      waiter.setValue(false)
      result.await() shouldBe true
    }

    // Updates that leave the predicate false must keep the waiter registered, not resume or drop it. The waiter runs
    // unconfined, so a wrong resume would complete it inside checkCondition, before isActive is read. Its finite
    // timeout means a satisfied waiter also has a timeout job to cancel.
    "an update that does not satisfy a waiter leaves it waiting for one that does" {
      val waiter = IntWaiter(0)

      withDetachedScope { scope ->
        val result = scope.async(Dispatchers.Unconfined) { waiter.awaitValue(1.hours) { waiter.current == 3 } }

        waiter.checkCondition(1)
        waiter.checkCondition(2)
        result.isActive shouldBe true

        waiter.checkCondition(3)
        withTimeout(hangGuard) { result.await() } shouldBe true
      }
    }

    // The timeout fires while checkCondition holds the lock evaluating a predicate that turns out true. The timeout
    // must then find the waiter already removed and leave it alone; resuming it again would fail with
    // "Already resumed".
    "a timeout that fires while a satisfying predicate runs does not resume the waiter twice" {
      val waiter = IntWaiter(0)
      val lock =
        GenericValueWaiter::class.java
          .getDeclaredField("lock")
          .apply { isAccessible = true }
          .get(waiter) as ReentrantLock
      val timeoutQueued = AtomicBoolean(false)

      withDetachedScope { scope ->
        // The detached scope runs on Dispatchers.Default, so the timeout job runs while this thread holds the lock.
        val result =
          scope.async(start = UNDISPATCHED) {
            waiter.awaitValue(50.milliseconds) {
              if (waiter.current == 1) {
                // Hold the lock until the timeout job is queued on it, rather than sleeping past the timeout.
                val mark = TimeSource.Monotonic.markNow()
                while (!lock.hasQueuedThreads() && mark.elapsedNow() < hangGuard) Thread.onSpinWait()
                timeoutQueued.store(lock.hasQueuedThreads())
                true
              } else {
                false
              }
            }
          }

        waiter.checkCondition(1)

        result.await() shouldBe true
      }
      timeoutQueued.load() shouldBe true
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

      // setValue already ran checkCondition, so the stored value satisfies the waitUntilTrue predicate
      // and the wait returns without suspending.
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

      val job = launch(start = UNDISPATCHED) { waiter.waitUntilTrue() }
      job.cancelAndJoin()
      job.isCancelled shouldBe true

      // The cancelled waiter was removed on cancellation, so signaling now resumes no one
      // and must not fail, and new waits observe the updated value.
      waiter.setValue(true)
      waiter.waitUntilTrue(1.seconds) shouldBe true
    }

    // With a finite timeout the wait also has a timeout job, which cancellation has to stop too: the structured
    // scope would otherwise keep the cancelled wait alive until the timeout elapsed.
    "a cancelled waiter with a finite timeout stops without waiting for the timeout" {
      val waiter = BooleanWaiter(false)

      withDetachedScope { scope ->
        val job = scope.launch(start = UNDISPATCHED) { waiter.waitUntilTrue(1.hours) }
        withTimeout(hangGuard) { job.cancelAndJoin() }
        job.isCancelled shouldBe true
      }

      waiter.setValue(true)
      waiter.waitUntilTrue(1.seconds) shouldBe true
    }

    "a throwing predicate with a finite timeout still fails its waiter promptly" {
      val waiter = IntWaiter(0)

      withDetachedScope { scope ->
        val bad =
          scope.async(start = UNDISPATCHED) {
            waiter.awaitValue(1.hours) { if (waiter.current == 1) error("kaboom") else false }
          }

        waiter.checkCondition(1)

        // The armed timeout job was cancelled, so the waiter failed without waiting out the hour.
        shouldThrow<IllegalStateException> { withTimeout(hangGuard) { bad.await() } }.message shouldBe "kaboom"
      }
    }

    "subclasses can read the monitored value but not assign it without notifying waiters" {
      val type = GenericValueWaiter::class.java
      type.declaredMethods.none { it.name == "setCurrValue" && !Modifier.isPrivate(it.modifiers) } shouldBe true
      Modifier.isVolatile(type.getDeclaredField("currValue").modifiers) shouldBe true
      type.declaredFields.none { it.name == "initValue" } shouldBe true
    }
  }
}
