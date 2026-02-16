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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import jakarta.servlet.Servlet
import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.junit.jupiter.api.Test

class ServletGroupTests {
  private class TestServlet : Servlet {
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

  @Test
  fun servletGroupCreationTest() {
    val group = ServletGroup(8080)
    group.port shouldBe 8080
    group.servletMap.size shouldBe 0
  }

  @Test
  fun servletGroupAddServletTest() {
    val group = ServletGroup(8080)
    val servlet = TestServlet()

    group.addServlet("test", servlet)

    group.servletMap.size shouldBe 1
    group.servletMap["test"] shouldBe servlet
  }

  @Test
  fun servletGroupAddMultipleServletsTest() {
    val group = ServletGroup(9090)
    val servlet1 = TestServlet()
    val servlet2 = TestServlet()
    val servlet3 = TestServlet()

    group.addServlet("ping", servlet1)
    group.addServlet("health", servlet2)
    group.addServlet("version", servlet3)

    group.servletMap.size shouldBe 3
    group.servletMap.keys shouldContainExactly setOf("ping", "health", "version")
  }

  @Test
  fun servletGroupEmptyPathIgnoredTest() {
    val group = ServletGroup(8080)
    val servlet = TestServlet()

    group.addServlet("", servlet)
    group.servletMap.size shouldBe 0

    group.addServlet("   ", servlet)
    group.servletMap.size shouldBe 0
  }

  @Test
  fun servletGroupOverwritePathTest() {
    val group = ServletGroup(8080)
    val servlet1 = TestServlet()
    val servlet2 = TestServlet()

    group.addServlet("test", servlet1)
    group.addServlet("test", servlet2)

    group.servletMap.size shouldBe 1
    group.servletMap["test"] shouldBe servlet2
  }
}
