@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MetricsConfigTests : StringSpec() {
  init {
    "default-style values are correct" {
      val config = MetricsConfig(
        enabled = false,
        port = 8082,
        path = "metrics",
        standardExportsEnabled = false,
        memoryPoolsExportsEnabled = false,
        garbageCollectorExportsEnabled = false,
        threadExportsEnabled = false,
        classLoadingExportsEnabled = false,
        versionInfoExportsEnabled = false,
      )
      config.enabled shouldBe false
      config.port shouldBe 8082
      config.path shouldBe "metrics"
      config.standardExportsEnabled shouldBe false
      config.memoryPoolsExportsEnabled shouldBe false
      config.garbageCollectorExportsEnabled shouldBe false
      config.threadExportsEnabled shouldBe false
      config.classLoadingExportsEnabled shouldBe false
      config.versionInfoExportsEnabled shouldBe false
    }

    "custom values are set correctly" {
      val config = MetricsConfig(
        enabled = true,
        port = 9090,
        path = "/custom-metrics",
        standardExportsEnabled = true,
        memoryPoolsExportsEnabled = true,
        garbageCollectorExportsEnabled = true,
        threadExportsEnabled = true,
        classLoadingExportsEnabled = true,
        versionInfoExportsEnabled = true,
      )
      config.enabled shouldBe true
      config.port shouldBe 9090
      config.path shouldBe "/custom-metrics"
      config.standardExportsEnabled shouldBe true
      config.memoryPoolsExportsEnabled shouldBe true
      config.garbageCollectorExportsEnabled shouldBe true
      config.threadExportsEnabled shouldBe true
      config.classLoadingExportsEnabled shouldBe true
      config.versionInfoExportsEnabled shouldBe true
    }
  }
}
