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

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException

/**
 * An [HttpServlet] that serves the result of a lambda function as the HTTP GET response body.
 *
 * Responses include `Cache-Control: must-revalidate,no-cache,no-store` headers. The body is encoded as UTF-8
 * unless [contentType] names a charset, and holds exactly what the lambda returned, with no line separator
 * appended. If the lambda throws, the response is left untouched, so the container reports the error instead of an
 * empty `200`.
 *
 * Only `doGet` is overridden, and [HttpServlet] answers `HEAD` through it, so a `HEAD` request runs the lambda too.
 *
 * @param contentType the MIME content type for the response. Defaults to `"text/plain"`.
 * @param block a lambda that produces the response body string.
 */
open class LambdaServlet(
  private val contentType: String,
  private val block: () -> String,
) : HttpServlet() {
  /**
   * Constructs a [LambdaServlet] with a default content type of `"text/plain"`.
   *
   * @param block a lambda that produces the response body string.
   */
  constructor(block: () -> String) : this("text/plain", block)

  // Final, so a subclass such as VersionServlet keeps the error handling and encoding below.
  @Throws(ServletException::class, IOException::class)
  final override fun doGet(
    req: HttpServletRequest,
    resp: HttpServletResponse,
  ) {
    // Produce the body before touching the response: once a status is set and the writer closed, the response is
    // committed, and a failure could no longer become a 500.
    val body = block()
    resp.apply {
      status = HttpServletResponse.SC_OK
      setHeader("Cache-Control", "must-revalidate,no-cache,no-store")
      // Jetty assumes ISO-8859-1 for text/plain, which turns other characters into '?'. Set before the content
      // type, so a charset named there still wins.
      characterEncoding = "UTF-8"
      contentType = this@LambdaServlet.contentType
      writer.use { it.print(body) }
    }
  }

  companion object {
    private const val serialVersionUID = -9215048679370216254L
  }
}
