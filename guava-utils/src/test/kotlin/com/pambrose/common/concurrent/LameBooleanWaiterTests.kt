@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LameBooleanWaiterTests : StringSpec() {
  init {
    "changeValue updates the value from false to true" {
      val waiter = LameBooleanWaiter(false)
      var completed = false

      val job = launch {
        waiter.waitForChangeInValue()
        completed = true
      }

      delay(50.milliseconds)
      completed shouldBe false

      waiter.changeValue(true)
      job.join()
      completed shouldBe true
    }

    "changeValue updates the value from true to false" {
      val waiter = LameBooleanWaiter(true)
      var completed = false

      val job = launch {
        waiter.waitForChangeInValue()
        completed = true
      }

      delay(50.milliseconds)
      waiter.changeValue(false)
      job.join()
      completed shouldBe true
    }

    "changeValue with same value does not trigger waiter" {
      val waiter = LameBooleanWaiter(true)
      var completed = false

      val job = launch {
        waiter.waitForChangeInValue()
        completed = true
      }

      delay(50.milliseconds)
      waiter.changeValue(true) // same value, should not trigger
      delay(50.milliseconds)
      completed shouldBe false

      waiter.changeValue(false) // different value, should trigger
      job.join()
      completed shouldBe true
    }

    "waitForChangeInValue completes within timeout" {
      val waiter = LameBooleanWaiter(false)

      val job = launch {
        withTimeout(1.seconds) {
          waiter.waitForChangeInValue()
        }
      }

      delay(50.milliseconds)
      waiter.changeValue(true)
      job.join()
    }
  }
}
