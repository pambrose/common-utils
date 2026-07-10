/*
 *   Copyright © 2026 Paul Ambrose (pambrose@mac.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.google.common.util.concurrent.Service
import com.pambrose.common.dsl.GuavaDsl.toStringElements
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk

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

    "service listener invokes all lifecycle callbacks with their arguments" {
      var startingCalled = false
      var runningCalled = false
      var stoppingFrom: Service.State? = null
      var terminatedFrom: Service.State? = null
      var failedFrom: Service.State? = null
      var failedCause: Throwable? = null

      val listener = GuavaDsl.serviceListener {
        starting { startingCalled = true }
        running { runningCalled = true }
        stopping { stoppingFrom = it }
        terminated { terminatedFrom = it }
        failed { from, cause ->
          failedFrom = from
          failedCause = cause
        }
      }

      listener.starting()
      startingCalled shouldBe true

      listener.running()
      runningCalled shouldBe true

      listener.stopping(Service.State.RUNNING)
      stoppingFrom shouldBe Service.State.RUNNING

      listener.terminated(Service.State.STOPPING)
      terminatedFrom shouldBe Service.State.STOPPING

      val cause = IllegalStateException("service failed")
      listener.failed(Service.State.RUNNING, cause)
      failedFrom shouldBe Service.State.RUNNING
      failedCause shouldBe cause
    }

    "service listener without callbacks ignores lifecycle events" {
      val listener = GuavaDsl.serviceListener {}

      shouldNotThrowAny {
        listener.starting()
        listener.running()
        listener.stopping(Service.State.STARTING)
        listener.terminated(Service.State.RUNNING)
        listener.failed(Service.State.RUNNING, IllegalStateException("ignored"))
      }
    }

    "service manager listener invokes failure callback with the failed service" {
      val service = mockk<Service>()
      var failedService: Service? = null

      val listener = GuavaDsl.serviceManagerListener {
        failure { failedService = it }
      }

      listener.failure(service)
      failedService shouldBe service
    }

    "service manager listener without callbacks ignores lifecycle events" {
      val listener = GuavaDsl.serviceManagerListener {}

      shouldNotThrowAny {
        listener.healthy()
        listener.stopped()
        listener.failure(mockk<Service>())
      }
    }
  }
}
