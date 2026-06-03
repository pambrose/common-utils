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

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.Emails
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll

/**
 * Pins the request-mapping and exception-propagation contract of [ResendService.sendEmail]. The
 * Resend SDK is intercepted via [mockkConstructor] (the `Resend` instance is constructed internally
 * and is not injectable), so no real API key or network is involved.
 */
class ResendServiceTests : StringSpec() {
  init {
    // Tear down the constructor mock so it cannot leak into other specs.
    afterTest { unmockkAll() }

    "sendEmail maps from/to/cc/bcc/subject/html onto the Resend request as .value strings" {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      val captured = slot<CreateEmailOptions>()
      every { anyConstructed<Resend>().emails() } returns emails
      every { emails.send(capture(captured)) } returns CreateEmailResponse("id-123")

      ResendService("test-api-key").sendEmail(
        from = Email("Sender@Example.com"),
        to = listOf(Email("a@example.com"), Email("b@example.com")),
        cc = listOf(Email("cc@example.com")),
        bcc = listOf(Email("bcc@example.com")),
        subject = "Hello",
        html = "<h1>Hi</h1>",
      )

      val req = captured.captured
      req.from shouldBe "Sender@Example.com"
      req.to shouldBe listOf("a@example.com", "b@example.com")
      req.cc shouldBe listOf("cc@example.com")
      req.bcc shouldBe listOf("bcc@example.com")
      req.subject shouldBe "Hello"
      req.html shouldBe "<h1>Hi</h1>"
    }

    "sendEmail builds a valid request with empty (defaulted) cc and bcc" {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      val captured = slot<CreateEmailOptions>()
      every { anyConstructed<Resend>().emails() } returns emails
      every { emails.send(capture(captured)) } returns CreateEmailResponse("id-456")

      // cc and bcc omitted -> exercise the emptyList() defaults
      ResendService("test-api-key").sendEmail(
        from = Email("from@example.com"),
        to = listOf(Email("only@example.com")),
        subject = "Subj",
        html = "<p>body</p>",
      )

      val req = captured.captured
      req.to shouldBe listOf("only@example.com")
      req.cc shouldBe emptyList()
      req.bcc shouldBe emptyList()
    }

    "sendEmail returns normally on success" {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      every { anyConstructed<Resend>().emails() } returns emails
      every { emails.send(any()) } returns CreateEmailResponse("ok-1")

      // Should not throw.
      ResendService("test-api-key").sendEmail(
        from = Email("from@example.com"),
        to = listOf(Email("to@example.com")),
        subject = "Subj",
        html = "<p>x</p>",
      )
    }

    "sendEmail propagates (rethrows) the exception thrown by send()" {
      mockkConstructor(Resend::class)
      val emails = mockk<Emails>()
      val boom = ResendException("send failed")
      every { anyConstructed<Resend>().emails() } returns emails
      every { emails.send(any()) } throws boom

      val thrown =
        shouldThrow<ResendException> {
          ResendService("test-api-key").sendEmail(
            from = Email("from@example.com"),
            to = listOf(Email("to@example.com")),
            subject = "Subj",
            html = "<p>x</p>",
          )
        }
      thrown.message shouldBe "send failed"
    }
  }
}
