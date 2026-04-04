package com.canvascache.email.msgs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Header(
  @SerialName("name")
  val name: String,
  @SerialName("value")
  val value: String,
)
