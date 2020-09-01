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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.dsl

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

object KtorDsl {

  fun newHttpClient(block: CIOEngineConfig.() -> Unit = {}): HttpClient =
    HttpClient(CIO.create(block)) { install(HttpTimeout) }

  suspend fun withHttpClient(httpClient: HttpClient? = null, block: suspend HttpClient.() -> Unit) {
    if (httpClient == null) {
      newHttpClient()
        .use { client ->
          client.block()
        }
    }
    else {
      httpClient.block()
    }
  }

  suspend fun httpClient(httpClient: HttpClient? = null, block: suspend (HttpClient) -> Unit) {
    if (httpClient == null) {
      newHttpClient()
        .use { client ->
          block(client)
        }
    }
    else {
      block(httpClient)
    }
  }

  suspend fun HttpClient.get(url: String,
                             setUp: HttpRequestBuilder.() -> Unit = {},
                             block: suspend (HttpResponse) -> Unit) {
    val clientCall =
      request<HttpStatement>(url) {
        method = HttpMethod.Get
        setUp()
      }
    clientCall.execute().also { resp -> block(resp) }
  }

  fun blockingGet(url: String,
                  setUp: HttpRequestBuilder.() -> Unit = {},
                  block: suspend (HttpResponse) -> Unit) {
    runBlocking {
      withHttpClient {
        get(url, setUp, block)
      }
    }
  }
}