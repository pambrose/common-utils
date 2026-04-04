package com.canvascache.email.msgs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
