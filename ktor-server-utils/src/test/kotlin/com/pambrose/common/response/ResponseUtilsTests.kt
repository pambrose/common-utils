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

package com.pambrose.common.response

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.RequestConnectionPoint
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk

class ResponseUtilsTests : StringSpec() {
  init {
    "ApplicationCall respondWith uses default text/html content type" {
      testApplication {
        routing {
          get("/page") {
            call.respondWith { "<h1>hi</h1>" }
          }
        }
        client.get("/page").apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "<h1>hi</h1>"
          ContentType.parse(headers[HttpHeaders.ContentType]!!).match(ContentType.Text.Html) shouldBe true
        }
      }
    }

    "ApplicationCall respondWith honors explicit content type" {
      testApplication {
        routing {
          get("/plain") {
            call.respondWith(ContentType.Text.Plain) { "hello" }
          }
        }
        client.get("/plain").apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "hello"
          ContentType.parse(headers[HttpHeaders.ContentType]!!).match(ContentType.Text.Plain) shouldBe true
        }
      }
    }

    "RoutingContext respondWith delegates to ApplicationCall" {
      testApplication {
        routing {
          get("/route") {
            respondWith(ContentType.Text.Plain) { "routed" }
          }
        }
        client.get("/route").apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "routed"
          ContentType.parse(headers[HttpHeaders.ContentType]!!).match(ContentType.Text.Plain) shouldBe true
        }
      }
    }

    "ApplicationCall redirectTo issues a 302 by default" {
      testApplication {
        routing {
          get("/old") {
            call.redirectTo { "/new" }
          }
        }
        val client = createClient { followRedirects = false }
        client.get("/old").apply {
          status shouldBe HttpStatusCode.Found
          headers[HttpHeaders.Location] shouldBe "/new"
        }
      }
    }

    "ApplicationCall redirectTo with permanent=true issues 301" {
      testApplication {
        routing {
          get("/old") {
            call.redirectTo(permanent = true) { "/new" }
          }
        }
        val client = createClient { followRedirects = false }
        client.get("/old").apply {
          status shouldBe HttpStatusCode.MovedPermanently
          headers[HttpHeaders.Location] shouldBe "/new"
        }
      }
    }

    "RoutingContext redirectTo delegates to ApplicationCall" {
      testApplication {
        routing {
          get("/old") {
            redirectTo(permanent = true) { "/new" }
          }
        }
        val client = createClient { followRedirects = false }
        client.get("/old").apply {
          status shouldBe HttpStatusCode.MovedPermanently
          headers[HttpHeaders.Location] shouldBe "/new"
        }
      }
    }

    "uriPrefix joins scheme, host, and port" {
      val cp = mockk<RequestConnectionPoint>()
      every { cp.scheme } returns "https"
      every { cp.serverHost } returns "example.com"
      every { cp.serverPort } returns 8443
      cp.uriPrefix shouldBe "https://example.com:8443"
    }
  }
}
