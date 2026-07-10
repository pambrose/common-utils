@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

class GenericMonitorTests : StringSpec() {
  init {
    "waitUntilTrue returns when condition becomes true" {
      val monitor = BooleanMonitor(false)
      var completed = false

      val t = thread {
        monitor.waitUntilTrue()
        completed = true
      }

      delay(50.milliseconds)
      completed shouldBe false
      monitor.set(true)
      t.join(1000)
      completed shouldBe true
    }

    "waitUntilFalse returns when condition becomes false" {
      val monitor = BooleanMonitor(true)
      var completed = false

      val t = thread {
        monitor.waitUntilFalse()
        completed = true
      }

      delay(50.milliseconds)
      completed shouldBe false
      monitor.set(false)
      t.join(1000)
      completed shouldBe true
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
  }
}
