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

/**
 * Represents the data payload within a [ResendWebhookMsg].
 *
 * Contains details about the email event such as sender, recipients, and optional
 * bounce or click information. Every field Resend documents as optional, or that only some event types
 * carry, is nullable. Decode with [ResendWebhookMsg.decode] so fields Resend adds later are ignored
 * rather than failing the whole payload.
 *
 * @property createdAt the ISO 8601 timestamp when the event was created.
 * @property emailId the unique identifier of the email.
 * @property from the sender email address.
 * @property subject the email subject line, or null if not available.
 * @property to the list of recipient email addresses, or null if not available.
 * @property headers the list of email headers, or null if not included.
 * @property bounce bounce details if this is a bounce event, or null otherwise.
 * @property click click details if this is a click event, or null otherwise.
 * @property tags the tags attached to the email as key-value pairs, or null if none were sent.
 * @property broadcastId the identifier of the broadcast that sent the email, or null for a regular send.
 * @property messageId the RFC Message-ID header value, or null if not sent.
 * @property templateId the identifier of the template used, or null if none was used.
 */
@Serializable
data class Data(
  @SerialName("created_at")
  val createdAt: String,
  @SerialName("email_id")
  val emailId: String,
  @SerialName("from")
  val from: String,
  @SerialName("subject")
  val subject: String? = null,
  @SerialName("to")
  val to: List<String>? = null,
  @SerialName("headers")
  val headers: List<Header>? = null,
  @SerialName("bounce")
  val bounce: Bounce? = null,
  @SerialName("click")
  val click: Click? = null,
  @SerialName("tags")
  val tags: Map<String, String>? = null,
  @SerialName("broadcast_id")
  val broadcastId: String? = null,
  @SerialName("message_id")
  val messageId: String? = null,
  @SerialName("template_id")
  val templateId: String? = null,
)
