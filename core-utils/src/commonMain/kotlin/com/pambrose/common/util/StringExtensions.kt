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
@file:JvmName("StringExtensionsKt")
@file:JvmMultifileClass

package com.pambrose.common.util

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.math.log10

/** Returns `true` if this trimmed [String] is wrapped in single quotes. */
fun String.isSingleQuoted() = trim().run { length >= 2 && startsWith("'") && endsWith("'") }

/** Returns `true` if this trimmed [String] is not wrapped in single quotes. */
fun String.isNotSingleQuoted() = !isSingleQuoted()

/** Returns `true` if this trimmed [String] is wrapped in double quotes. */
fun String.isDoubleQuoted() = trim().run { length >= 2 && startsWith("\"") && endsWith("\"") }

/** Returns `true` if this trimmed [String] is not wrapped in double quotes. */
fun String.isNotDoubleQuoted() = !isDoubleQuoted()

/** Returns `true` if this [String] is either single-quoted or double-quoted. */
fun String.isQuoted() = isSingleQuoted() || isDoubleQuoted()

/** Returns `true` if this [String] is not quoted. */
fun String.isNotQuoted() = !isQuoted()

/** Wraps this [String] in single quotes. */
fun String.toSingleQuoted() = "'$this'"

/** Wraps this [String] in double quotes. */
fun String.toDoubleQuoted() = "\"$this\""

/**
 * Appends [suffix] to this [String] when [cnt] is not 1 (simple English pluralization).
 *
 * @param cnt the count to check
 * @param suffix the suffix to append for plural form (default `"s"`)
 * @return the original string if [cnt] is 1, otherwise the string with [suffix] appended
 */
fun String.pluralize(
  cnt: Int,
  suffix: String = "s",
) = if (cnt == 1) this else "$this$suffix"

/**
 * Converts a single-quoted [String] to a double-quoted [String]. Surrounding whitespace is dropped, and inner
 * backslashes and double quotes are backslash-escaped so the result is a well-formed double-quoted string.
 * A string that is not single-quoted is returned unchanged.
 */
fun String.singleToDoubleQuoted() =
  when {
    !isSingleQuoted() -> {
      this
    }

    else -> {
      trim()
        .let { it.substring(1, it.length - 1) }
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .toDoubleQuoted()
    }
  }

/** Returns `null` if this [String] is blank, otherwise returns the string itself. */
fun String.nullIfBlank() = ifBlank { null }

/**
 * Ensures this [String] starts with [prefix], prepending it if absent.
 *
 * @param prefix the required prefix
 * @return the string guaranteed to start with [prefix]
 */
fun String.ensurePrefix(prefix: CharSequence) = if (startsWith(prefix)) this else "$prefix$this"

/**
 * Ensures this [String] ends with [suffix], appending it if absent.
 *
 * @param suffix the required suffix
 * @return the string guaranteed to end with [suffix]
 */
fun String.ensureSuffix(suffix: CharSequence) = if (this.endsWith(suffix)) this else "$this$suffix"

/** Ensures this [String] starts with a leading `/`. */
fun String.ensureLeadingSlash() = ensurePrefix("/")

/**
 * Joins this list of strings into a path without leading or trailing separators.
 *
 * Extension function on [List]<[String]>.
 *
 * @param separator the path separator (default `"/"`)
 * @return the joined path string
 */
fun List<String>.join(separator: CharSequence = "/") = toPath(addPrefix = false, addTrailing = false, separator)

/**
 * Joins this list of strings into a root-relative path (with a leading separator).
 *
 * Extension function on [List]<[String]>.
 *
 * @param addTrailing whether to add a trailing separator (default `false`)
 * @param separator the path separator (default `"/"`)
 * @return the joined path string
 */
fun List<String>.toRootPath(
  addTrailing: Boolean = false,
  separator: CharSequence = "/",
) = toPath(addPrefix = true, addTrailing = addTrailing, separator = separator)

/**
 * Joins this list of strings into a path with configurable leading and trailing separators.
 *
 * Extension function on [List]<[String]>. Empty elements are skipped, and a leading [separator] on any
 * element after the first is removed, so elements never produce doubled separators.
 *
 * @param addPrefix whether to prepend [separator] to the first element if it lacks one (default `true`)
 * @param addTrailing whether to append [separator] to the last element (default `true`)
 * @param separator the path separator (default `"/"`)
 * @return the joined path string
 */
fun List<String>.toPath(
  addPrefix: Boolean = true,
  addTrailing: Boolean = true,
  separator: CharSequence = "/",
): String {
  val elems = filter { it.isNotEmpty() }
  return elems
    .mapIndexed { i, s -> if (i == 0) (if (addPrefix) s.ensurePrefix(separator) else s) else s.removePrefix(separator) }
    .mapIndexed { i, s -> if (i < elems.size - 1 || addTrailing) s.ensureSuffix(separator) else s }
    .joinToString("")
}

/**
 * Returns the 0-based line number of the first line matching [regex], or -1 if not found.
 *
 * Extension function on [String].
 */
