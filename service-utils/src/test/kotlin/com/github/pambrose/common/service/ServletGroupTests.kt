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

package com.github.pambrose.common.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import jakarta.servlet.Servlet
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse

private fun testServlet() =
  object : Servlet {
    override fun init(config: ServletConfig?) {}

    override fun getServletConfig(): ServletConfig? = null

    override fun service(
      req: ServletRequest?,
      res: ServletResponse?,
    ) {
    }

    override fun getServletInfo(): String = "TestServlet"

    override fun destroy() {}
  }

class ServletGroupTests : StringSpec() {
  init {
    "servlet group creation" {
      val group = ServletGroup()
      group.servletMap.size shouldBe 0
    }

    "servlet group add servlet" {
      val group = ServletGroup()
      val servlet = testServlet()

      group.addServlet("test", servlet)

      group.servletMap.size shouldBe 1
      group.servletMap["test"] shouldBe servlet
    }

    "servlet group add multiple servlets" {
      val group = ServletGroup()

      val servlet1 = testServlet()
      val servlet2 = testServlet()
      val servlet3 = testServlet()

      group.addServlet("ping", servlet1)
      group.addServlet("health", servlet2)
      group.addServlet("version", servlet3)

      group.servletMap.size shouldBe 3
      group.servletMap.keys shouldContainExactly setOf("ping", "health", "version")
    }

    "servlet group empty path ignored" {
      val group = ServletGroup()
      val servlet = testServlet()

      group.addServlet("", servlet)
      group.servletMap.size shouldBe 0

      group.addServlet("   ", servlet)
      group.servletMap.size shouldBe 0
    }

    "servlet group overwrite path" {
      val group = ServletGroup()
      val servlet1 = testServlet()
      val servlet2 = testServlet()

      group.addServlet("test", servlet1)
      group.addServlet("test", servlet2)

      group.servletMap.size shouldBe 1
      group.servletMap["test"] shouldBe servlet2
    }
  }
}
