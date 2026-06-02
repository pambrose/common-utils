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

    // HTTP header field names are case-insensitive (RFC 9110 §5.1); the HttpServletResponse
    // contract treats them that way.
    "header lookups are case-insensitive" {
      val response = KtorServletResponse()
      response.setHeader("X-Foo", "a")

      response.containsHeader("x-foo") shouldBe true
      response.containsHeader("X-FOO") shouldBe true
      response.getHeader("x-foo") shouldBe "a"
      response.getHeaders("X-fOo").toList() shouldBe listOf("a")
    }

    "setHeader with different casing overwrites rather than duplicating" {
      val response = KtorServletResponse()
      response.setHeader("X-Foo", "a")
      response.setHeader("x-foo", "b")

      response.getHeaders("X-Foo").toList() shouldBe listOf("b")
      response.getHeader("x-FOO") shouldBe "b"
      // A single logical header, retaining the first-inserted casing.
      response.headerNames.toSet() shouldBe setOf("X-Foo")
    }

    "addHeader with different casing appends to the same header" {
      val response = KtorServletResponse()
      response.addHeader("Set-Cookie", "a")
      response.addHeader("set-cookie", "b")

      response.getHeaders("SET-COOKIE").toList() shouldBe listOf("a", "b")
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
  }
}
