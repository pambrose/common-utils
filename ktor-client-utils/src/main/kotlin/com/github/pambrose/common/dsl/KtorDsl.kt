/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.dsl

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.readText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking

object KtorDsl {

  fun newHttpClient(): HttpClient = HttpClient(CIO)

  suspend fun http(httpClient: HttpClient? = null, block: suspend HttpClient.() -> Unit) {
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

  suspend fun HttpClient.get(url: String,
                             setUp: HttpRequestBuilder.() -> Unit = {},
                             block: suspend (String) -> Unit) {
    val clientCall =
      request<HttpStatement>(url) {
        method = HttpMethod.Get
        setUp()
      }
    clientCall.execute().also { resp -> block(resp.readText()) }
  }

  fun blockingGet(url: String,
                  setUp: HttpRequestBuilder.() -> Unit = {},
                  block: suspend (String) -> Unit) {
    runBlocking {
      http {
        get(url, setUp, block)
      }
    }
  }
}