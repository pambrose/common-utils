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

import com.pambrose.common.concurrent.GenericIdleService
import com.pambrose.common.concurrent.genericServiceListener
import com.pambrose.common.dsl.GuavaDsl.toStringElements
import com.google.common.util.concurrent.MoreExecutors
import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.jetty.ee11.servlet.ServletHolder

/**
 * A Guava [GenericIdleService] that runs an embedded Jetty server to host servlets from a [ServletGroup].
 *
 * Each servlet in the group is registered under the root context path (`/`) at its path, given with or without
 * a leading slash. The service manages the Jetty server lifecycle, starting it on [startUp] and stopping it on
 * [shutDown].
 *
 * Used by [GenericService] for serving administrative endpoints (ping, version, health check, thread dump).
 *
 * @param port The HTTP port for the Jetty server.
 * @param servletGroup The [ServletGroup] containing servlets to register.
 * @param host The interface to bind to, or `null` to bind every interface.
 * @param initBlock An optional initialization block invoked after the service listener is registered.
 */
class ServletService(
  private val port: Int,
  private val servletGroup: ServletGroup,
  host: String? = null,
  initBlock: ServletService.() -> Unit = {},
) : GenericIdleService() {
  private val server =
    jettyServer(host, port) {
      servletGroup.servletMap.forEach { (path, servlet) ->
        addServlet(ServletHolder(servlet), path)
      }
    }

  /**
   * Visible for testing: the port the server listens on, which the OS chooses when [port] is 0. It is updated when
   * the service starts and keeps that value after it stops.
   */
  @Volatile
  internal var boundPort = port
    private set

  init {
    addListener(genericServiceListener(logger), MoreExecutors.directExecutor())
    initBlock(this)
  }

  @Deprecated("Binary compatibility with the constructor that predates host", level = DeprecationLevel.HIDDEN)
  constructor(
    port: Int,
    servletGroup: ServletGroup,
    initBlock: ServletService.() -> Unit,
  ) : this(port, servletGroup, null, initBlock)

  override fun startUp() {
    server.start()
    boundPort = server.localPort
  }

  override fun shutDown() = server.stop()

  override fun toString() =
    toStringElements {
      add("port", port)
      add("paths", servletGroup.servletMap.keys.toList())
    }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
