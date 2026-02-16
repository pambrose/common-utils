/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.servlet

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.httpVersion
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import jakarta.servlet.AsyncContext
import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
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

class KtorServletRequest(
  private val request: ApplicationRequest,
) : HttpServletRequest {
  private val params: Map<String, List<String>> by lazy {
    request.queryParameters
      .entries()
      .associate { (key, values) -> key to values }
  }

  override fun getMethod(): String = request.httpMethod.value

  override fun getRequestURI(): String = request.path()

  override fun getQueryString(): String? = request.queryString().ifEmpty { null }

  override fun getParameter(name: String): String? = request.queryParameters[name]

  override fun getParameterNames(): java.util.Enumeration<String> = Collections.enumeration(params.keys)

  override fun getParameterValues(name: String): Array<String>? = params[name]?.toTypedArray()

  override fun getParameterMap(): Map<String, Array<String>> = params.mapValues { it.value.toTypedArray() }

  override fun getHeader(name: String): String? = request.headers[name]

  override fun getHeaders(name: String): java.util.Enumeration<String> =
    Collections.enumeration(request.headers.getAll(name) ?: emptyList())

  override fun getHeaderNames(): java.util.Enumeration<String> = Collections.enumeration(request.headers.names())

  override fun getScheme(): String = request.local.scheme

  override fun getServerName(): String = request.local.serverHost

  override fun getServerPort(): Int = request.local.serverPort

  override fun getProtocol(): String = request.httpVersion

  override fun getContentType(): String? = request.contentType().toString()

  override fun getRemoteAddr(): String = request.local.remoteAddress

  override fun getContextPath(): String = ""

  override fun getServletPath(): String = request.path()

  // Unsupported methods below

  override fun getAuthType(): String = throw UnsupportedOperationException()

  override fun getCookies(): Array<Cookie> = throw UnsupportedOperationException()

  override fun getDateHeader(name: String): Long = throw UnsupportedOperationException()

  override fun getIntHeader(name: String): Int = throw UnsupportedOperationException()

  override fun getPathInfo(): String? = throw UnsupportedOperationException()

  override fun getPathTranslated(): String? = throw UnsupportedOperationException()

  override fun getRemoteUser(): String? = throw UnsupportedOperationException()

  override fun isUserInRole(role: String): Boolean = throw UnsupportedOperationException()

  override fun getUserPrincipal(): Principal? = throw UnsupportedOperationException()

  override fun getRequestedSessionId(): String? = throw UnsupportedOperationException()

  override fun getRequestURL(): StringBuffer = throw UnsupportedOperationException()

  override fun getSession(create: Boolean): HttpSession = throw UnsupportedOperationException()

  override fun getSession(): HttpSession = throw UnsupportedOperationException()

  override fun changeSessionId(): String = throw UnsupportedOperationException()

  override fun isRequestedSessionIdValid(): Boolean = throw UnsupportedOperationException()

  override fun isRequestedSessionIdFromCookie(): Boolean = throw UnsupportedOperationException()

  override fun isRequestedSessionIdFromURL(): Boolean = throw UnsupportedOperationException()

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun isRequestedSessionIdFromUrl(): Boolean = throw UnsupportedOperationException()

  override fun authenticate(response: HttpServletResponse): Boolean = throw UnsupportedOperationException()

  override fun login(
    username: String,
    password: String,
  ) = throw UnsupportedOperationException()

  override fun logout() = throw UnsupportedOperationException()

  override fun getParts(): Collection<Part> = throw UnsupportedOperationException()

  override fun getPart(name: String): Part = throw UnsupportedOperationException()

  override fun <T : HttpUpgradeHandler> upgrade(handlerClass: Class<T>): T = throw UnsupportedOperationException()

  override fun getHttpServletMapping(): HttpServletMapping = throw UnsupportedOperationException()

  override fun getAttribute(name: String): Any? = throw UnsupportedOperationException()

  override fun getAttributeNames(): java.util.Enumeration<String> = throw UnsupportedOperationException()

  override fun getCharacterEncoding(): String? = throw UnsupportedOperationException()

  override fun setCharacterEncoding(env: String) = throw UnsupportedOperationException()

  override fun getContentLength(): Int = throw UnsupportedOperationException()

  override fun getContentLengthLong(): Long = throw UnsupportedOperationException()

  override fun getInputStream(): ServletInputStream = throw UnsupportedOperationException()

  override fun getLocalName(): String = throw UnsupportedOperationException()

  override fun getLocalAddr(): String = throw UnsupportedOperationException()

  override fun getLocalPort(): Int = throw UnsupportedOperationException()

  override fun getServletContext(): ServletContext = throw UnsupportedOperationException()

  override fun startAsync(): AsyncContext = throw UnsupportedOperationException()

  override fun startAsync(
    servletRequest: ServletRequest,
    servletResponse: ServletResponse,
  ): AsyncContext = throw UnsupportedOperationException()

  override fun isAsyncStarted(): Boolean = throw UnsupportedOperationException()

  override fun isAsyncSupported(): Boolean = throw UnsupportedOperationException()

  override fun getAsyncContext(): AsyncContext = throw UnsupportedOperationException()

  override fun getDispatcherType(): DispatcherType = throw UnsupportedOperationException()

  override fun getRemoteHost(): String = throw UnsupportedOperationException()

  override fun getRemotePort(): Int = throw UnsupportedOperationException()

  override fun setAttribute(
    name: String,
    o: Any?,
  ) = throw UnsupportedOperationException()

  override fun removeAttribute(name: String) = throw UnsupportedOperationException()

  override fun getLocale(): Locale = throw UnsupportedOperationException()

  override fun getLocales(): java.util.Enumeration<Locale> = throw UnsupportedOperationException()

  override fun isSecure(): Boolean = throw UnsupportedOperationException()

  override fun getRequestDispatcher(path: String): RequestDispatcher = throw UnsupportedOperationException()

  @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
  override fun getRealPath(path: String): String = throw UnsupportedOperationException()

  override fun getReader(): BufferedReader = throw UnsupportedOperationException()
}
