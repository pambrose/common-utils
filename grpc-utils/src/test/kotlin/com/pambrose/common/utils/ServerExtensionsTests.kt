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

package com.pambrose.common.utils

import com.google.common.util.concurrent.ListenableFuture
import com.pambrose.common.dsl.stringMarshaller
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Runs block with Runtime.getRuntime() stubbed, so nothing is really registered with the JVM.
private inline fun <T> withStubbedRuntime(block: (Runtime) -> T): T {
  val runtime = mockk<Runtime>(relaxed = true)
  mockkStatic(Runtime::class)
  return try {
    every { Runtime.getRuntime() } returns runtime
    block(runtime)
  } finally {
    unmockkStatic(Runtime::class)
  }
}

// Captures the shutdown hook that shutdownWithJvm registers, or null when none was registered.
private fun registerHook(
  server: Server,
  maxWaitTime: Duration,
): Thread? {
  val hookSlot = slot<Thread>()
  return withStubbedRuntime { runtime ->
    every { runtime.addShutdownHook(capture(hookSlot)) } just Runs
    server.shutdownWithJvm(maxWaitTime)
    if (hookSlot.isCaptured) hookSlot.captured else null
  }
}

private val holdMethod: MethodDescriptor<String, String> =
  MethodDescriptor.newBuilder<String, String>()
    .setType(MethodDescriptor.MethodType.UNARY)
    .setFullMethodName(MethodDescriptor.generateFullMethodName("HoldService", "Hold"))
    .setRequestMarshaller(stringMarshaller)
    .setResponseMarshaller(stringMarshaller)
    .build()

// An in-process server whose single method leaves each call open, handing its response observer to the test.
private class HoldingServer : AutoCloseable {
  val heldCalls = LinkedBlockingQueue<StreamObserver<String>>()
  private val name = InProcessServerBuilder.generateName()

  val server: Server =
    InProcessServerBuilder
      .forName(name)
      .addService(
        ServerServiceDefinition.builder("HoldService")
          .addMethod(holdMethod, ServerCalls.asyncUnaryCall { _, responseObserver -> heldCalls.put(responseObserver) })
          .build(),
      ).build()
      .start()

  private val channel: ManagedChannel = InProcessChannelBuilder.forName(name).build()

  // Starts a call and returns once the server holds it.
  fun startHeldCall(): Pair<ListenableFuture<String>, StreamObserver<String>> {
    val response = ClientCalls.futureUnaryCall(channel.newCall(holdMethod, CallOptions.DEFAULT), "request")
    return response to heldCalls.poll(5, TimeUnit.SECONDS).shouldNotBeNull()
  }

  override fun close() {
    channel.shutdownNow()
    server.shutdownNow()
  }
}

class ServerExtensionsTests : StringSpec() {
  init {
    "shutdownGracefully lets an in-flight call finish before the server terminates" {
      HoldingServer().use { holding ->
        val server = holding.server
        val (response, responseObserver) = holding.startHeldCall()

        val shutdown = CompletableFuture.runAsync { server.shutdownGracefully(30.seconds) }
        eventually(5.seconds) { server.isShutdown shouldBe true }
        // The held call keeps the server from terminating, and it can still be answered.
        server.isTerminated shouldBe false
        responseObserver.onNext("finished")
        responseObserver.onCompleted()

        response.get(5, TimeUnit.SECONDS) shouldBe "finished"
        shutdown.get(10, TimeUnit.SECONDS)
        server.isTerminated shouldBe true
      }
    }

    "shutdownGracefully forces down a server whose in-flight call outlasts the wait" {
      HoldingServer().use { holding ->
        val server = holding.server
        val (response, _) = holding.startHeldCall()

        server.shutdownGracefully(100.milliseconds)

        server.awaitTermination(5, TimeUnit.SECONDS) shouldBe true
        val failure = shouldThrow<ExecutionException> { response.get(5, TimeUnit.SECONDS) }
        val cutOff: List<Status.Code> = [Status.Code.CANCELLED, Status.Code.UNAVAILABLE]
        Status.fromThrowable(failure.cause).code shouldBeIn cutOff
      }
    }

    "shutdownGracefully invokes shutdown, awaitTermination, and shutdownNow in order" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } returns true

