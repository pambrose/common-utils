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

import jakarta.servlet.http.HttpServlet

/**
 * A mutable collection of Jakarta [HttpServlet] instances mapped to URL paths.
 *
 * Used by [KtorServletService] to register HTTP servlets with an embedded Ktor server.
 * Servlets are added via [addServlet] or [addServlets], and blank/empty paths are silently ignored.
 */
class HttpServletGroup {
  internal val servletMap: MutableMap<String, HttpServlet> = mutableMapOf()

  /**
   * Registers multiple servlets at their respective URL paths.
   *
   * @param servlets Pairs of URL path to [HttpServlet] instance.
   */
  fun addServlets(vararg servlets: Pair<String, HttpServlet>) {
    servlets.forEach { (path, servlet) -> addServlet(path, servlet) }
  }

  /**
   * Registers a single servlet at the given URL path. Paths that are empty or blank are silently ignored.
   *
   * @param path The URL path to map the servlet to.
   * @param servlet The [HttpServlet] instance to register.
   */
  fun addServlet(
    path: String,
    servlet: HttpServlet,
  ) {
    if (path.isNotEmpty() && path.isNotBlank())
      servletMap[path] = servlet
  }
}
