@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.features

import com.pambrose.common.rawHttpExchange
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

private const val PINNED_HOST = "myapp.herokuapp.com"

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

  // Sends `GET [target]` with `X-Forwarded-Proto: http` to a real CIO server over a raw socket and returns the raw
  // response. Ktor's client normalizes the request target, so it cannot send the non-origin-form targets (or the
  // malformed Host headers) these tests need.
  private suspend fun rawRedirect(
    target: String,
    hostHeader: String = "myapp.example.com",
    configure: HerokuHttpsRedirect.Configuration.() -> Unit = {},
  ): String =
    rawHttpExchange(
      "GET $target HTTP/1.1\r\nHost: $hostHeader\r\nX-Forwarded-Proto: http\r\nConnection: close\r\n\r\n",
    ) {
      install(HerokuHttpsRedirect, configure)
      routing { get("{...}") { call.respondText("Hello") } }
    }

  private val String.statusCode: Int
    get() = lines().first().split(' ')[1].toInt()

  private val String.location: String?
    get() = lines().firstOrNull { it.startsWith("Location:", ignoreCase = true) }?.substringAfter(":")?.trim()

  init {
    forwardedProtoTests()
    configurationTests()
    excludeTests()
    requestTargetTests()
    absoluteFormTests()
    absoluteFormExcludeTests()
    requestHostTests()
    configuredHostTests()
  }

  private fun forwardedProtoTests() {
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

    // Rebuilding the URL from the parsed parameters reordered and lower-cased them, which breaks signed URLs. Ktor's
    // client normalizes a query itself, so the request is written to a raw socket.
    "redirect keeps the raw query string: order, case and encoding" {
      rawRedirect("/hello?b=2&a=1&B=3&flag&sig=a%2Fb").location shouldBe
        "https://myapp.example.com/hello?b=2&a=1&B=3&flag&sig=a%2Fb"
    }

    "redirect to a configured host keeps the raw query string" {
      rawRedirect("/hello?b=2&a=1&B=3&flag&sig=a%2Fb") { host = PINNED_HOST }.apply {
        statusCode shouldBe HttpStatusCode.MovedPermanently.value
        location shouldBe "https://$PINNED_HOST/hello?b=2&a=1&B=3&flag&sig=a%2Fb"
      }
    }
  }

  private fun configurationTests() {
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

    "redirect goes to a configured sslPort" {
      testApplication {
        helloApp { sslPort = 8443 }.get("/hello") {
          header(HttpHeaders.Host, "myapp.example.com")
          header(HttpHeaders.XForwardedProto, "http")
        }.apply {
          status shouldBe HttpStatusCode.MovedPermanently
          headers[HttpHeaders.Location] shouldBe "https://myapp.example.com:8443/hello"
        }
      }
    }
  }

  private fun excludeTests() {
    "a path matching none of the configured excludes is still redirected" {
      testApplication {
        val client =
          helloApp {
            excludePrefix("/health")
            excludeSuffix(".txt")
            exclude { false }
          }
        client.get("/hello.html") {
          header(HttpHeaders.Host, "myapp.example.com")
          header(HttpHeaders.XForwardedProto, "http")
        }.apply {
          status shouldBe HttpStatusCode.MovedPermanently
          headers[HttpHeaders.Location] shouldBe "https://myapp.example.com/hello.html"
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

  // CIO hands any non-space request target to the call verbatim. Spliced onto the origin, these turned a pinned
  // host into userinfo (https://myapp.herokuapp.com@evil.com/phish) or a subdomain of the attacker's
  // (https://myapp.herokuapp.com.evil.com/phish).
  private fun requestTargetTests() {
    "a target starting with @ gets 400, not a redirect to another host" {
      rawRedirect("@evil.com/phish") { host = PINNED_HOST }.apply {
        statusCode shouldBe HttpStatusCode.BadRequest.value
        location shouldBe null
      }
    }

    "a target starting with a dot gets 400, not a redirect to another host" {
      rawRedirect(".evil.com/phish") { host = PINNED_HOST }.apply {
        statusCode shouldBe HttpStatusCode.BadRequest.value
        location shouldBe null
      }
    }

    "other targets that are neither origin-form nor absolute-form http(s) get 400" {
      listOf("evil.com/phish", "*", "$PINNED_HOST:443", "ftp://evil.com/phish", "http:evil.com", "\\evil.com")
        .forEach { target ->
          withClue(target) {
            rawRedirect(target) { host = PINNED_HOST }.apply {
              statusCode shouldBe HttpStatusCode.BadRequest.value
              location shouldBe null
            }
          }
        }
    }

    "a path starting with two slashes stays a path on the redirect host" {
      rawRedirect("//evil.com/phish") { host = PINNED_HOST }.location shouldBe "https://$PINNED_HOST//evil.com/phish"
    }
  }

  // RFC 9112 section 3.2.2: a server must accept absolute-form targets. Only their path and query are kept, so the
  // authority in the target cannot steer the redirect.
  private fun absoluteFormTests() {
    "an absolute-form target redirects to its path and raw query on the configured host" {
      rawRedirect("http://$PINNED_HOST/hello?b=2&a=1") { host = PINNED_HOST }.apply {
        statusCode shouldBe HttpStatusCode.MovedPermanently.value
        location shouldBe "https://$PINNED_HOST/hello?b=2&a=1"
      }
    }

    "an absolute-form target redirects to the request's host when no host is configured" {
      rawRedirect("http://myapp.example.com/hello?b=2&a=1&sig=a%2Fb").location shouldBe
        "https://myapp.example.com/hello?b=2&a=1&sig=a%2Fb"
    }

    "the authority of an absolute-form target is dropped" {
      mapOf(
        "http://evil.com/phish" to "/phish",
        "HTTPS://evil.com/hello?b=2&a=1" to "/hello?b=2&a=1",
        "http://user@evil.com/phish" to "/phish",
        "http://evil.com:8080//other.com/x" to "//other.com/x",
        "http://evil.com" to "/",
        "http://evil.com?b=2&a=1" to "/?b=2&a=1",
      ).forEach { (target, pathAndQuery) ->
        withClue(target) {
          rawRedirect(target) { host = PINNED_HOST }.location shouldBe "https://$PINNED_HOST$pathAndQuery"
        }
      }
    }
  }

  // CIO leaves an absolute-form target's scheme and authority in call.request.path(), so excludePrefix("/health") did
  // not match GET http://myapp.example.com/health, which was redirected. The exclusions now match the path the
  // redirect keeps, so an absolute-form target is excluded exactly when its origin-form equivalent is.
  private fun absoluteFormExcludeTests() {
    val excludeHealthAndText: HerokuHttpsRedirect.Configuration.() -> Unit = {
      host = PINNED_HOST
      excludePrefix("/health")
      excludeSuffix(".txt")
    }

    "an absolute-form target whose path matches excludePrefix or excludeSuffix is not redirected" {
      listOf(
        "http://myapp.example.com/health",
        "HTTPS://evil.com/healthcheck?v=2",
        "http://user@myapp.example.com:8080/robots.txt?v=2",
        "http://myapp.example.com/a/notes.txt",
      ).forEach { target ->
        withClue(target) {
          rawRedirect(target, configure = excludeHealthAndText).apply {
            statusCode shouldBe HttpStatusCode.OK.value
            location shouldBe null
          }
        }
      }
    }

    "an absolute-form target is redirected when only its scheme, authority or query matches an exclusion" {
      mapOf(
        "http://myapp.example.com/hello?next=/health" to "/hello?next=/health",
        "http://myapp.example.com/hello?file=a.txt" to "/hello?file=a.txt",
        "http://health.txt" to "/",
        "http://myapp.example.com/x?a.txt" to "/x?a.txt",
      ).forEach { (target, pathAndQuery) ->
        withClue(target) {
          rawRedirect(target, configure = excludeHealthAndText).location shouldBe "https://$PINNED_HOST$pathAndQuery"
        }
      }
      rawRedirect("http://myapp.example.com/hello") {
        host = PINNED_HOST
        excludePrefix("http")
        excludeSuffix(".com")
      }.location shouldBe "https://$PINNED_HOST/hello"
    }

    "the exclusions match an origin-form target's path as before" {
      rawRedirect("/healthcheck?v=2", configure = excludeHealthAndText).statusCode shouldBe HttpStatusCode.OK.value
      rawRedirect("/hello?next=/health", configure = excludeHealthAndText).location shouldBe
        "https://$PINNED_HOST/hello?next=/health"
    }

    // Such a target has no origin-form equivalent, and gets 400 unless an exclusion matches its raw path.
    "a target that is neither origin-form nor absolute-form is matched on its raw path, as before" {
      rawRedirect("@evil.com/robots.txt?v=2", configure = excludeHealthAndText).apply {
        statusCode shouldBe HttpStatusCode.OK.value
        location shouldBe null
      }
    }
  }

  // With no configured host, the redirect host comes from the client's Host header.
  private fun requestHostTests() {
    // CIO passes these to the call unchecked: "evil com" redirected to "https://evil com/hello", and an empty Host
    // header to "https://localhost/hello".
    "a Host header that is not a host name or IP literal gets 400 when no host is configured" {
      listOf("evil com", "evil.com\\x", ".evil.com", "").forEach { hostHeader ->
        withClue("Host: <$hostHeader>") {
          rawRedirect("/hello", hostHeader = hostHeader).apply {
            statusCode shouldBe HttpStatusCode.BadRequest.value
            location shouldBe null
          }
        }
      }
    }

    // CIO itself answers 400 to a Host header holding '@', '/', '?' or '#', but other engines (and X-Forwarded-Host,
    // through Ktor's forwarded-header plugins) need not, so the plugin checks them too. The test engine passes the
    // header through as sent.
    "a Host header with userinfo, a path, a query or a fragment gets 400 on an engine that accepts it" {
      testApplication {
        val client = helloApp()
        listOf("myapp.example.com@evil.com", "evil.com/phish", "evil.com?x=1", "evil.com#x").forEach { hostHeader ->
          withClue("Host: <$hostHeader>") {
            client.get("/hello") {
              header(HttpHeaders.Host, hostHeader)
              header(HttpHeaders.XForwardedProto, "http")
            }.apply {
              status shouldBe HttpStatusCode.BadRequest
              headers[HttpHeaders.Location] shouldBe null
            }
          }
        }
      }
    }

    "a Host header with an IP literal and a port is redirected to that address" {
      rawRedirect("/hello?b=2&a=1", hostHeader = "[::1]:8080").location shouldBe "https://[::1]/hello?b=2&a=1"
      rawRedirect("/hello", hostHeader = "127.0.0.1:8080").location shouldBe "https://127.0.0.1/hello"
    }
  }

  private fun configuredHostTests() {
    "a configured host that is not a host name or IP literal fails when the plugin is created" {
      listOf(
        "evil.com/phish",
        "myapp.example.com@evil.com",
        "myapp.example.com@evil.com:8443",
        "exa mple.com",
        "evil.com?",
        "evil.com#",
        ".evil.com",
        "::1",
        "[::1",
        "bücher.example",
        "",
        // A port is accepted only as 1 to 65535 digits after a valid host
        "myapp.example.com:0",
        "myapp.example.com:65536",
        "myapp.example.com:123456",
        "myapp.example.com:https",
        "myapp.example.com:",
        "myapp.example.com:-1",
        "[2001:db8::1]:0",
        ":8443",
      ).forEach { configured ->
        withClue("host = <$configured>") {
          shouldThrow<IllegalArgumentException> {
            HerokuHttpsRedirect(HerokuHttpsRedirect.Configuration().apply { host = configured })
          }
        }
      }
    }

    "host names, IPv4 addresses and bracketed IPv6 literals are accepted as the configured host" {
      listOf("myapp.herokuapp.com", "localhost", "my_app-1.example.com.", "127.0.0.1", "[2001:db8::1]")
        .forEach { configured ->
          HerokuHttpsRedirect(HerokuHttpsRedirect.Configuration().apply { host = configured }).host shouldBe configured
        }
      rawRedirect("/hello?b=2&a=1") { host = "[2001:db8::1]" }.location shouldBe "https://[2001:db8::1]/hello?b=2&a=1"
    }

    // sslPort is the way to set the port, but a configured "host:port" redirected to https://host:port in 5.3.0, so
    // rejecting it would stop an app that relies on it from starting.
    "a configured host may still carry a port from 1 to 65535" {
      listOf("myapp.com:8443", "127.0.0.1:8443", "[2001:db8::1]:8443", "myapp.com:1", "myapp.com:65535")
        .forEach { configured ->
          HerokuHttpsRedirect(HerokuHttpsRedirect.Configuration().apply { host = configured }).host shouldBe configured
        }
    }

    "a configured host with a port redirects to that port, as it did in 5.3.0" {
      rawRedirect("/hello?b=2&a=1") { host = "myapp.com:8443" }.apply {
        statusCode shouldBe HttpStatusCode.MovedPermanently.value
        location shouldBe "https://myapp.com:8443/hello?b=2&a=1"
      }
      rawRedirect("/hello") { host = "[2001:db8::1]:8443" }.location shouldBe "https://[2001:db8::1]:8443/hello"
    }
  }
}
