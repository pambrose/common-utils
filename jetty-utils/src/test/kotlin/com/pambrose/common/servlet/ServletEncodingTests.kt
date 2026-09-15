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

import com.pambrose.common.dsl.JettyDsl
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import jakarta.servlet.http.HttpServlet
import org.eclipse.jetty.ee11.servlet.ServletHolder
import org.eclipse.jetty.server.ServerConnector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Characters outside ISO-8859-1, which Jetty otherwise assumes for text/plain.
private const val NON_LATIN_TEXT = "caf\u00e9 \u2615 \u65e5\u672c"

class ServletEncodingTests : StringSpec() {
  // Serves servlet from a real Jetty server on an ephemeral port and returns the response to one GET.
  private fun fetch(servlet: HttpServlet): HttpResponse<ByteArray> {
    val server =
      JettyDsl.server(0) {
        handler = JettyDsl.servletContextHandler { addServlet(ServletHolder(servlet), "/test") }
      }
    server.start()
    return try {
      val port = (server.connectors.single() as ServerConnector).localPort
      HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/test")).timeout(Duration.ofSeconds(10)).build(),
        HttpResponse.BodyHandlers.ofByteArray(),
      )
    } finally {
      server.stop()
    }
  }

  private fun HttpResponse<ByteArray>.contentType() = headers().firstValue("Content-Type").orElse("").lowercase()

  init {
    "LambdaServlet sends text as UTF-8 and says so in the Content-Type" {
      val response = fetch(LambdaServlet { NON_LATIN_TEXT })
      response.body().decodeToString().trim() shouldBe NON_LATIN_TEXT
      response.contentType() shouldContain "charset=utf-8"
    }

    "VersionServlet sends the version as UTF-8" {
      val response = fetch(VersionServlet(NON_LATIN_TEXT))
      response.body().decodeToString().trim() shouldBe NON_LATIN_TEXT
      response.contentType() shouldContain "charset=utf-8"
    }

    "a charset given in the content type is still honored" {
      val response = fetch(LambdaServlet("text/plain; charset=ISO-8859-1") { "caf\u00e9" })
      String(response.body(), Charsets.ISO_8859_1).trim() shouldBe "caf\u00e9"
      response.contentType() shouldContain "charset=iso-8859-1"
    }
  }
}
