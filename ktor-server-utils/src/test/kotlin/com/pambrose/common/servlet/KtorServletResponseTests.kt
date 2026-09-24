@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.servlet

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import jakarta.servlet.WriteListener
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import java.util.Locale

class KtorServletResponseTests : StringSpec() {
  init {
    "setting and getting status code" {
      val response = KtorServletResponse()
      response.status shouldBe HttpServletResponse.SC_OK

      response.status = HttpServletResponse.SC_NOT_FOUND
      response.status shouldBe HttpServletResponse.SC_NOT_FOUND

      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.status shouldBe HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    }

    "setting and getting content type" {
      val response = KtorServletResponse()
      response.contentType shouldBe null

      response.setContentType("text/html")
      response.contentType shouldBe "text/html"

      response.setContentType("application/json")
      response.contentType shouldBe "application/json"
    }

    "adding and getting headers" {
      val response = KtorServletResponse()
      response.containsHeader("X-Custom") shouldBe false

      response.setHeader("X-Custom", "value1")
      response.containsHeader("X-Custom") shouldBe true
      response.getHeader("X-Custom") shouldBe "value1"

      response.setHeader("X-Custom", "value2")
      response.getHeader("X-Custom") shouldBe "value2"
      response.getHeaders("X-Custom").toList() shouldBe ["value2"]

      response.addHeader("X-Custom", "value3")
      response.getHeaders("X-Custom").toList() shouldBe ["value2", "value3"]
      response.getHeader("X-Custom") shouldBe "value2"

      response.headerNames.toSet() shouldBe setOf("X-Custom")
    }

    // HTTP header field names are case-insensitive (RFC 9110 §5.1); the HttpServletResponse
    // contract treats them that way.
    "header lookups are case-insensitive" {
      val response = KtorServletResponse()
      response.setHeader("X-Foo", "a")

      response.containsHeader("x-foo") shouldBe true
      response.containsHeader("X-FOO") shouldBe true
      response.getHeader("x-foo") shouldBe "a"
      response.getHeaders("X-fOo").toList() shouldBe ["a"]
    }

    "setHeader with different casing overwrites rather than duplicating" {
      val response = KtorServletResponse()
      response.setHeader("X-Foo", "a")
      response.setHeader("x-foo", "b")

      response.getHeaders("X-Foo").toList() shouldBe ["b"]
      response.getHeader("x-FOO") shouldBe "b"
      // A single logical header, retaining the first-inserted casing.
      response.headerNames.toSet() shouldBe setOf("X-Foo")
    }

    "addHeader with different casing appends to the same header" {
      val response = KtorServletResponse()
      response.addHeader("Set-Cookie", "a")
      response.addHeader("set-cookie", "b")

      response.getHeaders("SET-COOKIE").toList() shouldBe ["a", "b"]
      response.headerNames.toSet() shouldBe setOf("Set-Cookie")
    }

    "writer output captures content" {
      val response = KtorServletResponse()
      val writer = response.writer
      writer.print("Hello, ")
      writer.print("World!")
      writer.flush()

      val body = response.getBodyBytes().toString(Charsets.UTF_8)
      body shouldBe "Hello, World!"
    }

    "missing headers return null and an empty collection" {
      val response = KtorServletResponse()
      response.getHeader("X-Missing") shouldBe null
      response.getHeaders("X-Missing").toList() shouldBe emptyList()
    }

    "character encoding defaults to UTF-8 and reflects updates" {
      val response = KtorServletResponse()
      response.characterEncoding shouldBe "UTF-8"

      response.characterEncoding = "ISO-8859-1"
      response.characterEncoding shouldBe "ISO-8859-1"
    }

    "getWriter returns the same writer on repeated calls" {
      val response = KtorServletResponse()
      val writer = response.writer
      response.writer shouldBeSameInstanceAs writer
    }

    "outputStream captures single-byte and ranged writes" {
      val response = KtorServletResponse()
      val stream = response.outputStream
      response.outputStream shouldBeSameInstanceAs stream

      stream.write('H'.code)
      stream.write("Hello".toByteArray(), 1, 4)
      stream.isReady shouldBe true
      stream.setWriteListener(mockk<WriteListener>())

      response.getBodyBytes().toString(Charsets.UTF_8) shouldBe "Hello"
    }

    "getWriter after getOutputStream throws IllegalStateException" {
      val response = KtorServletResponse()
      response.outputStream
      shouldThrow<IllegalStateException> { response.writer }
    }

    "getOutputStream after getWriter throws IllegalStateException" {
      val response = KtorServletResponse()
      response.writer
      shouldThrow<IllegalStateException> { response.outputStream }
    }

    "unsupported response methods throw UnsupportedOperationException" {
      val response = KtorServletResponse()
      shouldThrow<UnsupportedOperationException> { response.addCookie(Cookie("name", "value")) }
      shouldThrow<UnsupportedOperationException> { response.encodeURL("/url") }
      shouldThrow<UnsupportedOperationException> { response.encodeRedirectURL("/url") }
      shouldThrow<UnsupportedOperationException> { response.setBufferSize(1024) }
      shouldThrow<UnsupportedOperationException> { response.bufferSize }
      shouldThrow<UnsupportedOperationException> { response.setLocale(Locale.US) }
      shouldThrow<UnsupportedOperationException> { response.locale }
    }

    "null header values remove or skip the header, and a null content type or encoding clears it" {
      val response = KtorServletResponse()
      response.setHeader("X-A", "1")
      response.setHeader("X-A", null)
      response.addHeader("X-B", null)
      response.containsHeader("X-A") shouldBe false
      response.containsHeader("X-B") shouldBe false
      response.contentType = "text/html; charset=ISO-8859-1"
      response.setContentType(null)
      response.contentType shouldBe null
      response.setCharacterEncoding(null as String?)
      response.characterEncoding shouldBe "UTF-8"
    }

    "int and date headers are formatted, and content length is left to the route" {
      val response = KtorServletResponse()
      response.setIntHeader("X-Count", 1)
      response.addIntHeader("X-Count", 2)
      response.setDateHeader("X-When", 1000L)
      response.addDateHeader("X-When", 2000L)
      response.setContentLength(10)
      response.setContentLengthLong(10L)
      response.getHeaders("X-Count") shouldBe listOf("1", "2")
      response.getHeaders("X-When") shouldBe listOf("Thu, 01 Jan 1970 00:00:01 GMT", "Thu, 01 Jan 1970 00:00:02 GMT")
      response.containsHeader("Content-Length") shouldBe false
    }

    "reset clears status, headers and body; flushBuffer commits, after which resetBuffer fails" {
      val response = KtorServletResponse()
      response.status = 404
      response.setHeader("X-A", "1")
      response.contentType = "text/plain"
      response.writer.print("partial")
      response.reset()
      response.status shouldBe HttpServletResponse.SC_OK
      response.containsHeader("X-A") shouldBe false
      response.getBodyBytes().size shouldBe 0
      response.writer.print("kept")
      response.flushBuffer()
      response.isCommitted shouldBe true
      shouldThrow<IllegalStateException> { response.resetBuffer() }
      response.getBodyBytes().toString(Charsets.UTF_8) shouldBe "kept"
    }

    "sendError sets the status, discards buffered output, writes the message, and commits" {
      val response = KtorServletResponse()
      response.writer.print("partial output")
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "not allowed")
      response.status shouldBe HttpServletResponse.SC_METHOD_NOT_ALLOWED
      response.getBodyBytes().toString(Charsets.UTF_8) shouldBe "not allowed"
      response.contentType shouldBe "text/plain; charset=UTF-8"
      response.isCommitted shouldBe true
    }

