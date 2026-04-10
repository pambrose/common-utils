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

package com.pambrose.common.util

import kotlin.math.abs
import kotlin.math.log10
import kotlin.random.Random

/**
 * Returns a random [Int] between 0 (inclusive) and this value (exclusive).
 *
 * Extension function on [Int].
 */
fun Int.random(): Int = Random.nextInt(this)

/**
 * Returns a random [Long] between 0 (inclusive) and this value (exclusive).
 *
 * Extension function on [Long].
 */
fun Long.random(): Long = Random.nextLong(this)

/**
 * The number of decimal digits in this [Int].
 *
 * Extension property on [Int]. Returns 1 for the value 0.
 */
val Int.length
  get() =
    when (this) {
      0 -> 1
      else -> log10(abs(toDouble())).toInt() + 1
    }

/**
 * The number of decimal digits in this [Long].
 *
 * Extension property on [Long]. Returns 1 for the value 0.
 */
val Long.length
  get() =
    when (this) {
      0L -> 1
      else -> log10(abs(toDouble())).toInt() + 1
    }

/**
 * Executes [action] for each value from 0 until this [Short] value.
 *
 * Infix operator extension on [Short].
 *
 * @param action the action to invoke with each index
 */
inline infix operator fun Short.times(action: (Short) -> Unit) {
  var i = 0
  while (i < this) action((i++).toShort())
}

/**
 * Executes [action] for each value from 0 until this [Int] value.
 *
 * Infix operator extension on [Int].
 *
 * @param action the action to invoke with each index
 */
inline infix operator fun Int.times(action: (Int) -> Unit) {
  var i = 0
  while (i < this) action(i++)
}

/**
 * Executes [action] for each value from 0 until this [Long] value.
 *
 * Infix operator extension on [Long].
 *
 * @param action the action to invoke with each index
 */
inline infix operator fun Long.times(action: (Long) -> Unit) {
  var i = 0L
  while (i < this) action(i++)
}

/**
 * Repeats [block] this many times, passing the current iteration index.
 *
 * Infix extension on [Int].
 *
 * @param block the action to invoke with each iteration index
 */
inline infix fun Int.repeat(block: (Int) -> Unit) {
  repeat(this, block)
}
