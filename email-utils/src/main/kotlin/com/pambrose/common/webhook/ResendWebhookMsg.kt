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
 * Represents a webhook message received from the Resend email service.
 *
 * This is the top-level envelope containing the event type, timestamp, and event-specific [Data].
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
)
