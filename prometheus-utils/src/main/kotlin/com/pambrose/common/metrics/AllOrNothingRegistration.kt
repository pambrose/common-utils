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

package com.pambrose.common.metrics

import io.prometheus.metrics.model.registry.Collector
import io.prometheus.metrics.model.registry.MultiCollector
import io.prometheus.metrics.model.registry.PrometheusRegistry
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// Runs block with a registry that passes every registration straight on to this one and remembers it. If block
// throws, whatever it registered is unregistered again, so its collectors end up registered all together or not at
// all. The client's register(registry) methods return nothing to unregister with, and a set of them can be rejected
// part-way through, so this is where such a partial registration is undone.
internal inline fun <T> PrometheusRegistry.registerAllOrNothing(block: (PrometheusRegistry) -> T): T {
  contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
  val tracking = TrackingRegistry(this)
  var complete = false
  try {
    return block(tracking).also { complete = true }
  } finally {
    if (!complete)
      tracking.unregisterAll()
  }
}

// Registers with target and remembers what it registered, so unregisterAll can take it back out.
internal class TrackingRegistry(
  private val target: PrometheusRegistry,
) : PrometheusRegistry() {
  private val collectors: MutableList<Collector> = []
  private val multiCollectors: MutableList<MultiCollector> = []

  override fun register(collector: Collector) {
    target.register(collector)
    collectors += collector
  }

  override fun register(collector: MultiCollector) {
    target.register(collector)
    multiCollectors += collector
  }

  fun unregisterAll() {
    collectors.forEach(target::unregister)
    multiCollectors.forEach(target::unregister)
  }
}
