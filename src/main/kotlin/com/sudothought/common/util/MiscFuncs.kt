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

package com.sudothought.common.util

import com.google.common.base.StandardSystemProperty
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.math.abs
import kotlin.math.log10
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

val Int.length
    get() =
        when (this) {
            0 -> 1
            else -> log10(abs(toDouble())).toInt() + 1
        }

val Long.length
    get() =
        when (this) {
            0L -> 1
            else -> log10(abs(toDouble())).toInt() + 1
        }

val isWindows by lazy { StandardSystemProperty.OS_NAME.value().orEmpty().contains("Windows") }
val isMac by lazy { StandardSystemProperty.OS_NAME.value().orEmpty().contains("Mac OS X") }
val isJava6 by lazy { StandardSystemProperty.JAVA_VERSION.value().orEmpty().startsWith("1.6") }

data class HostInfo(val hostName: String, val ipAddress: String)

val hostInfo by lazy {
    try {
        val hostname = InetAddress.getLocalHost().hostName!!
        val address = InetAddress.getLocalHost().hostAddress!!
        //logger.debug { "Hostname: $hostname Address: $address" }
        HostInfo(hostname, address)
    } catch (e: UnknownHostException) {
        HostInfo("Unknown", "Unknown")
    }
}