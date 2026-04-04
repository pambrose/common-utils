@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

      Thread.sleep(50)
      completed shouldBe false

      latch.countDown()
      Thread.sleep(50)
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
  }
}
