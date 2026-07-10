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
@file:JvmName("MiscFuncsKt")
@file:JvmMultifileClass

package com.pambrose.common.util

import kotlin.contracts.contract
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Returns `true` if this value is not null, with a Kotlin contract that smart-casts the receiver.
 *
 * @return `true` if non-null
 */
fun Any?.isNotNull(): Boolean {
  contract {
    returns(true) implies (this@isNotNull != null)
  }
  return this != null
}

/**
 * Returns `true` if this value is null, with a Kotlin contract that smart-casts the receiver on `false`.
 *
 * @return `true` if null
 */
fun Any?.isNull(): Boolean {
  contract {
    returns(false) implies (this@isNull != null)
  }
  return this == null
}

/**
 * Left-pads this [Int] to the specified [width] with [padChar].
 *
 * Extension function on [Int].
 *
 * @param width the minimum width of the resulting string
 * @param padChar the character to pad with (default `'0'`)
 * @return the left-padded string
 */
fun Int.lpad(
  width: Int,
  padChar: Char = '0',
): String = toString().padStart(width, padChar)

/**
 * Right-pads this [Int] to the specified [width] with [padChar].
 *
 * Extension function on [Int].
 *
 * @param width the minimum width of the resulting string
 * @param padChar the character to pad with (default `'0'`)
 * @return the right-padded string
 */
fun Int.rpad(
  width: Int,
  padChar: Char = '0',
): String = toString().padEnd(width, padChar)

/**
 * Capitalizes the first character of this [String].
 *
 * Extension function on [String].
 *
 * @return the string with the first character in title case
 */
fun String.capitalizeFirstChar(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
