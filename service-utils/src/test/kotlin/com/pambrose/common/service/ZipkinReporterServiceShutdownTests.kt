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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.BytesMessageSender
import java.lang.reflect.InvocationTargetException

class ZipkinReporterServiceShutdownTests : StringSpec() {
  // Swaps mocks in for the service's reporter and sender, closing the real ones first so the test does not leak
  // the OkHttp sender they were built with.
  private fun serviceWith(
    reporter: AsyncReporter<*>,
    sender: BytesMessageSender,
  ): ZipkinReporterService =
    ZipkinReporterService("http://localhost:9411/api/v2/spans").apply {
      val reporterField = ZipkinReporterService::class.java.getDeclaredField("reporter").apply { isAccessible = true }
      val senderField = ZipkinReporterService::class.java.getDeclaredField("sender").apply { isAccessible = true }
      (reporterField.get(this) as AsyncReporter<*>).close()
      (senderField.get(this) as BytesMessageSender).close()
      reporterField.set(this, reporter)
      senderField.set(this, sender)
    }

  private fun ZipkinReporterService.invokeShutDown() {
    val shutDown = ZipkinReporterService::class.java.getDeclaredMethod("shutDown").apply { isAccessible = true }
    try {
      shutDown.invoke(this)
    } catch (e: InvocationTargetException) {
      throw e.targetException
    }
  }

  init {
    "sender is closed even when reporter close throws" {
      val mockReporter = mockk<AsyncReporter<*>>(relaxed = true)
      val mockSender = mockk<BytesMessageSender>(relaxed = true)
      every { mockReporter.close() } throws RuntimeException("boom")

      shouldThrow<RuntimeException> { serviceWith(mockReporter, mockSender).invokeShutDown() }.message shouldBe "boom"

      verify(exactly = 1) { mockReporter.close() }
      verify(exactly = 1) { mockSender.close() }
    }

    "shutDown flushes queued spans before closing the reporter and the sender" {
      val mockReporter = mockk<AsyncReporter<*>>(relaxed = true)
      val mockSender = mockk<BytesMessageSender>(relaxed = true)

      serviceWith(mockReporter, mockSender).invokeShutDown()

      verifyOrder {
        mockReporter.flush()
        mockReporter.close()
        mockSender.close()
      }
    }

    "a failing flush does not prevent the reporter and the sender from closing" {
      val mockReporter = mockk<AsyncReporter<*>>(relaxed = true)
      val mockSender = mockk<BytesMessageSender>(relaxed = true)
      every { mockReporter.flush() } throws IllegalStateException("flush failed")

      serviceWith(mockReporter, mockSender).invokeShutDown()

      verify(exactly = 1) { mockReporter.close() }
      verify(exactly = 1) { mockSender.close() }
    }
  }
}
