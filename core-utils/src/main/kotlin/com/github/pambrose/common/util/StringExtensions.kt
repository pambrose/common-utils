/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.regex.Pattern
import kotlin.math.log10

fun String.isSingleQuoted() = trim().run { length >= 2 && startsWith("'") && endsWith("'") }

fun String.isNotSingleQuoted() = !isSingleQuoted()

fun String.isDoubleQuoted() = trim().run { length >= 2 && startsWith("\"") && endsWith("\"") }

fun String.isNotDoubleQuoted() = !isDoubleQuoted()

fun String.isQuoted() = isSingleQuoted() || isDoubleQuoted()

fun String.isNotQuoted() = !isQuoted()

fun String.toSingleQuoted() = "'$this'"

fun String.toDoubleQuoted() = "\"$this\""

fun String.pluralize(cnt: Int, suffix: String = "s") = if (cnt == 1) this else "$this$suffix"

fun String.singleToDoubleQuoted() =
  when {
    !isSingleQuoted() -> this
    else -> subSequence(1, length - 1).replace(Regex("\""), "\\\"").toDoubleQuoted()
  }

fun String.nullIfBlank() = ifBlank { null }

fun String.ensureSuffix(suffix: CharSequence) = if (this.endsWith(suffix)) this else this + suffix

fun String.decode() = URLDecoder.decode(this, UTF_8.toString()) ?: this

fun String.encode() = URLEncoder.encode(this, UTF_8.toString()) ?: this

fun List<String>.join(separator: CharSequence = "/") = toPath(addPrefix = false, addTrailing = false, separator)

fun List<String>.toRootPath(addTrailing: Boolean = false, separator: CharSequence = "/") =
  toPath(addPrefix = true, addTrailing = addTrailing, separator = separator)

fun List<String>.toPath(
  addPrefix: Boolean = true,
  addTrailing: Boolean = true,
  separator: CharSequence = "/"
) =
  mapIndexed { i, s -> if (i == 0 && addPrefix && !s.startsWith(separator)) "$separator$s" else s }
    .mapIndexed { i, s -> if (i != 0 && s.startsWith(separator)) s.substring(1) else s }
    .mapIndexed { i, s -> if (i < size - 1 || addTrailing) s.ensureSuffix(separator) else s }
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

fun String.isNotBracketed(startChar: Char = '[', endChar: Char = ']') = !isBracketed(startChar, endChar)

fun String.asBracketed(startChar: Char = '[', endChar: Char = ']') = "$startChar$this$endChar"

fun String.isInt() =
  try {
    this.toInt()
    true
  } catch (e: Exception) {
    false
  }

fun String.isNotInt() = !isInt()

fun String.isFloat() =
  try {
    this.toFloat()
    true
  } catch (e: Exception) {
    false
  }

fun String.isNotFloat() = !isFloat()

fun String.isDouble() =
  try {
    this.toDouble()
    true
  } catch (e: Exception) {
    false
  }

fun String.isNotDouble() = !isDouble()

fun String.trimEnds(len: Int = 1) = trim().run { substring(len, this.length - len) }

fun String.substringBetween(begin: String, end: String) = substringAfter(begin).substringBeforeLast(end)

fun String.withLineNumbers(separator: Char = ':'): String {
  val lines = lines()
  val len = (log10(lines.size.toDouble()) + 1).toInt()
  return lines.mapIndexed { i, s -> "${(i + 1).toString().padEnd(len + 1)}$separator $s" }.joinToString("\n")
}

private const val dot = "__SINGLE__DOT__"

val String.toPattern: String
  get() {
    // First protect the period and then convert back at end
    val pattern = this.replace(".", dot).replace("*", ".*").replace("?", ".").replace(dot, """\.""")
    return """^$pattern$"""
  }

fun String.asRegex(ignoreCase: Boolean = false) =
  if (ignoreCase)
    Regex(this.toPattern, RegexOption.IGNORE_CASE)
  else
    Regex(this.toPattern)

private val emailPattern by lazy {
  Pattern.compile(
    "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]|[\\w-]{2,}))@" +
        "((([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?" +
        "[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\." +
        "([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?" +
        "[0-9]{1,2}|25[0-5]|2[0-4][0-9]))|" +
        "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$"
  )
}

fun String.isValidEmail() = emailPattern.matcher(this).matches()

fun String.isNotValidEmail() = !isValidEmail()

fun String.md5(salt: String = ""): String = encodedByteArray(this, { salt }, "MD5").asText

fun String.sha256(salt: String = ""): String = encodedByteArray(this, { salt }, "SHA-256").asText

fun String.md5(salt: ByteArray): String = encodedByteArray(this, salt, "MD5").asText

fun String.sha256(salt: ByteArray): String = encodedByteArray(this, salt, "SHA-256").asText

val ByteArray.asText get() = fold("") { str, it -> str + "%02x".format(it) }

private fun encodedByteArray(input: String, salt: ByteArray, algorithm: String) =
  with(MessageDigest.getInstance(algorithm)) {
    update(salt)
    digest(input.toByteArray())
  }

private fun encodedByteArray(input: String, salt: (String) -> String, algorithm: String) =
  with(MessageDigest.getInstance(algorithm)) {
    update(salt(input).toByteArray())
    digest(input.toByteArray())
  }

fun newByteArraySalt(len: Int = 16): ByteArray = ByteArray(len).apply { SecureRandom().nextBytes(this) }

fun newStringSalt(len: Int = 16): String = randomId(len)

fun md5Of(vararg keys: Any, separator: String = "|") = keys.joinToString(separator) { it.toString() }.md5()

fun pathOf(vararg elems: Any): String = elems.toList().map { it.toString() }.filter { it.isNotEmpty() }.join("/")

fun String.maskUrlCredentials() =
  if ("://" in this && "@" in this) {
    val scheme = split("://")
    val uri = split("@")
    "${scheme[0]}://*****:*****@${uri[1]}"
  } else {
    this
  }

fun String.obfuscate(freq: Int = 2) =
  mapIndexed { i, v -> if (i % freq == 0) '*' else v }.joinToString("")

fun String.maxLength(len: Int) = if (length <= len) this else this.substring(0, len)