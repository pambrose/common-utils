@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.features

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

class HerokuHttpsRedirectTests : StringSpec() {
  // Installs the plugin and a route answering "Hello" on every path, and returns a client that does not
  // follow redirects, so each test sees the plugin's response directly.
  private fun ApplicationTestBuilder.helloApp(
    configure: HerokuHttpsRedirect.Configuration.() -> Unit = {},
  ): HttpClient {
    install(HerokuHttpsRedirect, configure)
    routing {
      get("{...}") {
        call.respondText("Hello")
      }
    }
    return createClient { followRedirects = false }
  }

  init {
    "no redirect when x-forwarded-proto is https" {
      testApplication {
        helloApp().get("/hello") {
          header(HttpHeaders.XForwardedProto, "https")
        }.apply {
          status shouldBe HttpStatusCode.OK
          bodyAsText() shouldBe "Hello"
        }
      }
    }

    "no redirect when x-forwarded-proto is absent" {
      testApplication {
        helloApp().get("/hello").status shouldBe HttpStatusCode.OK
      }
    }

    "redirect keeps the request's host and query when no host is configured" {
      testApplication {
        helloApp().get("/hello?x=1") {
          header(HttpHeaders.Host, "myapp.example.com")
          header(HttpHeaders.XForwardedProto, "http")
        }.apply {
          status shouldBe HttpStatusCode.MovedPermanently
          headers[HttpHeaders.Location] shouldBe "https://myapp.example.com/hello?x=1"
        }
      }
    }

    "redirect uses a configured host, and a temporary redirect when permanentRedirect is false" {
      testApplication {
        val client =
          helloApp {
            host = "secure.example.com"
            permanentRedirect = false
          }
        client.get("/hello") {
          header(HttpHeaders.XForwardedProto, "http")
        }.apply {
          status shouldBe HttpStatusCode.Found
          headers[HttpHeaders.Location] shouldBe "https://secure.example.com/hello"
        }
      }
    }

    "excluded prefix paths are not redirected, whatever the query string" {
      testApplication {
        helloApp { excludePrefix("/health") }.get("/healthcheck?v=2") {
          header(HttpHeaders.XForwardedProto, "http")
        }.status shouldBe HttpStatusCode.OK
      }
    }

    "excluded suffix paths are not redirected, whatever the query string" {
      testApplication {
        helloApp { excludeSuffix(".txt") }.get("/robots.txt?v=2") {
          header(HttpHeaders.XForwardedProto, "http")
        }.status shouldBe HttpStatusCode.OK
      }
    }

    "a custom exclude predicate prevents the redirect" {
      testApplication {
        helloApp { exclude { call -> call.request.headers["X-Internal"] == "true" } }.get("/hello") {
          header(HttpHeaders.XForwardedProto, "http")
          header("X-Internal", "true")
        }.status shouldBe HttpStatusCode.OK
      }
    }
  }
}
