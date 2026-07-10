@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
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
      var completed = false

      val t = thread {
        latch.await()
        completed = true
      }

      delay(50.milliseconds)
      completed shouldBe false

      latch.countDown()
      delay(50.milliseconds)
      completed shouldBe false

      latch.countDown()
      t.join(1000)
      completed shouldBe true
    }

    "await with timeout returns false if not counted down" {
      val latch = VerboseCountDownLatch(1)
      val result = latch.await(50, TimeUnit.MILLISECONDS)
      result shouldBe false
    }

    "await with timeout returns true when counted down" {
      val latch = VerboseCountDownLatch(1)

      thread {
        Thread.sleep(10)
        latch.countDown()
      }

      val result = latch.await(1000, TimeUnit.MILLISECONDS)
      result shouldBe true
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
  }
}
