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

import com.codahale.metrics.health.HealthCheck
import com.pambrose.common.dsl.MetricsDsl

/**
 * Utility object providing factory methods for common [HealthCheck] patterns.
 */
object MetricsUtils {
  /**
   * Creates a [HealthCheck] that reports unhealthy when the backlog size meets or exceeds the given threshold.
   *
   * @param backlogSize the current backlog size to evaluate.
   * @param size the threshold above which the check is considered unhealthy.
   * @return a [HealthCheck] that monitors backlog size.
   */
  fun newBacklogHealthCheck(
    backlogSize: Int,
    size: Int,
  ) = MetricsDsl.healthCheck {
    if (backlogSize < size)
      HealthCheck.Result.healthy()
    else
      HealthCheck.Result.unhealthy("Large size: $backlogSize")
  }

  /**
   * Creates a [HealthCheck] that reports unhealthy when the map size meets or exceeds the given threshold.
   *
   * @param map the map whose size is evaluated.
   * @param size the threshold above which the check is considered unhealthy.
   * @return a [HealthCheck] that monitors map size.
   */
  fun newMapHealthCheck(
    map: Map<*, *>,
    size: Int,
  ) = MetricsDsl.healthCheck {
    if (map.size < size)
      HealthCheck.Result.healthy()
    else
      HealthCheck.Result.unhealthy("Large size: ${map.size}")
  }
}
