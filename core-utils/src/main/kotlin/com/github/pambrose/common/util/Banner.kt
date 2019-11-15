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

import org.slf4j.Logger

fun getBanner(filename: String, logger: Logger) =
    try {
        logger.javaClass.classLoader.getResourceAsStream(filename)
            .use { inputStream ->
                //val utf8 = Charsets.UTF_8.name()
                //val utf8 = kotlin.text.Charsets.UTF_8.name()
                //val banner = CharStreams.toString(InputStreamReader(inputStream ?: throw InternalError(), utf8))
                val banner =
                    inputStream?.bufferedReader()?.use { it.readText() } ?: throw InternalError("Null InputStream")

                //val lines: List<String> = Splitter.on("\n").splitToList(banner)
                val lines: List<String> = banner.split("\n")

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

                //val noNulls = Joiner.on("\n").skipNulls().join(vals)
                val noNulls = vals.joinToString("\n")
                "\n\n$noNulls\n\n"
            }
    } catch (e: Exception) {
        "Banner $filename cannot be found"
    }