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
import io.kotest.matchers.string.shouldContain
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

private const val NO_CACHE = "must-revalidate,no-cache,no-store"

// The media type without its parameters, so the checks do not depend on how Jetty spells the charset.
private fun HttpResponse<*>.mediaType() = contentType().substringBefore(';').trim()

// These specs run the servlets inside a real Jetty server; LambdaServletDoGetTests covers the same paths with mocks.
class LambdaServletTests : StringSpec() {
  init {
    "the default constructor serves the lambda output as text/plain with the no-cache header" {
      val response = fetch(LambdaServlet { "Hello, World!" })

      response.statusCode() shouldBe 200
      response.body().decodeToString() shouldBe "Hello, World!"
      response.mediaType() shouldBe "text/plain"
      response.header("Cache-Control") shouldBe NO_CACHE
    }

    "the content-type constructor serves that content type" {
      val response = fetch(LambdaServlet("application/json") { """{"status": "ok"}""" })

      response.statusCode() shouldBe 200
      response.body().decodeToString() shouldBe """{"status": "ok"}"""
      response.mediaType() shouldBe "application/json"
      response.header("Cache-Control") shouldBe NO_CACHE
    }

    "VersionServlet serves the version the same way" {
      val response = fetch(VersionServlet("1.0.0"))

      response.statusCode() shouldBe 200
      response.body().decodeToString() shouldBe "1.0.0"
      response.mediaType() shouldBe "text/plain"
      response.header("Cache-Control") shouldBe NO_CACHE
    }

    "a throwing lambda gets a 500 from the container" {
      val response = fetch(LambdaServlet { error("body failed") })

      response.statusCode() shouldBe 500
    }

    "a POST gets a 405 and never runs the lambda" {
      var calls = 0
      val response = fetch(LambdaServlet { "count=${++calls}" }) { POST(BodyPublishers.ofString("ignored")) }

      response.statusCode() shouldBe 405
      calls shouldBe 0
    }

    // The body used to end with System.lineSeparator(), which differs between Unix and Windows.
    "the body holds exactly the lambda output, with no line separator appended" {
      fetch(VersionServlet("1.0.0")).body() shouldBe "1.0.0".toByteArray()
    }

    // HttpServlet answers HEAD by running doGet and discarding the body, so the lambda still runs.
    "a HEAD request runs the lambda and gets the headers without a body" {
      var calls = 0
      val response = fetch(LambdaServlet { "count=${++calls}" }) { method("HEAD", BodyPublishers.noBody()) }

      response.statusCode() shouldBe 200
      response.body().size shouldBe 0
      response.header("Cache-Control") shouldBe NO_CACHE
      calls shouldBe 1
    }

    "an OPTIONS request gets a 200 listing the allowed methods and never runs the lambda" {
      var calls = 0
      val response = fetch(LambdaServlet { "count=${++calls}" }) { method("OPTIONS", BodyPublishers.noBody()) }

      response.statusCode() shouldBe 200
      response.header("Allow") shouldContain "GET"
      calls shouldBe 0
    }
  }
}
