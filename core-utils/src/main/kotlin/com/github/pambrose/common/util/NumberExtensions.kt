/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

import kotlin.math.abs
import kotlin.math.log10
import kotlin.random.Random

fun Int.random(): Int = Random.nextInt(this)

fun Long.random(): Long = Random.nextLong(this)

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

inline infix operator fun Short.times(action: (Short) -> Unit) {
  var i = 0
  while (i < this) action((i++).toShort())
}

inline infix operator fun Int.times(action: (Int) -> Unit) {
  var i = 0
  while (i < this) action(i++)
}

inline infix operator fun Long.times(action: (Long) -> Unit) {
  var i = 0L
  while (i < this) action(i++)
}

inline infix fun Int.repeat(block: (Int) -> Unit) {
  repeat(this, block)
}
