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

package com.github.pambrose.common.dsl

import com.github.pambrose.common.dsl.GuavaDsl.toStringElements
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class GuavaDslTests {
  @Test
  fun toStringElementsSimpleTest() {
    val testObj = object {
      override fun toString() =
        toStringElements {
          add("name", "test")
          add("value", 42)
        }
    }
    val result = testObj.toString()
    result shouldContain "name=test"
    result shouldContain "value=42"
  }

  @Test
  fun toStringElementsWithNullTest() {
    val testObj = object {
      override fun toString() =
        toStringElements {
          add("name", "test")
          add("nullValue", null)
        }
    }
    val result = testObj.toString()
    result shouldContain "name=test"
    result shouldContain "nullValue=null"
  }

  @Test
  fun toStringElementsEmptyTest() {
    val testObj = object {
      override fun toString() = toStringElements {}
    }
    val result = testObj.toString()
    result shouldContain "{}"
  }

  @Test
  fun serviceListenerCreationTest() {
    var startingCalled = false
    var runningCalled = false

    val listener = GuavaDsl.serviceListener {
      starting { startingCalled = true }
      running { runningCalled = true }
    }

    listener.starting()
    startingCalled shouldBe true
    runningCalled shouldBe false

    listener.running()
    runningCalled shouldBe true
  }

  @Test
  fun serviceManagerListenerCreationTest() {
    var healthyCalled = false
    var stoppedCalled = false

    val listener = GuavaDsl.serviceManagerListener {
      healthy { healthyCalled = true }
      stopped { stoppedCalled = true }
    }

    listener.healthy()
    healthyCalled shouldBe true
    stoppedCalled shouldBe false

    listener.stopped()
    stoppedCalled shouldBe true
  }
}
