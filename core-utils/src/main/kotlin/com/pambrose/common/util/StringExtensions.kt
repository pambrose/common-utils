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

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
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

/** Converts a single-quoted [String] to a double-quoted [String], escaping inner double quotes. */
fun String.singleToDoubleQuoted() =
  when {
    !isSingleQuoted() -> this
    else -> subSequence(1, length - 1).replace(Regex("\""), "\\\"").toDoubleQuoted()
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
fun String.ensureSuffix(suffix: CharSequence) = if (this.endsWith(suffix)) this else this + suffix

/** Ensures this [String] starts with a leading `/`. */
fun String.ensureLeadingSlash() = ensurePrefix("/")

/** URL-decodes this [String] using UTF-8 encoding. */
fun String.decode() = URLDecoder.decode(this, UTF_8.toString()) ?: this

/** URL-encodes this [String] using UTF-8 encoding. */
fun String.encode() = URLEncoder.encode(this, UTF_8.toString()) ?: this

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
 * Extension function on [List]<[String]>.
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
) = mapIndexed { i, s -> if (i == 0 && addPrefix && !s.startsWith(separator)) "$separator$s" else s }
  .mapIndexed { i, s -> if (i != 0 && s.startsWith(separator)) s.substring(1) else s }
  .mapIndexed { i, s -> if (i < size - 1 || addTrailing) s.ensureSuffix(separator) else s }
  .joinToString("")

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
fun String.isInt() =
  try {
    this.toInt()
    true
  } catch (e: Exception) {
    false
  }

/** Returns `true` if this [String] cannot be parsed as an [Int]. */
fun String.isNotInt() = !isInt()

/** Returns `true` if this [String] can be parsed as a [Float]. */
fun String.isFloat() =
  try {
    this.toFloat()
    true
  } catch (e: Exception) {
    false
  }

/** Returns `true` if this [String] cannot be parsed as a [Float]. */
fun String.isNotFloat() = !isFloat()

/** Returns `true` if this [String] can be parsed as a [Double]. */
fun String.isDouble() =
  try {
    this.toDouble()
    true
  } catch (e: Exception) {
    false
  }

/** Returns `true` if this [String] cannot be parsed as a [Double]. */
fun String.isNotDouble() = !isDouble()

/**
 * Trims whitespace and removes [len] characters from both ends of this [String].
 *
 * @param len the number of characters to remove from each end (default 1)
 * @return the trimmed substring
 */
fun String.trimEnds(len: Int = 1) = trim().run { substring(len, this.length - len) }

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

private const val DOT = "__SINGLE__DOT__"

/**
 * Converts a glob-style pattern (using `*` and `?` wildcards) to a regex pattern string.
 *
 * Extension property on [String]. Dots are escaped, `*` becomes `.*`, and `?` becomes `.`.
 */
val String.toPattern: String
  get() {
    // First protect the period and then convert back at end
    val pattern =
      replace(".", DOT)
        .replace("*", ".*")
        .replace("?", ".")
        .replace(DOT, """\.""")
    return """^$pattern$"""
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
 * Computes the MD5 hash of this [String] with an optional string [salt].
 *
 * @param salt the salt string (default empty)
 * @return the hex-encoded MD5 hash
 */
fun String.md5(salt: String = ""): String = encodedByteArray(this, { salt }, "MD5").asText

/**
 * Computes the SHA-256 hash of this [String] with an optional string [salt].
 *
 * @param salt the salt string (default empty)
 * @return the hex-encoded SHA-256 hash
 */
fun String.sha256(salt: String = ""): String = encodedByteArray(this, { salt }, "SHA-256").asText

/**
 * Computes the MD5 hash of this [String] with a byte array [salt].
 *
 * @param salt the salt bytes
 * @return the hex-encoded MD5 hash
 */
fun String.md5(salt: ByteArray): String = encodedByteArray(this, salt, "MD5").asText

/**
 * Computes the SHA-256 hash of this [String] with a byte array [salt].
 *
 * @param salt the salt bytes
 * @return the hex-encoded SHA-256 hash
 */
fun String.sha256(salt: ByteArray): String = encodedByteArray(this, salt, "SHA-256").asText

/**
 * Converts this [ByteArray] to a lowercase hex string.
 *
 * Extension property on [ByteArray].
 */
val ByteArray.asText get() = fold("") { str, it -> str + "%02x".format(it) }

private fun encodedByteArray(
  input: String,
  salt: ByteArray,
  algorithm: String,
) = with(MessageDigest.getInstance(algorithm)) {
  update(salt)
  digest(input.toByteArray(Charsets.UTF_8))
}

private fun encodedByteArray(
  input: String,
  salt: (String) -> String,
  algorithm: String,
) = with(MessageDigest.getInstance(algorithm)) {
  update(salt(input).toByteArray(Charsets.UTF_8))
  digest(input.toByteArray(Charsets.UTF_8))
}

/**
 * Generates a cryptographically secure random byte array for use as a salt.
 *
 * @param len the length of the salt in bytes (default 16)
 * @return a random [ByteArray]
 */
fun newByteArraySalt(len: Int = 16): ByteArray = ByteArray(len).apply { SecureRandom().nextBytes(this) }

/**
 * Generates a cryptographically secure random string for use as a salt.
 *
 * @param len the length of the salt string (default 16)
 * @return a random alphanumeric string
 */
fun newStringSalt(len: Int = 16): String = randomId(len)

/**
 * Computes an MD5 hash of the given [keys] joined by [separator].
 *
 * @param keys the values to hash
 * @param separator the separator between key values (default `"|"`)
 * @return the hex-encoded MD5 hash
 */
fun md5Of(
  vararg keys: Any,
  separator: String = "|",
) = keys.joinToString(separator) { it.toString() }.md5()

/**
 * Joins the given [elems] into a `/`-separated path, filtering out empty elements.
 *
 * @param elems the path elements
 * @return the joined path string
 */
fun pathOf(vararg elems: Any): String = elems.toList().map { it.toString() }.filter { it.isNotEmpty() }.join("/")

/**
 * Masks username and password in a URL string, replacing them with `*****`.
 *
 * For example, `"https://user:pass@host.com"` becomes `"https://xxxxx:xxxxx@host.com"`.
 *
 * @return the URL with masked credentials, or the original string if no credentials are present
 */
fun String.maskUrlCredentials() =
  if ("://" in this && "@" in this) {
    val scheme = substringBefore("://")
    val host = substringAfterLast("@")
    "$scheme://*****:*****@$host"
  } else {
    this
  }

/**
 * Obfuscates this [String] by replacing characters at every [freq]-th position with `'*'`.
 *
 * @param freq the replacement frequency (default every 2nd character, starting at index 0)
 * @return the obfuscated string
 */
fun String.obfuscate(freq: Int = 2) = mapIndexed { i, v -> if (i % freq == 0) '*' else v }.joinToString("")

/**
 * Truncates this [String] to at most [len] characters.
 *
 * @param len the maximum length
 * @return the original string if its length is within [len], otherwise the first [len] characters
 */
fun String.maxLength(len: Int) = if (length <= len) this else this.substring(0, len)
