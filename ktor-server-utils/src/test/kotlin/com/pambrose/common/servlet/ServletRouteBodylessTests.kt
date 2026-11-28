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

package com.pambrose.common.servlet

import com.pambrose.common.rawHttpExchange
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.http.toHttpDate
import io.ktor.server.routing.routing
import io.ktor.util.date.GMTDate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

private const val LAST_MODIFIED = 1_700_000_000_000L

// RFC 9110 forbids content in 1xx, 204 and 304 responses, and allows a 304's Content-Length only when it gives the
// length a 200 would have had. The route sent every response through respondBytes, with application/octet-stream as
// the default type, so a 304 got Content-Length: 0 and that type (replacing a cached representation's type on a
// conditional GET), and every bodyless response got the type. Ktor's client may drop or normalize those headers, so
// these tests read the raw response from a real CIO server.
class ServletRouteBodylessTests : StringSpec() {
  init {
    notModifiedTests()
    noContentTests()
    emptyBodyTests()
  }

  // Sends `[method] /s` with [headers] to a CIO server with [servlet] mounted at /s, and returns the raw response.
  private suspend fun exchange(
    servlet: HttpServlet,
    method: String = "GET",
    headers: String = "",
  ): String =
    rawHttpExchange("$method /s HTTP/1.1\r\nHost: localhost\r\n${headers}Connection: close\r\n\r\n") {
      routing { servlet("/s", servlet) }
    }

  private fun notModifiedTests() {
    "a conditional GET answered 304 by HttpServlet sends no Content-Type or Content-Length" {
      val ifModifiedSince = "If-Modified-Since: ${GMTDate(LAST_MODIFIED).toHttpDate()}\r\n"
      exchange(LastModifiedServlet(), headers = ifModifiedSince).apply {
        statusCode shouldBe HttpServletResponse.SC_NOT_MODIFIED
        headerValues("Content-Type").shouldBeEmpty()
        headerValues("Content-Length").shouldBeEmpty()
        body shouldBe ""
      }
    }

    "a 304 set by the servlet sends no Content-Type or Content-Length, on GET and HEAD" {
      listOf("GET", "HEAD").forEach { method ->
        withClue(method) {
          exchange(StatusServlet(HttpServletResponse.SC_NOT_MODIFIED), method).apply {
            statusCode shouldBe HttpServletResponse.SC_NOT_MODIFIED
            headerValues("Content-Type").shouldBeEmpty()
            headerValues("Content-Length").shouldBeEmpty()
          }
        }
      }
    }

    "a 304 keeps a content type the servlet set, but drops a body it wrote" {
      val servlet =
        StatusServlet(HttpServletResponse.SC_NOT_MODIFIED) {
          contentType = "text/plain"
          outputStream.write("ignored".toByteArray())
        }
      exchange(servlet).apply {
        statusCode shouldBe HttpServletResponse.SC_NOT_MODIFIED
        headerValues("Content-Type") shouldBe ["text/plain"]
        headerValues("Content-Length").shouldBeEmpty()
        body shouldBe ""
      }
    }
  }

  private fun noContentTests() {
    "a 204 sends no Content-Type or Content-Length, on GET and HEAD" {
      listOf("GET", "HEAD").forEach { method ->
        withClue(method) {
          exchange(StatusServlet(HttpServletResponse.SC_NO_CONTENT), method).apply {
            statusCode shouldBe HttpServletResponse.SC_NO_CONTENT
            headerValues("Content-Type").shouldBeEmpty()
            headerValues("Content-Length").shouldBeEmpty()
          }
        }
      }
    }

    "a body written on a 204 is dropped rather than sent after the headers" {
      exchange(StatusServlet(HttpServletResponse.SC_NO_CONTENT) { writer.print("ignored") }).apply {
        statusCode shouldBe HttpServletResponse.SC_NO_CONTENT
        headerValues("Content-Length").shouldBeEmpty()
        body shouldBe ""
      }
    }

    // RFC 9110 forbids content in a 205 too, and Ktor always gives one Content-Length: 0.
    "a body written on a 205 is dropped, leaving Content-Length 0 and no fabricated Content-Type" {
      exchange(StatusServlet(HttpServletResponse.SC_RESET_CONTENT) { writer.print("ignored") }).apply {
        statusCode shouldBe HttpServletResponse.SC_RESET_CONTENT
        headerValues("Content-Type").shouldBeEmpty()
        headerValues("Content-Length") shouldBe ["0"]
        body shouldBe ""
      }
    }
  }

  private fun emptyBodyTests() {
    "a redirect with no body gets Content-Length 0 and no fabricated Content-Type" {
      exchange(StatusServlet(HttpServletResponse.SC_OK) { sendRedirect("/new") }).apply {
        statusCode shouldBe HttpServletResponse.SC_FOUND
        headerValues("Location") shouldBe ["/new"]
        headerValues("Content-Type").shouldBeEmpty()
        headerValues("Content-Length") shouldBe ["0"]
      }
    }

    "an empty 200 with no content type gets Content-Length 0 and no Content-Type, on GET and HEAD" {
      listOf("GET", "HEAD").forEach { method ->
        withClue(method) {
          exchange(StatusServlet(HttpServletResponse.SC_OK), method).apply {
            statusCode shouldBe HttpServletResponse.SC_OK
            headerValues("Content-Type").shouldBeEmpty()
            headerValues("Content-Length") shouldBe ["0"]
          }
        }
      }
    }

    "a body with no content type, or a malformed one, is still sent as application/octet-stream" {
      exchange(StatusServlet(HttpServletResponse.SC_OK) { outputStream.write("abc".toByteArray()) }).apply {
        headerValues("Content-Type") shouldBe ["application/octet-stream"]
        headerValues("Content-Length") shouldBe ["3"]
        body shouldBe "abc"
      }
      exchange(StatusServlet(HttpServletResponse.SC_OK) { contentType = "bogus" }).apply {
        headerValues("Content-Type") shouldBe ["application/octet-stream"]
        headerValues("Content-Length") shouldBe ["0"]
      }
    }
  }

  // Overrides getLastModified, so HttpServlet.service answers a fresh If-Modified-Since with a bare 304.
  private class LastModifiedServlet : HttpServlet() {
    override fun getLastModified(req: HttpServletRequest): Long = LAST_MODIFIED

    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.contentType = "text/plain"
      resp.writer.print("fresh")
    }
  }

  // Sets [status], then applies [configure]. HttpServlet.doHead runs doGet, so it answers HEAD as well.
  private class StatusServlet(
    private val status: Int,
    private val configure: HttpServletResponse.() -> Unit = {},
  ) : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.status = status
      resp.configure()
    }
  }
}

private val String.statusCode: Int
  get() = lines().first().split(' ')[1].toInt()

private val String.body: String
  get() = substringAfter("\r\n\r\n")

// The values of every [name] header in this raw response, in the order sent.
private fun String.headerValues(name: String): List<String> =
  substringBefore("\r\n\r\n")
    .lines()
    .drop(1)
    .filter { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
    .map { it.substringAfter(':').trim() }
