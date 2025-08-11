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

package com.github.pambrose.time

import com.github.pambrose.common.time.timeUnitToDuration
import com.github.pambrose.common.util.repeat
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class TimeTests {
  @Test
  fun conversionTest() {
    100 repeat { i ->
      timeUnitToDuration(i.toLong(), MICROSECONDS) shouldBe i.microseconds
      timeUnitToDuration(i.toLong(), NANOSECONDS) shouldBe i.nanoseconds
      timeUnitToDuration(i.toLong(), MILLISECONDS) shouldBe i.milliseconds
      timeUnitToDuration(i.toLong(), SECONDS) shouldBe i.seconds
      timeUnitToDuration(i.toLong(), MINUTES) shouldBe i.minutes
      timeUnitToDuration(i.toLong(), HOURS) shouldBe i.hours
      timeUnitToDuration(i.toLong(), DAYS) shouldBe i.days
    }
  }
}
