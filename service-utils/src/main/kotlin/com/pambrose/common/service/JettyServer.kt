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

import com.pambrose.common.dsl.JettyDsl.servletContextHandler
import org.eclipse.jetty.ee11.servlet.ServletContextHandler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector

/**
 * Creates a Jetty [Server] with one connector on [port], bound to [host] or to every interface when [host] is
 * `null`, whose root context (`/`) holds the servlets that [servlets] adds.
 */
internal fun jettyServer(
  host: String?,
  port: Int,
  servlets: ServletContextHandler.() -> Unit,
): Server =
  Server().apply {
    addConnector(
      ServerConnector(this).also { connector ->
        connector.host = host
        connector.port = port
      },
    )
    handler =
      servletContextHandler {
        contextPath = "/"
        servlets()
      }
  }

/** The port the connector of a [jettyServer] is listening on, which the OS chooses when it was given port 0. */
internal val Server.localPort: Int
  get() = connectors.filterIsInstance<ServerConnector>().single().localPort
