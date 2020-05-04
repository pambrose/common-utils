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

import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.math.log10

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

fun String.ensureSuffix(suffix: CharSequence) = if (this.endsWith(suffix)) this else this + suffix

fun String.decode() = URLDecoder.decode(this, UTF_8.toString()) ?: this

fun List<String>.toPath(addTrailingSeparator: Boolean = true, separator: CharSequence = "/") =
  mapIndexed { i, s -> if (i != 0 && s.startsWith(separator)) s.substring(1) else s }
    .mapIndexed { i, s -> if (i < size - 1 || addTrailingSeparator) s.ensureSuffix(separator) else s }
    .joinToString("")

fun String.firstLineNumberOf(regex: Regex) = lines().firstLineNumberOf(regex)

fun List<String>.firstLineNumberOf(regex: Regex) =
  asSequence()
    .mapIndexed { i, str -> i to str }
    .filter { it.second.contains(regex) }
    .map { it.first }
    .firstOrNull() ?: -1

fun String.lastLineNumberOf(regex: Regex) = lines().lastLineNumberOf(regex)

fun List<String>.lastLineNumberOf(regex: Regex) =
  mapIndexed { i, str -> i to str }
    .asReversed()
    .asSequence()
    .filter { it.second.contains(regex) }
    .map { it.first }
    .firstOrNull() ?: -1

fun String.linesBetween(start: Regex, end: Regex) = lines().linesBetween(start, end)

fun List<String>.linesBetween(start: Regex, end: Regex) = subList(firstLineNumberOf(start) + 1, lastLineNumberOf(end))

fun String.isBracketed(startChar: Char = '[', endChar: Char = ']') =
  trim().run { startsWith(startChar) && endsWith(endChar) }

fun String.asBracketed(startChar: Char = '[', endChar: Char = ']') = "$startChar$this$endChar"

fun String.trimEnds(len: Int = 1) = trim().run { substring(len, this.length - len) }

fun String.substringBetween(begin: String, end: String) = substringAfter(begin).substringBeforeLast(end)

fun String.withLineNumbers(separator: Char = ':'): String {
  val lines = lines()
  val len = (log10(lines.size.toDouble()) + 1).toInt()
  return lines.mapIndexed { i, s -> "${(i + 1).toString().padEnd(len + 1)}$separator $s" }.joinToString("\n")
}