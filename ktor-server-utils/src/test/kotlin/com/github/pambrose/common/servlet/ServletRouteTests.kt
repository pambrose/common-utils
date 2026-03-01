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

package com.github.pambrose.common.servlet

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlin.test.Test

class ServletRouteTests {
  @Test
  fun `basic GET returning text`() =
    testApplication {
      routing {
        servlet("/hello", HelloServlet())
      }
      client.get("/hello").apply {
        status shouldBe HttpStatusCode.OK
        bodyAsText() shouldBe "Hello, World!"
      }
    }

  @Test
  fun `custom content type`() =
    testApplication {
      routing {
        servlet("/json", JsonServlet())
      }
      client.get("/json").apply {
        status shouldBe HttpStatusCode.OK
        contentType()?.withoutParameters() shouldBe ContentType.Application.Json
        bodyAsText() shouldBe """{"status":"ok"}"""
      }
    }

  @Test
  fun `query parameter passthrough`() =
    testApplication {
      routing {
        servlet("/greet", GreetServlet())
      }
      client.get("/greet?name=Ktor").apply {
        status shouldBe HttpStatusCode.OK
        bodyAsText() shouldBe "Hello, Ktor!"
      }
    }

  @Test
  fun `request header passthrough`() =
    testApplication {
      routing {
        servlet("/echo-header", EchoHeaderServlet())
      }
      client.get("/echo-header") {
        header("X-Custom", "test-value")
      }.apply {
        status shouldBe HttpStatusCode.OK
        bodyAsText() shouldBe "test-value"
      }
    }

  @Test
  fun `custom status codes`() =
    testApplication {
      routing {
        servlet("/not-found", NotFoundServlet())
      }
      client.get("/not-found").apply {
        status shouldBe HttpStatusCode.NotFound
        bodyAsText() shouldBe "Not Found"
      }
    }

  @Test
  fun `response headers`() =
    testApplication {
      routing {
        servlet("/with-headers", ResponseHeaderServlet())
      }
      client.get("/with-headers").apply {
        status shouldBe HttpStatusCode.OK
        headers["X-Custom-Response"] shouldBe "header-value"
        bodyAsText() shouldBe "ok"
      }
    }

  @Test
  fun `outputStream-based servlet`() =
    testApplication {
      routing {
        servlet("/binary", OutputStreamServlet())
      }
      client.get("/binary").apply {
        status shouldBe HttpStatusCode.OK
        contentType()?.withoutParameters() shouldBe ContentType.Text.Plain
        bodyAsText() shouldBe "binary-output"
      }
    }

  @Test
  fun `POST method dispatch`() =
    testApplication {
      routing {
        servlet("/post", PostServlet())
      }
      client.post("/post").apply {
        status shouldBe HttpStatusCode.OK
        bodyAsText() shouldBe "posted"
      }
    }

  // Test servlets

  private class HelloServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.contentType = "text/plain"
      resp.writer.print("Hello, World!")
    }
  }

  private class JsonServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.contentType = "application/json"
      resp.writer.print("""{"status":"ok"}""")
    }
  }

  private class GreetServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      val name = req.getParameter("name") ?: "World"
      resp.contentType = "text/plain"
      resp.writer.print("Hello, $name!")
    }
  }

  private class EchoHeaderServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.contentType = "text/plain"
      resp.writer.print(req.getHeader("X-Custom") ?: "missing")
    }
  }

  private class NotFoundServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.status = HttpServletResponse.SC_NOT_FOUND
      resp.contentType = "text/plain"
      resp.writer.print("Not Found")
    }
  }

  private class ResponseHeaderServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.setHeader("X-Custom-Response", "header-value")
      resp.contentType = "text/plain"
      resp.writer.print("ok")
    }
  }

  private class OutputStreamServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.contentType = "text/plain"
      resp.outputStream.write("binary-output".toByteArray())
    }
  }

  private class PostServlet : HttpServlet() {
    override fun doPost(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.contentType = "text/plain"
      resp.writer.print("posted")
    }
  }
}
