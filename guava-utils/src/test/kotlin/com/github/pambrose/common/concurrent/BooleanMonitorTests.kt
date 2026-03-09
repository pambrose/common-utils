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

package com.github.pambrose.common.concurrent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.milliseconds

class BooleanMonitorTests : StringSpec() {
  init {
    "boolean monitor initial value true" {
      val monitor = BooleanMonitor(true)
      monitor.get() shouldBe true
    }

    "boolean monitor initial value false" {
      val monitor = BooleanMonitor(false)
      monitor.get() shouldBe false
    }

    "boolean monitor set value" {
      val monitor = BooleanMonitor(false)
      monitor.get() shouldBe false

      monitor.set(true)
      monitor.get() shouldBe true

      monitor.set(false)
      monitor.get() shouldBe false
    }

    "boolean monitor to string" {
      val monitor = BooleanMonitor(true)
      monitor.toString() shouldContain "value=true"

      monitor.set(false)
      monitor.toString() shouldContain "value=false"
    }

    "boolean monitor wait until true timeout" {
      val monitor = BooleanMonitor(false)
      val result = monitor.waitUntilTrue(50.milliseconds)
      result shouldBe false
    }

    "boolean monitor wait until false timeout" {
      val monitor = BooleanMonitor(true)
      val result = monitor.waitUntilFalse(50.milliseconds)
      result shouldBe false
    }

    "boolean monitor wait until true already true" {
      val monitor = BooleanMonitor(true)
      val result = monitor.waitUntilTrue(50.milliseconds)
      result shouldBe true
    }

    "boolean monitor wait until false already false" {
      val monitor = BooleanMonitor(false)
      val result = monitor.waitUntilFalse(50.milliseconds)
      result shouldBe true
    }
  }
}
