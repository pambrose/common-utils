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

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class BooleanMonitorTests {
  @Test
  fun booleanMonitorInitialValueTrueTest() {
    val monitor = BooleanMonitor(true)
    monitor.get() shouldBe true
  }

  @Test
  fun booleanMonitorInitialValueFalseTest() {
    val monitor = BooleanMonitor(false)
    monitor.get() shouldBe false
  }

  @Test
  fun booleanMonitorSetValueTest() {
    val monitor = BooleanMonitor(false)
    monitor.get() shouldBe false

    monitor.set(true)
    monitor.get() shouldBe true

    monitor.set(false)
    monitor.get() shouldBe false
  }

  @Test
  fun booleanMonitorToStringTest() {
    val monitor = BooleanMonitor(true)
    monitor.toString() shouldContain "value=true"

    monitor.set(false)
    monitor.toString() shouldContain "value=false"
  }

  @Test
  fun booleanMonitorWaitUntilTrueTimeoutTest() {
    val monitor = BooleanMonitor(false)
    val result = monitor.waitUntilTrue(50.milliseconds)
    result shouldBe false
  }

  @Test
  fun booleanMonitorWaitUntilFalseTimeoutTest() {
    val monitor = BooleanMonitor(true)
    val result = monitor.waitUntilFalse(50.milliseconds)
    result shouldBe false
  }

  @Test
  fun booleanMonitorWaitUntilTrueAlreadyTrueTest() {
    val monitor = BooleanMonitor(true)
    val result = monitor.waitUntilTrue(50.milliseconds)
    result shouldBe true
  }

  @Test
  fun booleanMonitorWaitUntilFalseAlreadyFalseTest() {
    val monitor = BooleanMonitor(false)
    val result = monitor.waitUntilFalse(50.milliseconds)
    result shouldBe true
  }
}
