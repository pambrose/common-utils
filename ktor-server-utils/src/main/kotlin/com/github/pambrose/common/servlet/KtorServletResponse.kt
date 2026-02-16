/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

package com.github.pambrose.common.servlet

import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.util.*

class KtorServletResponse : HttpServletResponse {
  private val headers = mutableMapOf<String, MutableList<String>>()
  private val buffer = ByteArrayOutputStream()
  private var statusCode: Int = HttpServletResponse.SC_OK
  private var contentTypeValue: String? = null
  private var charEncodingValue: String = "UTF-8"
  private var writerUsed = false
  private var streamUsed = false
  private var printWriter: PrintWriter? = null
  private var servletOutputStream: ServletOutputStream? = null

  internal fun getBodyBytes(): ByteArray {
    printWriter?.flush()
    return buffer.toByteArray()
  }

  override fun getStatus(): Int = statusCode

  override fun setStatus(sc: Int) {
    statusCode = sc
  }

  override fun setHeader(
    name: String,
    value: String,
  ) {
    headers[name] = mutableListOf(value)
  }

  override fun addHeader(
    name: String,
    value: String,
  ) {
    headers.getOrPut(name) { mutableListOf() }.add(value)
  }

  override fun containsHeader(name: String): Boolean = headers.containsKey(name)

  override fun getHeader(name: String): String? = headers[name]?.firstOrNull()

  override fun getHeaders(name: String): Collection<String> = headers[name] ?: emptyList()

  override fun getHeaderNames(): Collection<String> = headers.keys

  override fun setContentType(type: String) {
    contentTypeValue = type
  }

  override fun getContentType(): String? = contentTypeValue

  override fun setCharacterEncoding(charset: String) {
    charEncodingValue = charset
  }

  override fun getCharacterEncoding(): String = charEncodingValue

  override fun getWriter(): PrintWriter {
    check(!streamUsed) { "getOutputStream() has already been called on this response" }
    writerUsed = true
    if (printWriter == null) {
      printWriter = PrintWriter(buffer, true)
    }
    return printWriter!!
  }

  override fun getOutputStream(): ServletOutputStream {
    check(!writerUsed) { "getWriter() has already been called on this response" }
    streamUsed = true
    if (servletOutputStream == null) {
      servletOutputStream =
        object : ServletOutputStream() {
          override fun write(b: Int) {
            buffer.write(b)
          }

          override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
          ) {
            buffer.write(b, off, len)
          }

          override fun isReady(): Boolean = true

          override fun setWriteListener(writeListener: WriteListener) = Unit
        }
    }
    return servletOutputStream!!
  }

  // Unsupported methods below

  override fun addCookie(cookie: Cookie) = throw UnsupportedOperationException()

  override fun encodeURL(url: String): String = throw UnsupportedOperationException()

  override fun encodeRedirectURL(url: String): String = throw UnsupportedOperationException()

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun encodeUrl(url: String): String = throw UnsupportedOperationException()

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun encodeRedirectUrl(url: String): String = throw UnsupportedOperationException()

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun setStatus(
    sc: Int,
    sm: String,
  ) {
    statusCode = sc
  }

  override fun sendError(
    sc: Int,
    msg: String,
  ) = throw UnsupportedOperationException()

  override fun sendError(sc: Int) = throw UnsupportedOperationException()

  override fun sendRedirect(location: String) = throw UnsupportedOperationException()

  override fun setDateHeader(
    name: String,
    date: Long,
  ) = throw UnsupportedOperationException()

  override fun addDateHeader(
    name: String,
    date: Long,
  ) = throw UnsupportedOperationException()

  override fun setIntHeader(
    name: String,
    value: Int,
  ) = throw UnsupportedOperationException()

  override fun addIntHeader(
    name: String,
    value: Int,
  ) = throw UnsupportedOperationException()

  override fun setContentLength(len: Int) = throw UnsupportedOperationException()

  override fun setContentLengthLong(len: Long) = throw UnsupportedOperationException()

  override fun setBufferSize(size: Int) = throw UnsupportedOperationException()

  override fun getBufferSize(): Int = throw UnsupportedOperationException()

  override fun flushBuffer() = throw UnsupportedOperationException()

  override fun resetBuffer() = throw UnsupportedOperationException()

  override fun isCommitted(): Boolean = throw UnsupportedOperationException()

  override fun reset() = throw UnsupportedOperationException()

  override fun setLocale(loc: Locale) = throw UnsupportedOperationException()

  override fun getLocale(): Locale = throw UnsupportedOperationException()
}
