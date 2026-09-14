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

import java.io.File

/**
 * Reads `key=value` lines from each of [fileNames] and sets them as JVM system properties.
 *
 * See the [List] overload for the accepted format.
 *
 * @throws IllegalStateException if a file does not exist
 */
fun readProperties(vararg fileNames: String) {
  readProperties(fileNames.toList())
}

/**
 * Reads `key=value` lines from each of [fileNames] and sets them as JVM system properties.
 *
 * The format is a simple subset of `.properties`: one `key=value` per line, split on the first `=`, with the
 * key and value trimmed. Blank lines, comment lines starting with `#` or `!` (after any leading whitespace),
 * lines without `=`, and lines with an empty key are skipped. Escapes, line continuations, and `:` or whitespace
 * separators are not supported.
 *
 * Every file is checked and parsed before any property is set, so a missing file leaves the system properties
 * unchanged. Properties from later files override earlier ones.
 *
 * @throws IllegalStateException if a file does not exist
 */
fun readProperties(fileNames: List<String>) {
  val files = fileNames.map { File(it) }
  files.firstOrNull { !it.exists() }?.let { error("File not found: ${it.absolutePath}") }
  files
    .flatMap { parsePropertyLines(it.readLines()) }
    .forEach { (key, value) -> System.setProperty(key, value) }
}

private fun parsePropertyLines(lines: List<String>): List<Pair<String, String>> =
  lines
    .map { it.trim() }
    .filterNot { it.startsWith("#") || it.startsWith("!") }
    .mapNotNull { line ->
      val key = line.substringBefore("=", missingDelimiterValue = "").trim()
      if (key.isEmpty()) null else key to line.substringAfter("=").trim()
    }
