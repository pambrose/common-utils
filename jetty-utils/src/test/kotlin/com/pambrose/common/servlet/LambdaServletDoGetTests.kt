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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.PrintWriter
import java.io.StringWriter

class LambdaServletDoGetTests : StringSpec() {
  private fun createGetRequest(): HttpServletRequest =
    mockk<HttpServletRequest> {
      every { method } returns "GET"
      every { protocol } returns "HTTP/1.1"
      every { getHeader(any()) } returns null
    }

  init {
    "lambda output is written to the response body" {
      val response = mockk<HttpServletResponse>(relaxed = true)
      val stringWriter = StringWriter()
      every { response.writer } returns PrintWriter(stringWriter)

      LambdaServlet { "Hello, World!" }.service(createGetRequest(), response)

      stringWriter.toString().trim() shouldBe "Hello, World!"
    }

    "default constructor yields a text/plain content type" {
      val response = mockk<HttpServletResponse>(relaxed = true)
      every { response.writer } returns PrintWriter(StringWriter())

      LambdaServlet { "body" }.service(createGetRequest(), response)

      verify { response.contentType = "text/plain" }
    }

    "custom content type is honored" {
      val response = mockk<HttpServletResponse>(relaxed = true)
      val stringWriter = StringWriter()
      every { response.writer } returns PrintWriter(stringWriter)

      LambdaServlet("application/json") { """{"status":"ok"}""" }.service(createGetRequest(), response)

      verify { response.contentType = "application/json" }
      stringWriter.toString().trim() shouldBe """{"status":"ok"}"""
    }

    "status is set to SC_OK" {
      val response = mockk<HttpServletResponse>(relaxed = true)
      every { response.writer } returns PrintWriter(StringWriter())

      LambdaServlet { "body" }.service(createGetRequest(), response)

      verify { response.status = HttpServletResponse.SC_OK }
    }

    "cache-control header is set to the no-cache literal" {
      val response = mockk<HttpServletResponse>(relaxed = true)
      every { response.writer } returns PrintWriter(StringWriter())

      LambdaServlet { "body" }.service(createGetRequest(), response)

      verify { response.setHeader("Cache-Control", "must-revalidate,no-cache,no-store") }
    }

    "the lambda is evaluated on every request" {
      var counter = 0
      val servlet = LambdaServlet { "count=${++counter}" }

      val firstWriter = StringWriter()
      val firstResponse = mockk<HttpServletResponse>(relaxed = true)
      every { firstResponse.writer } returns PrintWriter(firstWriter)
      servlet.service(createGetRequest(), firstResponse)

      val secondWriter = StringWriter()
      val secondResponse = mockk<HttpServletResponse>(relaxed = true)
      every { secondResponse.writer } returns PrintWriter(secondWriter)
      servlet.service(createGetRequest(), secondResponse)

      firstWriter.toString().trim() shouldBe "count=1"
      secondWriter.toString().trim() shouldBe "count=2"
    }
  }
}
