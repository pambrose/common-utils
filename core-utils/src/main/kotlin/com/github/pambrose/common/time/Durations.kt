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

@file:JvmName("DurationUtils")
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.time

import java.util.concurrent.TimeUnit
import kotlin.time.*

fun timeUnitToDuration(value: Long, timeUnit: TimeUnit): Duration =
  when (timeUnit) {
    TimeUnit.MICROSECONDS -> value.microseconds
    TimeUnit.NANOSECONDS -> value.nanoseconds
    TimeUnit.MILLISECONDS -> value.milliseconds
    TimeUnit.SECONDS -> value.seconds
    TimeUnit.MINUTES -> value.minutes
    TimeUnit.HOURS -> value.hours
    TimeUnit.DAYS -> value.days
  }
