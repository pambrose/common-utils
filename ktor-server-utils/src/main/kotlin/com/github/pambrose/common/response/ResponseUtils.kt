/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

package com.github.pambrose.common.response

import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

suspend inline fun ApplicationCall.respondWith(
  contentType: ContentType = ContentType.Text.Html,
  block: () -> String
) {
  val html = block.invoke()
  respondText(html, contentType)
}

suspend inline fun ApplicationCall.redirectTo(
  permanent: Boolean = false,
  block: () -> String
) {
  val url = block.invoke()
  respondRedirect(url, permanent)
}

suspend inline fun PipelineContext<*, ApplicationCall>.respondWith(
  contentType: ContentType = Text.Html,
  block: () -> String
) = call.respondWith(contentType, block)

suspend inline fun PipelineContext<*, ApplicationCall>.redirectTo(
  permanent: Boolean = false,
  block: () -> String
) = call.redirectTo(permanent, block)

val RequestConnectionPoint.uriPrefix get() = "$scheme://$host:$port"
