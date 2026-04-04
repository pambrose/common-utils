@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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

      Thread.sleep(50)
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

      Thread.sleep(50)
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
  }
}
