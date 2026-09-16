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

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.route
import jakarta.servlet.http.HttpServlet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

/**
 * Mounts a Jakarta [HttpServlet] at the given [path] within a Ktor [Route].
 *
 * The servlet is initialized once via `init(ServletConfig)`, as a servlet container would. The config
 * is named after the servlet's class and has no init parameters. Its `ServletContext` supports
 * attributes and logging, and throws [UnsupportedOperationException] for container features such as
 * dynamic registration. Each incoming request is translated into a
 * [KtorServletRequest]/[KtorServletResponse] pair. The servlet's response headers, status code, and
 * body are then forwarded back through the Ktor response pipeline, with the character encoding the servlet
 * used included in the `Content-Type`. A content type that does not parse is logged and replaced by
 * `application/octet-stream`. `Content-Length` comes from the body actually sent, and the servlet's
 * `Content-Length`, `Transfer-Encoding` and `Upgrade` headers are dropped. Servlet processing runs on
 * [kotlinx.coroutines.Dispatchers.IO]. The servlet's `destroy()` is called when the application stops.
 *
 * @param path the URL path at which the servlet should be mounted
 * @param servlet the Jakarta servlet instance to handle requests
 */
fun Route.servlet(
  path: String,
  servlet: HttpServlet,
) {
  // init(ServletConfig) stores the config and then calls the no-arg init(); servlets such as
  // Dropwizard's HealthCheckServlet do their setup only in the former.
  servlet.init(KtorServletConfig(servlet.javaClass.name, KtorServletContext()))
  // Mirror a container's lifecycle: destroy the servlet when this application stops. The event bus can outlive
  // the application (development-mode reloads), so ignore other applications and unsubscribe once done.
  val app = application
  lateinit var subscription: DisposableHandle
  subscription =
    app.monitor.subscribe(ApplicationStopped) { stopped ->
      if (stopped === app) {
        subscription.dispose()
        servlet.destroy()
      }
    }
  route(path) {
    handle {
      val request = KtorServletRequest(call.request)
      val response = KtorServletResponse()
      @Suppress("InjectDispatcher")
      withContext(Dispatchers.IO) { servlet.service(request, response) }
      response.getHeaderNames()
        // Ktor sets Content-Length from the body it sends and rejects the unsafe headers (Transfer-Encoding,
        // Upgrade), so the servlet's values would be sent twice or fail the call.
        .filterNot { it.equals(HttpHeaders.ContentLength, ignoreCase = true) || HttpHeaders.isUnsafe(it) }
        .forEach { name ->
          response.getHeaders(name).forEach { value ->
            call.response.headers.append(name, value)
          }
        }
      val contentType =
        response.getContentType()?.let { type ->
          runCatching { ContentType.parse(type) }
            .onFailure { logger.warn { "Servlet ${servlet.javaClass.name} set a malformed content type: $type" } }
            .getOrNull()
        } ?: ContentType.Application.OctetStream
      call.response.status(HttpStatusCode.fromValue(response.status))
      call.respondBytes(response.getBodyBytes(), contentType)
    }
  }
}
