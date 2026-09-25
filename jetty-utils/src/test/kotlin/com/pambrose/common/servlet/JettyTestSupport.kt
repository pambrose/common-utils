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
import jakarta.servlet.http.HttpServlet
import org.eclipse.jetty.ee11.servlet.ServletHolder
import org.eclipse.jetty.server.ServerConnector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// The server binds the loopback address rather than the wildcard. On macOS another process can bind
// 127.0.0.1 on the same port as a wildcard listener and then receive the test's loopback connections.
private const val LOOPBACK = "127.0.0.1"

// Serves servlet from a real Jetty server on an ephemeral loopback port and returns the response to one
// request. The request is a GET unless configure picks another method.
internal fun fetch(
  servlet: HttpServlet,
  configure: HttpRequest.Builder.() -> Unit = {},
): HttpResponse<ByteArray> {
  val server =
    JettyDsl.server(0, LOOPBACK) {
      handler = JettyDsl.servletContextHandler { addServlet(ServletHolder(servlet), "/test") }
    }
  server.start()
  return try {
    val port = (server.connectors.single() as ServerConnector).localPort
    HttpClient.newHttpClient().send(
      HttpRequest.newBuilder(URI("http://$LOOPBACK:$port/test"))
        .timeout(Duration.ofSeconds(10))
        .apply(configure)
        .build(),
      HttpResponse.BodyHandlers.ofByteArray(),
    )
  } finally {
    server.stop()
  }
}

internal fun HttpResponse<*>.header(name: String): String = headers().firstValue(name).orElse("")

internal fun HttpResponse<*>.contentType(): String = header("Content-Type").lowercase()
