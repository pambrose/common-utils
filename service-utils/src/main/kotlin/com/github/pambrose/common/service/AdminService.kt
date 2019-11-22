/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.service

import com.github.pambrose.common.concurrent.GenericIdleService
import com.github.pambrose.common.concurrent.genericServiceListener
import com.github.pambrose.common.dsl.GuavaDsl.toStringElements
import com.github.pambrose.common.dsl.JettyDsl.servletContextHandler
import com.google.common.util.concurrent.MoreExecutors
import mu.KLogging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletHolder

class AdminService(private val servletGroup: ServletGroup,
                   initBlock: AdminService.() -> Unit = {}) : GenericIdleService() {

  private val server =
    Server(servletGroup.port)
      .apply {
        handler =
          servletContextHandler {
            contextPath = "/"
            servletGroup.servletMap.forEach { (path, servlet) -> addServlet(ServletHolder(servlet), "/$path") }
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
      add("port", servletGroup.port)
      add("paths", servletGroup.servletMap.keys.map { "/$it" })
    }

  companion object : KLogging()
}
