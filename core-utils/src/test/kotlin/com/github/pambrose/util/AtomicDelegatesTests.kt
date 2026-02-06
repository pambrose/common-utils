/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.util

import com.github.pambrose.common.delegate.AtomicDelegates
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AtomicDelegatesTests {
  @Test
  fun atomicBooleanTest() {
    var flag by AtomicDelegates.atomicBoolean(false)
    flag shouldBe false
    flag = true
    flag shouldBe true
    flag = false
    flag shouldBe false
  }

  @Test
  fun atomicIntegerTest() {
    var count by AtomicDelegates.atomicInteger(0)
    count shouldBe 0
    count = 42
    count shouldBe 42
    count = -1
    count shouldBe -1
    count = Int.MAX_VALUE
    count shouldBe Int.MAX_VALUE
  }

  @Test
  fun atomicLongTest() {
    var count by AtomicDelegates.atomicLong(0L)
    count shouldBe 0L
    count = 42L
    count shouldBe 42L
    count = -1L
    count shouldBe -1L
    count = Long.MAX_VALUE
    count shouldBe Long.MAX_VALUE
  }

  @Test
  fun nonNullableReferenceTest() {
    var value: String by AtomicDelegates.nonNullableReference()

    // Should throw when accessed before initialization
    shouldThrow<IllegalStateException> {
      @Suppress("UNUSED_EXPRESSION")
      value
    }

    // Should work after initialization
    value = "test"
    value shouldBe "test"

    // Should allow reassignment
    value = "updated"
    value shouldBe "updated"
  }

  @Test
  fun nonNullableReferenceWithInitialValueTest() {
    var value: String by AtomicDelegates.nonNullableReference("initial")
    value shouldBe "initial"
    value = "updated"
    value shouldBe "updated"
  }

  @Test
  fun singleSetReferenceTest() {
    var value: String? by AtomicDelegates.singleSetReference<String>()
    value shouldBe null

    // First set should work
    value = "first"
    value shouldBe "first"

    // Second set should throw since the value has already been set
    shouldThrow<IllegalStateException> {
      value = "second"
    }
    value shouldBe "first"
  }

  @Test
  fun singleSetReferenceWithCompareValueTest() {
    var value: String? by AtomicDelegates.singleSetReference(initValue = "init", compareValue = "init")
    value shouldBe "init"

    // Should update when current value matches compareValue
    value = "updated"
    value shouldBe "updated"

    // Should throw since current value no longer matches compareValue
    shouldThrow<IllegalStateException> {
      value = "third"
    }
    value shouldBe "updated"
  }
}
