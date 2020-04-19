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

package com.github.pambrose.common.util

fun String.isSingleQuoted() = trim().run { length >= 2 && startsWith("'") && endsWith("'") }

fun String.isDoubleQuoted() = trim().run { length >= 2 && startsWith("\"") && endsWith("\"") }

fun String.isQuoted() = isSingleQuoted() || isDoubleQuoted()

fun String.toSingleQuoted() = "'$this'"

fun String.toDoubleQuoted() = "\"$this\""

fun String.pluralize(cnt: Int, suffix: String = "s") = if (cnt == 1) this else "$this$suffix"

fun String.singleToDoubleQuoted() =
  when {
    !isSingleQuoted() -> this
    else -> subSequence(1, length - 1).replace(Regex("\""), "\\\"").toDoubleQuoted()
  }