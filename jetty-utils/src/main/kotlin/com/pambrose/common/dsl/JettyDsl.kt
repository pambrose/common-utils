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

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler

/**
 * Provides a Kotlin DSL for constructing and configuring Jetty [Server] and [ServletContextHandler] instances.
 */
object JettyDsl {
  /**
   * Creates and configures a Jetty [Server] on the specified port.
   *
   * @param port the port number the server will listen on.
   * @param block a lambda with [Server] as receiver for configuring the server.
   * @return the configured [Server] instance.
   */
  fun server(
    port: Int,
    block: Server.() -> Unit,
  ) = Server(port).apply { block(this) }

  /**
   * Creates and configures a [ServletContextHandler].
   *
   * @param block a lambda with [ServletContextHandler] as receiver for adding servlets and filters.
   * @return the configured [ServletContextHandler] instance.
   */
  fun servletContextHandler(block: ServletContextHandler.() -> Unit) = ServletContextHandler().apply { block(this) }
}
