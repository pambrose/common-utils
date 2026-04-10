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

package com.pambrose.common.email

import com.pambrose.common.util.ReadResources.readResourceFile
import java.util.regex.Pattern
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

/**
 * Utility object for email address validation and HTML email body generation.
 */
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

  /**
   * Returns `true` if this [String] matches a valid email address pattern.
   */
  fun String.isValidEmail() = emailPattern.matcher(this).matches()

  /**
   * Returns `true` if this [String] does not match a valid email address pattern.
   */
  fun String.isNotValidEmail() = !isValidEmail()

  /**
   * Builds an HTML email string with an embedded CSS stylesheet and the given body content.
   *
   * @param cssFilename the classpath resource path to the CSS file to embed. Defaults to `"css/email.css"`.
   * @param block a lambda with [BODY] as receiver to define the email body content.
   * @return the complete HTML string for the email.
   */
  fun email(
    cssFilename: String = "css/email.css",
    block: BODY.() -> Unit,
  ): String =
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
