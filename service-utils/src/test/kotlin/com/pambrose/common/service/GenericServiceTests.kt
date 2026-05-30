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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.time.Duration

private val disabledAdmin =
  AdminConfig(
    enabled = false,
    port = 0,
    pingPath = "/ping",
    versionPath = "/version",
    healthCheckPath = "/healthcheck",
    threadDumpPath = "/threaddump",
  )

private val disabledMetrics =
  MetricsConfig(
    enabled = false,
    port = 0,
    path = "metrics",
    standardExportsEnabled = false,
    memoryPoolsExportsEnabled = false,
    garbageCollectorExportsEnabled = false,
    threadExportsEnabled = false,
    classLoadingExportsEnabled = false,
    versionInfoExportsEnabled = false,
  )

private val disabledZipkin =
  ZipkinConfig(
    enabled = false,
    hostname = "localhost",
    port = 0,
    path = "api/v2/spans",
    serviceName = "test",
  )

private class JettyTestService :
  GenericService<String>(
    configVals = "jetty-config",
    adminConfig = disabledAdmin,
    metricsConfig = disabledMetrics,
    zipkinConfig = disabledZipkin,
  ) {
  override fun run() = Unit

  val healthCheckNames get() = healthCheckRegistry.names.toList()

  init {
    initServletService()
  }
}

private class KtorTestService :
  GenericKtorService<String>(
    configVals = "ktor-config",
    adminConfig = disabledAdmin,
    metricsConfig = disabledMetrics,
    zipkinConfig = disabledZipkin,
  ) {
  override fun run() = Unit

  val healthCheckNames get() = healthCheckRegistry.names.toList()

  init {
    initKtorServletService()
  }
}

class GenericServiceTests : StringSpec() {
  init {
    "both variants derive the same enablement flags from disabled config" {
      val jetty = JettyTestService()
      jetty.isAdminEnabled shouldBe false
      jetty.isMetricsEnabled shouldBe false
      jetty.isZipkinEnabled shouldBe false

      val ktor = KtorTestService()
      ktor.isAdminEnabled shouldBe false
      ktor.isMetricsEnabled shouldBe false
      ktor.isZipkinEnabled shouldBe false
    }

    "both variants register the same base health checks after init" {
      val expected = listOf("all_services_healthy", "thread_deadlock")
      JettyTestService().healthCheckNames shouldContainExactlyInAnyOrder expected
      KtorTestService().healthCheckNames shouldContainExactlyInAnyOrder expected
    }

    "both variants expose configVals and a non-negative upTime" {
      val jetty = JettyTestService()
      jetty.configVals shouldBe "jetty-config"
      (jetty.upTime >= Duration.ZERO) shouldBe true

      val ktor = KtorTestService()
      ktor.configVals shouldBe "ktor-config"
      (ktor.upTime >= Duration.ZERO) shouldBe true
    }
  }
}
