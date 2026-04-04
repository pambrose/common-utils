package com.canvascache.email.msgs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Bounce(
  @SerialName("message")
  val message: String,
)
