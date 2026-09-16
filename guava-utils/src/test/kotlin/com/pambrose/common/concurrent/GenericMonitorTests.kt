@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// A monitor whose guard throws, to check that the guard's own exception reaches the caller.
private class ThrowingMonitor : GenericMonitor() {
  override val monitorSatisfied: Boolean get() = error("guard failed")

  val isHeldByCurrentThread get() = monitor.isOccupiedByCurrentThread

  fun <T> holding(block: () -> T): T = mutate(block)
}

// A monitor over a counter that changes only through mutate, so waiting threads re-check the guard.
private class CountingMonitor(
  private val target: Int,
) : GenericMonitor() {
  private var count = 0

  override val monitorSatisfied: Boolean get() = count >= target

  fun increment() = mutate { ++count }
}

// A BooleanMonitor that also reports whether any thread holds its monitor.
private class InspectableMonitor : GenericMonitor() {
  @Volatile
  private var value = false

  override val monitorSatisfied get() = value

  val isOccupied get() = monitor.isOccupied

  fun set(newValue: Boolean) = mutate { value = newValue }
}

// What a wait on another thread ended with: its result or exception, and whether the thread was left interrupted.
private class WaitOutcome<T>(
  val result: Result<T>,
  val interruptedAfter: Boolean,
)

private fun <T> waitOnDaemonThread(
  interruptFirst: Boolean = false,
  wait: () -> T,
) = inDaemonThread {
  if (interruptFirst) Thread.currentThread().interrupt()
  val result = runCatching(wait)
  WaitOutcome(result, Thread.currentThread().isInterrupted)
}

