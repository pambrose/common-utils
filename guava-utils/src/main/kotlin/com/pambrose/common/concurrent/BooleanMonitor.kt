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

package com.pambrose.common.concurrent

import com.google.common.base.MoreObjects
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * A [GenericMonitor] implementation backed by an atomic boolean value.
 *
 * Threads can block until the value becomes `true` or `false` using the inherited
 * `waitUntilTrue` and `waitUntilFalse` methods from [GenericMonitor].
 *
 * @param initValue the initial boolean value.
 */
class BooleanMonitor(
  initValue: Boolean,
) : GenericMonitor() {
  private val monVal = AtomicBoolean(false)

  override val monitorSatisfied get() = get()

  init {
    set(initValue)
  }

  /**
   * Returns the current boolean value.
   *
   * @return the current value.
   */
  fun get() = monVal.load()

  /**
   * Sets the boolean value in a thread-safe manner, entering and leaving the monitor.
   *
   * @param value the new boolean value.
   */
  fun set(value: Boolean) {
    monitor.enter()
    try {
      monVal.store(value)
    } finally {
      monitor.leave()
    }
  }

  override fun toString() =
    MoreObjects
      .toStringHelper(this)
      .add("value", monVal.load())
      .toString()

  companion object {
    private val logger = KotlinLogging.logger {}

    /**
     * Creates a [MonitorAction] that logs the message at DEBUG level and returns `true`.
     *
     * @param msg a lambda producing the log message.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun debug(msg: () -> Any?): MonitorAction =
      {
        logger.debug { msg() }
        true
      }

    /**
     * Creates a [MonitorAction] that logs the message at DEBUG level and returns `true`.
     *
     * @param msg the log message string.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun debug(msg: String): MonitorAction =
      {
        logger.debug { msg }
        true
      }

    /**
     * Creates a [MonitorAction] that logs the message at INFO level and returns `true`.
     *
     * @param msg a lambda producing the log message.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun info(msg: () -> Any?): MonitorAction =
      {
        logger.info { msg() }
        true
      }

    /**
     * Creates a [MonitorAction] that logs the message at INFO level and returns `true`.
     *
     * @param msg the log message string.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun info(msg: String): MonitorAction =
      {
        logger.info { msg }
        true
      }

    /**
     * Creates a [MonitorAction] that logs the message at WARN level and returns `true`.
     *
     * @param msg a lambda producing the log message.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun warn(msg: () -> Any?): MonitorAction =
      {
        logger.warn { msg() }
        true
      }

    /**
     * Creates a [MonitorAction] that logs the message at WARN level and returns `true`.
     *
     * @param msg the log message string.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun warn(msg: String): MonitorAction =
      {
        logger.warn { msg }
        true
      }

    /**
     * Creates a [MonitorAction] that logs the message at ERROR level and returns `true`.
     *
     * @param msg a lambda producing the log message.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun error(msg: () -> Any?): MonitorAction =
      {
        logger.error { msg() }
        true
      }

    /**
     * Creates a [MonitorAction] that logs the message at ERROR level and returns `true`.
     *
     * @param msg the log message string.
     * @return a [MonitorAction] that always returns `true`.
     */
    @JvmStatic
    fun error(msg: String): MonitorAction =
      {
        logger.error { msg }
        true
      }
  }
}