      server.shutdownGracefully(500L, TimeUnit.MILLISECONDS)

      verifyOrder {
        server.shutdown()
        server.awaitTermination(500L, TimeUnit.MILLISECONDS)
        server.shutdownNow()
      }
    }

    "shutdownGracefully always calls shutdownNow even when awaitTermination throws" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } throws InterruptedException("boom")

      shouldThrow<InterruptedException> {
        server.shutdownGracefully(100L, TimeUnit.MILLISECONDS)
      }

      verify(exactly = 1) { server.shutdown() }
      verify(exactly = 1) { server.shutdownNow() }
    }

    // shutdown() sits inside the try, so an already-terminated server is still forced down rather than
    // skipping the finally.
    "shutdownGracefully calls shutdownNow even when shutdown itself throws" {
      val server = mockk<Server>(relaxed = true)
      every { server.shutdown() } throws IllegalStateException("already terminated")

      shouldThrow<IllegalStateException> {
        server.shutdownGracefully(100L, TimeUnit.MILLISECONDS)
      }

      verify(exactly = 1) { server.shutdownNow() }
    }

    "shutdownGracefully(Duration) converts to milliseconds" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } returns true

      server.shutdownGracefully(2.seconds)

      verify(exactly = 1) { server.awaitTermination(2_000L, TimeUnit.MILLISECONDS) }
    }

    "shutdownGracefully rejects non-positive timeout" {
      val server = mockk<Server>(relaxed = true)
      shouldThrow<IllegalArgumentException> {
        server.shutdownGracefully(0L, TimeUnit.MILLISECONDS)
      }
      shouldThrow<IllegalArgumentException> {
        server.shutdownGracefully(-1L, TimeUnit.MILLISECONDS)
      }
    }

    "shutdownGracefully(Duration) of zero throws IllegalArgumentException" {
      val server = mockk<Server>(relaxed = true)
      shouldThrow<IllegalArgumentException> {
        server.shutdownGracefully(0.milliseconds)
      }
    }

    "shutdownWithJvm registers a shutdown hook that shuts down the server" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } returns true

      requireNotNull(registerHook(server, 2.seconds)).run()

      verifyOrder {
        server.shutdown()
        server.awaitTermination(2_000L, TimeUnit.MILLISECONDS)
        server.shutdownNow()
      }
    }

    "shutdownWithJvm hook swallows InterruptedException from awaitTermination" {
      val server = mockk<Server>(relaxed = true)
      every { server.awaitTermination(any(), any()) } throws InterruptedException("interrupted")

      val hook = requireNotNull(registerHook(server, 1.seconds))

      shouldNotThrowAny {
        hook.run()
      }
      verify(exactly = 1) { server.shutdown() }
      verify(exactly = 1) { server.shutdownNow() }
    }

    // The hook is the last chance to stop the server, so an unexpected failure must not skip shutdownNow().
    "shutdownWithJvm hook still forces shutdown when the graceful path throws" {
      val server = mockk<Server>(relaxed = true)
      every { server.shutdown() } throws IllegalStateException("already terminated")

      val hook = requireNotNull(registerHook(server, 1.seconds))

      shouldNotThrowAny {
        hook.run()
      }
      verify(exactly = 1) { server.shutdownNow() }
    }

    // A bad timeout used to surface only at JVM exit, inside the hook, where it stopped the shutdown from
    // running at all. Sub-millisecond durations count as bad: they convert to a 0 ms timeout.
    "shutdownWithJvm rejects a timeout below a millisecond when the hook is registered" {
      val server = mockk<Server>(relaxed = true)

      withStubbedRuntime { runtime ->
        shouldThrow<IllegalArgumentException> { server.shutdownWithJvm(Duration.ZERO) }
        shouldThrow<IllegalArgumentException> { server.shutdownWithJvm(500.microseconds) }

        verify(exactly = 0) { runtime.addShutdownHook(any()) }
      }
    }
  }
}
