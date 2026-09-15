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

import com.google.common.io.ByteStreams
import com.google.common.math.LongMath
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPInputStream.GZIP_MAGIC
import java.util.zip.GZIPOutputStream

// Returned for empty input. An empty array has no elements to change, so sharing one instance is safe.
private val EMPTY_BYTE_ARRAY = ByteArray(0)

/**
 * Compresses this [String] using GZIP encoding.
 *
 * @return a GZIP-compressed byte array, or an empty array if this string is empty.
 */
fun String.zip(): ByteArray = toByteArray(StandardCharsets.UTF_8).zip()

/**
 * Compresses this [ByteArray] using GZIP encoding.
 *
 * Equivalent to [String.zip] but operates on raw bytes, avoiding a redundant UTF-8 re-encoding when
 * the caller already holds the content as a byte array. For the same content, `bytes.zip()` produces
 * identical output to `string.zip()` when `bytes == string.toByteArray(StandardCharsets.UTF_8)`.
 *
 * @return a GZIP-compressed byte array, or an empty array if this array is empty.
 */
fun ByteArray.zip(): ByteArray =
  if (isEmpty())
    EMPTY_BYTE_ARRAY
  else
    ByteArrayOutputStream().use { baos ->
      GZIPOutputStream(baos).use { gzos ->
        gzos.write(this)
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
 * GZIP-compressed (no GZIP magic header), returns the raw bytes decoded as UTF-8.
 *
 * @param maxBytes the largest decompressed size accepted, which guards against a small input that expands until
 *   memory runs out. Defaults to no limit.
 * @return the decompressed string content.
 * @throws IllegalArgumentException if the decompressed content is larger than [maxBytes], or [maxBytes] is
 *   negative.
 * @throws java.io.IOException if the data has a GZIP header but is corrupt or truncated, such as a
 *   `java.util.zip.ZipException` or `java.io.EOFException`.
 */
@JvmOverloads
fun ByteArray.unzip(maxBytes: Long = Long.MAX_VALUE): String {
  require(maxBytes >= 0) { "maxBytes must not be negative, but was $maxBytes" }
  return when {
    isEmpty() -> {
      ""
    }

    !isZipped() -> {
      // Decode explicitly as UTF-8 to match the encoding side ([String.zip] / [ByteArray.zip]); the
      // bare String(this) used the JVM default charset and corrupted non-ASCII content on non-UTF-8 JVMs.
      String(this, StandardCharsets.UTF_8)
    }

    else -> {
      // Read one byte past the limit, so content of exactly maxBytes is accepted and anything larger is detected.
      val readLimit = LongMath.saturatedAdd(maxBytes, 1)
      val bytes =
        GZIPInputStream(ByteArrayInputStream(this)).use { gzis ->
          ByteStreams.toByteArray(ByteStreams.limit(gzis, readLimit))
        }
      require(bytes.size <= maxBytes) { "Decompressed content is larger than $maxBytes bytes" }
      String(bytes, StandardCharsets.UTF_8)
    }
  }
}
