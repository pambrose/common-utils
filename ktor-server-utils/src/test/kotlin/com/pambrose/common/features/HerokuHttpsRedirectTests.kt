@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.features

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication

class HerokuHttpsRedirectTests : StringSpec() {
  init {
    "no redirect when x-forwarded-proto is https" {
      testApplication {
        install(HerokuHttpsRedirect)
        routing {
          get("/hello") {
            call.respondText("Hello")
          }
        }
        client.get("/hello") {
          header("x-forwarded-proto", "https")
        }.apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "Hello"
        }
      }
    }

    "redirect when x-forwarded-proto is http" {
      testApplication {
        install(HerokuHttpsRedirect)
        routing {
          get("/hello") {
            call.respondText("Hello")
          }
        }
        val httpClient = createClient {
          followRedirects = false
        }
        httpClient.get("/hello") {
          header("x-forwarded-proto", "http")
        }.apply {
          status shouldBe HttpStatusCode.MovedPermanently
        }
      }
    }

    "excluded prefix paths are not redirected" {
      testApplication {
        install(HerokuHttpsRedirect) {
          excludePrefix("/health")
        }
        routing {
          get("/healthcheck") {
            call.respondText("OK")
          }
        }
        client.get("/healthcheck") {
          header("x-forwarded-proto", "http")
        }.apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "OK"
        }
      }
    }

    "excluded suffix paths are not redirected" {
      testApplication {
        install(HerokuHttpsRedirect) {
          excludeSuffix(".txt")
        }
        routing {
          get("/robots.txt") {
            call.respondText("User-agent: *")
          }
        }
        client.get("/robots.txt") {
          header("x-forwarded-proto", "http")
        }.apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "User-agent: *"
        }
      }
    }
  }
}
