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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServlet
import java.util.EventListener

class KtorServletConfigTests : StringSpec() {
  init {
    "config exposes its name and context and has no init parameters" {
      val context = KtorServletContext()
      val config = KtorServletConfig("my-servlet", context)
      config.servletName shouldBe "my-servlet"
      config.servletContext shouldBeSameInstanceAs context
      config.getInitParameter("anything") shouldBe null
      config.initParameterNames.toList().shouldBeEmpty()
    }

    "context stores, lists, and removes attributes" {
      val context = KtorServletContext()
      context.getAttribute("key") shouldBe null
      context.setAttribute("key", "value")
      context.getAttribute("key") shouldBe "value"
      context.attributeNames.toList() shouldBe ["key"]
      context.removeAttribute("key")
      context.getAttribute("key") shouldBe null
    }

    "setting a null attribute removes it" {
      val context = KtorServletContext()
      context.setAttribute("key", "value")
      context.setAttribute("key", null)
      context.getAttribute("key") shouldBe null
      context.attributeNames.toList().shouldBeEmpty()
    }

    "context logging delegates to the logger without throwing" {
      val context = KtorServletContext()
      shouldNotThrowAny {
        context.log("message")
        context.log("failure", IllegalStateException("boom"))
      }
    }

    "context reports Servlet 6.1 and no init parameters, resources, or dispatchers" {
      val context = KtorServletContext()
      context.contextPath shouldBe ""
      context.servletContextName shouldBe null
      context.serverInfo shouldBe "Ktor"
      context.majorVersion shouldBe 6
      context.minorVersion shouldBe 1
      context.effectiveMajorVersion shouldBe 6
      context.effectiveMinorVersion shouldBe 1
      context.getInitParameter("anything") shouldBe null
      context.initParameterNames.toList().shouldBeEmpty()
      context.getContext("/other") shouldBe null
      context.getMimeType("file.txt") shouldBe null
      context.getResourcePaths("/") shouldBe null
      context.getResource("/file.txt") shouldBe null
      context.getResourceAsStream("/file.txt") shouldBe null
      context.getRequestDispatcher("/path") shouldBe null
      context.getNamedDispatcher("name") shouldBe null
      context.getRealPath("/file.txt") shouldBe null
    }

    "unsupported context methods throw UnsupportedOperationException" {
      val context = KtorServletContext()
      shouldThrow<UnsupportedOperationException> { context.setInitParameter("name", "value") }
      shouldThrow<UnsupportedOperationException> { context.addServlet("name", "com.example.MyServlet") }
      shouldThrow<UnsupportedOperationException> { context.addServlet("name", object : HttpServlet() {}) }
      shouldThrow<UnsupportedOperationException> { context.addServlet("name", HttpServlet::class.java) }
      shouldThrow<UnsupportedOperationException> { context.addJspFile("name", "/page.jsp") }
      shouldThrow<UnsupportedOperationException> { context.createServlet(HttpServlet::class.java) }
      shouldThrow<UnsupportedOperationException> { context.getServletRegistration("name") }
      shouldThrow<UnsupportedOperationException> { context.servletRegistrations }
      shouldThrow<UnsupportedOperationException> { context.addFilter("name", "com.example.MyFilter") }
      shouldThrow<UnsupportedOperationException> { context.addFilter("name") { _, _, _ -> } }
      shouldThrow<UnsupportedOperationException> { context.addFilter("name", Filter::class.java) }
      shouldThrow<UnsupportedOperationException> { context.createFilter(Filter::class.java) }
      shouldThrow<UnsupportedOperationException> { context.getFilterRegistration("name") }
      shouldThrow<UnsupportedOperationException> { context.filterRegistrations }
      shouldThrow<UnsupportedOperationException> { context.sessionCookieConfig }
      shouldThrow<UnsupportedOperationException> { context.setSessionTrackingModes(emptySet()) }
      shouldThrow<UnsupportedOperationException> { context.defaultSessionTrackingModes }
      shouldThrow<UnsupportedOperationException> { context.effectiveSessionTrackingModes }
      shouldThrow<UnsupportedOperationException> { context.addListener("com.example.MyListener") }
      shouldThrow<UnsupportedOperationException> { context.addListener(object : EventListener {}) }
      shouldThrow<UnsupportedOperationException> { context.addListener(EventListener::class.java) }
      shouldThrow<UnsupportedOperationException> { context.createListener(EventListener::class.java) }
      shouldThrow<UnsupportedOperationException> { context.jspConfigDescriptor }
      shouldThrow<UnsupportedOperationException> { context.declareRoles("admin") }
      shouldThrow<UnsupportedOperationException> { context.virtualServerName }
      shouldThrow<UnsupportedOperationException> { context.sessionTimeout }
      shouldThrow<UnsupportedOperationException> { context.setSessionTimeout(30) }
      shouldThrow<UnsupportedOperationException> { context.requestCharacterEncoding }
      shouldThrow<UnsupportedOperationException> { context.setRequestCharacterEncoding("UTF-8") }
      shouldThrow<UnsupportedOperationException> { context.responseCharacterEncoding }
      shouldThrow<UnsupportedOperationException> { context.setResponseCharacterEncoding("UTF-8") }
    }
  }
}
