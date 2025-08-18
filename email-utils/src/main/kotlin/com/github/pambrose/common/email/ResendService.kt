package com.github.pambrose.common.email

import com.resend.Resend
import com.resend.services.emails.model.CreateEmailOptions
import com.resend.services.emails.model.CreateEmailResponse
import io.github.oshai.kotlinlogging.KotlinLogging.logger

class ResendService(
  envResendApiKey: String,
) {
  private val resend = Resend(envResendApiKey)

  fun sendEmail(
    from: Email,
    to: List<Email>,
    cc: List<Email> = emptyList(),
    bcc: List<Email> = emptyList(),
    subject: String,
    html: String,
  ) {
    runCatching {
      val request =
        CreateEmailOptions.builder().run {
          from(from.value)
          to(to.map { it.value })
          cc(cc.map { it.value })
          bcc(bcc.map { it.value })
          subject(subject)
          html(html)
          build()
        }
      val response: CreateEmailResponse = resend.emails().send(request)

      val toStr = to.joinToString(", ").let { it.ifBlank { "None" } }
      val ccStr = cc.joinToString(", ").let { it.ifBlank { "None" } }
      val bccStr = bcc.joinToString(", ").let { it.ifBlank { "None" } }
      logger.info { "Sent email to: $toStr cc: $ccStr bcc: $bccStr [${response.id}]" }
    }.onFailure { e ->
      logger.error(e) { "sendEmail() error: ${e.message}" }
    }
  }

  companion object {
    private val logger = logger {}
  }
}
