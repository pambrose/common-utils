/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit.*
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
      timeUnitToDuration(i.toLong(), MICROSECONDS) shouldBeEqualTo microseconds(i)
      timeUnitToDuration(i.toLong(), NANOSECONDS) shouldBeEqualTo nanoseconds(i)
      timeUnitToDuration(i.toLong(), MILLISECONDS) shouldBeEqualTo milliseconds(i)
      timeUnitToDuration(i.toLong(), SECONDS) shouldBeEqualTo seconds(i)
      timeUnitToDuration(i.toLong(), MINUTES) shouldBeEqualTo minutes(i)
      timeUnitToDuration(i.toLong(), HOURS) shouldBeEqualTo hours(i)
      timeUnitToDuration(i.toLong(), DAYS) shouldBeEqualTo days(i)
    }
  }
}