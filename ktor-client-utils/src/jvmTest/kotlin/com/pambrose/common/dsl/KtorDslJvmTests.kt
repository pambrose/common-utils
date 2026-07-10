@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.pambrose.common.dsl.KtorDsl.httpClient
import com.pambrose.common.dsl.KtorDsl.newHttpClient
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.net.InetAddress
import java.net.InetSocketAddress

class KtorDslJvmTests : StringSpec() {
  private fun <T> withLocalHttpServer(
    responseBody: String,
    block: (url: String) -> T,
  ): T {
    val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    server.createContext("/") { exchange ->
      val bytes = responseBody.encodeToByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
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
      withLocalHttpServer("configured") { url ->
        val result =
          KtorDsl.blockingGet(
            url = url,
            setUp = { headers.append("X-Test-Header", "present") },
          ) { response ->
            response.bodyAsText()
          }
        result shouldBe "configured"
      }
    }
  }
}
