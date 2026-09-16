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

// DEPRECATION: the Int-snapshot form of newBacklogHealthCheck is still tested until it is removed.
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "DEPRECATION")

package com.pambrose.common.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.lang.reflect.Modifier

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

    "unhealthy messages state the threshold as well as the size" {
      MetricsUtils.newBacklogHealthCheck(backlogSize = 15, size = 10).execute().message shouldBe
        "Large size: 15 (threshold: 10)"
      MetricsUtils.newMapHealthCheck(mapOf("a" to 1, "b" to 2), size = 2).execute().message shouldBe
        "Large size: 2 (threshold: 2)"
    }

    "the factories are static methods, so Java callers need no INSTANCE" {
      val factories =
        MetricsUtils::class.java.declaredMethods
          .filter { Modifier.isPublic(it.modifiers) && it.name.startsWith("new") }
      factories shouldHaveSize 3
      factories.all { Modifier.isStatic(it.modifiers) } shouldBe true
    }

    "a map health check reads the map's current size on every check" {
      val map = HashMap<String, Int>()
      val healthCheck = MetricsUtils.newMapHealthCheck(map, size = 2)
      healthCheck.execute().isHealthy shouldBe true

      map["a"] = 1
      map["b"] = 2
      healthCheck.execute().apply {
        isHealthy shouldBe false
        message shouldBe "Large size: 2 (threshold: 2)"
      }

      map.remove("a")
      healthCheck.execute().isHealthy shouldBe true
    }

    "a backlog check built from a supplier reads the current size on every check" {
      var backlog = 5
      val healthCheck = MetricsUtils.newBacklogHealthCheck(backlogSize = { backlog }, size = 10)
      healthCheck.execute().isHealthy shouldBe true

      backlog = 10
      healthCheck.execute().apply {
        isHealthy shouldBe false
        message shouldBe "Large size: 10 (threshold: 10)"
      }

      backlog = 9
      healthCheck.execute().isHealthy shouldBe true
    }
  }
}
