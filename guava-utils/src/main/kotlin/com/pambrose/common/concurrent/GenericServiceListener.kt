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

import com.pambrose.common.dsl.GuavaDsl
import com.pambrose.common.dsl.GuavaDsl.serviceListener
import com.google.common.util.concurrent.Service
import io.github.oshai.kotlinlogging.KLogger

/**
 * Creates a [Service.Listener] for this [Service] that logs all lifecycle transitions using the provided [logger]:
 * running and terminated at INFO, the starting and stopping that lead to them at DEBUG, and failed at ERROR with
 * its cause.
 *
 * Java callers still get the method from before 5.4.0, which is declared to return the mutable
 * [GuavaDsl.ServiceListenerHelper]; in 6.0.0 it will return [Service.Listener] for them too.
 *
 * @param logger the [KLogger] instance to use for logging lifecycle events.
 * @return a configured [Service.Listener].
 */
@JvmSynthetic
fun Service.genericServiceListener(logger: KLogger): Service.Listener = genericServiceListenerHelper(logger)

// The 5.3 method, which returned the mutable helper, kept for binary compatibility. @PublishedApi keeps it public and
// visible to Java under its old JVM name, while Kotlin callers resolve to the Service.Listener function above. That
// one is @JvmSynthetic, so Java does not see two methods that differ only in their return type. In 6.0.0, remove this
// function and that @JvmSynthetic.
@PublishedApi
@JvmName("genericServiceListener")
internal fun Service.genericServiceListenerHelper(logger: KLogger): GuavaDsl.ServiceListenerHelper =
  serviceListener {
    val service = this@genericServiceListenerHelper
    starting { logger.debug { "Starting $service" } }
    running { logger.info { "Running $service" } }
    stopping { logger.debug { "Stopping $service" } }
    terminated { logger.info { "Terminated $service" } }
    failed { from, t -> logger.error(t) { "Failed on $from $service" } }
  }