fun String.firstLineNumberOf(regex: Regex) = lines().firstLineNumberOf(regex)

/**
 * Returns the 0-based index of the first element matching [regex], or -1 if not found.
 *
 * Extension function on [List]<[String]>.
 */
fun List<String>.firstLineNumberOf(regex: Regex) =
  asSequence()
    .mapIndexed { i, str -> i to str }
    .filter { it.second.contains(regex) }
    .map { it.first }
    .firstOrNull() ?: -1

/**
 * Returns the 0-based line number of the last line matching [regex], or -1 if not found.
 *
 * Extension function on [String].
 */
fun String.lastLineNumberOf(regex: Regex) = lines().lastLineNumberOf(regex)

/**
 * Returns the 0-based index of the last element matching [regex], or -1 if not found.
 *
 * Extension function on [List]<[String]>.
 */
fun List<String>.lastLineNumberOf(regex: Regex) =
  mapIndexed { i, str -> i to str }
    .asReversed()
    .asSequence()
    .filter { it.second.contains(regex) }
    .map { it.first }
    .firstOrNull() ?: -1

/**
 * Returns the lines between the first match of [start] and the last match of [end], exclusive.
 *
 * Extension function on [String].
 *
 * @param start the regex marking the start boundary
 * @param end the regex marking the end boundary
 * @return the lines between the boundaries, or an empty list if boundaries are not found
 */
fun String.linesBetween(
  start: Regex,
  end: Regex,
) = lines().linesBetween(start, end)

/**
 * Returns the elements between the first match of [start] and the last match of [end], exclusive.
 *
 * Extension function on [List]<[String]>.
 *
 * @param start the regex marking the start boundary
 * @param end the regex marking the end boundary
 * @return the elements between the boundaries, or an empty list if boundaries are not found
 */
fun List<String>.linesBetween(
  start: Regex,
  end: Regex,
): List<String> {
  val startIdx = firstLineNumberOf(start)
  val endIdx = lastLineNumberOf(end)
  if (startIdx == -1 || endIdx == -1 || startIdx + 1 > endIdx) return emptyList()
  return subList(startIdx + 1, endIdx)
}

/**
 * Returns `true` if this trimmed [String] starts with [startChar] and ends with [endChar].
 *
 * @param startChar the expected opening character (default `'['`)
 * @param endChar the expected closing character (default `']'`)
 */
fun String.isBracketed(
  startChar: Char = '[',
  endChar: Char = ']',
) = trim().run { startsWith(startChar) && endsWith(endChar) }

/**
 * Returns `true` if this [String] is not bracketed by [startChar] and [endChar].
 *
 * @param startChar the opening character to check (default `'['`)
 * @param endChar the closing character to check (default `']'`)
 */
fun String.isNotBracketed(
  startChar: Char = '[',
  endChar: Char = ']',
) = !isBracketed(startChar, endChar)

/**
 * Wraps this [String] with [startChar] and [endChar].
 *
 * @param startChar the opening character (default `'['`)
 * @param endChar the closing character (default `']'`)
 * @return the bracketed string
 */
fun String.asBracketed(
  startChar: Char = '[',
  endChar: Char = ']',
) = "$startChar$this$endChar"

/** Returns `true` if this [String] can be parsed as an [Int]. */
fun String.isInt() = toIntOrNull() != null

/** Returns `true` if this [String] cannot be parsed as an [Int]. */
fun String.isNotInt() = !isInt()

/**
 * Returns `true` if this [String] can be parsed as a [Float].
 *
 * Parsing is the platform's own [toFloatOrNull]. All platforms accept a sign, an exponent, surrounding
 * whitespace, `NaN` and `Infinity`. They differ on other forms: the JVM and Kotlin/Native also accept a trailing
 * `f`/`d` and hexadecimal floating-point literals (`0x1p3`), JS accepts hexadecimal integers (`0x10`), and
 * wasmJs accepts neither.
 */
fun String.isFloat() = toFloatOrNull() != null

/** Returns `true` if this [String] cannot be parsed as a [Float]. */
fun String.isNotFloat() = !isFloat()

/**
 * Returns `true` if this [String] can be parsed as a [Double].
 *
 * Parsing is the platform's own [toDoubleOrNull], so the same platform differences as [isFloat] apply.
 */
fun String.isDouble() = toDoubleOrNull() != null

/** Returns `true` if this [String] cannot be parsed as a [Double]. */
fun String.isNotDouble() = !isDouble()

/**
 * Trims whitespace and removes [len] characters from both ends of this [String].
 *
 * @param len the number of characters to remove from each end (default 1)
 * @return the trimmed substring
 * @throws IllegalArgumentException if [len] is negative or the trimmed string is shorter than `2 * len`.
 */
fun String.trimEnds(len: Int = 1): String {
  // Checked explicitly because JS's substring swaps or clamps out-of-range indices instead of throwing.
  require(len >= 0) { "len must not be negative but was $len" }
  val trimmed = trim()
  require(trimmed.length >= 2 * len) { "Cannot remove $len characters from each end of \"$trimmed\"" }
  return trimmed.substring(len, trimmed.length - len)
}

