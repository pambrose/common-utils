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

package com.pambrose.time

import com.pambrose.common.time.format
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormatTests : StringSpec() {
  init {
    "format zero duration" {
      val duration = 0.seconds
      duration.format() shouldBe "0:00:00:00"
      duration.format(includeMillis = true) shouldBe "0:00:00:00.000"
    }

    "format seconds" {
      30.seconds.format() shouldBe "0:00:00:30"
      59.seconds.format() shouldBe "0:00:00:59"
    }

    "format minutes" {
      1.minutes.format() shouldBe "0:00:01:00"
      (1.minutes + 30.seconds).format() shouldBe "0:00:01:30"
    }

    "format hours" {
      1.hours.format() shouldBe "0:01:00:00"
      (2.hours + 30.minutes + 45.seconds).format() shouldBe "0:02:30:45"
    }

    "format days" {
      1.days.format() shouldBe "1:00:00:00"
      (2.days + 12.hours + 30.minutes + 15.seconds).format() shouldBe "2:12:30:15"
    }

    "format with millis" {
      500.milliseconds.format(includeMillis = true) shouldBe "0:00:00:00.500"
      (1.seconds + 250.milliseconds).format(includeMillis = true) shouldBe "0:00:00:01.250"
      (1.hours + 2.minutes + 3.seconds + 456.milliseconds).format(includeMillis = true) shouldBe "0:01:02:03.456"
    }

    "format complex duration" {
      val duration = 3.days + 14.hours + 25.minutes + 36.seconds + 789.milliseconds
      duration.format() shouldBe "3:14:25:36"
      duration.format(includeMillis = true) shouldBe "3:14:25:36.789"
    }
  }
}
