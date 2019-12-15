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

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPInputStream.GZIP_MAGIC
import java.util.zip.GZIPOutputStream


fun String.isSingleQuoted() = trim().run { length >= 2 && startsWith("'") && endsWith("'") }

fun String.isDoubleQuoted() = trim().run { length >= 2 && startsWith("\"") && endsWith("\"") }

fun String.isQuoted() = isSingleQuoted() || isDoubleQuoted()

fun String.singleQuoted() = "'$this'"

fun String.doubleQuoted() = "\"$this\""

fun String.pluralize(cnt: Int) = if (cnt == 1) this else "${this}s"

val EMPTY_BYTE_ARRAY = ByteArray(0)

fun String.zip(): ByteArray {
  if (isEmpty())
    return EMPTY_BYTE_ARRAY
  else
    ByteArrayOutputStream()
      .use { baos ->
        GZIPOutputStream(baos)
          .use { gzos ->
            gzos.write(toByteArray(StandardCharsets.UTF_8))
          }
        return baos.toByteArray()
      }
}

fun ByteArray.isZipped() = this[0] == GZIP_MAGIC.toByte() && this[1] == (GZIP_MAGIC shr 8).toByte()

fun ByteArray.unzip(): String {
  when {
    size == 0 -> return ""
    !isZipped() -> return String(this)
    else -> {
      ByteArrayInputStream(this)
        .use { bais ->
          GZIPInputStream(bais)
            .use { gzis ->
              InputStreamReader(gzis, StandardCharsets.UTF_8)
                .use { isReader ->
                  BufferedReader(isReader)
                    .use { bufferedReader ->
                      val output = StringBuilder()
                      var line: String?
                      while (bufferedReader.readLine().also { line = it } != null)
                        output.append(line)
                      return output.toString()
                    }
                }
            }
        }
    }
  }
}
