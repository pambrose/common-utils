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

package com.github.pambrose.common.dsl

import com.github.pambrose.common.util.isNull
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

object KtorDsl {
  fun newHttpClient(expectSuccess: Boolean = false): HttpClient =
    HttpClient {
      this.expectSuccess = expectSuccess
      install(HttpTimeout)
    }

  suspend fun <T> withHttpClient(
    httpClient: HttpClient? = null,
    expectSuccess: Boolean = false,
    block: suspend HttpClient.() -> T,
  ): T =
    if (httpClient.isNull())
      newHttpClient(expectSuccess).use { client -> client.block() }
    else
      httpClient.block()

  suspend fun <T> httpClient(
    httpClient: HttpClient? = null,
    expectSuccess: Boolean = false,
    block: suspend (HttpClient) -> T,
  ): T =
    if (httpClient.isNull())
      newHttpClient(expectSuccess).use { client -> block(client) }
    else
      block(httpClient)

  suspend fun <T> HttpClient.get(
    url: String,
    setUp: HttpRequestBuilder.() -> Unit = {},
    block: suspend (HttpResponse) -> T,
  ): T =
    request(url) {
      method = HttpMethod.Get
      setUp.invoke(this)
    }.let { clientCall: HttpResponse -> block(clientCall) }

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
