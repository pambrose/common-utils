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

import org.slf4j.Logger

// Banner is from: http://patorjk.com/software/taag/#p=display&f=Big%20Money-nw&t=ReadingBat%0A%20%20%20Server
fun getBanner(filename: String, logger: Logger) =
  try {
    logger.javaClass.classLoader.getResourceAsStream(filename)
      .use { inputStream ->
        val banner = inputStream?.bufferedReader()?.use { it.readText() }
          ?: throw IllegalArgumentException("Invalid file name: $filename")

        val lines: List<String> = banner.lines()

        // Trim initial and trailing blank lines, but preserve blank lines in middle;
        var first = -1
        var last = -1
        var lineNum = 0
        lines.forEach { arg1 ->
          if (arg1.trim { arg2 -> arg2 <= ' ' }.isNotEmpty()) {
            if (first == -1)
              first = lineNum
            last = lineNum
          }
          lineNum++
        }

        lineNum = 0

        val vals =
          lines
            .filter {
              val currLine = lineNum++
              currLine in first..last
            }
            .map { arg -> "     $arg" }
            .toList()

        val noNulls = vals.joinToString("\n")
        "\n\n$noNulls\n\n"
      }
  } catch (e: Exception) {
    "Banner \"$filename\" cannot be found"
  }