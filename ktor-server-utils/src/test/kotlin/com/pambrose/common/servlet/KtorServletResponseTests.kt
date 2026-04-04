@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.servlet

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.HttpServletResponse

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
      response.getHeaders("X-Custom").toList() shouldBe listOf("value2")

      response.addHeader("X-Custom", "value3")
      response.getHeaders("X-Custom").toList() shouldBe listOf("value2", "value3")
      response.getHeader("X-Custom") shouldBe "value2"

      response.headerNames.toSet() shouldBe setOf("X-Custom")
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
  }
}
