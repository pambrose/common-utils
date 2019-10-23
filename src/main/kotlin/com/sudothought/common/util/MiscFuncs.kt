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

package com.sudothought.common.util

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.seconds

fun sleep(sleepTime: Duration) = Thread.sleep(sleepTime.toLongMilliseconds())

private val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomId(length: Int = 10): String =
    (1..length)
        .map { Random.nextInt(0, charPool.size) }
        .map { i -> charPool[i] }
        .joinToString("")

fun repeatWithSleep(iterations: Int,
                    sleepTime: Duration = 1.seconds,
                    block: (count: Int, startMillis: Long) -> Unit) {
    val startMillis = System.currentTimeMillis()
    repeat(iterations) { i ->
        block(i, startMillis)
        sleep(sleepTime)
    }
}