    "sendError without a message leaves an empty body" {
      val response = KtorServletResponse()
      response.writer.print("partial output")
      response.sendError(HttpServletResponse.SC_NOT_FOUND)
      response.status shouldBe HttpServletResponse.SC_NOT_FOUND
      response.getBodyBytes().size shouldBe 0
    }

    "sendRedirect sets a redirect status and Location header and commits" {
      val response = KtorServletResponse()
      response.sendRedirect("/elsewhere")
      response.status shouldBe HttpServletResponse.SC_FOUND
      response.getHeader("Location") shouldBe "/elsewhere"
      response.isCommitted shouldBe true

      val permanent = KtorServletResponse()
      permanent.sendRedirect("/moved", HttpServletResponse.SC_MOVED_PERMANENTLY, true)
      permanent.status shouldBe HttpServletResponse.SC_MOVED_PERMANENTLY
      permanent.getHeader("Location") shouldBe "/moved"
    }

    "sendRedirect discards a buffered body by default" {
      val response = KtorServletResponse()
      response.writer.print("partial output")
      response.sendRedirect("/elsewhere")
      response.getBodyBytes().size shouldBe 0
    }

    "sendRedirect with clearBuffer false keeps the buffered body" {
      val response = KtorServletResponse()
      response.writer.print("see other")
      response.sendRedirect("/elsewhere", HttpServletResponse.SC_SEE_OTHER, false)
      response.status shouldBe HttpServletResponse.SC_SEE_OTHER
      response.getHeader("Location") shouldBe "/elsewhere"
      response.getBodyBytes().toString(Charsets.UTF_8) shouldBe "see other"
    }

