package com.github.pambrose.common.util

import com.google.common.io.CharStreams
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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