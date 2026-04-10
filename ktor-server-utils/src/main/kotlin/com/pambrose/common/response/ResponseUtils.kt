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

package com.pambrose.common.response

import io.ktor.http.ContentType
import io.ktor.http.ContentType.Text
import io.ktor.http.RequestConnectionPoint
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext

/**
 * Responds to the client with text content produced by [block].
 *
 * This is an extension function on [ApplicationCall].
 *
 * @param contentType the content type of the response; defaults to [ContentType.Text.Html]
 * @param block a lambda that returns the response body as a [String]
 */
suspend inline fun ApplicationCall.respondWith(
  contentType: ContentType = Text.Html,
  block: () -> String,
) = respondText(block.invoke(), contentType)

/**
 * Redirects the client to the URL produced by [block].
 *
 * This is an extension function on [ApplicationCall].
 *
 * @param permanent whether to issue a 301 (permanent) redirect; defaults to `false` (302 temporary)
 * @param block a lambda that returns the redirect target URL as a [String]
 */
suspend inline fun ApplicationCall.redirectTo(
  permanent: Boolean = false,
  block: () -> String,
) = respondRedirect(block.invoke(), permanent)

/**
 * Responds to the client with text content produced by [block].
 *
 * This is an extension function on [RoutingContext] that delegates to [ApplicationCall.respondWith].
 *
 * @param contentType the content type of the response; defaults to [ContentType.Text.Html]
 * @param block a lambda that returns the response body as a [String]
 */
suspend inline fun RoutingContext.respondWith(
  contentType: ContentType = Text.Html,
  block: () -> String,
) = call.respondWith(contentType, block)

/**
 * Redirects the client to the URL produced by [block].
 *
 * This is an extension function on [RoutingContext] that delegates to [ApplicationCall.redirectTo].
 *
 * @param permanent whether to issue a 301 (permanent) redirect; defaults to `false` (302 temporary)
 * @param block a lambda that returns the redirect target URL as a [String]
 */
suspend inline fun RoutingContext.redirectTo(
  permanent: Boolean = false,
  block: () -> String,
) = call.redirectTo(permanent, block)

/**
 * Builds the URI prefix (scheme, host, and port) for this [RequestConnectionPoint].
 *
 * For example, returns `"https://example.com:443"`.
 *
 * This is an extension property on [RequestConnectionPoint].
 */
val RequestConnectionPoint.uriPrefix get() = "$scheme://$serverHost:$serverPort"
