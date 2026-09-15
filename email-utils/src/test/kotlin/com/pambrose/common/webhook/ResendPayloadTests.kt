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

package com.pambrose.common.webhook

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Decodes the payloads Resend documents, verbatim, rather than the models' own output. Copied from
 * https://resend.com/docs/webhooks/emails/bounced and .../clicked, checked 2026-09-15.
 */
class ResendPayloadTests : StringSpec() {
  init {
    val bouncedPayload =
      """
      {
        "type": "email.bounced",
        "created_at": "2026-11-22T23:41:12.126Z",
        "data": {
          "broadcast_id": "8b146471-e88e-4322-86af-016cd36fd216",
          "created_at": "2026-11-22T23:41:11.894Z",
          "email_id": "56761188-7520-42d8-8898-ff6fc54ce618",
          "message_id": "<111-222-333@email.example.com>",
          "from": "Acme <onboarding@resend.dev>",
          "to": ["delivered@resend.dev"],
          "subject": "Sending this example",
          "template_id": "43f68331-0622-4e15-8202-246a0388854b",
          "bounce": {
            "message": "The recipient's email address is on the suppression list because it has a recent history of producing hard bounces.",
            "subType": "Suppressed",
            "type": "Permanent"
          },
          "tags": {
            "category": "confirm_email"
          }
        }
      }
      """.trimIndent()

    val clickedPayload =
      """
      {
        "type": "email.clicked",
        "created_at": "2026-11-22T23:41:12.126Z",
        "data": {
          "broadcast_id": "8b146471-e88e-4322-86af-016cd36fd216",
          "created_at": "2026-11-22T23:41:11.894Z",
          "email_id": "56761188-7520-42d8-8898-ff6fc54ce618",
          "message_id": "<111-222-333@email.example.com>",
          "from": "Acme <onboarding@resend.dev>",
          "to": ["delivered@resend.dev"],
          "click": {
            "ipAddress": "122.115.53.11",
            "link": "https://resend.com",
            "timestamp": "2026-11-24T05:00:57.163Z",
            "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.1 Safari/605.1.15"
          },
          "subject": "Sending this example",
          "template_id": "43f68331-0622-4e15-8202-246a0388854b",
          "tags": {
            "category": "confirm_email"
          }
        }
      }
      """.trimIndent()

    "the documented bounced payload decodes with a default Json" {
      val msg = Json.decodeFromString<ResendWebhookMsg>(bouncedPayload)

      msg.type shouldBe "email.bounced"
      msg.data.broadcastId shouldBe "8b146471-e88e-4322-86af-016cd36fd216"
      msg.data.messageId shouldBe "<111-222-333@email.example.com>"
      msg.data.templateId shouldBe "43f68331-0622-4e15-8202-246a0388854b"
      msg.data.tags shouldBe mapOf("category" to "confirm_email")
      msg.data.to shouldBe ["delivered@resend.dev"]
    }

    "the documented bounce classification is kept" {
      val bounce = Json.decodeFromString<ResendWebhookMsg>(bouncedPayload).data.bounce

      bounce?.type shouldBe "Permanent"
      bounce?.subType shouldBe "Suppressed"
      bounce?.message shouldBe
        "The recipient's email address is on the suppression list because it has a recent history of " +
        "producing hard bounces."
    }

    "the documented clicked payload decodes with a default Json" {
      val msg = Json.decodeFromString<ResendWebhookMsg>(clickedPayload)

      msg.type shouldBe "email.clicked"
      msg.data.click?.ipAddress shouldBe "122.115.53.11"
      msg.data.click?.link shouldBe "https://resend.com"
      msg.data.broadcastId shouldBe "8b146471-e88e-4322-86af-016cd36fd216"
    }

    "decode accepts a payload carrying fields the models do not declare" {
      val payload =
        """
        {
          "type": "email.opened",
          "created_at": "2026-11-22T23:41:12.126Z",
          "data": {
            "created_at": "2026-11-22T23:41:11.894Z",
            "email_id": "56761188-7520-42d8-8898-ff6fc54ce618",
            "from": "Acme <onboarding@resend.dev>",
            "a_field_resend_added_later": {"nested": [1, 2, 3]}
          }
        }
        """.trimIndent()

      val msg = ResendWebhookMsg.decode(payload)

      msg.type shouldBe "email.opened"
      msg.data.emailId shouldBe "56761188-7520-42d8-8898-ff6fc54ce618"
    }

    "decode reads the documented payloads too" {
      ResendWebhookMsg.decode(bouncedPayload).data.bounce?.type shouldBe "Permanent"
      ResendWebhookMsg.decode(clickedPayload).data.click?.link shouldBe "https://resend.com"
    }
  }
}
