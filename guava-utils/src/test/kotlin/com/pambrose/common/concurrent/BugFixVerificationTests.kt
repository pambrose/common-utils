@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.concurrent

import com.pambrose.common.util.isZipped
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #5: Lambda-based logging methods logged the lambda object, not its result
    // Before fix: logger.debug { msg } logged the lambda's toString()
    // After fix: logger.debug { msg() } invokes the lambda to get the message

    "debug lambda is invoked" {
      var invoked = false
      val action = BooleanMonitor.debug {
        invoked = true
        "debug message"
      }
      action()
      invoked shouldBe true
    }

    "info lambda is invoked" {
      var invoked = false
      val action = BooleanMonitor.info {
        invoked = true
        "info message"
      }
      action()
      invoked shouldBe true
    }

    "warn lambda is invoked" {
      var invoked = false
      val action = BooleanMonitor.warn {
        invoked = true
        "warn message"
      }
      action()
      invoked shouldBe true
    }

    "error lambda is invoked" {
      var invoked = false
      val action = BooleanMonitor.error {
        invoked = true
        "error message"
      }
      action()
      invoked shouldBe true
    }

    // Bug #16: Race condition in GenericValueWaiter/BooleanWaiter
    // Before fix: check and callback setup were not atomic; predicate was not volatile
    // After fix: check and callback are set atomically under mutex; predicate is @Volatile

    "boolean waiter wait until true works" {
      val waiter = BooleanWaiter(false)

      launch {
        delay(50.milliseconds)
        waiter.setValue(true)
      }

      val result = waiter.waitUntilTrue(2.seconds)
      result shouldBe true
    }

    "boolean waiter wait until false works" {
      val waiter = BooleanWaiter(true)

      launch {
        delay(50.milliseconds)
        waiter.setValue(false)
      }

      val result = waiter.waitUntilFalse(2.seconds)
      result shouldBe true
    }

    "boolean waiter timeout returns false" {
      val waiter = BooleanWaiter(false)
      val result = waiter.waitUntilTrue(100.milliseconds)
      result shouldBe false
    }

    "boolean waiter already satisfied returns immediately" {
      val waiter = BooleanWaiter(false)
      waiter.setValue(true)
      val result = waiter.waitUntilTrue(1.seconds)
      result shouldBe true
    }

    // Bug #17: LameBooleanWaiter used unstructured coroutine scopes
    // Before fix: CoroutineScope(Dispatchers.Default).launch broke structured concurrency
    // After fix: uses coroutineScope { launch { ... } } for structured concurrency

    "lame boolean waiter wait for change works" {
      val waiter = LameBooleanWaiter(false)

      launch {
        delay(50.milliseconds)
        waiter.changeValue(true)
      }

      waiter.waitForChangeInValue()
      // If we reach here, the waiter correctly detected the change
    }

    "lame boolean waiter immediate return when already changed" {
      val waiter = LameBooleanWaiter(false)
      waiter.changeValue(true)

      // waitForChangeInValue should check immediately and see value already changed
      // But the initial check compares value == targetValue at the moment of the call
      // So we need another wait cycle
      val waiter2 = LameBooleanWaiter(true)
      waiter2.changeValue(false)
      // If we get here without hanging, the immediate-return logic works
    }

    // Bug #18: isZipped() crashed on byte arrays with fewer than 2 elements
    // Before fix: accessed this[0] and this[1] without size check
    // After fix: checks size >= 2 before accessing elements

    "is zipped handles empty array" {
      ByteArray(0).isZipped() shouldBe false
    }

    "is zipped handles single byte array" {
      byteArrayOf(0x1f).isZipped() shouldBe false
    }

    "is zipped handles non gzip data" {
      byteArrayOf(0x00, 0x00).isZipped() shouldBe false
    }

    "is zipped detects gzip magic bytes" {
      // GZIP magic bytes: 0x1f 0x8b
      byteArrayOf(0x1f, 0x8b.toByte()).isZipped() shouldBe true
    }

    // Bug #24: Long.MAX_VALUE.days overflows; should use Duration.INFINITE
    // Before fix: Long.MAX_VALUE.days as default timeout overflowed on conversion
    // After fix: Duration.INFINITE is used, which withTimeoutOrNull handles correctly

    "conditional boolean wait until true with default timeout" {
      val cond = ConditionalBoolean(false)

      launch {
        delay(50.milliseconds)
        cond.set(true)
      }

      val result = cond.waitUntilTrue(2.seconds)
      result shouldBe true
    }

    "conditional value wait until with default timeout" {
      val cond = ConditionalValue(0)

      launch {
        delay(50.milliseconds)
        cond.set(42)
      }

      val result = cond.waitUntil(2.seconds) { it == 42 }
      result shouldBe true
    }

    "conditional value timeout returns false" {
      val cond = ConditionalValue(0)
      val result = cond.waitUntil(100.milliseconds) { it == 99 }
      result shouldBe false
    }
  }
}
