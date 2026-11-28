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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
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
 * used included in the `Content-Type`. A body sent without a content type, or with one that does not parse (which
 * is logged), is labeled `application/octet-stream`; an empty body without a content type gets no `Content-Type`.
 * `Content-Length` comes from the body actually sent, and the servlet's `Content-Length`, `Transfer-Encoding` and
 * `Upgrade` headers are dropped. A `1xx`, `204`, `205` or `304` response, which carries no content, is sent without
 * a body (whatever the servlet wrote is dropped), without a `Content-Length` (Ktor gives a `205` a
 * `Content-Length: 0`), and with a `Content-Type` only when the servlet set a valid one. Servlet processing runs on
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
  val context = KtorServletContext()
  servlet.init(KtorServletConfig(servlet.javaClass.name, context))
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
      val request = KtorServletRequest(call.request, context)
      val response = KtorServletResponse()
      @Suppress("InjectDispatcher")
      withContext(Dispatchers.IO) { servlet.service(request, response) }
      call.respondServletResponse(response, servlet)
    }
  }
}

// Sends the headers, status and body that [servlet] left in [servletResponse].
private suspend fun ApplicationCall.respondServletResponse(
  servletResponse: KtorServletResponse,
  servlet: HttpServlet,
) {
  servletResponse.getHeaderNames()
    // Ktor sets Content-Length from the body it sends and rejects the unsafe headers (Transfer-Encoding,
    // Upgrade), so the servlet's values would be sent twice or fail the call.
    .filterNot { it.equals(HttpHeaders.ContentLength, ignoreCase = true) || HttpHeaders.isUnsafe(it) }
    .forEach { name ->
      servletResponse.getHeaders(name).forEach { value ->
        response.headers.append(name, value)
      }
    }
  // The servlet's own content type, or null when it set none or set one that does not parse.
  val declaredType =
    servletResponse.getContentType()?.let { type ->
      runCatching { ContentType.parse(type) }
        .onFailure { logger.warn { "Servlet ${servlet.javaClass.name} set a malformed content type: $type" } }
        .getOrNull()
    }
  val status = HttpStatusCode.fromValue(servletResponse.status)
  when {
    // RFC 9110 forbids content in these responses, and a Content-Length of 0 on a 304 would misstate the length of
    // the cached representation. Ktor sends no Content-Length for a NoContent with no length (but gives a 205
    // Content-Length: 0), so the body is dropped and the only Content-Type sent is one the servlet set.
    status.forbidsContent -> {
      respond(headersOnly(status, declaredType, contentLength = null))
    }

    // HttpServlet.doHead runs doGet, leaving the container to drop the body. Ktor sends whatever it is given, and
    // a body after a HEAD response corrupts the connection, so send only the headers GET would have sent.
    request.httpMethod == HttpMethod.Head -> {
      val bodySize = servletResponse.getBodySize()
      respond(headersOnly(status, servletResponse.bodyContentType(declaredType, bodySize), bodySize.toLong()))
    }

    else -> {
      val body = servletResponse.getBodyBytes()
      response.status(status)
      respondBytes(body, servletResponse.bodyContentType(declaredType, body.size))
    }
  }
}

// The statuses whose responses carry no content (RFC 9110 sections 15.2, 15.3.5, 15.3.6 and 15.4.5).
private val HttpStatusCode.forbidsContent: Boolean
  get() =
    value in 100..199 ||
      value == HttpStatusCode.NoContent.value ||
      value == HttpStatusCode.ResetContent.value ||
      value == HttpStatusCode.NotModified.value

// The content type of a body of [bodySize] bytes: the servlet's [declared] one, or application/octet-stream when the
// body is not empty or the servlet set a content type that does not parse. As from a servlet container, an empty body
// with no content type is sent without one, rather than with a fabricated application/octet-stream.
private fun KtorServletResponse.bodyContentType(
  declared: ContentType?,
  bodySize: Int,
): ContentType? = declared ?: ContentType.Application.OctetStream.takeIf { bodySize > 0 || getContentType() != null }

// A response of headers only, with Ktor adding no Content-Length when [contentLength] is null (except on a 205).
private fun headersOnly(
  status: HttpStatusCode,
  contentType: ContentType?,
  contentLength: Long?,
): OutgoingContent =
  object : OutgoingContent.NoContent() {
    override val status = status
    override val contentType = contentType
    override val contentLength = contentLength
  }
