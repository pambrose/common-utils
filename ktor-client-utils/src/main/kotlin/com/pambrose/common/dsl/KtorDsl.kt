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

import com.pambrose.common.util.isNull
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking

/**
 * A DSL object providing factory methods and utilities for Ktor [HttpClient] operations.
 *
 * Supports both suspending and blocking HTTP request patterns with automatic
 * client lifecycle management.
 */
object KtorDsl {
  /**
   * Creates a new [HttpClient] configured with [HttpTimeout] support.
   *
   * @param expectSuccess if `true`, non-2xx responses will throw exceptions
   * @return a new [HttpClient] instance
   */
  fun newHttpClient(expectSuccess: Boolean = false): HttpClient =
    HttpClient {
      this.expectSuccess = expectSuccess
      install(HttpTimeout)
    }

  /**
   * Executes [block] with an [HttpClient] as the receiver. If [httpClient] is `null`, a new
   * client is created and automatically closed after the block completes.
   *
   * @param T the return type of the block
   * @param httpClient an optional pre-existing client to reuse; if `null`, a new client is created
   * @param expectSuccess if `true`, non-2xx responses will throw exceptions (only applies to newly created clients)
   * @param block the suspending block to execute with the client as receiver
   * @return the result of [block]
   */
  suspend fun <T> withHttpClient(
    httpClient: HttpClient? = null,
    expectSuccess: Boolean = false,
    block: suspend HttpClient.() -> T,
  ): T =
    if (httpClient.isNull())
      newHttpClient(expectSuccess).use { client -> client.block() }
    else
      httpClient.block()

  /**
   * Executes [block] with an [HttpClient] passed as a parameter. If [httpClient] is `null`, a new
   * client is created and automatically closed after the block completes.
   *
   * @param T the return type of the block
   * @param httpClient an optional pre-existing client to reuse; if `null`, a new client is created
   * @param expectSuccess if `true`, non-2xx responses will throw exceptions (only applies to newly created clients)
   * @param block the suspending block to execute, receiving the client as a parameter
   * @return the result of [block]
   */
  suspend fun <T> httpClient(
    httpClient: HttpClient? = null,
    expectSuccess: Boolean = false,
    block: suspend (HttpClient) -> T,
  ): T =
    if (httpClient.isNull())
      newHttpClient(expectSuccess).use { client -> block(client) }
    else
      block(httpClient)

  /**
   * Performs an HTTP GET request on this [HttpClient] and passes the response to [block].
   *
   * This is an extension function on [HttpClient].
   *
   * @param T the return type of the block
   * @param url the URL to send the GET request to
   * @param setUp an optional configuration block for the [HttpRequestBuilder]
   * @param block a suspending block that receives the [HttpResponse]
   * @return the result of [block]
   */
  suspend fun <T> HttpClient.get(
    url: String,
    setUp: HttpRequestBuilder.() -> Unit = {},
    block: suspend (HttpResponse) -> T,
  ): T =
    request(url) {
      method = HttpMethod.Get
      setUp.invoke(this)
    }.let { clientCall: HttpResponse -> block(clientCall) }

  /**
   * Performs a blocking HTTP GET request using a temporary [HttpClient].
   *
   * Creates a new client, executes the GET request, and blocks the current thread
   * until the response is received and processed.
   *
   * @param T the return type of the block
   * @param url the URL to send the GET request to
   * @param setUp an optional configuration block for the [HttpRequestBuilder]
   * @param block a suspending block that receives the [HttpResponse]
   * @return the result of [block]
   */
  fun <T> blockingGet(
    url: String,
    setUp: HttpRequestBuilder.() -> Unit = {},
    block: suspend (HttpResponse) -> T,
  ): T =
    runBlocking {
      withHttpClient {
        get(url, setUp, block)
      }
    }
}
