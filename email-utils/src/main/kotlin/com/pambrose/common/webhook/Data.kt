package com.canvascache.email.msgs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
)
