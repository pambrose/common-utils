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

import com.google.common.io.CharStreams
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPInputStream.GZIP_MAGIC
import java.util.zip.GZIPOutputStream

/** An empty byte array constant, returned when compressing an empty string. */
val EMPTY_BYTE_ARRAY = ByteArray(0)

/**
 * Compresses this [String] using GZIP encoding.
 *
 * @return a GZIP-compressed byte array, or [EMPTY_BYTE_ARRAY] if this string is empty.
 */
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

/**
 * Checks whether this [ByteArray] has a GZIP magic number header.
 *
 * @return `true` if the byte array starts with the GZIP magic bytes, `false` otherwise.
 */
fun ByteArray.isZipped() = size >= 2 && this[0] == GZIP_MAGIC.toByte() && this[1] == (GZIP_MAGIC shr 8).toByte()

/**
 * Decompresses this [ByteArray] from GZIP encoding back to a [String].
 *
 * If the byte array is empty, returns an empty string. If the byte array is not
 * GZIP-compressed (no GZIP magic header), returns the raw bytes as a string.
 *
 * @return the decompressed string content.
 */
fun ByteArray.unzip(): String =
  when {
    isEmpty() -> {
      ""
    }

    !isZipped() -> {
      String(this)
    }

    else -> {
      ByteArrayInputStream(this).use { bais ->
        GZIPInputStream(bais).use { gzis ->
          InputStreamReader(gzis, StandardCharsets.UTF_8).use { CharStreams.toString(it) }
        }
      }
    }
  }
