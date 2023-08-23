/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

package com.github.pambrose.common.util

object ArrayUtils {

  @JvmStatic
  fun arrayPrint(vals: BooleanArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: BooleanArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: CharArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: CharArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: ByteArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: ByteArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: ShortArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: ShortArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: IntArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: IntArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: LongArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: LongArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: FloatArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: FloatArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: DoubleArray) = println(asString(vals))

  @JvmStatic
  fun asString(vals: DoubleArray) = vals.joinToString().asBracketed()

  @JvmStatic
  fun arrayPrint(vals: Array<String>) = println(asString(vals))

  @JvmStatic
  fun asString(vals: Array<String>) = vals.joinToString { it.toDoubleQuoted() }.asBracketed()
}