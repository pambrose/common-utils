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

package com.pambrose.common.dsl

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.runBlocking

/**
 * Performs a blocking HTTP GET request using a temporary [io.ktor.client.HttpClient].
 *
 * Creates a new client, executes the GET request, and blocks the current thread
 * until the response is received and processed. JVM only (uses [runBlocking]);
 * declared as an extension on [KtorDsl] so `KtorDsl.blockingGet(...)` call sites
 * keep compiling.
 *
 * @param T the return type of the block
 * @param url the URL to send the GET request to
 * @param setUp an optional configuration block for the [HttpRequestBuilder]
 * @param block a suspending block that receives the [HttpResponse]
 * @return the result of [block]
 */
fun <T> KtorDsl.blockingGet(
  url: String,
  setUp: HttpRequestBuilder.() -> Unit = {},
  block: suspend (HttpResponse) -> T,
): T =
  runBlocking {
    withHttpClient {
      get(url, setUp, block)
    }
  }
