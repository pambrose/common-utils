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

class VerboseCountDownLatchTests : StringSpec() {
  init {
    "countDown decrements count" {
      val latch = VerboseCountDownLatch(3)
      latch.count shouldBe 3

      latch.countDown()
      latch.count shouldBe 2

      latch.countDown()
      latch.count shouldBe 1

      latch.countDown()
      latch.count shouldBe 0
    }

    "await returns when count reaches zero" {
      val latch = VerboseCountDownLatch(2)
      val (waiter, done) = inDaemonThread { latch.await() }

      waiter.awaitParked()
      done.isDone shouldBe false

      // A latch releases its waiters only at zero, so one count down cannot wake this one.
      latch.countDown()
      done.isDone shouldBe false

      latch.countDown()
      done.getGuarded()
    }

    "await with timeout returns false if not counted down" {
      val latch = VerboseCountDownLatch(1)
      val result = latch.await(50, TimeUnit.MILLISECONDS)
      result shouldBe false
    }

    "await with timeout returns true when counted down" {
      val latch = VerboseCountDownLatch(1)

      thread { latch.countDown() }

      latch.await(HANG_GUARD_SECONDS, TimeUnit.SECONDS) shouldBe true
    }

    "isFinished extension returns correct value" {
      val latch = VerboseCountDownLatch(1)
      latch.isFinished shouldBe false

      latch.countDown()
      latch.isFinished shouldBe true
    }

    "await with a string message returns without logging when already counted down" {
      val latch = VerboseCountDownLatch(1)
      latch.countDown()

      latch.await(50.milliseconds, "should never be logged")
      latch.count shouldBe 0
    }

    "await with a message lambda logs on each timeout and returns once counted down" {
      val latch = VerboseCountDownLatch(1)
      val timedOut = CountDownLatch(1)
      val finished = CountDownLatch(1)

      val t = thread {
        latch.await(50.milliseconds) {
          timedOut.countDown()
          "still waiting"
        }
        finished.countDown()
      }

      try {
        // The message lambda runs only after an await timeout, proving at least one retry happened.
        timedOut.await(5, TimeUnit.SECONDS) shouldBe true
        latch.countDown()
        finished.await(5, TimeUnit.SECONDS) shouldBe true
      } finally {
        // Guarantee the background thread can always exit, even if an assertion above failed.
        latch.countDown()
        t.join(5000)
      }
    }

    "the verbose awaits throw InterruptedException when interrupted while waiting" {
      val awaits: List<VerboseCountDownLatch.() -> Unit> =
        [
          { await(1.hours, "still waiting") },
          { await(1.hours) { "still waiting" } },
        ]

      for (awaitVerbosely in awaits) {
        val latch = VerboseCountDownLatch(1)
        val (waiter, outcome) = inDaemonThread { runCatching { latch.awaitVerbosely() } }

        waiter.awaitParked()
        waiter.interrupt()

        outcome.getGuarded().exceptionOrNull().shouldBeInstanceOf<InterruptedException>()
        latch.count shouldBe 1L
      }
    }

    "the verbose await rejects a timeout below 1 ms instead of logging in a tight loop" {
      val latch = VerboseCountDownLatch(0)
      shouldThrow<IllegalArgumentException> { latch.await(Duration.ZERO, "never logged") }
      shouldThrow<IllegalArgumentException> { latch.await(500.microseconds, "never logged") }
      shouldThrow<IllegalArgumentException> { latch.await((-1).milliseconds) { "never logged" } }
    }
  }
}