/**
 * Returns the substring between the first occurrence of [begin] and the last occurrence of [end].
 *
 * @param begin the start delimiter
 * @param end the end delimiter
 * @return the substring between the delimiters
 */
fun String.substringBetween(
  begin: String,
  end: String,
) = substringAfter(begin).substringBeforeLast(end)

/**
 * Prepends line numbers to each line of this [String].
 *
 * @param separator the character between the line number and content (default `':'`)
 * @return the string with line numbers
 */
fun String.withLineNumbers(separator: Char = ':'): String {
  val lines = lines()
  val len = (log10(lines.size.toDouble()) + 1).toInt()
  return lines.mapIndexed { i, s -> "${(i + 1).toString().padEnd(len + 1)}$separator $s" }
    .joinToString("\n")
}

private const val REGEX_METACHARACTERS = """\^$.|+()[]{}"""

/**
 * Converts a glob-style pattern (using `*` and `?` wildcards) to a regex pattern string.
 *
 * Extension property on [String]. `*` becomes `.*` and `?` becomes `.`. Every other character matches
 * itself, with the regex metacharacters `\ ^ $ . | + ( ) [ ] { }` escaped.
 */
val String.toPattern: String
  get() =
    buildString {
      append('^')
      this@toPattern.forEach { ch ->
        when (ch) {
          '*' -> append(".*")
          '?' -> append('.')
          in REGEX_METACHARACTERS -> append('\\').append(ch)
          else -> append(ch)
        }
      }
      append('$')
    }

/**
 * Converts this glob-style pattern string to a [Regex].
 *
 * @param ignoreCase whether to ignore case when matching (default `false`)
 * @return the compiled [Regex]
 */
fun String.asRegex(ignoreCase: Boolean = false) =
  if (ignoreCase)
    Regex(this.toPattern, RegexOption.IGNORE_CASE)
  else
    Regex(this.toPattern)

/**
 * Joins the given [elems] into a `/`-separated path, filtering out empty elements.
 *
 * @param elems the path elements
 * @return the joined path string
 */
fun pathOf(vararg elems: Any): String = elems.toList().map { it.toString() }.filter { it.isNotEmpty() }.join("/")

private val AUTHORITY_TERMINATORS = charArrayOf('/', '?', '#')

/**
 * Masks username and password in a URL string, replacing them with `*****`.
 *
 * For example, the credentials in `"https://user:pass@host.com"` become `*****:*****`. Only an `@`
 * inside the authority (between `://` and the first `/`, `?`, or `#`) separates credentials, so an `@` in
 * the path, query, or fragment is left alone. Credentials must be percent-encoded as RFC 3986 requires: an
 * unencoded `/`, `?`, or `#` in a password ends the authority early.
 *
 * @return the URL with masked credentials, or the original string if no credentials are present
 */
fun String.maskUrlCredentials(): String {
  val schemeEnd = indexOf("://")
  if (schemeEnd == -1) return this

  val authorityStart = schemeEnd + 3
  val authorityEnd = indexOfAny(AUTHORITY_TERMINATORS, authorityStart).takeIf { it >= 0 } ?: length
  val at = lastIndexOf('@', authorityEnd - 1)
  return if (at >= authorityStart) replaceRange(authorityStart, at, "*****:*****") else this
}

/**
 * Obfuscates this [String] by replacing characters at every [freq]-th position with `'*'`.
 *
 * Positions count code points, so a surrogate pair (such as an emoji) is replaced or kept as a whole.
 *
 * @param freq the replacement frequency (default every 2nd character, starting at index 0)
 * @return the obfuscated string
 * @throws IllegalArgumentException if [freq] is not positive (it is used as a modulus divisor).
 */
fun String.obfuscate(freq: Int = 2): String {
  require(freq > 0) { "freq must be positive but was $freq" }
  return buildString {
    var index = 0
    var position = 0
    while (index < this@obfuscate.length) {
      val end = index + if (this@obfuscate.isSurrogatePairAt(index)) 2 else 1
      if (position % freq == 0) append('*') else append(this@obfuscate, index, end)
      index = end
      position++
    }
  }
}

private fun String.isSurrogatePairAt(index: Int) =
  this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()

/**
 * Truncates this [String] to at most [len] characters.
 *
 * A surrogate pair (such as an emoji) that would be cut in half is dropped whole, so the result can be one
 * character shorter than [len].
 *
 * @param len the maximum length
 * @return the original string if its length is within [len], otherwise its first [len] characters
 * @throws IllegalArgumentException if [len] is negative.
 */
fun String.maxLength(len: Int): String {
  // Checked explicitly because JS's substring clamps a negative index instead of throwing.
  require(len >= 0) { "len must not be negative but was $len" }
  return when {
    length <= len -> this
    len > 0 && isSurrogatePairAt(len - 1) -> substring(0, len - 1)
    else -> substring(0, len)
  }
}
