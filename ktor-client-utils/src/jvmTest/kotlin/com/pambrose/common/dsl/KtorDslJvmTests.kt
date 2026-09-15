@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.pambrose.common.dsl.KtorDsl.httpClient
import com.pambrose.common.dsl.KtorDsl.newHttpClient
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout

class KtorDslJvmTests : StringSpec() {
  private inline fun <T> withLocalHttpServer(
    responseBody: String,
    block: (url: String) -> T,
  ): T = withLocalHttpServer(respond = { 200 to responseBody }, block = block)

  private inline fun <T> withLocalHttpServer(
    noinline respond: (HttpExchange) -> Pair<Int, String>,
    block: (url: String) -> T,
  ): T {
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      val (status, body) = respond(exchange)
      val bytes = body.encodeToByteArray()
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
      exchange.close()
    }
    server.start()
    return try {
      block("http://${server.address.hostString}:${server.address.port}/")
    } finally {
      server.stop(0)
    }
  }

  init {
    "newHttpClient creates a client that performs requests" {
      withLocalHttpServer("working") { url ->
        newHttpClient().use { client ->
          with(KtorDsl) { client.get(url) { it.bodyAsText() } } shouldBe "working"
        }
      }
    }

    "withHttpClient closes a client it created" {
      lateinit var created: HttpClient
      withHttpClient {
        created = this
        "created"
      } shouldBe "created"
      // close() completes the client's job; join() would hang past the timeout if it were left open.
      withTimeout(5.seconds) { created.coroutineContext.job.join() }
    }

    "httpClient creates client when null passed" {
      val result =
        httpClient { client ->
          client shouldNotBe null
          "created"
        }
      result shouldBe "created"
    }

    "blockingGet performs a GET request" {
      withLocalHttpServer("hello from server") { url ->
        val result =
          KtorDsl.blockingGet(url) { response ->
            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText()
          }
        result shouldBe "hello from server"
      }
    }

    "blockingGet applies the setUp block to the request" {
      // The server echoes the header back, so the test fails if setUp is not applied.
      withLocalHttpServer(respond = { 200 to (it.requestHeaders.getFirst("X-Test-Header") ?: "missing") }) { url ->
        val body = KtorDsl.blockingGet(url, setUp = { headers.append("X-Test-Header", "present") }) { it.bodyAsText() }
        body shouldBe "present"
      }
    }

    "blockingGet reuses a provided client and leaves it open" {
      withLocalHttpServer("reused") { url ->
        newHttpClient().use { client ->
          KtorDsl.blockingGet(url, httpClient = client) { it.bodyAsText() } shouldBe "reused"
          client.coroutineContext.job.isActive shouldBe true
        }
      }
    }

    "blockingGet with expectSuccess throws on a server error" {
      withLocalHttpServer(respond = { 500 to "boom" }) { url ->
        shouldThrow<ServerResponseException> {
          KtorDsl.blockingGet(url, expectSuccess = true) { it.bodyAsText() }
        }
      }
    }
  }
}
