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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.servlet

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.server.request.ApplicationRequest
import io.mockk.every
import io.mockk.mockk

class KtorServletRequestTests : StringSpec() {
  init {
    "getContentType returns null when no Content-Type header is present" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns Headers.Empty
      KtorServletRequest(request).contentType shouldBe null
    }

    "getContentType returns the header value when present" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns headersOf(HttpHeaders.ContentType, "application/json")
      KtorServletRequest(request).contentType shouldBe "application/json"
    }

    "getContentType preserves the charset parameter" {
      val request = mockk<ApplicationRequest>()
      every { request.headers } returns headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")
      KtorServletRequest(request).contentType shouldBe "text/html; charset=UTF-8"
    }
  }
}
