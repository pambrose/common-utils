@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AdminConfigTests : StringSpec() {
  init {
    "default-style values are correct" {
      val config = AdminConfig(
        enabled = false,
        port = 8081,
        pingPath = "ping",
        versionPath = "version",
        healthCheckPath = "healthcheck",
        threadDumpPath = "threaddump",
      )
      config.enabled shouldBe false
      config.port shouldBe 8081
      config.pingPath shouldBe "ping"
      config.versionPath shouldBe "version"
      config.healthCheckPath shouldBe "healthcheck"
      config.threadDumpPath shouldBe "threaddump"
    }

    "custom values are set correctly" {
      val config = AdminConfig(
        enabled = true,
        port = 9090,
        pingPath = "/custom-ping",
        versionPath = "/custom-version",
        healthCheckPath = "/custom-health",
        threadDumpPath = "/custom-threads",
      )
      config.enabled shouldBe true
      config.port shouldBe 9090
      config.pingPath shouldBe "/custom-ping"
      config.versionPath shouldBe "/custom-version"
      config.healthCheckPath shouldBe "/custom-health"
      config.threadDumpPath shouldBe "/custom-threads"
    }

    "copy creates independent instance" {
      val original = AdminConfig(
        enabled = true,
        port = 8080,
        pingPath = "ping",
        versionPath = "version",
        healthCheckPath = "healthcheck",
        threadDumpPath = "threaddump",
      )
      val copied = original.copy(enabled = false, port = 9090)

      copied.enabled shouldBe false
      copied.port shouldBe 9090
      copied.pingPath shouldBe "ping"
      copied.versionPath shouldBe "version"
      copied.healthCheckPath shouldBe "healthcheck"
      copied.threadDumpPath shouldBe "threaddump"

      original.enabled shouldBe true
      original.port shouldBe 8080
      copied shouldNotBe original
    }
  }
}
