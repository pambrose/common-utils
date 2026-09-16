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

package com.pambrose.common.dsl

import com.pambrose.common.utils.TlsContext
import com.pambrose.common.utils.TlsContext.Companion.PLAINTEXT_CONTEXT
import com.pambrose.common.utils.TlsUtils
import com.pambrose.common.utils.tlsResourcePath
import io.grpc.Attributes
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.ClientCalls
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.reflect.KClass

// A built ManagedChannel exposes no way to read its retry configuration back, so the transport's builder is
// stubbed in and the calls are verified on it. stubFactory runs once the statics are mocked.
private fun <T : ManagedChannelBuilder<*>> withStubbedBuilder(
  transport: KClass<*>,
  builder: T,
  stubFactory: () -> Unit,
  block: (T) -> Unit,
) {
  every { builder.build() } returns mockk<ManagedChannel>(relaxed = true)
  mockkStatic(transport)
  try {
    stubFactory()
    block(builder)
  } finally {
    unmockkStatic(transport)
  }
}

private fun withStubbedNettyChannelBuilder(block: (NettyChannelBuilder) -> Unit) =
  mockk<NettyChannelBuilder>(relaxed = true).let { builder ->
    withStubbedBuilder(
      NettyChannelBuilder::class,
      builder,
      { every { NettyChannelBuilder.forAddress(any<String>(), any<Int>()) } returns builder },
      block,
    )
  }

// The server counterpart of withStubbedBuilder: GrpcDsl.server gets a mock Netty builder to configure.
private fun withStubbedNettyServerBuilder(block: (NettyServerBuilder) -> Unit) {
  val builder = mockk<NettyServerBuilder>(relaxed = true)
  every { builder.build() } returns mockk<Server>(relaxed = true)
  mockkStatic(NettyServerBuilder::class)
  try {
    every { NettyServerBuilder.forPort(any()) } returns builder
    block(builder)
  } finally {
    unmockkStatic(NettyServerBuilder::class)
  }
}

