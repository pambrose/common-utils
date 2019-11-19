/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package com.github.pambrose.common.servlet

import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class DynamicServlet(private val block: () -> String,
                     private val contentType: String = "text/plain") : HttpServlet() {

  @Throws(ServletException::class, IOException::class)
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    resp.apply {
      status = HttpServletResponse.SC_OK
      setHeader("Cache-Control", "must-revalidate,no-cache,no-store")
      contentType = this@DynamicServlet.contentType
      writer.use { it.println(block()) }
    }
  }

  companion object {
    private const val serialVersionUID = -9215048679370216254L
  }
}