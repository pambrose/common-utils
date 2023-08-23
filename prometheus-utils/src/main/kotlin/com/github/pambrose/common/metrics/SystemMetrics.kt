/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

package com.github.pambrose.common.metrics

import io.prometheus.client.Collector
import io.prometheus.client.hotspot.*
import mu.two.KLogging

object SystemMetrics : KLogging() {
  private var initialized = false

  @Synchronized
  fun initialize(
    enableStandardExports: Boolean = false,
    enableMemoryPoolsExports: Boolean = false,
    enableGarbageCollectorExports: Boolean = false,
    enableThreadExports: Boolean = false,
    enableClassLoadingExports: Boolean = false,
    enableVersionInfoExports: Boolean = false
  ) {
    if (!initialized) {
      if (enableStandardExports) {
        logger.info { "Enabling standard JMX metrics" }
        StandardExports().register<Collector>()
      }

      if (enableMemoryPoolsExports) {
        logger.info { "Enabling memory pool JMX metrics" }
        MemoryPoolsExports().register<Collector>()
      }

      if (enableGarbageCollectorExports) {
        logger.info { "Enabling garbage collector JMX metrics" }
        GarbageCollectorExports().register<Collector>()
      }

      if (enableThreadExports) {
        logger.info { "Enabling thread JMX metrics" }
        ThreadExports().register<Collector>()
      }

      if (enableClassLoadingExports) {
        logger.info { "Enabling class loading JMX metrics" }
        ClassLoadingExports().register<Collector>()
      }

      if (enableVersionInfoExports) {
        logger.info { "Enabling version info metrics" }
        VersionInfoExports().register<Collector>()
      }

      initialized = true
    }
  }
}
