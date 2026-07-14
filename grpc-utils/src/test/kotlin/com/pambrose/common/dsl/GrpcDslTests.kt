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

import com.pambrose.common.utils.TlsContext.Companion.PLAINTEXT_CONTEXT
import com.pambrose.common.utils.TlsUtils
import io.grpc.Attributes
import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

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

      val channel =
        GrpcDsl.channel(inProcessServerName = serverName, tlsContext = PLAINTEXT_CONTEXT) {
          channelBlockCalled = true
          directExecutor()
        }

      try {
        val response = ClientCalls.blockingUnaryCall(channel, echoMethod, CallOptions.DEFAULT, "hello")
        response shouldBe "echo: hello"
        serverBlockCalled shouldBe true
        channelBlockCalled shouldBe true
        server.services.map { it.serviceDescriptor.name } shouldBe listOf("EchoService")
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

    "server builds a plaintext Netty server without starting it" {
      var blockCalled = false
      val server =
        GrpcDsl.server(port = 0) {
          blockCalled = true
          addService(echoService())
        }
      blockCalled shouldBe true
      server.services.map { it.serviceDescriptor.name } shouldBe listOf("EchoService")
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
  }
}

private val stringMarshaller =
  object : MethodDescriptor.Marshaller<String> {
    override fun stream(value: String): InputStream = ByteArrayInputStream(value.toByteArray())

    override fun parse(stream: InputStream): String = stream.readBytes().decodeToString()
  }

private val echoMethod: MethodDescriptor<String, String> =
  MethodDescriptor.newBuilder<String, String>()
    .setType(MethodDescriptor.MethodType.UNARY)
    .setFullMethodName(MethodDescriptor.generateFullMethodName("EchoService", "Echo"))
    .setRequestMarshaller(stringMarshaller)
    .setResponseMarshaller(stringMarshaller)
    .build()

private fun echoService(): ServerServiceDefinition =
  ServerServiceDefinition.builder("EchoService")
    .addMethod(
      echoMethod,
      ServerCalls.asyncUnaryCall<String, String> { request, responseObserver ->
        responseObserver.onNext("echo: $request")
        responseObserver.onCompleted()
      },
    )
    .build()

private fun tlsResourcePath(name: String): String {
  val url = GrpcDslTests::class.java.classLoader.getResource("tls/$name")
    ?: error("Missing test resource: tls/$name")
  return File(url.toURI()).absolutePath
}