private val clientTlsContext by lazy {
  TlsUtils.buildClientTlsContext(trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"))
}

private val serverTlsContext by lazy {
  TlsUtils.buildServerTlsContext(
    certChainFilePath = tlsResourcePath("server-cert.pem"),
    privateKeyFilePath = tlsResourcePath("server-key.pem"),
  )
}

class GrpcDslTests : StringSpec() {
  init {
    "attributes builds Attributes from the DSL block" {
      val key = Attributes.Key.create<String>("test-key")
      val attributes =
        GrpcDsl.attributes {
          set(key, "test-value")
        }
      attributes.get(key) shouldBe "test-value"
      @Suppress("DEPRECATION")
      attributes.keys() shouldBe setOf(key)
    }

    "server and channel round-trip an RPC over the in-process transport" {
      val serverName = InProcessServerBuilder.generateName()
      var serverBlockCalled = false
      var channelBlockCalled = false

      val server =
        GrpcDsl.server(inProcessServerName = serverName) {
          serverBlockCalled = true
          addService(echoService())
          directExecutor()
        }

      server.start()

      // No tlsContext argument: an in-process channel needs none, and the default matches server().
      val channel =
        GrpcDsl.channel(inProcessServerName = serverName) {
          channelBlockCalled = true
          directExecutor()
        }

      try {
        val response = ClientCalls.blockingUnaryCall(channel, echoMethod, CallOptions.DEFAULT, "hello")
        response shouldBe "echo: hello"
        serverBlockCalled shouldBe true
        channelBlockCalled shouldBe true
        server.services.map { it.serviceDescriptor.name } shouldBe ["EchoService"]
      } finally {
        channel.shutdownNow()
        server.shutdownNow()
      }
    }

    "channel builds a plaintext Netty channel for host and port" {
      val channel =
        GrpcDsl.channel(
          hostName = "localhost",
          port = 15551,
          maxRetryAttempts = -1,
          tlsContext = PLAINTEXT_CONTEXT,
        ) {}
      try {
        channel.authority() shouldBe "localhost:15551"
      } finally {
        channel.shutdownNow()
      }
    }

    "channel applies TLS context, overrideAuthority, and retry settings" {
      val tlsContext =
        TlsUtils.buildClientTlsContext(trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"))
      var blockCalled = false
      val channel =
        GrpcDsl.channel(
          hostName = "localhost",
          port = 15552,
          enableRetry = true,
          maxRetryAttempts = 3,
          tlsContext = tlsContext,
          overrideAuthority = "override.example.com",
        ) {
          blockCalled = true
        }
      try {
        channel.authority() shouldBe "override.example.com"
        blockCalled shouldBe true
      } finally {
        channel.shutdownNow()
      }
    }

    // grpc-java turns retry on by default, so only an explicit disableRetry() switches it off.
    "enableRetry = false disables retry rather than leaving grpc's default on" {
      val builder = mockk<NettyChannelBuilder>(relaxed = true)
      withStubbedBuilder(
        NettyChannelBuilder::class,
        builder,
        { every { NettyChannelBuilder.forAddress(any<String>(), any<Int>()) } returns builder },
      ) {
        GrpcDsl.channel(
          hostName = "localhost",
          port = 15553,
          enableRetry = false,
          tlsContext = PLAINTEXT_CONTEXT,
        ) {}

        verify(exactly = 1) { builder.disableRetry() }
        verify(exactly = 0) { builder.enableRetry() }
      }
    }

    "enableRetry = true enables retry" {
      val builder = mockk<NettyChannelBuilder>(relaxed = true)
      withStubbedBuilder(
        NettyChannelBuilder::class,
        builder,
        { every { NettyChannelBuilder.forAddress(any<String>(), any<Int>()) } returns builder },
      ) {
        GrpcDsl.channel(
          hostName = "localhost",
          port = 15554,
          enableRetry = true,
          maxRetryAttempts = 4,
          tlsContext = PLAINTEXT_CONTEXT,
        ) {}

        verify(exactly = 1) { builder.enableRetry() }
        verify(exactly = 1) { builder.maxRetryAttempts(4) }
        verify(exactly = 0) { builder.disableRetry() }
      }
    }

    "the in-process transport applies the retry and authority options too" {
      val builder = mockk<InProcessChannelBuilder>(relaxed = true)
      withStubbedBuilder(
        InProcessChannelBuilder::class,
        builder,
        { every { InProcessChannelBuilder.forName(any<String>()) } returns builder },
      ) {
        GrpcDsl.channel(
          enableRetry = true,
          maxRetryAttempts = 2,
          overrideAuthority = "override.example.com",
          inProcessServerName = "ignored",
        ) {}

        verify(exactly = 1) { builder.enableRetry() }
        verify(exactly = 1) { builder.maxRetryAttempts(2) }
        verify(exactly = 1) { builder.overrideAuthority("override.example.com") }
      }
    }

    "a TLS context gives the Netty channel that context instead of plaintext" {
      withStubbedNettyChannelBuilder { builder ->
        GrpcDsl.channel(hostName = "localhost", port = 15555, tlsContext = clientTlsContext) {}

        verify(exactly = 1) { builder.sslContext(clientTlsContext.sslContext) }
        verify(exactly = 0) { builder.usePlaintext() }
      }
    }

    "the plaintext context makes the Netty channel use plaintext" {
      withStubbedNettyChannelBuilder { builder ->
        GrpcDsl.channel(hostName = "localhost", port = 15556, tlsContext = PLAINTEXT_CONTEXT) {}

        verify(exactly = 1) { builder.usePlaintext() }
        verify(exactly = 0) { builder.sslContext(any()) }
      }
    }

    "a TLS context is set on the Netty server, and the plaintext context sets none" {
      withStubbedNettyServerBuilder { builder ->
        GrpcDsl.server(port = 0, tlsContext = serverTlsContext) {}
        verify(exactly = 1) { builder.sslContext(serverTlsContext.sslContext) }
      }

      withStubbedNettyServerBuilder { builder ->
        GrpcDsl.server(port = 0, tlsContext = PLAINTEXT_CONTEXT) {}
        verify(exactly = 0) { builder.sslContext(any()) }
      }
    }

    "maxRetryAttempts = -1 keeps grpc's own limit, while 0 is passed through" {
      withStubbedNettyChannelBuilder { builder ->
        GrpcDsl.channel(hostName = "localhost", port = 15557, enableRetry = true, maxRetryAttempts = -1) {}
        verify(exactly = 0) { builder.maxRetryAttempts(any()) }
      }

      withStubbedNettyChannelBuilder { builder ->
        GrpcDsl.channel(hostName = "localhost", port = 15558, enableRetry = true, maxRetryAttempts = 0) {}
        verify(exactly = 1) { builder.maxRetryAttempts(0) }
      }
    }

    "a blank overrideAuthority is ignored, and a padded one is trimmed" {
      withStubbedNettyChannelBuilder { builder ->
        GrpcDsl.channel(hostName = "localhost", port = 15559, overrideAuthority = "   ") {}
        verify(exactly = 0) { builder.overrideAuthority(any()) }
      }

      withStubbedNettyChannelBuilder { builder ->
        GrpcDsl.channel(hostName = "localhost", port = 15560, overrideAuthority = "  override.example.com ") {}
        verify(exactly = 1) { builder.overrideAuthority("override.example.com") }
      }
    }

    "server builds a plaintext Netty server without starting it" {
      var blockCalled = false
      val server =
        GrpcDsl.server(port = 0) {
          blockCalled = true
          addService(echoService())
        }
      blockCalled shouldBe true
      server.services.map { it.serviceDescriptor.name } shouldBe ["EchoService"]
      server.isShutdown shouldBe false
    }

    "server builds a Netty server with a TLS context" {
      val tlsContext =
        TlsUtils.buildServerTlsContext(
          certChainFilePath = tlsResourcePath("server-cert.pem"),
          privateKeyFilePath = tlsResourcePath("server-key.pem"),
        )
      val server = GrpcDsl.server(port = 0, tlsContext = tlsContext) {}
      server.services.shouldBeEmpty()
      server.isShutdown shouldBe false
    }

    // NettyServerBuilder.sslContext() rejects a context without ALPN, so a builder handed to callers for
    // customization has to carry gRPC's ALPN configuration already.
    "a server context built by hand from serverTlsContext is accepted by server()" {
      val sslContext =
        TlsUtils.serverTlsContext(
          certChainFilePath = tlsResourcePath("server-cert.pem"),
          privateKeyFilePath = tlsResourcePath("server-key.pem"),
        ).builder.build()

      sslContext.applicationProtocolNegotiator().protocols() shouldContain "h2"

      val server = GrpcDsl.server(port = 0, tlsContext = TlsContext(sslContext, false)) {}
      server.isShutdown shouldBe false
    }
  }
}
