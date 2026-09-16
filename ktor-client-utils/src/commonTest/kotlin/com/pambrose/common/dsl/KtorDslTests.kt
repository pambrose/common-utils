@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.pambrose.common.dsl.KtorDsl.httpClient
import com.pambrose.common.dsl.KtorDsl.newHttpClient
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout

class KtorDslTests : StringSpec() {
  init {
    "withHttpClient uses provided client when non-null and leaves it open" {
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
      providedClient.coroutineContext.job.isActive shouldBe true
      providedClient.close()
    }

    "httpClient uses provided client when non-null and leaves it open" {
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
      providedClient.coroutineContext.job.isActive shouldBe true
      providedClient.close()
    }

    "a provided client ignores expectSuccess" {
      // expectSuccess only configures a client the helpers create; this one keeps its own default (false).
      HttpClient(MockEngine { respond(content = "boom", status = HttpStatusCode.InternalServerError) }).use { client ->
        val fromReceiver =
          withHttpClient(httpClient = client, expectSuccess = true) {
            with(KtorDsl) { get(URL) { it.status to it.bodyAsText() } }
          }
        fromReceiver shouldBe (HttpStatusCode.InternalServerError to "boom")

        val fromParameter =
          httpClient(httpClient = client, expectSuccess = true) { c ->
            with(KtorDsl) { c.get(URL) { it.status to it.bodyAsText() } }
          }
        fromParameter shouldBe (HttpStatusCode.InternalServerError to "boom")
      }
    }

    "get performs a GET request" {
      val mockEngine =
        MockEngine { request ->
          respond(content = "hello from mock", status = HttpStatusCode.OK)
        }
      val client = HttpClient(mockEngine)

      val result =
        withHttpClient(httpClient = client) {
          with(KtorDsl) {
            this@withHttpClient.get(URL) { response ->
              response.status shouldBe HttpStatusCode.OK
              response.bodyAsText()
            }
          }
        }
      result shouldBe "hello from mock"
      client.close()

      val request = mockEngine.requestHistory.single()
      request.method shouldBe HttpMethod.Get
      request.url.toString() shouldBe URL
    }

    "get applies the setUp block to the request" {
      val mockEngine = MockEngine { respondOk() }
      HttpClient(mockEngine).use { client ->
        val status =
          with(KtorDsl) {
            client.get(
              url = URL,
              setUp = {
                headers.append("X-Test-Header", "present")
                url.parameters.append("q", "kotlin")
              },
            ) { it.status }
          }
        status shouldBe HttpStatusCode.OK
      }

      val request = mockEngine.requestHistory.single()
      request.method shouldBe HttpMethod.Get
      request.headers["X-Test-Header"] shouldBe "present"
      request.url.toString() shouldBe "$URL?q=kotlin"
    }

    // The tests below let the helpers create their own client, so they need a default engine (see hasDefaultEngine).

    "newHttpClient installs HttpTimeout".config(enabled = hasDefaultEngine) {
      newHttpClient().use { client ->
        client.pluginOrNull(HttpTimeout).shouldNotBeNull()
      }
    }

    "withHttpClient closes a client it created".config(enabled = hasDefaultEngine) {
      lateinit var created: HttpClient
      withHttpClient {
        created = this
        "created"
      } shouldBe "created"
      // close() completes the client's job; join() would hang past the timeout if it were left open.
      withTimeout(5.seconds) { created.coroutineContext.job.join() }
    }

    "httpClient closes a client it created".config(enabled = hasDefaultEngine) {
      lateinit var created: HttpClient
      httpClient { client ->
        created = client
        "created"
      } shouldBe "created"
      withTimeout(5.seconds) { created.coroutineContext.job.join() }
    }
  }

  private companion object {
    const val URL = "http://localhost/test"
  }
}
