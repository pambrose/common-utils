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

package com.pambrose.common.servlet

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.Parameters
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.httpVersion
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import jakarta.servlet.AsyncContext
import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.ServletConnection
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletInputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletMapping
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import jakarta.servlet.http.HttpUpgradeHandler
import jakarta.servlet.http.Part
import java.io.BufferedReader
import java.security.Principal
import java.util.*

/**
 * An [HttpServletRequest] adapter that wraps a Ktor [ApplicationRequest], exposing HTTP method,
 * URI, query parameters, headers, and connection metadata through the standard servlet API.
 *
 * Only the subset of methods commonly needed by servlet-based libraries is implemented;
 * all other methods throw [UnsupportedOperationException]. Parameter names are case-insensitive, unlike in a
 * servlet container, and [getPathInfo] is always `null` because the servlet is mounted at its exact path.
 *
 * @param request the Ktor [ApplicationRequest] to delegate to
 * @see servlet
 */
internal class KtorServletRequest(
  private val request: ApplicationRequest,
) : HttpServletRequest {
  // Case-insensitive, and names that differ only in case are merged: ?id=1&ID=2 gives getParameterValues("id") == [1, 2].
  private val params: Parameters by lazy { request.queryParameters }
  private val attributes = mutableMapOf<String, Any>()

  // Set by Route.servlet to the context the servlet was initialized with.
  internal var context: ServletContext? = null

  override fun getMethod(): String = request.httpMethod.value

  override fun getRequestURI(): String = request.path()

  override fun getQueryString(): String? = request.queryString().ifEmpty { null }

  override fun getParameter(name: String): String? = params[name]

  override fun getParameterNames(): Enumeration<String> = Collections.enumeration(params.names())

  override fun getParameterValues(name: String): Array<String>? = params.getAll(name)?.toTypedArray()

  override fun getParameterMap(): Map<String, Array<String>> =
    params.entries().associateTo(TreeMap(String.CASE_INSENSITIVE_ORDER)) { (key, values) ->
      key to values.toTypedArray()
    }

  override fun getHeader(name: String): String? = request.headers[name]

  override fun getHeaders(name: String): Enumeration<String> =
    Collections.enumeration(request.headers.getAll(name).orEmpty())

  override fun getHeaderNames(): Enumeration<String> = Collections.enumeration(request.headers.names())

  override fun getScheme(): String = request.local.scheme

  override fun getServerName(): String = request.local.serverHost

  override fun getServerPort(): Int = request.local.serverPort

  override fun getProtocol(): String = request.httpVersion

  override fun getContentType(): String? = request.headers[HttpHeaders.ContentType]

  override fun getRemoteAddr(): String = request.local.remoteAddress

  override fun getContextPath(): String = ""

  override fun getServletPath(): String = request.path()

  override fun getPathInfo(): String? = null

  override fun getAttribute(name: String): Any? = attributes[name]

  override fun getAttributeNames(): Enumeration<String> = Collections.enumeration(attributes.keys)

  override fun setAttribute(
    name: String,
    o: Any?,
  ) {
    if (o == null) attributes.remove(name) else attributes[name] = o
  }

  override fun removeAttribute(name: String) {
    attributes.remove(name)
  }

  // As the servlet spec defines, null when the request carries no cookies.
  override fun getCookies(): Array<Cookie>? =
    request.cookies.rawCookies
      .map { (name, value) -> Cookie(name, value) }
      .takeIf { it.isNotEmpty() }
      ?.toTypedArray()

  // -1 when the header is absent; IllegalArgumentException when it is not an HTTP date, as the servlet spec defines.
  override fun getDateHeader(name: String): Long =
    getHeader(name)?.let { value ->
      runCatching { value.fromHttpToGmtDate().timestamp }
        .getOrElse { throw IllegalArgumentException("Header $name is not a date: $value", it) }
    } ?: -1L

  // -1 when the header is absent; NumberFormatException when it is not an integer, as the servlet spec defines.
  override fun getIntHeader(name: String): Int = getHeader(name)?.trim()?.toInt() ?: -1

  override fun getRequestURL(): StringBuffer =
    StringBuffer().apply {
      append(scheme).append("://").append(serverName)
      val defaultPort = if (isSecure) 443 else 80
      if (serverPort > 0 && serverPort != defaultPort)
        append(':').append(serverPort)
      append(requestURI)
    }

  override fun isSecure(): Boolean = scheme.equals("https", ignoreCase = true)

  // The charset parameter of the Content-Type header, or null when there is none.
  override fun getCharacterEncoding(): String? =
    contentType?.let { runCatching { ContentType.parse(it).parameter("charset") }.getOrNull() }

  override fun getContentLength(): Int = contentLengthLong.let { if (it > Int.MAX_VALUE) -1 else it.toInt() }

  override fun getContentLengthLong(): Long = getHeader(HttpHeaders.ContentLength)?.trim()?.toLongOrNull() ?: -1L

  override fun getDispatcherType(): DispatcherType = DispatcherType.REQUEST

  override fun getServletContext(): ServletContext =
    context ?: throw UnsupportedOperationException("This request is not bound to a servlet context")

  // Unsupported methods below

  override fun getAuthType(): String = throw UnsupportedOperationException()

  override fun getPathTranslated(): String = throw UnsupportedOperationException()

  override fun getRemoteUser(): String = throw UnsupportedOperationException()

  override fun isUserInRole(role: String): Boolean = throw UnsupportedOperationException()

  override fun getUserPrincipal(): Principal = throw UnsupportedOperationException()

  override fun getRequestedSessionId(): String = throw UnsupportedOperationException()

  override fun getSession(create: Boolean): HttpSession = throw UnsupportedOperationException()

  override fun getSession(): HttpSession = throw UnsupportedOperationException()

  override fun changeSessionId(): String = throw UnsupportedOperationException()

  override fun isRequestedSessionIdValid(): Boolean = throw UnsupportedOperationException()

  override fun isRequestedSessionIdFromCookie(): Boolean = throw UnsupportedOperationException()

  override fun isRequestedSessionIdFromURL(): Boolean = throw UnsupportedOperationException()

  override fun authenticate(response: HttpServletResponse): Boolean = throw UnsupportedOperationException()

  override fun login(
    username: String,
    password: String,
  ): Unit = throw UnsupportedOperationException()

  override fun logout(): Unit = throw UnsupportedOperationException()

  override fun getParts(): Collection<Part> = throw UnsupportedOperationException()

  override fun getPart(name: String): Part = throw UnsupportedOperationException()

  override fun <T : HttpUpgradeHandler> upgrade(handlerClass: Class<T>): T = throw UnsupportedOperationException()

  override fun getHttpServletMapping(): HttpServletMapping = throw UnsupportedOperationException()

  override fun setCharacterEncoding(env: String): Unit = throw UnsupportedOperationException()

  override fun getInputStream(): ServletInputStream = throw UnsupportedOperationException()

  override fun getLocalName(): String = throw UnsupportedOperationException()

  override fun getLocalAddr(): String = throw UnsupportedOperationException()

  override fun getLocalPort(): Int = throw UnsupportedOperationException()

  override fun startAsync(): AsyncContext = throw UnsupportedOperationException()

  override fun startAsync(
    servletRequest: ServletRequest,
    servletResponse: ServletResponse,
  ): AsyncContext = throw UnsupportedOperationException()

  override fun isAsyncStarted(): Boolean = throw UnsupportedOperationException()

  override fun isAsyncSupported(): Boolean = throw UnsupportedOperationException()

  override fun getAsyncContext(): AsyncContext = throw UnsupportedOperationException()

  override fun getRemoteHost(): String = throw UnsupportedOperationException()

  override fun getRemotePort(): Int = throw UnsupportedOperationException()

  override fun getLocale(): Locale = throw UnsupportedOperationException()

  override fun getLocales(): Enumeration<Locale> = throw UnsupportedOperationException()

  override fun getRequestDispatcher(path: String): RequestDispatcher = throw UnsupportedOperationException()

  override fun getReader(): BufferedReader = throw UnsupportedOperationException()

  override fun getRequestId(): String = throw UnsupportedOperationException()

  override fun getProtocolRequestId(): String = throw UnsupportedOperationException()

  override fun getServletConnection(): ServletConnection = throw UnsupportedOperationException()
}
