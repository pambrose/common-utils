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

package com.pambrose.common.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import jakarta.servlet.http.HttpServlet

private fun httpServlet() = object : HttpServlet() {}

class HttpServletGroupTests : StringSpec() {
  init {
    "a new group has no servlets" {
      HttpServletGroup().servletMap.shouldBeEmpty()
    }

    "addServlet registers the servlet at its path" {
      val servlet = httpServlet()
      val group = HttpServletGroup().apply { addServlet("/ping", servlet) }

      group.servletMap.keys shouldBe setOf("/ping")
      group.servletMap["/ping"] shouldBeSameInstanceAs servlet
    }

    "empty and blank paths are ignored" {
      val group =
        HttpServletGroup().apply {
          addServlet("", httpServlet())
          addServlet("   ", httpServlet())
        }

      group.servletMap.shouldBeEmpty()
    }

    "a later servlet at the same path replaces the earlier one" {
      val replacement = httpServlet()
      val group =
        HttpServletGroup().apply {
          addServlet("/version", httpServlet())
          addServlet("/version", replacement)
        }

      group.servletMap.size shouldBe 1
      group.servletMap["/version"] shouldBeSameInstanceAs replacement
    }

    "a path with and without a leading slash is the same endpoint" {
      val replacement = httpServlet()
      val group =
        HttpServletGroup().apply {
          addServlet("ping", httpServlet())
          addServlet("/ping", replacement)
        }

      group.servletMap.keys shouldBe setOf("/ping")
      group.servletMap["/ping"] shouldBeSameInstanceAs replacement
    }

    "addServlets registers every pair and skips blank paths" {
      val ping = httpServlet()
      val health = httpServlet()
      val group = HttpServletGroup().apply { addServlets("/ping" to ping, " " to httpServlet(), "/health" to health) }

      group.servletMap.keys shouldBe setOf("/ping", "/health")
      group.servletMap["/ping"] shouldBeSameInstanceAs ping
      group.servletMap["/health"] shouldBeSameInstanceAs health
    }
  }
}
