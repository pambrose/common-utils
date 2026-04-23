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
import com.pambrose.common.dsl.JettyDsl.servletContextHandler
import com.google.common.util.concurrent.MoreExecutors
import io.github.oshai.kotlinlogging.KotlinLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.ee11.servlet.ServletHolder

/**
 * A Guava [GenericIdleService] that runs an embedded Jetty server to host servlets from a [ServletGroup].
 *
 * Each servlet in the group is registered under the root context path (`/`). The service
 * manages the Jetty server lifecycle, starting it on [startUp] and stopping it on [shutDown].
 *
 * Used by [GenericService] for serving administrative endpoints (ping, version, health check, thread dump).
 *
 * @param port The HTTP port for the Jetty server.
 * @param servletGroup The [ServletGroup] containing servlets to register.
 * @param initBlock An optional initialization block invoked after the service listener is registered.
 */
class ServletService(
  private val port: Int,
  private val servletGroup: ServletGroup,
  initBlock: ServletService.() -> Unit = {},
) : GenericIdleService() {
  private val server =
    Server(port)
      .apply {
        handler =
          servletContextHandler {
            contextPath = "/"
            servletGroup.servletMap.forEach { (path, servlet) ->
              addServlet(ServletHolder(servlet), "/$path")
            }
          }
      }

  init {
    addListener(genericServiceListener(logger), MoreExecutors.directExecutor())
    initBlock(this)
  }

  override fun startUp() = server.start()

  override fun shutDown() = server.stop()

  override fun toString() =
    toStringElements {
      add("port", port)
      add("paths", servletGroup.servletMap.keys.map { "/$it" })
    }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
