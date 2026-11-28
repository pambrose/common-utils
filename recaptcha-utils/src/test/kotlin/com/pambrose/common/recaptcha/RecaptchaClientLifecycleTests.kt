/*
 *   Copyright © 2026 Paul Ambrose (pambrose@mac.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.recaptcha

import ch.qos.logback.classic.Level
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.createHTML

private const val PACKAGE_PREFIX = "com.pambrose.common.recaptcha."

// How long an engine handler waits to be cancelled before it fails the request, so a regression fails the
// assertions instead of hanging the build.
private val CANCELLATION_BOUND = 10.seconds

// Suspends until the in-flight request is cancelled, failing it with a 504 if that never happens.
private suspend fun MockRequestHandleScope.awaitRequestCancellation(): HttpResponseData {
  delay(CANCELLATION_BOUND)
  return respondError(HttpStatusCode.GatewayTimeout)
}

// Defines its own copy of every class in the recaptcha package and delegates every other class to the parent, so
// loading RecaptchaService through it runs the object's static initializer again in an otherwise shared JVM.
private class FreshRecaptchaLoader(
  parent: ClassLoader,
) : ClassLoader(parent) {
  override fun loadClass(
    name: String,
    resolve: Boolean,
  ): Class<*> =
    if (!name.startsWith(PACKAGE_PREFIX)) {
      super.loadClass(name, resolve)
    } else {
      synchronized(getClassLoadingLock(name)) {
        findLoadedClass(name) ?: run {
          val resource = "${name.replace('.', '/')}.class"
          val bytes = parent.getResourceAsStream(resource).shouldNotBeNull().use { it.readBytes() }
          defineClass(name, bytes, 0, bytes.size)
        }
      }
    }
}

/**
 * Covers the lifetime of the service's [io.ktor.client.HttpClient]: it is built on first use rather than when the
 * object loads, and a [RecaptchaService.close] that lands as a verification starts is told apart from the caller's
 * own cancellation.
 */
class RecaptchaClientLifecycleTests : StringSpec() {
  init {
    classLoadingTests()
    lazyClientTests()
    concurrentCloseTests()
    callerCancellationTests()
  }

  private fun classLoadingTests() {
    // HttpClient(CIO) opens an NIO Selector as soon as it is built, so building it when the object loads cost every
    // application that touches RecaptchaService, reCAPTCHA enabled or not, a live engine until close().
    "loading RecaptchaService builds no HttpClient" {
      val loader = FreshRecaptchaLoader(RecaptchaService::class.java.classLoader)
      val service = Class.forName(RecaptchaService::class.java.name, true, loader)
      val client = service.getDeclaredField("httpClient").apply { isAccessible = true }.get(null)
      (client as? AutoCloseable)?.close()

      client.shouldBeNull()
    }
  }

  private fun lazyClientTests() {
    // Rendering, a disabled gate and close() never need Google, so none of them may build a client.
    "a service that verifies no token builds no client, even when closed" {
      withNoClient { clientsBuilt ->
        val configured = recaptchaConfig(enabled = true, siteKey = "site", secretKey = "secret")
        with(RecaptchaService) {
          createHTML().html {
            head { loadRecaptchaScript(configured) }
            body { recaptchaWidget(configured) }
          }
        }
        testApplication {
          routing {
            post("/v") {
              val disabled = recaptchaConfig(enabled = false, siteKey = null, secretKey = null)
              val ok = with(RecaptchaService) { validateRecaptcha(disabled, Parameters.Empty) }
              call.respondText("$ok")
            }
          }
          client.post("/v").bodyAsText() shouldBe "true"
        }
        RecaptchaService.close()

        clientsBuilt() shouldBe 0
        RecaptchaService.httpClient.shouldBeNull()
      }
    }

    // The first client is built quietly; the info message is kept for a rebuild after close().
    "the first verification builds the client without the after-close message" {
      val engine = successEngine()

      val (result, events) =
        capturingRecaptchaLogs { logs ->
          postToken(engine, beforePost = { withNoClient { } }) to logs()
        }

      result shouldBe (HttpStatusCode.OK to "passed")
      engine.requestHistory shouldHaveSize 1
      events.filter { "HttpClient" in it.formattedMessage }.shouldBeEmpty()
    }
  }

