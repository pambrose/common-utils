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

package com.pambrose.common.webhook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Represents a webhook message received from the Resend email service.
 *
 * This is the top-level envelope containing the event type, timestamp, and event-specific [Data].
 * Decode incoming request bodies with [decode], which tolerates fields Resend adds later. Only `email.*` events
 * decode; see [decode].
 *
 * This type models the payload only. It does not verify the Svix signature headers Resend sends
 * (`svix-id`, `svix-timestamp`, `svix-signature`), so a handler that trusts unverified requests will accept
 * forged events; verify the signature before decoding.
 *
 * @property createdAt the ISO 8601 timestamp when the webhook event was created.
 * @property data the event-specific payload containing email and event details.
 * @property type the event type (e.g., "email.sent", "email.bounced", "email.clicked").
 */
@Serializable
data class ResendWebhookMsg(
  @SerialName("created_at")
  val createdAt: String,
  @SerialName("data")
  val `data`: Data,
  @SerialName("type")
  val type: String,
) {
  companion object {
    /**
     * A [Json] configured for Resend payloads, which ignores fields these models do not declare.
     *
     * Register it wherever the payload is decoded, for example in Ktor's
     * `install(ContentNegotiation) { json(ResendWebhookMsg.json) }`, so `call.receive<ResendWebhookMsg>()`
     * tolerates fields Resend adds later just as [decode] does.
     */
    val json = Json { ignoreUnknownKeys = true }

    /**
     * Decodes a Resend webhook request [body] with [json].
     *
     * Unknown fields are ignored, so an `email.*` event carrying fields these models do not declare still decodes.
     * Only `email.*` events are supported: [Data] requires `email_id` and `from`, which other event families
     * (such as `contact.*` and `domain.*`) do not send, so their payloads throw
     * [kotlinx.serialization.MissingFieldException]. An endpoint subscribed to other events should read `type`
     * first, for example with `json.parseToJsonElement(body).jsonObject["type"]`, and decode only `email.*` ones.
     *
     * @param body the raw JSON request body
     * @return the decoded message
     * @throws kotlinx.serialization.SerializationException if [body] is not a valid webhook payload
     */
    fun decode(body: String): ResendWebhookMsg = json.decodeFromString(body)
  }
}
