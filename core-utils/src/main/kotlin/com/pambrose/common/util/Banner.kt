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

import io.github.oshai.kotlinlogging.KLogger

/**
 * Loads an ASCII art banner from a classpath resource file, trims leading/trailing blank lines,
 * and indents each line for display.
 *
 * Banner text can be generated at: http://patorjk.com/software/taag/
 *
 * @param filename the classpath resource file containing the banner text
 * @param logger a [KLogger] whose classloader is used to locate the resource
 * @return the formatted banner string with surrounding newlines
 * @throws IllegalArgumentException if the banner resource file is not found
 */
fun getBanner(
  filename: String,
  logger: KLogger,
): String {
  val banner = logger.javaClass.classLoader.getResource(filename)?.readText()
    ?: throw IllegalArgumentException("Banner not found: \"$filename\"")

  val lines = banner.lines()

  // Trim initial and trailing blank lines, but preserve blank lines in middle;
  var first = -1
  var last = -1
  lines.forEachIndexed { index, line ->
    if (line.trim { arg -> arg <= ' ' }.isNotEmpty()) {
      if (first == -1)
        first = index
      last = index
    }
  }

  val vals =
    lines
      .filterIndexed { index, _ -> index in first..last }
      .map { arg -> "     $arg" }

  return "\n\n${vals.joinToString("\n")}\n\n"
}
