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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.util

import java.net.InetAddress
import java.net.UnknownHostException
import java.security.SecureRandom
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.seconds


data class HostInfo(val hostName: String, val ipAddress: String)

val hostInfo by lazy {
  try {
    val hostname = InetAddress.getLocalHost().hostName!!
    val address = InetAddress.getLocalHost().hostAddress!!
    //logger.debug { "Hostname: $hostname Address: $address" }
    HostInfo(hostname, address)
  }
  catch (e: UnknownHostException) {
    HostInfo("Unknown", "Unknown")
  }
}

fun sleep(sleepTime: Duration) = Thread.sleep(sleepTime.toLongMilliseconds())

fun randomId(length: Int = 10, charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')) =
  SecureRandom().let { random ->
    (1..length)
      .map { random.nextInt(charPool.size) }
      .map { charPool[it] }
      .joinToString("")
  }

fun repeatWithSleep(iterations: Int,
                    sleepTime: Duration = 1.seconds,
                    block: (count: Int, startMillis: Long) -> Unit) {
  val startMillis = System.currentTimeMillis()
  iterations repeat { i ->
    block(i, startMillis)
    sleep(sleepTime)
  }
}

fun Any?.isNotNull(): Boolean {
  contract {
    returns(true) implies (this@isNotNull != null)
  }
  return this != null
}

fun Any?.isNull(): Boolean {
  contract {
    returns(false) implies (this@isNull != null)
  }
  return this == null
}
