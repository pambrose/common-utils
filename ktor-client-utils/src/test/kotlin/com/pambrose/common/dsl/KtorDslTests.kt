@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.pambrose.common.dsl.KtorDsl.httpClient
import com.pambrose.common.dsl.KtorDsl.newHttpClient
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

class KtorDslTests : StringSpec() {
  init {
    "newHttpClient creates a working client" {
      val client = newHttpClient()
      client shouldNotBe null
      client.close()
    }

    "withHttpClient creates client when null passed" {
      val result =
        withHttpClient {
          this shouldNotBe null
          "created"
        }
      result shouldBe "created"
    }

    "withHttpClient uses provided client when non-null" {
      val mockEngine =
        MockEngine { request ->
          respond(content = "test response", status = HttpStatusCode.OK)
        }
      val providedClient = HttpClient(mockEngine)

      val result =
        withHttpClient(httpClient = providedClient) {
          this shouldBe providedClient
          "provided"
        }
      result shouldBe "provided"
      providedClient.close()
    }

    "httpClient creates client when null passed" {
      val result =
        httpClient { client ->
          client shouldNotBe null
          "created"
        }
      result shouldBe "created"
    }

    "httpClient uses provided client when non-null" {
      val mockEngine =
        MockEngine { request ->
          respond(content = "test response", status = HttpStatusCode.OK)
        }
      val providedClient = HttpClient(mockEngine)

      val result =
        httpClient(httpClient = providedClient) { client ->
          client shouldBe providedClient
          "provided"
        }
      result shouldBe "provided"
      providedClient.close()
    }

    "blockingGet performs a GET request" {
      val mockEngine =
        MockEngine { request ->
          respond(content = "hello from mock", status = HttpStatusCode.OK)
        }
      val client = HttpClient(mockEngine)

      val result =
        withHttpClient(httpClient = client) {
          with(KtorDsl) {
            this@withHttpClient.get("http://localhost/test") { response ->
              response.status shouldBe HttpStatusCode.OK
              response.bodyAsText()
            }
          }
        }
      result shouldBe "hello from mock"
      client.close()
    }
  }
}
