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

package com.pambrose.common.email

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.Emails
import com.resend.services.emails.model.CreateEmailResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.slf4j.LoggerFactory

// Runs block with a function that snapshots the log events emitted by the email package in the meantime.
private inline fun <T> capturingEmailLogs(block: (logs: () -> List<ILoggingEvent>) -> T): T {
  val appender = ListAppender<ILoggingEvent>().apply { start() }
  val logger = LoggerFactory.getLogger(ResendService::class.java.packageName) as Logger
  logger.addAppender(appender)
  return try {
    block { synchronized(appender) { appender.list.toList() } }
  } finally {
    logger.detachAppender(appender)
  }
}

/**
 * Recipient addresses are PII, so they must stay out of routine INFO logs, and a rethrown failure must not
 * also be logged (the caller logs it).
 */
class ResendServiceLoggingTests : StringSpec() {
  init {
    afterTest { unmockkAll() }

    "a successful send logs counts and the response id, never recipient addresses" {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      every { anyConstructed<Resend>().emails() } returns emails
      every { emails.send(any()) } returns CreateEmailResponse("id-123")

      val events =
        capturingEmailLogs { logs ->
          ResendService("test-api-key").sendEmail(
            from = Email("from@example.com"),
            to = [Email("first@example.com"), Email("second@example.com")],
            cc = [Email("cc@example.com")],
            bcc = [Email("bcc@example.com")],
            subject = "Subj",
            html = "<p>x</p>",
          )
          logs()
        }

      val message = events.single { it.level == Level.INFO }.formattedMessage
      message shouldContain "id-123"
      message shouldContain "2"
      message shouldNotContain "@example.com"
    }

    "a failed send is rethrown without being logged" {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      every { anyConstructed<Resend>().emails() } returns emails
      every { emails.send(any()) } throws ResendException("send failed")

      val events =
        capturingEmailLogs { logs ->
          shouldThrow<ResendException> {
            ResendService("test-api-key").sendEmail(
              from = Email("from@example.com"),
              to = [Email("to@example.com")],
              subject = "Subj",
              html = "<p>x</p>",
            )
          }
          logs()
        }

      events.count { it.level == Level.ERROR } shouldBe 0
    }
  }
}
