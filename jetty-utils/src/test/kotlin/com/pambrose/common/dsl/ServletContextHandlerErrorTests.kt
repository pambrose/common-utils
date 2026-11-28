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

package com.pambrose.common.dsl

import com.pambrose.common.servlet.StatusOnlyErrorHandler
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.ee11.servlet.ErrorHandler
import org.eclipse.jetty.ee11.servlet.ServletContextHandler
import org.eclipse.jetty.ee11.servlet.ServletHolder
import org.eclipse.jetty.server.ServerConnector
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// The server binds the loopback address rather than the wildcard. On macOS another process can bind
// 127.0.0.1 on the same port as a wildcard listener and then receive the test's loopback connections.
private const val LOOPBACK = "127.0.0.1"

private const val FAILURE_MESSAGE = "secret-failure-detail"
private const val FAILURE_CAUSE = "secret-cause-detail"

// A servlet whose every GET throws an exception with a distinctive message and cause.
private class FailingServlet : HttpServlet() {
  override fun doGet(
    req: HttpServletRequest,
    resp: HttpServletResponse,
  ): Unit = throw IllegalStateException(FAILURE_MESSAGE, IOException(FAILURE_CAUSE))
}

// The Accept headers that select each format of an error page: none (HTML), plain text, and JSON.
private val errorPageAcceptHeaders = listOf(null, "text/plain", "application/json")

// Nothing internal may appear in an error page, in any case: not Jetty (its name or the "Powered by" footer), the
// servlet that failed, the exception's class, message or cause.
private val forbidden =
  listOf(
    "jetty",
    "servlet",
    "exception",
    "caused by",
    "com.pambrose",
    FAILURE_MESSAGE,
    FAILURE_CAUSE,
  )

// Serves a context built by servletContextHandler, with the failing servlet at /fail and nothing else, from a real
// Jetty server whose connector keeps Jetty's default HttpConfiguration (sendServerVersion on), and requests path once
// per Accept header.
private fun fetchAll(
  path: String,
  block: ServletContextHandler.() -> Unit = {},
): List<Pair<String, HttpResponse<String>>> {
  val server =
    JettyDsl.server(0, LOOPBACK) {
      handler =
        JettyDsl.servletContextHandler {
          addServlet(ServletHolder(FailingServlet()), "/fail")
          block()
        }
    }
  server.start()
  return try {
    val port = (server.connectors.single() as ServerConnector).localPort
    val client = HttpClient.newHttpClient()
    errorPageAcceptHeaders.map { accept ->
      val request =
        HttpRequest.newBuilder(URI("http://$LOOPBACK:$port$path"))
          .timeout(Duration.ofSeconds(10))
          .apply { accept?.let { header("Accept", it) } }
          .build()
      "$path, Accept ${accept ?: "absent"}" to client.send(request, HttpResponse.BodyHandlers.ofString())
    }
  } finally {
    server.stop()
  }
}

class ServletContextHandlerErrorTests : StringSpec() {
  init {
    errorPageTests()
    errorHandlerTests()
  }

  private fun errorPageTests() {
    "a servlet exception's 500 page names neither Jetty nor the exception, its message or its cause" {
      checkErrorPages("/fail", 500, "Server Error")
    }

    "a 404 page names neither Jetty nor the servlet handler" {
      checkErrorPages("/no-such-path", 404, "Not Found")
    }
  }

  // Each format of path's error page has the status and its reason phrase, and nothing internal.
  private fun checkErrorPages(
    path: String,
    status: Int,
    reason: String,
  ) {
    fetchAll(path).forEach { (label, response) ->
      withClue("$label, body:\n${response.body()}") {
        response.statusCode() shouldBe status
        response.body() shouldContain reason
        forbidden.forEach { response.body().lowercase() shouldNotContain it.lowercase() }
      }
    }
  }

  private fun errorHandlerTests() {
    "the handler starts with the status-only error handler" {
      JettyDsl.servletContextHandler().errorHandler.shouldBeInstanceOf<StatusOnlyErrorHandler>()
    }

    "an error handler set in the block replaces the default" {
      val custom = ErrorHandler()

      JettyDsl.servletContextHandler { errorHandler = custom }.errorHandler shouldBeSameInstanceAs custom
    }

    "a caller's own error handler controls the page" {
      val showsMessage = ErrorHandler().apply { isShowStacks = false }

      fetchAll("/fail") { errorHandler = showsMessage }.forEach { (label, response) ->
        withClue("$label, body:\n${response.body()}") {
          response.statusCode() shouldBe 500
          response.body() shouldContain FAILURE_MESSAGE
        }
      }
    }

    "clearing the error handler in the block falls back to the server's" {
      JettyDsl.servletContextHandler { errorHandler = null }.errorHandler.shouldBeNull()
    }
  }
}
