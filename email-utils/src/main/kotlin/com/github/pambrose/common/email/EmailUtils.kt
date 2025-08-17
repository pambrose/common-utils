package com.github.pambrose.common.email

import com.github.pambrose.common.util.ReadResources.readResourceFile
import kotlinx.html.BODY
import kotlinx.html.FlowOrMetaDataContent
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.unsafe
import java.util.regex.Pattern

object EmailUtils {

  private val emailPattern by lazy {
    Pattern.compile(
      "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]|[\\w-]{2,}))@" +
        "((([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?" +
        "[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\." +
        "([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?" +
        "[0-9]{1,2}|25[0-5]|2[0-4][0-9]))|" +
        "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$",
    )
  }

  fun String.isValidEmail() = emailPattern.matcher(this).matches()

  fun String.isNotValidEmail() = !isValidEmail()

  fun email(
    cssFilename: String = "css/email.css",
    block: BODY.() -> Unit,
  ) =
    createHTML()
      .html {
        emailHead(cssFilename)
        body {
          block()
        }
      }

  private fun HTML.emailHead(cssFilename: String) {
    head {
      embedCss(cssFilename)
      setBackground()
    }
  }

  private fun FlowOrMetaDataContent.embedCss(filename: String) {
    style {
      unsafe {
        raw(readResourceFile(filename))
      }
    }
  }

  private fun FlowOrMetaDataContent.setBackground(color: String = "light") {
    meta {
      name = "color-scheme"
      content = color
    }
    meta {
      name = "supported-color-schemes"
      content = color
    }
  }
}
