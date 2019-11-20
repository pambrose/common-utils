/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.time

import com.github.pambrose.common.time.timeUnitToDuration
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.time.*

class TimeTests {

  @Test
  fun conversionTest() {
    repeat(100) { i ->
      timeUnitToDuration(i.toLong(), TimeUnit.MICROSECONDS) shouldEqual i.microseconds
      timeUnitToDuration(i.toLong(), TimeUnit.NANOSECONDS) shouldEqual i.nanoseconds
      timeUnitToDuration(i.toLong(), TimeUnit.MILLISECONDS) shouldEqual i.milliseconds
      timeUnitToDuration(i.toLong(), TimeUnit.SECONDS) shouldEqual i.seconds
      timeUnitToDuration(i.toLong(), TimeUnit.MINUTES) shouldEqual i.minutes
      timeUnitToDuration(i.toLong(), TimeUnit.HOURS) shouldEqual i.hours
      timeUnitToDuration(i.toLong(), TimeUnit.DAYS) shouldEqual i.days
    }
  }
}