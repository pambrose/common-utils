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

import jakarta.servlet.Servlet

/**
 * A mutable collection of Jakarta [Servlet] instances mapped to URL paths.
 *
 * Used by [ServletService] to register servlets with an embedded Jetty server.
 * Servlets are added via [addServlet] and blank/empty paths are silently ignored.
 */
class ServletGroup {
  internal val servletMap: MutableMap<String, Servlet> = mutableMapOf()

  /**
   * Registers a servlet at the given URL path. Paths that are empty or blank are silently ignored.
   *
   * @param path The URL path to map the servlet to.
   * @param servlet The [Servlet] instance to register.
   */
  fun addServlet(
    path: String,
    servlet: Servlet,
  ) {
    if (path.isNotEmpty() && path.isNotBlank())
      servletMap[path] = servlet
  }
}
