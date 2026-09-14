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

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.Filter
import jakarta.servlet.FilterRegistration
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.Servlet
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletRegistration
import jakarta.servlet.SessionCookieConfig
import jakarta.servlet.SessionTrackingMode
import jakarta.servlet.descriptor.JspConfigDescriptor
import java.io.InputStream
import java.net.URL
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The [ServletConfig] passed to [HttpServlet.init][jakarta.servlet.GenericServlet.init] by [servlet],
 * so servlets that do their setup in `init(ServletConfig)` are initialized as a container would.
 *
 * There are no init parameters.
 */
internal class KtorServletConfig(
  private val name: String,
  private val context: ServletContext,
) : ServletConfig {
  override fun getServletName(): String = name

  override fun getServletContext(): ServletContext = context

  override fun getInitParameter(name: String): String? = null

  override fun getInitParameterNames(): Enumeration<String> = Collections.emptyEnumeration()
}

/**
 * A minimal [ServletContext] backing [KtorServletConfig].
 *
 * Supports attributes and logging, and reports no init parameters, resources, or dispatchers. Dynamic
 * registration, sessions, and other container features throw [UnsupportedOperationException].
 */
internal class KtorServletContext : ServletContext {
  private val attributes = ConcurrentHashMap<String, Any>()

  override fun getAttribute(name: String): Any? = attributes[name]

  override fun getAttributeNames(): Enumeration<String> = Collections.enumeration(attributes.keys)

  override fun setAttribute(
    name: String,
    value: Any?,
  ) {
    if (value == null) attributes.remove(name) else attributes[name] = value
  }

  override fun removeAttribute(name: String) {
    attributes.remove(name)
  }

  override fun log(msg: String) = logger.info { msg }

  override fun log(
    message: String,
    throwable: Throwable,
  ) = logger.error(throwable) { message }

  override fun getInitParameter(name: String): String? = null

  override fun getInitParameterNames(): Enumeration<String> = Collections.emptyEnumeration()

  override fun getContextPath(): String = ""

  override fun getServletContextName(): String? = null

  override fun getServerInfo(): String = "Ktor"

  override fun getMajorVersion(): Int = 6

  override fun getMinorVersion(): Int = 1

  override fun getEffectiveMajorVersion(): Int = 6

  override fun getEffectiveMinorVersion(): Int = 1

  override fun getContext(uripath: String): ServletContext? = null

  override fun getMimeType(file: String): String? = null

  override fun getResourcePaths(path: String): Set<String>? = null

  override fun getResource(path: String): URL? = null

  override fun getResourceAsStream(path: String): InputStream? = null

  override fun getRequestDispatcher(path: String): RequestDispatcher? = null

  override fun getNamedDispatcher(name: String): RequestDispatcher? = null

  override fun getRealPath(path: String): String? = null

  override fun setInitParameter(
    name: String,
    value: String,
  ): Boolean = throw UnsupportedOperationException()

  override fun addServlet(
    servletName: String,
    className: String,
  ): ServletRegistration.Dynamic = throw UnsupportedOperationException()

  override fun addServlet(
    servletName: String,
    servlet: Servlet,
  ): ServletRegistration.Dynamic = throw UnsupportedOperationException()

  override fun addServlet(
    servletName: String,
    servletClass: Class<out Servlet>,
  ): ServletRegistration.Dynamic = throw UnsupportedOperationException()

  override fun addJspFile(
    servletName: String,
    jspFile: String,
  ): ServletRegistration.Dynamic = throw UnsupportedOperationException()

  override fun <T : Servlet> createServlet(clazz: Class<T>): T = throw UnsupportedOperationException()

  override fun getServletRegistration(servletName: String): ServletRegistration = throw UnsupportedOperationException()

  override fun getServletRegistrations(): Map<String, ServletRegistration> = throw UnsupportedOperationException()

  override fun addFilter(
    filterName: String,
    className: String,
  ): FilterRegistration.Dynamic = throw UnsupportedOperationException()

  override fun addFilter(
    filterName: String,
    filter: Filter,
  ): FilterRegistration.Dynamic = throw UnsupportedOperationException()

  override fun addFilter(
    filterName: String,
    filterClass: Class<out Filter>,
  ): FilterRegistration.Dynamic = throw UnsupportedOperationException()

  override fun <T : Filter> createFilter(clazz: Class<T>): T = throw UnsupportedOperationException()

  override fun getFilterRegistration(filterName: String): FilterRegistration = throw UnsupportedOperationException()

  override fun getFilterRegistrations(): Map<String, FilterRegistration> = throw UnsupportedOperationException()

  override fun getSessionCookieConfig(): SessionCookieConfig = throw UnsupportedOperationException()

  override fun setSessionTrackingModes(sessionTrackingModes: Set<SessionTrackingMode>): Unit =
    throw UnsupportedOperationException()

  override fun getDefaultSessionTrackingModes(): Set<SessionTrackingMode> = throw UnsupportedOperationException()

  override fun getEffectiveSessionTrackingModes(): Set<SessionTrackingMode> = throw UnsupportedOperationException()

  override fun addListener(className: String): Unit = throw UnsupportedOperationException()

  override fun <T : EventListener> addListener(t: T): Unit = throw UnsupportedOperationException()

  override fun addListener(listenerClass: Class<out EventListener>): Unit = throw UnsupportedOperationException()

  override fun <T : EventListener> createListener(clazz: Class<T>): T = throw UnsupportedOperationException()

  override fun getJspConfigDescriptor(): JspConfigDescriptor = throw UnsupportedOperationException()

  override fun getClassLoader(): ClassLoader = throw UnsupportedOperationException()

  override fun declareRoles(vararg roleNames: String): Unit = throw UnsupportedOperationException()

  override fun getVirtualServerName(): String = throw UnsupportedOperationException()

  override fun getSessionTimeout(): Int = throw UnsupportedOperationException()

  override fun setSessionTimeout(sessionTimeout: Int): Unit = throw UnsupportedOperationException()

  override fun getRequestCharacterEncoding(): String = throw UnsupportedOperationException()

  override fun setRequestCharacterEncoding(encoding: String): Unit = throw UnsupportedOperationException()

  override fun getResponseCharacterEncoding(): String = throw UnsupportedOperationException()

  override fun setResponseCharacterEncoding(encoding: String): Unit = throw UnsupportedOperationException()

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