    "a malformed content type is kept verbatim and leaves the character encoding alone" {
      val response = KtorServletResponse()
      response.characterEncoding = "ISO-8859-1"
      response.setContentType("bogus")
      response.characterEncoding shouldBe "ISO-8859-1"
      // The charset cannot be added to a value that does not parse, so the value is reported as given.
      response.contentType shouldBe "bogus"
      response.writer
      response.contentType shouldBe "bogus"
    }

    "a Content-Type header, in any casing, is the content type rather than a separate header" {
      val response = KtorServletResponse()
      response.containsHeader("Content-Type") shouldBe false
      response.getHeader("Content-Type") shouldBe null

      response.setHeader("content-type", "text/html; charset=ISO-8859-1")
      response.contentType shouldBe "text/html; charset=ISO-8859-1"
      response.characterEncoding shouldBe "ISO-8859-1"
      response.containsHeader("Content-Type") shouldBe true
      response.getHeader("CONTENT-TYPE") shouldBe "text/html; charset=ISO-8859-1"
      response.getHeaders("Content-Type").toList() shouldBe ["text/html; charset=ISO-8859-1"]
      response.headerNames.toList() shouldBe emptyList()

      response.addHeader("Content-Type", "application/json")
      response.getHeaders("Content-Type").toList() shouldBe ["application/json; charset=ISO-8859-1"]
      response.headerNames.toList() shouldBe emptyList()
    }

    "setContentType with a charset parameter sets the character encoding the writer uses" {
      val response = KtorServletResponse()
      response.setContentType("text/html; charset=ISO-8859-1")
      response.characterEncoding shouldBe "ISO-8859-1"
      response.writer.print("caf\u00e9")
      response.getBodyBytes() shouldBe "caf\u00e9".toByteArray(Charsets.ISO_8859_1)
    }

    "getContentType includes the character encoding once it is specified or the writer is used" {
      val specified = KtorServletResponse()
      specified.setContentType("application/json")
      specified.contentType shouldBe "application/json"
      specified.characterEncoding = "ISO-8859-1"
      specified.contentType shouldBe "application/json; charset=ISO-8859-1"

      val written = KtorServletResponse()
      written.setContentType("text/plain")
      written.writer
      written.contentType shouldBe "text/plain; charset=UTF-8"
    }

    "the character encoding cannot change after getWriter is called" {
      val response = KtorServletResponse()
      response.setContentType("text/plain; charset=ISO-8859-1")
      response.writer.print("caf\u00e9")
      response.characterEncoding = "UTF-8"
      response.setContentType("text/html; charset=UTF-8")
      response.characterEncoding shouldBe "ISO-8859-1"
      response.contentType shouldBe "text/html; charset=ISO-8859-1"
      response.getBodyBytes() shouldBe "caf\u00e9".toByteArray(Charsets.ISO_8859_1)
    }
  }
}
