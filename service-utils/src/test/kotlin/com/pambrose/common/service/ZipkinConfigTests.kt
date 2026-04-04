@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ZipkinConfigTests : StringSpec() {
  init {
    "default-style values are correct" {
      val config = ZipkinConfig(
        enabled = false,
        hostname = "localhost",
        port = 9411,
        path = "api/v2/spans",
        serviceName = "",
      )
      config.enabled shouldBe false
      config.hostname shouldBe "localhost"
      config.port shouldBe 9411
      config.path shouldBe "api/v2/spans"
      config.serviceName shouldBe ""
    }

    "custom values are set correctly" {
      val config = ZipkinConfig(
        enabled = true,
        hostname = "zipkin.example.com",
        port = 8080,
        path = "custom/path",
        serviceName = "my-service",
      )
      config.enabled shouldBe true
      config.hostname shouldBe "zipkin.example.com"
      config.port shouldBe 8080
      config.path shouldBe "custom/path"
      config.serviceName shouldBe "my-service"
    }

    "url can be constructed from config fields" {
      val config = ZipkinConfig(
        enabled = true,
        hostname = "localhost",
        port = 9411,
        path = "api/v2/spans",
        serviceName = "test-service",
      )
      val url = "http://${config.hostname}:${config.port}/${config.path}"
      url shouldBe "http://localhost:9411/api/v2/spans"
    }
  }
}
