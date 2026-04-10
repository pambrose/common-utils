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

import brave.Tracing

/**
 * Provides a Kotlin DSL for constructing Brave [Tracing] instances for Zipkin integration.
 */
object ZipkinDsl {
  /**
   * Creates and configures a Brave [Tracing] instance.
   *
   * @param block a lambda with [Tracing.Builder] as receiver for configuring the tracer
   *   (e.g., local service name, span reporter, sampler).
   * @return the configured [Tracing] instance.
   */
  fun tracing(block: Tracing.Builder.() -> Unit): Tracing =
    Tracing.newBuilder()
      .run {
        block(this)
        build()
      }
}
