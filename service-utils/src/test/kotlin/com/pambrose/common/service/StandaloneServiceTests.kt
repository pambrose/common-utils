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

import com.pambrose.common.concurrent.GenericIdleService
import com.pambrose.common.servlet.VersionServlet
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

// Starts the service, runs block, and stops the service even when block fails.
private inline fun GenericIdleService.whileRunning(block: () -> Unit) {
  startSync()
  try {
    block()
  } finally {
    stopSync()
  }
}

private val GenericIdleService.boundPort: Int
  get() =
    when (this) {
      is MetricsService -> boundPort
      is ServletService -> boundPort
      is KtorServletService -> boundPort
      else -> error("${this::class.simpleName} has no server")
    }

// MetricsService, ServletService and KtorServletService built directly, rather than through a GenericService, which
// always passes every argument. The optional arguments keep their defaults, except that the servers sent requests
// bind to LOOPBACK.
class StandaloneServiceTests : StringSpec() {
  init {
    "a MetricsService serves metrics and its health check follows the server across start and stop" {
      val service = MetricsService(0, "metrics", LOOPBACK)
      service.toString() shouldBe "MetricsService{port=0, path=/metrics}"
      service.healthCheck.execute().isHealthy shouldBe false

      service.whileRunning {
        service.healthCheck.execute().isHealthy shouldBe true
        httpGet(service.boundPort, "/metrics").apply {
          statusCode() shouldBe 200
          headers().firstValue("Content-Type").orElse("") shouldContain "text/plain"
        }
      }

      service.healthCheck.execute().apply {
        isHealthy shouldBe false
        message shouldBe "Jetty server not running"
      }
    }

    "a ServletService serves the servlets in its group" {
      val group = ServletGroup().apply { addServlet("version", VersionServlet("servlet-service")) }
      val service = ServletService(0, group, LOOPBACK)
      service.toString() shouldBe "ServletService{port=0, paths=[/version]}"

      service.whileRunning {
        httpGet(service.boundPort, "/version").apply {
          statusCode() shouldBe 200
          body().trim() shouldBe "servlet-service"
        }
        httpGet(service.boundPort, "/missing").statusCode() shouldBe 404
      }
    }

    "a KtorServletService serves the servlets in its group" {
      val group = HttpServletGroup().apply { addServlet("/version", VersionServlet("ktor-servlet-service")) }
      val service = KtorServletService(0, group, host = LOOPBACK)
      service.toString() shouldBe "KtorServletService{port=0, paths=[/version]}"

      service.whileRunning {
        httpGet(service.boundPort, "/version").apply {
          statusCode() shouldBe 200
          body().trim() shouldBe "ktor-servlet-service"
        }
        httpGet(service.boundPort, "/missing").statusCode() shouldBe 404
      }
    }

    "each server reports the port the OS chose once it starts, and keeps it after stopping" {
      val services =
        [
          MetricsService(0, "metrics"),
          ServletService(0, ServletGroup()),
          KtorServletService(0, HttpServletGroup()),
        ]
      services.forEach { service ->
        service.boundPort shouldBe 0
        service.whileRunning { service.boundPort shouldBeGreaterThan 0 }
        // shouldBeReleased rejects port 0, so this also shows the port survived the stop.
        shouldBeReleased(service.boundPort, host = null)
      }
    }
  }
}
