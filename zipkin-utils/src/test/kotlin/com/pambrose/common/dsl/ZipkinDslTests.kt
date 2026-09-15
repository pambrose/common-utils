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
import brave.sampler.Sampler
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ZipkinDslTests : StringSpec() {
  init {
    "the configuration block is applied to the builder" {
      ZipkinDsl
        .tracing {
          localServiceName("test-service")
          sampler(Sampler.NEVER_SAMPLE)
        }.use { tracing ->
          tracing.sampler() shouldBe Sampler.NEVER_SAMPLE
          tracing.tracer().newTrace().context().sampled() shouldBe false
        }
    }

    "spans from the tracer carry trace and span ids" {
      ZipkinDsl
        .tracing {
          localServiceName("test-tracer-service")
          sampler(Sampler.ALWAYS_SAMPLE)
        }.use { tracing ->
          val span = tracing.tracer().newTrace().name("test-span").start()
          span.context().sampled() shouldBe true
          // Trace IDs are 64-bit (16 hex chars) by default, or 128-bit (32 hex chars) if configured
          span.context().traceIdString().length shouldBe 16
          span.context().spanIdString().length shouldBe 16
          span.finish()
        }
    }

    "a scoped span becomes the current trace context" {
      ZipkinDsl.tracing { localServiceName("test-current-span-service") }.use { tracing ->
        val scoped = tracing.tracer().startScopedSpan("scoped-span")
        try {
          tracing.currentTraceContext().get() shouldBe scoped.context()
        } finally {
          scoped.finish()
        }
      }
    }

    "closing the tracing clears Tracing.current()" {
      val tracing = ZipkinDsl.tracing { localServiceName("test-close-service") }
      tracing.use { Tracing.current() shouldBe it }
      Tracing.current() shouldBe null
    }
  }
}
