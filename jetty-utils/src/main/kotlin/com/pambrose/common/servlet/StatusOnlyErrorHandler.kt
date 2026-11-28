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

import org.eclipse.jetty.ee11.servlet.ErrorHandler
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.io.Writer

/**
 * The servlet [ErrorHandler] that [com.pambrose.common.dsl.JettyDsl.servletContextHandler] installs, whose pages
 * show only the status code, its reason phrase and the request URI.
 *
 * Without a context error handler, a servlet error falls to the server's, which puts the error message in the page.
 * When a servlet throws, Jetty sets that message to the exception's `toString()` (its class and message). Jetty's
 * servlet handler would show more still: its constructor turns on stack traces and the origin row, which names the
 * servlet holder (`ServletHandler$Default404Servlet` on every 404), and it lists every cause whatever `showCauses`
 * says. So this keeps the servlet handler, whose error dispatch the servlet channel is built around, turns those off,
 * and replaces the message and cause in [generateResponse]. The HTML, plain-text and JSON pages are all written from
 * what [generateResponse] receives, so that covers every format and status.
 *
 * Both handlers also end an HTML page with a "Powered by Jetty" footer, naming the version, whenever the connector's
 * `HttpConfiguration.sendServerVersion` is on, as it is by default. A context handler cannot turn that setting off,
 * so [writeErrorHtmlBody] leaves the footer out.
 */
internal class StatusOnlyErrorHandler : ErrorHandler() {
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

  override fun writeErrorHtmlBody(
    request: Request,
    writer: Writer,
    code: Int,
    message: String?,
    cause: Throwable?,
  ) = writeErrorHtmlMessage(request, writer, code, message, cause, request.httpURI.toString())
}
