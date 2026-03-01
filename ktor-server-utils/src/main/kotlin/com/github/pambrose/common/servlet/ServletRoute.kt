/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.servlet

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import jakarta.servlet.http.HttpServlet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.servlet(
  path: String,
  servlet: HttpServlet,
) {
  servlet.init()
  route(path) {
    handle {
      val request = KtorServletRequest(call.request)
      val response = KtorServletResponse()
      withContext(Dispatchers.IO) { servlet.service(request, response) }
      response.getHeaderNames().forEach { name ->
        response.getHeaders(name).forEach { value ->
          call.response.headers.append(name, value)
        }
      }
      val contentType =
        response.getContentType()?.let { ContentType.parse(it) }
          ?: ContentType.Application.OctetStream
      call.response.status(HttpStatusCode.fromValue(response.status))
      call.respondBytes(response.getBodyBytes(), contentType)
    }
  }
}
