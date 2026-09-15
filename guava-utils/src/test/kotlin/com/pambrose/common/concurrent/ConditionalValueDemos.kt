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

package com.pambrose.common.concurrent

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

// Manual playground demos for ConditionalValue, kept in test sources so they stay out of the published jar.
// Run them from the IDE.

fun main() =
  runBlocking {
    val waiter = ConditionalValue(false)

    // Launch a coroutine that waits for the condition to become true
    val job = launch {
      println("Waiting for condition to become true...")
      waiter.waitUntil(20.seconds) { it }.also { if (!it) println("Timed out") }
      println("Condition is now true!")
    }

    yield()
    for (s in [false, false, true]) {
      println("Setting value to $s")
      waiter.set(s)
      delay(1.seconds)
    }

    job.join()
  }

fun main2() =
  runBlocking {
    val waiter = ConditionalValue(1)

    // Launch a coroutine that waits for the condition to become true
    val job = launch {
      println("Waiting for condition to become true...")
      waiter.waitUntil(20.seconds) { it == 5 }.also { if (!it) println("Timed out") }
      println("Condition is now true!")
    }

    yield()
    for (i in 3..7) {
      println("Setting value to $i  curr: ${waiter.get()}")
      waiter.set(i)
      delay(1.seconds)
    }

    job.join()
  }

fun main3() =
  runBlocking {
    val waiter = ConditionalValue([1])

    // Launch a coroutine that waits for the condition to become true
    val job = launch {
      println("Waiting for condition to become true...")
      waiter.waitUntil(20.seconds) { 5 in it }.also { if (!it) println("Timed out") }
      println("Condition is now true!")
    }

    yield()
    for (i in 3..7) {
      val l = List(i) { it }
      println("Setting value to $l  curr: ${waiter.get()}")
      waiter.set(l)
      delay(1.seconds)
    }

    job.join()
  }

fun main4() =
  runBlocking {
    val waiter = ConditionalValue("Hello")

    // Launch a coroutine that waits for the condition to become true
    val job = launch {
      println("Waiting for condition to become true...")
      waiter.waitUntil(20.seconds) { "Paul" in it }.also { if (!it) println("Timed out") }
      println("Condition is now true!")
    }

    yield()
    for (s in ["Bill", "Bob", "Paul", "John"].map { "$it Ambrose" }) {
      println("Setting value to $s  curr: ${waiter.get()}")
      waiter.set(s)
      delay(1.seconds)
    }

    job.join()
  }
