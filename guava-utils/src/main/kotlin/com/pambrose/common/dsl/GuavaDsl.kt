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

package com.pambrose.common.dsl

import com.pambrose.common.delegate.SingleAssignVar.singleAssign
import com.google.common.base.MoreObjects
import com.google.common.util.concurrent.Service
import com.google.common.util.concurrent.ServiceManager

/**
 * Container object for Guava DSL builder functions.
 */
object GuavaDsl {
  /**
   * Builds a `toString()` representation using Guava's [MoreObjects.ToStringHelper].
   *
   * This extension function on [Any] creates a [MoreObjects.ToStringHelper] and applies
   * the given [block] to configure its fields, then returns the resulting string.
   *
   * @param block configuration block applied to the [MoreObjects.ToStringHelper].
   * @return the formatted string representation.
   */
  fun Any.toStringElements(block: MoreObjects.ToStringHelper.() -> Unit) =
    MoreObjects
      .toStringHelper(this)
      .run {
        block(this)
        toString()
      }

  /**
   * Creates and configures a Guava [ServiceManager] from the given list of [services].
   *
   * @param services the list of [Service] instances to manage.
   * @param block configuration block applied to the [ServiceManager].
   * @return the configured [ServiceManager].
   */
  fun serviceManager(
    services: List<Service>,
    block: ServiceManager.() -> Unit,
  ) = ServiceManager(services).apply { block(this) }

  /**
   * Creates a [ServiceManager.Listener] using a DSL builder.
   *
   * @param init configuration block for setting lifecycle callbacks.
   * @return the configured [ServiceManagerListenerHelper].
   */
  fun serviceManagerListener(init: ServiceManagerListenerHelper.() -> Unit) =
    ServiceManagerListenerHelper().apply { init() }

  /**
   * DSL builder for [ServiceManager.Listener] that allows setting callbacks for
   * [healthy], [stopped], and [failure] lifecycle events.
   */
  class ServiceManagerListenerHelper : ServiceManager.Listener() {
    private var healthyBlock: (() -> Unit)? by singleAssign()
    private var stoppedBlock: (() -> Unit)? by singleAssign()
    private var failureBlock: ((Service) -> Unit)? by singleAssign()

    override fun healthy() {
      super.healthy()
      healthyBlock?.invoke()
    }

    override fun stopped() {
      super.stopped()
      stoppedBlock?.invoke()
    }

    override fun failure(service: Service) {
      super.failure(service)
      failureBlock?.invoke(service)
    }

    /**
     * Sets the callback invoked when all services are healthy.
     *
     * @param block the callback to invoke.
     */
    fun healthy(block: () -> Unit) {
      healthyBlock = block
    }

    /**
     * Sets the callback invoked when all services have stopped.
     *
     * @param block the callback to invoke.
     */
    fun stopped(block: () -> Unit) {
      stoppedBlock = block
    }

    /**
     * Sets the callback invoked when a service fails.
     *
     * @param block the callback to invoke, receiving the failed [Service].
     */
    fun failure(block: (Service) -> Unit) {
      failureBlock = block
    }
  }

  /**
   * Creates a [Service.Listener] using a DSL builder.
   *
   * @param init configuration block for setting lifecycle callbacks.
   * @return the configured [ServiceListenerHelper].
   */
  fun serviceListener(init: ServiceListenerHelper.() -> Unit) = ServiceListenerHelper().apply { init() }

  /**
   * DSL builder for [Service.Listener] that allows setting callbacks for
   * [starting], [running], [stopping], [terminated], and [failed] lifecycle events.
   */
  class ServiceListenerHelper : Service.Listener() {
    private var startingBlock: (() -> Unit)? by singleAssign()
    private var runningBlock: (() -> Unit)? by singleAssign()
    private var stoppingBlock: ((Service.State) -> Unit)? by singleAssign()
    private var terminatedBlock: ((Service.State) -> Unit)? by singleAssign()
    private var failedBlock: ((Service.State, Throwable) -> Unit)? by singleAssign()

    override fun starting() {
      super.starting()
      startingBlock?.invoke()
    }

    override fun running() {
      super.running()
      runningBlock?.invoke()
    }

    override fun stopping(from: Service.State) {
      super.stopping(from)
      stoppingBlock?.invoke(from)
    }

    override fun terminated(from: Service.State) {
      super.terminated(from)
      terminatedBlock?.invoke(from)
    }

    override fun failed(
      from: Service.State,
      failure: Throwable,
    ) {
      super.failed(from, failure)
      failedBlock?.invoke(from, failure)
    }

    /**
     * Sets the callback invoked when the service is starting.
     *
     * @param block the callback to invoke, or `null` to clear.
     */
    fun starting(block: (() -> Unit)?) {
      startingBlock = block
    }

    /**
     * Sets the callback invoked when the service enters the running state.
     *
     * @param block the callback to invoke.
     */
    fun running(block: () -> Unit) {
      runningBlock = block
    }

    /**
     * Sets the callback invoked when the service is stopping.
     *
     * @param block the callback to invoke, receiving the [Service.State] the service is transitioning from.
     */
    fun stopping(block: (Service.State) -> Unit) {
      stoppingBlock = block
    }

    /**
     * Sets the callback invoked when the service has terminated.
     *
     * @param block the callback to invoke, receiving the [Service.State] the service was in before termination.
     */
    fun terminated(block: (Service.State) -> Unit) {
      terminatedBlock = block
    }

    /**
     * Sets the callback invoked when the service has failed.
     *
     * @param block the callback to invoke, receiving the [Service.State] the service was in and the [Throwable] cause.
     */
    fun failed(block: (Service.State, Throwable) -> Unit) {
      failedBlock = block
    }
  }
}
