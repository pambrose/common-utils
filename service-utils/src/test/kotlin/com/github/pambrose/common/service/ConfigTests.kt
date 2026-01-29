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

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ConfigTests {
  @Test
  fun adminConfigCreationTest() {
    val config = AdminConfig(
      enabled = true,
      port = 8080,
      pingPath = "/ping",
      versionPath = "/version",
      healthCheckPath = "/healthcheck",
      threadDumpPath = "/threaddump",
    )
    config.enabled shouldBe true
    config.port shouldBe 8080
    config.pingPath shouldBe "/ping"
    config.versionPath shouldBe "/version"
    config.healthCheckPath shouldBe "/healthcheck"
    config.threadDumpPath shouldBe "/threaddump"
  }

  @Test
  fun adminConfigCopyTest() {
    val config = AdminConfig(
      enabled = true,
      port = 8080,
      pingPath = "/ping",
      versionPath = "/version",
      healthCheckPath = "/healthcheck",
      threadDumpPath = "/threaddump",
    )
    val copied = config.copy(enabled = false, port = 9090)
    copied.enabled shouldBe false
    copied.port shouldBe 9090
    copied.pingPath shouldBe "/ping"
  }

  @Test
  fun metricsConfigCreationTest() {
    val config = MetricsConfig(
      enabled = true,
      port = 9090,
      path = "metrics",
      standardExportsEnabled = true,
      memoryPoolsExportsEnabled = false,
      garbageCollectorExportsEnabled = true,
      threadExportsEnabled = false,
      classLoadingExportsEnabled = true,
      versionInfoExportsEnabled = false,
    )
    config.enabled shouldBe true
    config.port shouldBe 9090
    config.path shouldBe "metrics"
    config.standardExportsEnabled shouldBe true
    config.memoryPoolsExportsEnabled shouldBe false
    config.garbageCollectorExportsEnabled shouldBe true
    config.threadExportsEnabled shouldBe false
    config.classLoadingExportsEnabled shouldBe true
    config.versionInfoExportsEnabled shouldBe false
  }

  @Test
  fun zipkinConfigCreationTest() {
    val config = ZipkinConfig(
      enabled = true,
      hostname = "localhost",
      port = 9411,
      path = "api/v2/spans",
      serviceName = "test-service",
    )
    config.enabled shouldBe true
    config.hostname shouldBe "localhost"
    config.port shouldBe 9411
    config.path shouldBe "api/v2/spans"
    config.serviceName shouldBe "test-service"
  }
}
