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
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
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
 * Pins the request-mapping, logging and exception-propagation contract of [ResendService.sendEmail]. The
 * Resend SDK is intercepted via [mockkConstructor] (the `Resend` instance is constructed internally
 * and is not injectable), so no real API key or network is involved.
 */
class ResendServiceTests : StringSpec() {
  init {
    // Tear down the constructor mock so it cannot leak into other specs.
    afterTest { unmockkAll() }

    // Stubs the SDK so that send() captures its request and returns a response with the given id.
    fun stubSend(
      responseId: String = "id-1",
      captured: CapturingSlot<CreateEmailOptions>? = null,
    ): Emails {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      every { anyConstructed<Resend>().emails() } returns emails
      if (captured != null)
        every { emails.send(capture(captured)) } returns CreateEmailResponse(responseId)
      else
        every { emails.send(any()) } returns CreateEmailResponse(responseId)
      return emails
    }

    "sendEmail maps from/to/cc/bcc/subject/html onto the Resend request as .value strings" {
      val captured = slot<CreateEmailOptions>()
      stubSend(responseId = "id-123", captured = captured)

      ResendService("test-api-key").sendEmail(
        from = Email("Sender@Example.com"),
        to = [Email("a@example.com"), Email("b@example.com")],
        cc = [Email("cc@example.com")],
        bcc = [Email("bcc@example.com")],
        subject = "Hello",
        html = "<h1>Hi</h1>",
      )

      val req = captured.captured
      req.from shouldBe "Sender@Example.com"
      req.to shouldBe ["a@example.com", "b@example.com"]
      req.cc shouldBe ["cc@example.com"]
      req.bcc shouldBe ["bcc@example.com"]
      req.subject shouldBe "Hello"
      req.html shouldBe "<h1>Hi</h1>"
    }

    "sendEmail builds a valid request with empty (defaulted) cc and bcc" {
      val captured = slot<CreateEmailOptions>()
      stubSend(responseId = "id-456", captured = captured)

      // cc and bcc omitted -> exercise the emptyList() defaults
      ResendService("test-api-key").sendEmail(
        from = Email("from@example.com"),
        to = [Email("only@example.com")],
        subject = "Subj",
        html = "<p>body</p>",
      )

      val req = captured.captured
      req.to shouldBe ["only@example.com"]
      req.cc shouldBe emptyList()
      req.bcc shouldBe emptyList()
    }

    // Recipient addresses are personal data, so routine logs carry counts and the message id instead.
    "a successful send logs counts and the response id, never recipient addresses" {
      stubSend(responseId = "id-123")

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
      message shouldBe "Sent email [id-123] to 2 to, 1 cc, and 1 bcc recipients"
      message shouldNotContain "@example.com"
    }

    // The caller logs the exception it catches; logging it here too would report every failure twice.
    "sendEmail rethrows the exception thrown by send() without logging it" {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      val boom = ResendException("send failed")
      every { anyConstructed<Resend>().emails() } returns emails
      every { emails.send(any()) } throws boom

      val events =
        capturingEmailLogs { logs ->
          val thrown =
            shouldThrow<ResendException> {
              ResendService("test-api-key").sendEmail(
                from = Email("from@example.com"),
                to = [Email("to@example.com")],
                subject = "Subj",
                html = "<p>x</p>",
              )
            }
          thrown.message shouldBe "send failed"
          logs()
        }

      events.count { it.level == Level.ERROR } shouldBe 0
    }
  }
}