  // Closes and drops the service's client, so it is in the state of a service that has never verified a token,
  // and runs block with the number of (MockEngine) clients the factory has built since. The factory is restored
  // afterwards; the closed client is not.
  private inline fun withNoClient(block: (clientsBuilt: () -> Int) -> Unit) {
    val previousFactory = RecaptchaService.clientFactory
    val built = AtomicInt(0)
    RecaptchaService.close()
    RecaptchaService.httpClient = null
    RecaptchaService.clientFactory = {
      built.incrementAndFetch()
      RecaptchaService.verificationClient(successEngine())
    }
    try {
      block { built.load() }
    } finally {
      RecaptchaService.clientFactory = previousFactory
    }
  }

  private fun concurrentCloseTests() {
    // A second application in the same JVM (or Ktor auto-reload) can close the shared service between
    // verifyRecaptcha fetching the client and Ktor attaching the request to it. Ktor then fails the request with a
    // CancellationException although nobody cancelled the caller, so the caller must still get an answer.
    "a close racing a verification retries once with a new client instead of cancelling the caller" {
      val engine = successEngine()
      val factory = closingClientFactory(engine, closes = 1)

      val (result, events) = capturingRecaptchaLogs { logs -> postToken(engine, clientFactory = factory) to logs() }

      result shouldBe (HttpStatusCode.OK to "passed")
      engine.requestHistory shouldHaveSize 1
      events.single { "retrying with a new HttpClient" in it.formattedMessage }.level shouldBe Level.INFO
      events.single { "new reCAPTCHA HttpClient" in it.formattedMessage }.level shouldBe Level.INFO
    }

    // Retrying only once keeps a service that is closed again and again from looping; the check fails closed.
    "a close racing the retry too fails the verification closed" {
      val engine = successEngine()
      val factory = closingClientFactory(engine, closes = 2)

      val (result, events) = capturingRecaptchaLogs { logs -> postToken(engine, clientFactory = factory) to logs() }

      result shouldBe (HttpStatusCode.BadRequest to "reCAPTCHA verification failed")
      engine.requestHistory.shouldBeEmpty()
      events.single { "interrupted the reCAPTCHA verification retry" in it.formattedMessage }.level shouldBe
        Level.ERROR
    }
  }

  // Builds production-configured clients on engine whose requests, for the first `closes` of them, call
  // RecaptchaService.close() as they start: after verifyRecaptcha has fetched the client, before the engine runs.
  private fun closingClientFactory(
    engine: MockEngine,
    closes: Int,
  ): () -> HttpClient {
    val closesLeft = AtomicInt(closes)
    return {
      RecaptchaService.verificationClient(engine).apply {
        requestPipeline.intercept(HttpRequestPipeline.Before) {
          if (closesLeft.decrementAndFetch() >= 0) RecaptchaService.close()
        }
      }
    }
  }

  private fun callerCancellationTests() {
    // The caller's own cancellation (a disconnected client, or shutdown) must propagate as itself and is never
    // retried, even when the service's client happens to have been closed at the same moment.
    [false, true].forEach { closeToo ->
      "a cancelled caller propagates its cancellation without a retry${if (closeToo) " when also closed" else ""}" {
        val callerJob = AtomicReference<Job?>(null)
        val attempts = AtomicInt(0)
        val engine =
          MockEngine {
            attempts.incrementAndFetch()
            if (closeToo) RecaptchaService.close()
            callerJob.load().shouldNotBeNull().cancel()
            awaitRequestCancellation()
          }

        val (outcome, events) = capturingRecaptchaLogs { logs ->
          verifyInCancellableCaller(engine, callerJob) to logs()
        }

        outcome.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
        attempts.load() shouldBe 1
        events.filter { "HttpClient" in it.formattedMessage }.shouldBeEmpty()
      }
    }
  }

  // Runs validateRecaptcha in a child coroutine whose job is published in callerJob, so the engine can cancel the
  // caller itself, and returns what validateRecaptcha did in that coroutine.
  private fun verifyInCancellableCaller(
    engine: MockEngine,
    callerJob: AtomicReference<Job?>,
  ): Result<Boolean> {
    val seen = AtomicReference<Result<Boolean>?>(null)
    postToken(engine) { config, params ->
      val route = this
      coroutineScope {
        async {
          callerJob.store(currentCoroutineContext().job)
          runCatching { with(RecaptchaService) { route.validateRecaptcha(config, params) } }
            .also { seen.store(it) }
            .getOrThrow()
        }.await()
      }
    }
    return seen.load().shouldNotBeNull()
  }
}
