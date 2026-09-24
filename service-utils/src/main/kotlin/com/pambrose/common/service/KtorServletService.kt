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
import com.pambrose.common.servlet.servlet
import com.google.common.util.concurrent.MoreExecutors
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking

/**
 * A Guava [GenericIdleService] that runs an embedded Ktor CIO server to host servlets from an [HttpServletGroup].
 *
 * Each servlet in the group is registered as a Ktor route. The service manages the Ktor server
 * lifecycle, starting it on [startUp] and stopping it on [shutDown].
 *
 * Used by [GenericKtorService] for serving administrative endpoints (ping, version, health check, thread dump).
 *
 * @param port The HTTP port for the Ktor server.
 * @param servletGroup The [HttpServletGroup] containing servlets to register as routes.
 * @param initKtor An optional Ktor [Application] configuration block applied before routing.
 * @param host The interface to bind to, or `null` to bind every interface.
 * @param initBlock An optional initialization block invoked after the service listener is registered.
 */
class KtorServletService(
  private val port: Int,
  private val servletGroup: HttpServletGroup,
  initKtor: Application.() -> Unit = {},
  host: String? = null,
  initBlock: KtorServletService.() -> Unit = {},
) : GenericIdleService() {
  private val ktorServer =
    embeddedServer(CIO, port = port, host = host ?: "0.0.0.0") {
      initKtor()
      routing {
        servletGroup.servletMap.forEach { (path, servlet) ->
          servlet(path, servlet)
        }
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
    servletGroup: HttpServletGroup,
    initKtor: Application.() -> Unit,
    initBlock: KtorServletService.() -> Unit,
  ) : this(port, servletGroup, initKtor, null, initBlock)

  override fun startUp() {
    try {
      ktorServer.start(wait = false)
    } catch (e: Throwable) {
      // start() registers Ktor's JVM shutdown hook and runs the modules, initializing the servlets, before the engine
      // binds its port. Guava never calls shutDown() after a failed startUp(), so stop the server here; otherwise a
      // failed bind leaves the hook holding the server, and the servlets are never destroyed.
      runCatching { ktorServer.stop(0, 0) }.exceptionOrNull()?.let(e::addSuppressed)
      throw e
    }
    // start() returns once the connectors are bound, so they are already resolved.
    boundPort = runBlocking { ktorServer.engine.resolvedConnectors().single().port }
  }

  override fun shutDown() = ktorServer.stop()

  override fun toString() =
    toStringElements {
      add("port", port)
      add("paths", servletGroup.servletMap.keys)
    }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
