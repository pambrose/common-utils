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

package com.github.pambrose.common.dsl

import com.codahale.metrics.health.HealthCheck
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MetricsDslTests : StringSpec() {
  init {
    "health check healthy" {
      val healthCheck =
        MetricsDsl.healthCheck {
          HealthCheck.Result.healthy()
        }

      val result = healthCheck.execute()
      result.isHealthy shouldBe true
    }

    "health check unhealthy" {
      val healthCheck =
        MetricsDsl.healthCheck {
          HealthCheck.Result.unhealthy("Test error")
        }

      val result = healthCheck.execute()
      result.isHealthy shouldBe false
      result.message shouldBe "Test error"
    }

    "health check with exception" {
      val healthCheck =
        MetricsDsl.healthCheck {
          HealthCheck.Result.unhealthy(RuntimeException("Test exception"))
        }

      val result = healthCheck.execute()
      result.isHealthy shouldBe false
    }
  }
}
