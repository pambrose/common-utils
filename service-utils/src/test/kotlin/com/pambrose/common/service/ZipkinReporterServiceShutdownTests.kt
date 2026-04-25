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
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.BytesMessageSender

class ZipkinReporterServiceShutdownTests : StringSpec() {
  init {
    "sender is closed even when reporter close throws" {
      val mockReporter = mockk<AsyncReporter<*>>(relaxed = true)
      val mockSender = mockk<BytesMessageSender>(relaxed = true)
      every { mockReporter.close() } throws RuntimeException("boom")

      val service = ZipkinReporterService("http://localhost:9411/api/v2/spans")

      val reporterField = ZipkinReporterService::class.java.getDeclaredField("reporter")
      reporterField.isAccessible = true
      reporterField.set(service, mockReporter)

      val senderField = ZipkinReporterService::class.java.getDeclaredField("sender")
      senderField.isAccessible = true
      senderField.set(service, mockSender)

      shouldThrow<RuntimeException> {
        val shutDownMethod = ZipkinReporterService::class.java.getDeclaredMethod("shutDown")
        shutDownMethod.isAccessible = true
        try {
          shutDownMethod.invoke(service)
        } catch (e: java.lang.reflect.InvocationTargetException) {
          throw e.targetException
        }
      }.message shouldBe "boom"

      verify(exactly = 1) { mockReporter.close() }
      verify(exactly = 1) { mockSender.close() }
    }
  }
}
