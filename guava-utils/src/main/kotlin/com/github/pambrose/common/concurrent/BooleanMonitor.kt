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

package com.github.pambrose.common.concurrent

import com.google.common.base.MoreObjects
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

class BooleanMonitor(initValue: Boolean) : GenericMonitor() {
  private val monVal = AtomicBoolean(false)

  override val monitorSatisfied get() = get()

  init {
    set(initValue)
  }

  fun get() = monVal.get()

  fun set(value: Boolean) {
    monitor.enter()
    try {
      this.monVal.set(value)
    } finally {
      monitor.leave()
    }
  }

  override fun toString() =
    MoreObjects.toStringHelper(this)
      .add("value", monVal.get())
      .toString()

  companion object {
    private val logger = KotlinLogging.logger {}

    @JvmStatic
    fun debug(msg: () -> Any?): MonitorAction =
      {
        logger.debug { msg }
        true
      }

    @JvmStatic
    fun debug(msg: String): MonitorAction =
      {
        logger.debug { msg }
        true
      }

    @JvmStatic
    fun info(msg: () -> Any?): MonitorAction =
      {
        logger.info { msg }
        true
      }

    @JvmStatic
    fun info(msg: String): MonitorAction =
      {
        logger.info { msg }
        true
      }

    @JvmStatic
    fun warn(msg: () -> Any?): MonitorAction =
      {
        logger.warn { msg }
        true
      }

    @JvmStatic
    fun warn(msg: String): MonitorAction =
      {
        logger.warn { msg }
        true
      }

    @JvmStatic
    fun error(msg: () -> Any?): MonitorAction =
      {
        logger.error { msg }
        true
      }

    @JvmStatic
    fun error(msg: String): MonitorAction =
      {
        logger.error { msg }
        true
      }
  }
}
