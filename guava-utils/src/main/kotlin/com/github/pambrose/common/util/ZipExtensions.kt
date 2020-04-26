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

package com.github.pambrose.common.util

import com.google.common.io.CharStreams
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

val EMPTY_BYTE_ARRAY = ByteArray(0)

fun String.zip(): ByteArray =
  if (isEmpty())
    EMPTY_BYTE_ARRAY
  else
    ByteArrayOutputStream().use { baos ->
      GZIPOutputStream(baos).use { gzos ->
        gzos.write(toByteArray(StandardCharsets.UTF_8))
      }
      baos.toByteArray()
    }

fun ByteArray.isZipped() =
  this[0] == GZIPInputStream.GZIP_MAGIC.toByte() && this[1] == (GZIPInputStream.GZIP_MAGIC shr 8).toByte()

fun ByteArray.unzip(): String =
  when {
    size == 0 -> ""
    !isZipped() -> String(this)
    else ->
      ByteArrayInputStream(this).use { bais ->
        GZIPInputStream(bais).use { gzis ->
          InputStreamReader(gzis, StandardCharsets.UTF_8).use { CharStreams.toString(it) }
        }
      }
  }