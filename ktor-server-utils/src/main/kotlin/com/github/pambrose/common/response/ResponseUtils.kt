/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Text
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.util.pipeline.PipelineContext

suspend fun PipelineContext<Unit, ApplicationCall>.respondWith(contentTye: ContentType = Text.Html,
                                                               block: () -> String) {
  val html = block.invoke()
  call.respondText(html, contentTye)
}

suspend fun PipelineContext<Unit, ApplicationCall>.redirectTo(block: () -> String) {
  val html = block.invoke()
  call.respondRedirect(html)
}
