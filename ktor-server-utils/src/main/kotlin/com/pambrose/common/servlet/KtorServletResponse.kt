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

package com.pambrose.common.servlet

import io.ktor.http.ContentType
import io.ktor.http.HeaderValueParam
import io.ktor.http.HttpHeaders
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.WriteListener
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.nio.charset.Charset
import java.util.*

/**
 * A lightweight, in-memory [HttpServletResponse] implementation that captures response headers,
 * status code, and body content written through either [getWriter] or [getOutputStream].
 *
 * This class is intended for use with [servlet] to execute a Jakarta [HttpServlet][jakarta.servlet.http.HttpServlet]
 * within a Ktor route handler. Only the subset of methods needed for typical servlet output is
 * implemented; all other methods throw [UnsupportedOperationException].
 *
 * @see servlet
 */
class KtorServletResponse : HttpServletResponse {
  // HTTP header field names are case-insensitive (RFC 9110 §5.1). A TreeMap with the
  // case-insensitive comparator matches header names regardless of casing while retaining
  // the first-inserted casing for getHeaderNames().
  private val headers = TreeMap<String, MutableList<String>>(String.CASE_INSENSITIVE_ORDER)
  private val buffer = ByteArrayOutputStream()
  private var statusCode: Int = HttpServletResponse.SC_OK
  private var contentTypeValue: String? = null

  // null until a character encoding is specified; getCharacterEncoding() reports UTF-8 until then.
  private var charEncodingValue: String? = null
  private var writerUsed = false
  private var streamUsed = false
  private var printWriter: PrintWriter? = null
  private var servletOutputStream: ServletOutputStream? = null
  private var committed = false

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
    headers[name] = [value]
  }

  override fun addHeader(
    name: String,
    value: String,
  ) {
    headers.getOrPut(name) { [] }.add(value)
  }

  override fun containsHeader(name: String): Boolean = headers.containsKey(name)

  override fun getHeader(name: String): String? = headers[name]?.firstOrNull()

  override fun getHeaders(name: String): Collection<String> = headers[name].orEmpty()

  override fun getHeaderNames(): Collection<String> = headers.keys

  override fun setContentType(type: String) {
    contentTypeValue = type
    // As in a servlet container, a charset parameter in the content type sets the character encoding.
    runCatching { ContentType.parse(type).parameter("charset") }.getOrNull()?.let(::setCharacterEncoding)
  }

  // As the servlet spec requires, the content type includes the character encoding once one has been specified
  // or getWriter() has been called.
  override fun getContentType(): String? =
    contentTypeValue?.let { type ->
      if (charEncodingValue == null && !writerUsed) type else type.withCharset(characterEncoding)
    }

  override fun setCharacterEncoding(charset: String) {
    // As in a servlet container, the encoding cannot change once getWriter() has been called.
    if (!writerUsed)
      charEncodingValue = charset
  }

  override fun getCharacterEncoding(): String = charEncodingValue ?: Charsets.UTF_8.name()

  override fun getWriter(): PrintWriter {
    check(!streamUsed) { "getOutputStream() has already been called on this response" }
    writerUsed = true
    if (printWriter == null) {
      printWriter = PrintWriter(OutputStreamWriter(buffer, Charset.forName(characterEncoding)), true)
    }
    return printWriter ?: error("PrintWriter is null")
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
    return servletOutputStream ?: error("ServletOutputStream is null")
  }

  // sendError and sendRedirect behave as in a servlet container: they set the status, discard any buffered
  // output (for sendRedirect, only when clearBuffer is true), and commit the response.

  override fun sendError(
    sc: Int,
    msg: String?,
  ) {
    discardBufferedOutput()
    statusCode = sc
    committed = true
    if (!msg.isNullOrEmpty()) {
      contentTypeValue = ContentType.Text.Plain.toString()
      // Pin the encoding so the content type names the charset the message is written in.
      charEncodingValue = characterEncoding
      buffer.write(msg.toByteArray(Charset.forName(characterEncoding)))
    }
  }

  override fun sendError(sc: Int) = sendError(sc, null)

  override fun sendRedirect(location: String) = sendRedirect(location, HttpServletResponse.SC_FOUND, true)

  override fun sendRedirect(
    location: String,
    sc: Int,
    clearBuffer: Boolean,
  ) {
    if (clearBuffer)
      discardBufferedOutput()
    statusCode = sc
    setHeader(HttpHeaders.Location, location)
    committed = true
  }

  override fun isCommitted(): Boolean = committed

  private fun discardBufferedOutput() {
    printWriter?.flush()
    buffer.reset()
  }

  // Unsupported methods below

  override fun addCookie(cookie: Cookie) = throw UnsupportedOperationException()

  override fun encodeURL(url: String): String = throw UnsupportedOperationException()

  override fun encodeRedirectURL(url: String): String = throw UnsupportedOperationException()

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

  override fun reset() = throw UnsupportedOperationException()

  override fun setLocale(loc: Locale) = throw UnsupportedOperationException()

  override fun getLocale(): Locale = throw UnsupportedOperationException()
}

// ContentType.withCharset would add a second charset parameter, so any existing one is replaced instead.
private fun String.withCharset(charset: String): String =
  runCatching { ContentType.parse(this) }
    .map { type ->
      val params = type.parameters.filterNot { it.name.equals("charset", ignoreCase = true) }
      ContentType(type.contentType, type.contentSubtype, params + HeaderValueParam("charset", charset)).toString()
    }.getOrDefault(this)
