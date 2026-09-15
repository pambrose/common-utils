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

package com.pambrose.common.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import zipkin2.reporter.BytesMessageSender
import zipkin2.reporter.Encoding

class ZipkinReporterServiceTests : StringSpec() {
  // A sender that records each encoded span instead of posting it to a Zipkin server.
  private fun recordingSender(sent: MutableList<String>): BytesMessageSender =
    mockk<BytesMessageSender>(relaxed = true) {
      every { encoding() } returns Encoding.JSON
      every { messageMaxBytes() } returns 500_000
      every { messageSizeInBytes(any<Int>()) } answers { firstArg<Int>() + 2 }
      every { messageSizeInBytes(any<List<ByteArray>>()) } answers
        { firstArg<List<ByteArray>>().sumOf { bytes -> bytes.size } + 2 }
      every { send(any()) } answers { firstArg<List<ByteArray>>().forEach { bytes -> sent += bytes.decodeToString() } }
    }

  // Starts a reporter over the recording sender, finishes one span, and stops the reporter right away.
  private fun reportOneSpanAndStop(
    serviceName: String,
    tracingServiceName: String? = null,
  ): List<String> {
    val sent = mutableListOf<String>()
    val service = ZipkinReporterService("http://localhost:9411/api/v2/spans", serviceName, recordingSender(sent))
    service.startSync()
    val tracing = if (tracingServiceName == null) service.newTracing() else service.newTracing(tracingServiceName)
    tracing.use { it.tracer().nextSpan().name("work").start().finish() }
    service.stopSync()
    return sent
  }

  init {
    "the default service name falls back to Brave's default" {
      ZipkinReporterService("http://localhost:9411/api/v2/spans").apply {
        defaultServiceName shouldBe "unknown"
        // Start and stop so shutDown() closes the real sender.
        startSync()
        stopSync()
      }
    }

    "newTracing defaults to the reporter's service name" {
      reportOneSpanAndStop(serviceName = "reporter-name").joinToString() shouldContain
        "\"serviceName\":\"reporter-name\""
    }

    "newTracing still accepts an explicit service name" {
      reportOneSpanAndStop(
        serviceName = "reporter-name",
        tracingServiceName = "explicit-name",
      ).joinToString() shouldContain
        "\"serviceName\":\"explicit-name\""
    }

    "a span finished just before a graceful stop is sent rather than dropped" {
      reportOneSpanAndStop(serviceName = "flushed").size shouldBe 1
    }

    // The reporter's flusher thread holds a batch for up to the message timeout, and close() waits only the close
    // timeout for that thread before dropping what it holds.
    "the reporter sends a held batch before close stops waiting for it" {
      fun Any.field(name: String): Any = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

      val service = ZipkinReporterService("http://localhost:9411/api/v2/spans", "timeouts", recordingSender([]))
      // The public AsyncReporter wraps the internal reporter that holds the timeouts.
      val reporter = service.field("reporter").field("delegate")

      ((reporter.field("messageTimeoutNanos") as Long) < (reporter.field("closeTimeoutNanos") as Long)) shouldBe true
    }
  }
}
