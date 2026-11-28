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

package com.pambrose.common.service

import com.pambrose.common.dsl.JettyDsl
import com.pambrose.common.dsl.JettyDsl.servletContextHandler
import org.eclipse.jetty.ee11.servlet.ErrorHandler
import org.eclipse.jetty.ee11.servlet.ServletContextHandler
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.HttpConnectionFactory
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.util.Callback

/**
 * Creates a Jetty [Server] with one connector on [port], bound to [host] or to every interface when [host] is
 * `null`, whose root context (`/`) holds the servlets that [servlets] adds.
 *
 * The admin and metrics endpoints it serves are unauthenticated, so responses do not reveal the Jetty version
 * (no `Server` header, no "Powered by" footer on error pages), and an error page shows only the status code, its
 * reason phrase and the request URI: no stack trace, exception, cause or servlet name.
 */
internal fun jettyServer(
  host: String?,
  port: Int,
  servlets: ServletContextHandler.() -> Unit,
): Server =
  JettyDsl.server(port, host) {
    connectors.single().getConnectionFactory(HttpConnectionFactory::class.java).httpConfiguration.sendServerVersion =
      false
    handler =
      servletContextHandler {
        contextPath = "/"
        errorHandler = StatusOnlyErrorHandler()
        servlets()
      }
  }

/** The port the connector of a [jettyServer] is listening on, which the OS chooses when it was given port 0. */
internal val Server.localPort: Int
  get() = connectors.filterIsInstance<ServerConnector>().single().localPort

/**
 * The servlet [ErrorHandler] of a [jettyServer], whose pages show only the status code, its reason phrase and the
 * request URI.
 *
 * Jetty's servlet handler shows more by default. Its constructor turns on stack traces and the origin row, which
 * names the servlet holder (`ServletHandler$Default404Servlet` on every 404). When a servlet throws, Jetty sets the
 * error message to the exception's `toString()`, which the page puts in its title, heading and message row, and the
 * servlet handler lists every cause whatever `showCauses` says. Jetty's core `ErrorHandler` hides the origin and the
 * causes but still prints that message, so this keeps the servlet handler, whose error dispatch the servlet channel
 * is built around, and replaces the message instead.
 *
 * The HTML, plain-text and JSON pages are all written from the message and cause that [generateResponse] receives,
 * so replacing them there covers every format and status.
 */
private class StatusOnlyErrorHandler : ErrorHandler() {
  init {
    isShowOrigin = false
    isShowStacks = false
    isShowCauses = false
  }

  override fun generateResponse(
    request: Request,
    response: Response,
    code: Int,
    message: String?,
    cause: Throwable?,
    callback: Callback,
  ) = super.generateResponse(request, response, code, HttpStatus.getMessage(code), null, callback)
}
