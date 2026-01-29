/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

package com.github.pambrose.common.dsl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class ZipkinDslTests {
  @Test
  fun tracingCreationTest() {
    val tracing = ZipkinDsl.tracing {
      localServiceName("test-service")
    }
    tracing shouldNotBe null
    tracing.close()
  }

  @Test
  fun tracingWithTracerTest() {
    val tracing = ZipkinDsl.tracing {
      localServiceName("test-tracer-service")
    }
    val tracer = tracing.tracer()
    tracer shouldNotBe null

    val span = tracer.newTrace().name("test-span").start()
    span shouldNotBe null
    span.finish()

    tracing.close()
  }

  @Test
  fun tracingCurrentSpanTest() {
    val tracing = ZipkinDsl.tracing {
      localServiceName("test-current-span-service")
    }
    val tracer = tracing.tracer()

    val span = tracer.newTrace().name("parent-span").start()
    val scopedSpan = tracer.startScopedSpan("scoped-span")
    scopedSpan shouldNotBe null

    val currentSpan = tracing.currentTraceContext().get()
    currentSpan shouldNotBe null

    scopedSpan.finish()
    span.finish()
    tracing.close()
  }

  @Test
  fun tracingWithSamplerTest() {
    val tracing = ZipkinDsl.tracing {
      localServiceName("test-sampler-service")
      sampler(brave.sampler.Sampler.ALWAYS_SAMPLE)
    }
    tracing shouldNotBe null
    tracing.close()
  }

  @Test
  fun tracingTraceIdTest() {
    val tracing = ZipkinDsl.tracing {
      localServiceName("test-trace-id-service")
    }
    val tracer = tracing.tracer()
    val span = tracer.newTrace().name("test-trace-id").start()

    span.context().traceIdString() shouldNotBe null
    span.context().spanIdString() shouldNotBe null
    // Trace IDs are 64-bit (16 hex chars) by default, or 128-bit (32 hex chars) if configured
    span.context().traceIdString().length shouldBe 16

    span.finish()
    tracing.close()
  }
}
