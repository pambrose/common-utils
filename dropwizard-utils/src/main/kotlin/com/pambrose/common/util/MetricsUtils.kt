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
 *
 * Unhealthy results read `"Large size: N (threshold: T)"`.
 */
object MetricsUtils {
  /**
   * Creates a [HealthCheck] that reports unhealthy when the backlog size meets or exceeds the given threshold.
   *
   * @param backlogSize returns the current backlog size; it is called on every check.
   * @param size the threshold at or above which the check is considered unhealthy.
   * @return a [HealthCheck] that monitors backlog size.
   */
  @JvmStatic
  fun newBacklogHealthCheck(
    backlogSize: () -> Int,
    size: Int,
  ): HealthCheck = sizeHealthCheck(size, backlogSize)

  /**
   * Creates a [HealthCheck] that reports unhealthy when [backlogSize] meets or exceeds the given threshold.
   *
   * @param backlogSize the backlog size, captured once when the check is created.
   * @param size the threshold at or above which the check is considered unhealthy.
   * @return a [HealthCheck] that always evaluates the captured [backlogSize].
   */
  @Deprecated(
    "The size is captured once, so the check never changes. Pass a () -> Int that reads the current size.",
    ReplaceWith("newBacklogHealthCheck({ backlogSize }, size)"),
  )
  @JvmStatic
  fun newBacklogHealthCheck(
    backlogSize: Int,
    size: Int,
  ): HealthCheck = newBacklogHealthCheck({ backlogSize }, size)

  /**
   * Creates a [HealthCheck] that reports unhealthy when the map size meets or exceeds the given threshold.
   *
   * @param map the map whose size is evaluated on every check.
   * @param size the threshold at or above which the check is considered unhealthy.
   * @return a [HealthCheck] that monitors map size.
   */
  @JvmStatic
  fun newMapHealthCheck(
    map: Map<*, *>,
    size: Int,
  ): HealthCheck = sizeHealthCheck(size) { map.size }

  private fun sizeHealthCheck(
    threshold: Int,
    currentSize: () -> Int,
  ) = MetricsDsl.healthCheck {
    val current = currentSize()
    if (current < threshold)
      HealthCheck.Result.healthy()
    else
      HealthCheck.Result.unhealthy("Large size: $current (threshold: $threshold)")
  }
}
