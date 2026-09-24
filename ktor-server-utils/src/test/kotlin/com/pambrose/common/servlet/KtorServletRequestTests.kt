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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.headersOf
import io.ktor.http.parametersOf
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.ApplicationRequest
import io.ktor.util.Attributes
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpUpgradeHandler

class KtorServletRequestTests : StringSpec() {
  init {
    "getContentType returns null when no Content-Type header is present" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns Headers.Empty
      KtorServletRequest(request).contentType shouldBe null
    }

    "getContentType returns the header value when present" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns headersOf(HttpHeaders.ContentType, "application/json")
      KtorServletRequest(request).contentType shouldBe "application/json"
    }

    "getContentType preserves the charset parameter" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")
      KtorServletRequest(request).contentType shouldBe "text/html; charset=UTF-8"
    }

    "getRequestURI and getServletPath return the path without the query string" {
      val servletRequest = KtorServletRequest(connectedRequest(uri = "/api/items?id=42"))
      servletRequest.requestURI shouldBe "/api/items"
      servletRequest.servletPath shouldBe "/api/items"
      servletRequest.contextPath shouldBe ""
    }

    "getQueryString returns the query string when present" {
      KtorServletRequest(connectedRequest(uri = "/api/items?id=42&sort=asc")).queryString shouldBe "id=42&sort=asc"
    }

    "getQueryString returns null when there is no query string" {
      KtorServletRequest(connectedRequest(uri = "/api/items")).queryString shouldBe null
    }

    "connection metadata is derived from the request connection point" {
      val servletRequest = KtorServletRequest(connectedRequest())
      servletRequest.method shouldBe "GET"
      servletRequest.protocol shouldBe "HTTP/1.1"
      servletRequest.scheme shouldBe "https"
      servletRequest.serverName shouldBe "example.com"
      servletRequest.serverPort shouldBe 8443
      servletRequest.remoteAddr shouldBe "10.0.0.7"
    }

    "getParameterValues returns null for a missing parameter" {
      val request = mockk<ApplicationRequest>()
      every { request.queryParameters } returns parametersOf("id", ["42"])
      val servletRequest = KtorServletRequest(request)
      servletRequest.getParameterValues("id")?.toList() shouldBe ["42"]
      servletRequest.getParameterValues("missing") shouldBe null
    }

    "getHeaders returns all values for a header and an empty enumeration when absent" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns headersOf("Accept" to ["text/html", "application/json"])
      val servletRequest = KtorServletRequest(request)
      servletRequest.getHeaders("Accept").toList() shouldBe ["text/html", "application/json"]
      servletRequest.getHeaders("X-Missing").toList() shouldBe emptyList()
      servletRequest.headerNames.toList() shouldBe ["Accept"]
    }

    "unsupported request methods throw UnsupportedOperationException" {
      val request = KtorServletRequest(mockk())
      val response = KtorServletResponse()
      shouldThrow<UnsupportedOperationException> { request.authType }
      shouldThrow<UnsupportedOperationException> { request.pathTranslated }
      shouldThrow<UnsupportedOperationException> { request.remoteUser }
      shouldThrow<UnsupportedOperationException> { request.isUserInRole("admin") }
      shouldThrow<UnsupportedOperationException> { request.userPrincipal }
      shouldThrow<UnsupportedOperationException> { request.requestedSessionId }
      shouldThrow<UnsupportedOperationException> { request.getSession(true) }
      shouldThrow<UnsupportedOperationException> { request.session }
      shouldThrow<UnsupportedOperationException> { request.changeSessionId() }
      shouldThrow<UnsupportedOperationException> { request.isRequestedSessionIdValid }
      shouldThrow<UnsupportedOperationException> { request.isRequestedSessionIdFromCookie }
      shouldThrow<UnsupportedOperationException> { request.isRequestedSessionIdFromURL }
      shouldThrow<UnsupportedOperationException> { request.authenticate(response) }
      shouldThrow<UnsupportedOperationException> { request.login("user", "password") }
      shouldThrow<UnsupportedOperationException> { request.logout() }
      shouldThrow<UnsupportedOperationException> { request.parts }
      shouldThrow<UnsupportedOperationException> { request.getPart("file") }
      shouldThrow<UnsupportedOperationException> { request.upgrade(HttpUpgradeHandler::class.java) }
      shouldThrow<UnsupportedOperationException> { request.httpServletMapping }
      shouldThrow<UnsupportedOperationException> { request.setCharacterEncoding("UTF-8") }
      shouldThrow<UnsupportedOperationException> { request.inputStream }
      shouldThrow<UnsupportedOperationException> { request.localName }
      shouldThrow<UnsupportedOperationException> { request.localAddr }
      shouldThrow<UnsupportedOperationException> { request.localPort }
      shouldThrow<UnsupportedOperationException> { request.servletContext }
      shouldThrow<UnsupportedOperationException> { request.startAsync() }
      shouldThrow<UnsupportedOperationException> { request.startAsync(request, response) }
      shouldThrow<UnsupportedOperationException> { request.isAsyncStarted }
      shouldThrow<UnsupportedOperationException> { request.isAsyncSupported }
      shouldThrow<UnsupportedOperationException> { request.asyncContext }
      shouldThrow<UnsupportedOperationException> { request.remoteHost }
      shouldThrow<UnsupportedOperationException> { request.remotePort }
      shouldThrow<UnsupportedOperationException> { request.locale }
      shouldThrow<UnsupportedOperationException> { request.locales }
      shouldThrow<UnsupportedOperationException> { request.getRequestDispatcher("/other") }
      shouldThrow<UnsupportedOperationException> { request.reader }
      shouldThrow<UnsupportedOperationException> { request.requestId }
      shouldThrow<UnsupportedOperationException> { request.protocolRequestId }
      shouldThrow<UnsupportedOperationException> { request.servletConnection }
    }

    "headers the servlet spec derives values from are parsed, with -1 or null when absent" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns
        headersOf(
          HttpHeaders.ContentType to ["text/plain; charset=ISO-8859-1"],
          HttpHeaders.ContentLength to ["12"],
          "X-Int" to ["7"],
          "X-Bad-Int" to ["seven"],
          "X-Date" to ["Thu, 01 Jan 1970 00:00:01 GMT"],
          "X-Bad-Date" to ["yesterday"],
        )
      val servletRequest = KtorServletRequest(request)
      servletRequest.characterEncoding shouldBe "ISO-8859-1"
      servletRequest.contentLength shouldBe 12
      servletRequest.contentLengthLong shouldBe 12L
      servletRequest.getIntHeader("X-Int") shouldBe 7
      servletRequest.getIntHeader("X-Missing") shouldBe -1
      shouldThrow<NumberFormatException> { servletRequest.getIntHeader("X-Bad-Int") }
      servletRequest.getDateHeader("X-Date") shouldBe 1000L
      servletRequest.getDateHeader("X-Missing") shouldBe -1L
      shouldThrow<IllegalArgumentException> { servletRequest.getDateHeader("X-Bad-Date") }
    }

    "a request without Content-Type or Content-Length reports null and -1" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns Headers.Empty
      val servletRequest = KtorServletRequest(request)
      servletRequest.characterEncoding shouldBe null
      servletRequest.contentLength shouldBe -1
      servletRequest.dispatcherType shouldBe DispatcherType.REQUEST
    }

    "an unparseable Content-Type has no charset, and an unusable Content-Length reports -1" {
      listOf("12345678901", "twelve").forEach { length ->
        val request = mockk<ApplicationRequest>()
        every { request.headers } returns
          headersOf(HttpHeaders.ContentType to ["not a content type"], HttpHeaders.ContentLength to [length])
        val servletRequest = KtorServletRequest(request)
        servletRequest.characterEncoding shouldBe null
        servletRequest.contentLength shouldBe -1
      }
    }

    "getRequestURL omits the default port and isSecure follows the scheme" {
      val servletRequest = KtorServletRequest(connectedRequest(uri = "/api/items?id=42"))
      servletRequest.requestURL.toString() shouldBe "https://example.com:8443/api/items"
      servletRequest.isSecure shouldBe true
    }

    "getPathInfo is null because the servlet is mapped to its exact path" {
      KtorServletRequest(mockk()).pathInfo shouldBe null
    }

    "request attributes can be set, read, listed, and removed" {
      val request = KtorServletRequest(mockk())
      request.getAttribute("missing") shouldBe null
      request.setAttribute("key", "value")
      request.getAttribute("key") shouldBe "value"
      request.attributeNames.toList() shouldBe ["key"]
      request.setAttribute("key", null)
      request.getAttribute("key") shouldBe null
      request.setAttribute("other", 1)
      request.removeAttribute("other")
      request.attributeNames.toList() shouldBe emptyList()
    }
  }

  // Builds a mocked ApplicationRequest whose connection point supplies the URI and connection
  // metadata. The empty Attributes instance makes the origin extension fall back to local.
  private fun connectedRequest(uri: String = "/api/items"): ApplicationRequest {
    val connectionPoint = mockk<RequestConnectionPoint>()
    every { connectionPoint.uri } returns uri
    every { connectionPoint.method } returns HttpMethod.Get
    every { connectionPoint.version } returns "HTTP/1.1"
    every { connectionPoint.scheme } returns "https"
    every { connectionPoint.serverHost } returns "example.com"
    every { connectionPoint.serverPort } returns 8443
    every { connectionPoint.remoteAddress } returns "10.0.0.7"
    val call = mockk<ApplicationCall>()
    every { call.attributes } returns Attributes()
    val request = mockk<ApplicationRequest>()
    every { request.call } returns call
    every { request.local } returns connectionPoint
    every { request.headers } returns Headers.Empty
    return request
  }
}
