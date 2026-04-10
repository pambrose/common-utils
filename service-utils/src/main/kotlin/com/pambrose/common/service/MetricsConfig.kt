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

package com.pambrose.common.service

/**
 * Configuration data for the Prometheus metrics service.
 *
 * Controls whether metrics collection is enabled and defines the port and path for the
 * Prometheus scrape endpoint, as well as which JVM system metric exporters are active.
 *
 * @property enabled Whether the metrics service is enabled.
 * @property port The HTTP port on which the Prometheus metrics endpoint is served.
 * @property path The URL path for the Prometheus metrics endpoint.
 * @property standardExportsEnabled Whether standard JVM exports are enabled.
 * @property memoryPoolsExportsEnabled Whether memory pool exports are enabled.
 * @property garbageCollectorExportsEnabled Whether garbage collector exports are enabled.
 * @property threadExportsEnabled Whether thread exports are enabled.
 * @property classLoadingExportsEnabled Whether class loading exports are enabled.
 * @property versionInfoExportsEnabled Whether version info exports are enabled.
 */
data class MetricsConfig(
  val enabled: Boolean,
  val port: Int,
  val path: String,
  val standardExportsEnabled: Boolean,
  val memoryPoolsExportsEnabled: Boolean,
  val garbageCollectorExportsEnabled: Boolean,
  val threadExportsEnabled: Boolean,
  val classLoadingExportsEnabled: Boolean,
  val versionInfoExportsEnabled: Boolean,
)
