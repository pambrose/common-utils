@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GenericValueWaiterTests : StringSpec() {
  init {
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
  }
}
