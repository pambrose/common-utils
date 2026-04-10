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
 * Represents a link click event from a Resend webhook notification.
 *
 * @property ipAddress the IP address of the user who clicked the link.
 * @property link the URL that was clicked.
 * @property linkTags optional tags associated with the clicked link.
 * @property timestamp the ISO 8601 timestamp of the click event.
 * @property userAgent the user agent string of the browser that performed the click.
 */
@Serializable
data class Click(
  @SerialName("ipAddress")
  val ipAddress: String,
  @SerialName("link")
  val link: String,
  @SerialName("linkTags")
  val linkTags: String? = null,
  @SerialName("timestamp")
  val timestamp: String,
  @SerialName("userAgent")
  val userAgent: String,
)
