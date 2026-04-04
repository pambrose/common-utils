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

package com.pambrose.common.dsl

import com.pambrose.common.dsl.GuavaDsl.toStringElements
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class GuavaDslTests : StringSpec() {
  init {
    "to string elements simple" {
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

    "to string elements with null" {
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

    "to string elements empty" {
      val testObj = object {
        override fun toString() = toStringElements {}
      }
      val result = testObj.toString()
      result shouldContain "{}"
    }

    "service listener creation" {
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

    "service manager listener creation" {
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
}
