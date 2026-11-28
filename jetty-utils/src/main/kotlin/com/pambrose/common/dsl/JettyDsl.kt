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
import org.eclipse.jetty.ee11.servlet.ServletContextHandler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector

/**
 * Provides a Kotlin DSL for constructing and configuring Jetty [Server] and [ServletContextHandler] instances.
 */
object JettyDsl {
  /**
   * Creates and configures a Jetty [Server] listening on [port] on every interface.
   *
   * @param port the port number the server will listen on; `0` lets the OS choose one.
   * @param block an optional lambda with [Server] as receiver for configuring the server.
   * @return the configured [Server] instance.
   */
  fun server(
    port: Int,
    block: Server.() -> Unit = {},
  ) = Server(port).apply(block)

  /**
   * Creates and configures a Jetty [Server] with one [ServerConnector] listening on [port] at [host].
   *
   * @param port the port number the server will listen on; `0` lets the OS choose one.
   * @param host the address to bind, such as `"127.0.0.1"`, or `null` for every interface.
   * @param block an optional lambda with [Server] as receiver for configuring the server.
   * @return the configured [Server] instance.
   */
  fun server(
    port: Int,
    host: String?,
    block: Server.() -> Unit = {},
  ): Server =
    Server().apply {
      addConnector(
        ServerConnector(this).also { connector ->
          connector.host = host
          connector.port = port
        },
      )
      block()
    }

  /**
   * Creates and configures a [ServletContextHandler].
   *
   * The handler starts with an error handler whose pages show only the status code, its reason phrase and the
   * request URI: no exception class, message, cause or stack trace, no servlet name, and no "Powered by Jetty"
   * footer. Without one, a servlet error would fall to the server's error handler, which shows the exception's class
   * and message and, unless the connector's `sendServerVersion` is off, the Jetty version. To show more, set
   * [ServletContextHandler.setErrorHandler] in [block], for example to an `ErrorPageErrorHandler`; setting it to
   * `null` hands this context's errors back to the server's error handler.
   *
   * @param block an optional lambda with [ServletContextHandler] as receiver for adding servlets and filters.
   * @return the configured [ServletContextHandler] instance.
   */
  fun servletContextHandler(block: ServletContextHandler.() -> Unit = {}): ServletContextHandler =
    ServletContextHandler().apply {
      errorHandler = StatusOnlyErrorHandler()
      block()
    }
}
