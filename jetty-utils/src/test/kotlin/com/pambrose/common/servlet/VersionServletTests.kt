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

class VersionServletTests : StringSpec() {
  private fun createGetRequest(): HttpServletRequest =
    mockk<HttpServletRequest> {
      every { method } returns "GET"
      every { protocol } returns "HTTP/1.1"
      every { getHeader(any()) } returns null
    }

  init {
    "response content type is set to text/plain" {
      val request = createGetRequest()
      val response = mockk<HttpServletResponse>(relaxed = true)
      val stringWriter = StringWriter()
      every { response.writer } returns PrintWriter(stringWriter)

      val servlet = VersionServlet("1.0.0")
      servlet.service(request, response)

      verify { response.contentType = "text/plain" }
    }

    "cache-control header is set correctly" {
      val request = createGetRequest()
      val response = mockk<HttpServletResponse>(relaxed = true)
      val stringWriter = StringWriter()
      every { response.writer } returns PrintWriter(stringWriter)

      val servlet = VersionServlet("1.0.0")
      servlet.service(request, response)

      verify { response.setHeader("Cache-Control", "must-revalidate,no-cache,no-store") }
    }

    "version string is written to response writer" {
      val request = createGetRequest()
      val response = mockk<HttpServletResponse>(relaxed = true)
      val stringWriter = StringWriter()
      every { response.writer } returns PrintWriter(stringWriter)

      val servlet = VersionServlet("1.0.0")
      servlet.service(request, response)

      stringWriter.toString().trim() shouldBe "1.0.0"
    }

    "different version strings work correctly" {
      val versions = listOf("2.5.3", "0.0.1-SNAPSHOT", "3.0.0-beta.1")

      for (version in versions) {
        val request = createGetRequest()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val stringWriter = StringWriter()
        every { response.writer } returns PrintWriter(stringWriter)

        val servlet = VersionServlet(version)
        servlet.service(request, response)

        stringWriter.toString().trim() shouldBe version
      }
    }
  }
}
