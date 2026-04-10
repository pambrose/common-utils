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

package com.pambrose.common.util

/** Utility object providing [arrayPrint] and [asString] overloads for all primitive array types and [String] arrays. */
object ArrayUtils {
  /** Prints a [BooleanArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: BooleanArray) = println(asString(vals))

  /** Returns a bracketed string representation of a [BooleanArray]. */
  @JvmStatic
  fun asString(vals: BooleanArray) = vals.joinToString().asBracketed()

  /** Prints a [CharArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: CharArray) = println(asString(vals))

  /** Returns a bracketed string representation of a [CharArray]. */
  @JvmStatic
  fun asString(vals: CharArray) = vals.joinToString().asBracketed()

  /** Prints a [ByteArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: ByteArray) = println(asString(vals))

  /** Returns a bracketed string representation of a [ByteArray]. */
  @JvmStatic
  fun asString(vals: ByteArray) = vals.joinToString().asBracketed()

  /** Prints a [ShortArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: ShortArray) = println(asString(vals))

  /** Returns a bracketed string representation of a [ShortArray]. */
  @JvmStatic
  fun asString(vals: ShortArray) = vals.joinToString().asBracketed()

  /** Prints an [IntArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: IntArray) = println(asString(vals))

  /** Returns a bracketed string representation of an [IntArray]. */
  @JvmStatic
  fun asString(vals: IntArray) = vals.joinToString().asBracketed()

  /** Prints a [LongArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: LongArray) = println(asString(vals))

  /** Returns a bracketed string representation of a [LongArray]. */
  @JvmStatic
  fun asString(vals: LongArray) = vals.joinToString().asBracketed()

  /** Prints a [FloatArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: FloatArray) = println(asString(vals))

  /** Returns a bracketed string representation of a [FloatArray]. */
  @JvmStatic
  fun asString(vals: FloatArray) = vals.joinToString().asBracketed()

  /** Prints a [DoubleArray] in bracketed format to stdout. */
  @JvmStatic
  fun arrayPrint(vals: DoubleArray) = println(asString(vals))

  /** Returns a bracketed string representation of a [DoubleArray]. */
  @JvmStatic
  fun asString(vals: DoubleArray) = vals.joinToString().asBracketed()

  /** Prints a [String] array in bracketed format with double-quoted elements to stdout. */
  @JvmStatic
  fun arrayPrint(vals: Array<String>) = println(asString(vals))

  /** Returns a bracketed string representation of a [String] array with double-quoted elements. */
  @JvmStatic
  fun asString(vals: Array<String>) = vals.joinToString { it.toDoubleQuoted() }.asBracketed()
}
