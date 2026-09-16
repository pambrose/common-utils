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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.application
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

class ServletRouteTests : StringSpec() {
  init {
    "basic GET returning text" {
      testApplication {
        routing {
          servlet("/hello", HelloServlet())
        }
        client.get("/hello").apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "Hello, World!"
        }
      }
    }

    "custom content type" {
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
    }

    "query parameter passthrough" {
      testApplication {
        routing {
          servlet("/greet", GreetServlet())
        }
        client.get("/greet?name=Ktor").apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "Hello, Ktor!"
        }
      }
    }

    "parameter access is case-insensitive and multi-valued across all accessors" {
      testApplication {
        routing {
          servlet("/params", ParamServlet())
        }
        // Query has mixed-case names ("Name", "x") looked up with different casing.
        client.get("/params?Name=Ktor&x=a&x=b").apply {
          status shouldBe HttpStatusCode.OK
          // getParameter("name") -> first value of "Name"; getParameterValues("X") -> all of "x";
          // getParameterMap()["NAME"] -> "Ktor" (case-insensitive lookup; keys keep the request's casing)
          bodyAsText() shouldBe "p=Ktor;v=a,b;map=Ktor;names=Name,x"
        }
      }
    }

    "request header passthrough" {
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
    }

    "custom status codes" {
      testApplication {
        routing {
          servlet("/not-found", NotFoundServlet())
        }
        client.get("/not-found").apply {
          status shouldBe HttpStatusCode.NotFound
          bodyAsText() shouldBe "Not Found"
        }
      }
    }

    "response headers" {
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
    }

    "outputStream-based servlet" {
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
    }

    "POST method dispatch" {
      testApplication {
        routing {
          servlet("/post", PostServlet())
        }
        client.post("/post").apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "posted"
        }
      }
    }

    "servlet is initialized through init(ServletConfig) with a usable ServletContext" {
      testApplication {
        routing {
          servlet("/configured", ConfiguredServlet())
        }
        client.get("/configured").apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "name=${ConfiguredServlet::class.java.name};attr=null;param=null"
        }
      }
    }

    "POST to a servlet that only handles GET returns 405 instead of 500" {
      testApplication {
        routing {
          servlet("/hello", HelloServlet())
        }
        client.post("/hello").status shouldBe HttpStatusCode.MethodNotAllowed
      }
    }

    "servlet sendRedirect produces a redirect with a Location header" {
      testApplication {
        routing {
          servlet("/old", RedirectServlet())
        }
        createClient { followRedirects = false }.get("/old").apply {
          status shouldBe HttpStatusCode.Found
          headers[HttpHeaders.Location] shouldBe "/new"
        }
      }
    }

    // listOf rather than a [...] literal: detekt's analyzer crashes on a collection literal holding lambdas.
    val encodingSetups =
      listOf<Pair<String, HttpServletResponse.() -> Unit>>(
        "setContentType" to { contentType = "text/plain; charset=ISO-8859-1" },
        "setCharacterEncoding" to {
          contentType = "text/plain"
          characterEncoding = "ISO-8859-1"
        },
        // As in a servlet container, a Content-Type header is the content type, whatever its casing.
        "setHeader" to { setHeader("Content-Type", "text/plain; charset=ISO-8859-1") },
        "addHeader" to { addHeader("content-type", "text/plain; charset=ISO-8859-1") },
      )
    encodingSetups.forEach { (method, setEncoding) ->
      "servlet output honors the charset given through $method and labels the Content-Type with it" {
        testApplication {
          routing {
            servlet("/latin1", Latin1Servlet(setEncoding))
          }
          client.get("/latin1").apply {
            bodyAsBytes() shouldBe "caf\u00e9".toByteArray(Charsets.ISO_8859_1)
            contentType()?.charset() shouldBe Charsets.ISO_8859_1
          }
        }
      }
    }

    "a malformed servlet content type falls back to application/octet-stream instead of a 500" {
      testApplication {
        routing {
          servlet("/bogus", BogusContentTypeServlet())
        }
        client.get("/bogus").apply {
          status shouldBe HttpStatusCode.OK
          contentType() shouldBe ContentType.Application.OctetStream
          bodyAsText() shouldBe "x"
        }
      }
    }

    "a servlet that throws produces a 500 without its partial output or headers" {
      testApplication {
        // Respond with a 500, as a production engine does, rather than rethrowing into the test.
        environment { config = MapApplicationConfig("ktor.test.throwOnException" to "false") }
        routing {
          servlet("/throws", ThrowingServlet())
        }
        client.get("/throws").apply {
          status shouldBe HttpStatusCode.InternalServerError
          bodyAsText() shouldNotContain "partial output"
          headers["X-Partial"] shouldBe null
        }
      }
    }

    "every value of a multi-valued response header reaches the client" {
      testApplication {
        routing {
          servlet("/multi", MultiHeaderServlet())
        }
        client.get("/multi").apply {
          status shouldBe HttpStatusCode.OK
          headers.getAll("X-Multi") shouldBe ["a", "b"]
          bodyAsText() shouldBe "ok"
        }
      }
    }

    "the servlet's Content-Length and Transfer-Encoding headers give way to the body actually sent" {
      testApplication {
        routing {
          servlet("/framing", FramingHeaderServlet())
        }
        client.get("/framing").apply {
          status shouldBe HttpStatusCode.OK
          headers.getAll(HttpHeaders.ContentLength) shouldBe ["2"]
          headers[HttpHeaders.TransferEncoding] shouldBe null
          bodyAsText() shouldBe "ok"
        }
      }
    }

    "servlet is destroyed once, when its own application stops" {
      val destroyCount = AtomicInt(0)
      val tracked =
        object : HttpServlet() {
          override fun destroy() {
            destroyCount.incrementAndFetch()
          }
        }
      testApplication {
        routing {
          servlet("/tracked", tracked)
          // Another application stopping on the same event bus, as in a development-mode reload, is ignored.
          application.monitor.raise(ApplicationStopped, mockk<Application>(relaxed = true))
        }
        startApplication()
        destroyCount.load() shouldBe 0
      }
      destroyCount.load() shouldBe 1
    }
  }

  // Test servlets

  // Mirrors Dropwizard's HealthCheckServlet, which does its setup in init(ServletConfig) by reading
  // ServletContext attributes, rather than in the no-arg init().
  private class ConfiguredServlet : HttpServlet() {
    private var contextAttr: Any? = "init(ServletConfig) not called"

    override fun init(config: ServletConfig) {
      super.init(config)
      contextAttr = config.servletContext.getAttribute("missing")
    }

    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      log("handling ${req.requestURI}")
      resp.contentType = "text/plain"
      resp.writer.print("name=$servletName;attr=$contextAttr;param=${getInitParameter("missing")}")
    }
  }

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

  private class ParamServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      val p = req.getParameter("name") // first value, case-insensitive
      val v = req.getParameterValues("X")?.joinToString(",") // all values, case-insensitive
      // getParameterMap is case-insensitive too, like the other accessors, and keeps the request's casing.
      val map = req.parameterMap["NAME"]?.firstOrNull()
      val names = req.parameterNames.toList().sorted().joinToString(",")
      resp.contentType = "text/plain"
      resp.writer.print("p=$p;v=$v;map=$map;names=$names")
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

  private class RedirectServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.sendRedirect("/new")
    }
  }

  private class BogusContentTypeServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.contentType = "bogus"
      resp.writer.print("x")
    }
  }

  private class ThrowingServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.setHeader("X-Partial", "true")
      resp.contentType = "text/plain"
      resp.writer.print("partial output")
      throw ServletException("servlet failed")
    }
  }

  private class MultiHeaderServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.addHeader("X-Multi", "a")
      resp.addHeader("X-Multi", "b")
      resp.contentType = "text/plain"
      resp.writer.print("ok")
    }
  }

  private class FramingHeaderServlet : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.setHeader("content-length", "5")
      resp.setHeader(HttpHeaders.TransferEncoding, "chunked")
      resp.contentType = "text/plain"
      resp.writer.print("ok")
    }
  }

  private class Latin1Servlet(
    private val setEncoding: HttpServletResponse.() -> Unit,
  ) : HttpServlet() {
    override fun doGet(
      req: HttpServletRequest,
      resp: HttpServletResponse,
    ) {
      resp.setEncoding()
      resp.writer.print("caf\u00e9")
    }
  }
}
