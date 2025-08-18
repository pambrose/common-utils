package com.canvascache.email.msgs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResendWebhookMsg(
  @SerialName("created_at")
  val createdAt: String,
  @SerialName("data")
  val `data`: Data,
  @SerialName("type")
  val type: String,
)
