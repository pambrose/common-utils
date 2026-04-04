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

package com.pambrose.common.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class MetricsUtilsTests : StringSpec() {
  init {
    "backlog health check healthy" {
      val healthCheck = MetricsUtils.newBacklogHealthCheck(backlogSize = 5, size = 10)
      val result = healthCheck.execute()
      result.isHealthy shouldBe true
    }

    "backlog health check unhealthy" {
      val healthCheck = MetricsUtils.newBacklogHealthCheck(backlogSize = 15, size = 10)
      val result = healthCheck.execute()
      result.isHealthy shouldBe false
      result.message shouldContain "15"
    }

    "backlog health check boundary" {
      // At boundary (equal) should be unhealthy
      val healthCheck = MetricsUtils.newBacklogHealthCheck(backlogSize = 10, size = 10)
      val result = healthCheck.execute()
      result.isHealthy shouldBe false
    }

    "map health check healthy" {
      val map = mapOf("a" to 1, "b" to 2)
      val healthCheck = MetricsUtils.newMapHealthCheck(map, size = 5)
      val result = healthCheck.execute()
      result.isHealthy shouldBe true
    }

    "map health check unhealthy" {
      val map = mapOf("a" to 1, "b" to 2, "c" to 3, "d" to 4, "e" to 5)
      val healthCheck = MetricsUtils.newMapHealthCheck(map, size = 3)
      val result = healthCheck.execute()
      result.isHealthy shouldBe false
      result.message shouldContain "5"
    }

    "map health check empty map" {
      val map = emptyMap<String, Int>()
      val healthCheck = MetricsUtils.newMapHealthCheck(map, size = 1)
      val result = healthCheck.execute()
      result.isHealthy shouldBe true
    }
  }
}
