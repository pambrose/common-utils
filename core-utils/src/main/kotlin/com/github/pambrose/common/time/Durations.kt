/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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

@file:JvmName("DurationUtils")
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.time

import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.*
import kotlin.time.Duration

fun timeUnitToDuration(value: Long, timeUnit: TimeUnit): Duration =
  when (timeUnit) {
    MICROSECONDS -> Duration.microseconds(value)
    NANOSECONDS -> Duration.nanoseconds(value)
    MILLISECONDS -> Duration.milliseconds(value)
    SECONDS -> Duration.seconds(value)
    MINUTES -> Duration.minutes(value)
    HOURS -> Duration.hours(value)
    DAYS -> Duration.days(value)
  }


fun Duration.format(includeMillis: Boolean = false): String {
  val diff = inWholeMilliseconds
  val day = MILLISECONDS.toDays(diff)
  val dayMillis = DAYS.toMillis(day)
  val hr = MILLISECONDS.toHours(diff - dayMillis)
  val hrMillis = HOURS.toMillis(hr)
  val min = MILLISECONDS.toMinutes(diff - dayMillis - hrMillis)
  val minMillis = MINUTES.toMillis(min)
  val sec = MILLISECONDS.toSeconds(diff - dayMillis - hrMillis - minMillis)
  val secMillis = SECONDS.toMillis(sec)
  val ms = MILLISECONDS.toMillis(diff - dayMillis - hrMillis - minMillis - secMillis)

  return if (includeMillis)
    String.format("%d:%02d:%02d:%02d.%03d", day, hr, min, sec, ms)
  else
    String.format("%d:%02d:%02d:%02d", day, hr, min, sec)
}