class GenericMonitorTests : StringSpec() {
  init {
    "waitUntilTrue returns when condition becomes true" {
      val monitor = BooleanMonitor(false)
      val (waiter, done) = inDaemonThread { monitor.waitUntilTrue() }

      waiter.awaitParked()
      done.isDone shouldBe false
      monitor.set(true)
      done.getGuarded()
    }

    "waitUntilFalse returns when condition becomes false" {
      val monitor = BooleanMonitor(true)
      val (waiter, done) = inDaemonThread { monitor.waitUntilFalse() }

      waiter.awaitParked()
      done.isDone shouldBe false
      monitor.set(false)
      done.getGuarded()
    }

    "waitUntilTrue with timeout returns true when condition met" {
      val monitor = BooleanMonitor(false)

      val t = thread { monitor.set(true) }
      t.join()

      val result = monitor.waitUntilTrue(500.milliseconds)
      result shouldBe true
    }

    "waitUntilTrue with timeout returns false when not met" {
      val monitor = BooleanMonitor(false)
      val result = monitor.waitUntilTrue(50.milliseconds)
      result shouldBe false
    }

    "waitUntilFalse with timeout returns true when condition met" {
      val monitor = BooleanMonitor(true)

      val t = thread { monitor.set(false) }
      t.join()

      val result = monitor.waitUntilFalse(500.milliseconds)
      result shouldBe true
    }

    "waitUntilFalse with timeout returns false when not met" {
      val monitor = BooleanMonitor(true)
      val result = monitor.waitUntilFalse(50.milliseconds)
      result shouldBe false
    }

    "waitUntil delegates to correct method based on value" {
      val monitorTrue = BooleanMonitor(true)
      monitorTrue.waitUntil(true, 50.milliseconds) shouldBe true

      val monitorFalse = BooleanMonitor(false)
      monitorFalse.waitUntil(false, 50.milliseconds) shouldBe true
    }

    "waitUntilTrue with timeout and block returns false on maxWait exceeded" {
      val monitor = BooleanMonitor(false)
      val result =
        monitor.waitUntilTrue(
          timeout = 50.milliseconds,
          maxWait = 100.milliseconds,
          block = null,
        )
      result shouldBe false
    }

    "waitUntilTrue with timeout and block calls block on timeout" {
      val monitor = BooleanMonitor(false)
      var blockCalled = false

      val result =
        monitor.waitUntilTrue(50.milliseconds) {
          blockCalled = true
          false // stop waiting
        }

      blockCalled shouldBe true
      result shouldBe false
    }

    "waitUntilTrueWithInterruption returns when condition is true" {
      val monitor = BooleanMonitor(false)
      val done = CountDownLatch(1)

      val t = thread {
        monitor.waitUntilTrueWithInterruption()
        done.countDown()
      }

      monitor.set(true)
      done.await(5, TimeUnit.SECONDS) shouldBe true
      t.join(5000)
    }

    "waitUntilTrueWithInterruption throws InterruptedException when interrupted" {
      val monitor = BooleanMonitor(false)
      val started = CountDownLatch(1)
      val interrupted = CountDownLatch(1)

      val t = thread {
        started.countDown()
        try {
          monitor.waitUntilTrueWithInterruption()
        } catch (e: InterruptedException) {
          interrupted.countDown()
        }
      }

      started.await(5, TimeUnit.SECONDS) shouldBe true
      t.interrupt()
      interrupted.await(5, TimeUnit.SECONDS) shouldBe true
      t.join(5000)
    }

    // The thread is interrupted before it waits, so an interruptible wait would throw at once. An uninterruptible
    // one blocks anyway, returns only once the condition holds, and then restores the interrupt.
    "the uninterruptible waits ignore an interrupt and restore it on return" {
      val waits: List<Pair<Boolean, BooleanMonitor.() -> Boolean>> =
        [
          false to {
            waitUntilTrue()
            true
          },
          true to {
            waitUntilFalse()
            true
          },
          false to { waitUntilTrue(1.hours) },
          true to { waitUntilFalse(1.hours) },
          false to { waitUntilTrue(timeout = 1.hours, maxWait = Duration.INFINITE, block = null) },
          true to { waitUntilFalse(timeout = 1.hours, maxWait = Duration.INFINITE, block = null) },
        ]

      for ((initValue, waitFor) in waits) {
        val monitor = BooleanMonitor(initValue)
        val (waiter, outcome) = waitOnDaemonThread(interruptFirst = true) { monitor.waitFor() }

        waiter.awaitParked()
        outcome.isDone shouldBe false
        monitor.set(!initValue)

        val finished = outcome.getGuarded()
        finished.result.getOrThrow() shouldBe true
        finished.interruptedAfter shouldBe true
      }
    }

    "the timed interruptible waits throw InterruptedException when interrupted while waiting" {
      val waits: List<InspectableMonitor.() -> Boolean> =
        [
          { waitUntilTrueWithInterruption(1.hours) },
          { waitUntilTrueWithInterruption(timeout = 1.hours, maxWait = Duration.INFINITE, block = null) },
          { waitUntilTrueWithInterruption(1.hours) { true } },
        ]

      for (waitFor in waits) {
        val monitor = InspectableMonitor()
        val (waiter, outcome) = waitOnDaemonThread { monitor.waitFor() }

        waiter.awaitParked()
        waiter.interrupt()

        outcome.getGuarded().result.exceptionOrNull().shouldBeInstanceOf<InterruptedException>()
        monitor.isOccupied shouldBe false
      }
    }

    "waitUntilTrueWithInterruption with timeout returns true when condition met" {
      val monitor = BooleanMonitor(true)
      monitor.waitUntilTrueWithInterruption(500.milliseconds) shouldBe true
    }

    "waitUntilTrueWithInterruption with timeout returns false when not met" {
      val monitor = BooleanMonitor(false)
      monitor.waitUntilTrueWithInterruption(50.milliseconds) shouldBe false
    }

    "waitUntilTrue with block returns true when block makes condition true" {
      val monitor = BooleanMonitor(false)
      var calls = 0

      val result =
        monitor.waitUntilTrue(50.milliseconds) {
          calls++
          monitor.set(true)
          true // keep waiting
        }

      result shouldBe true
      calls shouldBe 1
    }

    "waitUntilTrueWithInterruption with block returns true when block makes condition true" {
      val monitor = BooleanMonitor(false)
      var calls = 0

      val result =
        monitor.waitUntilTrueWithInterruption(50.milliseconds) {
          calls++
          monitor.set(true)
          true // keep waiting
        }

      result shouldBe true
      calls shouldBe 1
    }

    "waitUntilTrueWithInterruption with block stops when block returns false" {
      val monitor = BooleanMonitor(false)

      val result =
        monitor.waitUntilTrueWithInterruption(50.milliseconds) {
          false // stop waiting
        }

      result shouldBe false
    }

    "waitUntilTrueWithInterruption with maxWait returns false when exceeded" {
      val monitor = BooleanMonitor(false)
      val result =
        monitor.waitUntilTrueWithInterruption(
          timeout = 50.milliseconds,
          maxWait = 100.milliseconds,
          block = null,
        )
      result shouldBe false
    }

    "waitUntilFalse with block returns true when block makes condition false" {
      val monitor = BooleanMonitor(true)
      var calls = 0

      val result =
        monitor.waitUntilFalse(50.milliseconds) {
          calls++
          monitor.set(false)
          true // keep waiting
        }

      result shouldBe true
      calls shouldBe 1
    }

    "waitUntilFalse with block stops when block returns false" {
      val monitor = BooleanMonitor(true)

      val result =
        monitor.waitUntilFalse(50.milliseconds) {
          false // stop waiting
        }

      result shouldBe false
    }

    "waitUntilFalse with maxWait returns false when exceeded" {
      val monitor = BooleanMonitor(true)
      val result =
        monitor.waitUntilFalse(
          timeout = 50.milliseconds,
          maxWait = 100.milliseconds,
          block = null,
        )
      result shouldBe false
    }

    "waitUntil without timeout returns immediately when condition already matches" {
      val monitorTrue = BooleanMonitor(true)
      monitorTrue.waitUntil(true)
      monitorTrue.get() shouldBe true

      val monitorFalse = BooleanMonitor(false)
      monitorFalse.waitUntil(false)
      monitorFalse.get() shouldBe false
    }

    "a guard that throws surfaces its own exception from the untimed waits" {
      val monitor = ThrowingMonitor()
      shouldThrow<IllegalStateException> { monitor.waitUntilTrue() }.message shouldBe "guard failed"
      shouldThrow<IllegalStateException> { monitor.waitUntilFalse() }.message shouldBe "guard failed"
      monitor.isHeldByCurrentThread shouldBe false
    }

    "a guard that throws surfaces its own exception from the timed waits" {
      val monitor = ThrowingMonitor()
      val waits: List<ThrowingMonitor.() -> Boolean> =
        [
          { waitUntilTrue(1.hours) },
          { waitUntilTrueWithInterruption(1.hours) },
          { waitUntilFalse(1.hours) },
          { waitUntilTrue(timeout = 1.hours, maxWait = Duration.INFINITE, block = null) },
        ]

      for (waitFor in waits) {
        shouldThrow<IllegalStateException> { monitor.waitFor() }.message shouldBe "guard failed"
        monitor.isHeldByCurrentThread shouldBe false
      }

      // Nor does a timed wait release a monitor the caller already holds.
      monitor.holding {
        for (waitFor in waits) {
          shouldThrow<IllegalStateException> { monitor.waitFor() }.message shouldBe "guard failed"
          monitor.isHeldByCurrentThread shouldBe true
        }
      }
      monitor.isHeldByCurrentThread shouldBe false
    }

    "a throwing guard does not release a monitor the caller already holds" {
      val monitor = ThrowingMonitor()
      monitor.holding {
        shouldThrow<IllegalStateException> { monitor.waitUntilTrueWithInterruption() }.message shouldBe "guard failed"
        monitor.isHeldByCurrentThread shouldBe true
      }
      monitor.isHeldByCurrentThread shouldBe false
    }

    // Each attempt would wait an hour, so the wait returns within the hang guard only if maxWait cuts it short.
    "retrying waits stop at maxWait even when each attempt is longer" {
      val waits: List<Pair<Boolean, BooleanMonitor.() -> Boolean>> =
        [
          false to { waitUntilTrue(timeout = 1.hours, maxWait = 200.milliseconds, block = null) },
          false to { waitUntilTrueWithInterruption(timeout = 1.hours, maxWait = 200.milliseconds, block = null) },
          true to { waitUntilFalse(timeout = 1.hours, maxWait = 200.milliseconds, block = null) },
        ]

      for ((initValue, waitFor) in waits) {
        val monitor = BooleanMonitor(initValue)
        val mark = TimeSource.Monotonic.markNow()
        val (_, outcome) = inDaemonThread { monitor.waitFor() }

        outcome.getGuarded() shouldBe false
        (mark.elapsedNow() >= 200.milliseconds) shouldBe true
      }
    }

    "a zero maxWait checks once instead of waiting without limit" {
      val monitor = BooleanMonitor(false)
      var retries = 0
      monitor.waitUntilTrue(timeout = 20.milliseconds, maxWait = Duration.ZERO) { ++retries < 3 } shouldBe false
      retries shouldBe 0
    }

    "a negative maxWait still waits without an overall limit" {
      val monitor = BooleanMonitor(false)
      var retries = 0
      monitor.waitUntilTrue(timeout = 10.milliseconds, maxWait = (-1).seconds) { ++retries < 3 } shouldBe false
      retries shouldBe 3
    }

    "a sub-millisecond retry timeout is rejected instead of spinning" {
      val monitor = BooleanMonitor(false)
      shouldThrow<IllegalArgumentException> {
        monitor.waitUntilTrue(timeout = 500.microseconds, maxWait = 50.milliseconds, block = null)
      }
    }

    // The wait lasts an hour unless set wakes it, so returning within the hang guard shows set woke it.
    "a thread blocked in a timed wait is woken by set long before the timeout" {
      val monitor = BooleanMonitor(false)
      val (waiter, outcome) = inDaemonThread { monitor.waitUntilTrue(1.hours) }

      waiter.awaitParked()
      monitor.set(true)

      outcome.getGuarded() shouldBe true
    }

    "state changed through mutate wakes waiting threads" {
      val monitor = CountingMonitor(target = 3)
      val (waiter, done) = inDaemonThread { monitor.waitUntilTrue() }

      waiter.awaitParked()
      monitor.increment()
      monitor.increment()
      done.isDone shouldBe false
      monitor.increment()

      done.getGuarded()
    }
  }
}
