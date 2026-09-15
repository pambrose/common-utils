@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.features

import io.kotest.core.spec.style.StringSpec
import io.ktor.http.HttpHeaders
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

    "redirect keeps the request's host and query when no host is configured" {
      testApplication {
        install(HerokuHttpsRedirect)
        routing {
          get("/hello") {
            call.respondText("Hello")
          }
        }
        createClient { followRedirects = false }.get("/hello?x=1") {
          header(HttpHeaders.Host, "myapp.example.com")
          header("x-forwarded-proto", "http")
        }.apply {
          status shouldBe HttpStatusCode.MovedPermanently
          headers[HttpHeaders.Location] shouldBe "https://myapp.example.com/hello?x=1"
        }
      }
    }

    "redirect uses a configured host, and a temporary redirect when permanentRedirect is false" {
      testApplication {
        install(HerokuHttpsRedirect) {
          host = "secure.example.com"
          permanentRedirect = false
        }
        routing {
          get("/hello") {
            call.respondText("Hello")
          }
        }
        createClient { followRedirects = false }.get("/hello") {
          header("x-forwarded-proto", "http")
        }.apply {
          status shouldBe HttpStatusCode.Found
          headers[HttpHeaders.Location] shouldBe "https://secure.example.com/hello"
        }
      }
    }

    "no redirect when x-forwarded-proto is absent" {
      testApplication {
        install(HerokuHttpsRedirect)
        routing {
          get("/hello") {
            call.respondText("Hello")
          }
        }
        createClient { followRedirects = false }.get("/hello").status shouldBe HttpStatusCode.OK
      }
    }

    "a custom exclude predicate prevents the redirect" {
      testApplication {
        install(HerokuHttpsRedirect) {
          exclude { call -> call.request.headers["X-Internal"] == "true" }
        }
        routing {
          get("/hello") {
            call.respondText("Hello")
          }
        }
        createClient { followRedirects = false }.get("/hello") {
          header("x-forwarded-proto", "http")
          header("X-Internal", "true")
        }.status shouldBe HttpStatusCode.OK
      }
    }

    "excludeSuffix and excludePrefix match the path and ignore the query string" {
      testApplication {
        install(HerokuHttpsRedirect) {
          excludeSuffix(".txt")
          excludePrefix("/.well-known")
        }
        routing {
          get("/robots.txt") {
            call.respondText("User-agent: *")
          }
          get("/.well-known/security.txt") {
            call.respondText("Contact: me")
          }
        }
        val noRedirects = createClient { followRedirects = false }
        noRedirects.get("/robots.txt?v=2") { header("x-forwarded-proto", "http") }.status shouldBe HttpStatusCode.OK
        noRedirects.get("/.well-known/security.txt?v=2") { header("x-forwarded-proto", "http") }.status shouldBe
          HttpStatusCode.OK
      }
    }
  }
}
