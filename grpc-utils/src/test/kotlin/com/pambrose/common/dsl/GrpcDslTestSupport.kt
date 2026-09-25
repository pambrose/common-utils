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
import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerServiceDefinition
import io.grpc.StatusRuntimeException
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.kotest.assertions.throwables.shouldThrow
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

internal const val LOOPBACK = "127.0.0.1"

internal val stringMarshaller =
  object : MethodDescriptor.Marshaller<String> {
    override fun stream(value: String): InputStream = ByteArrayInputStream(value.toByteArray())

    override fun parse(stream: InputStream): String = stream.readBytes().decodeToString()
  }

internal val echoMethod: MethodDescriptor<String, String> =
  MethodDescriptor.newBuilder<String, String>()
    .setType(MethodDescriptor.MethodType.UNARY)
    .setFullMethodName(MethodDescriptor.generateFullMethodName("EchoService", "Echo"))
    .setRequestMarshaller(stringMarshaller)
    .setResponseMarshaller(stringMarshaller)
    .build()

internal fun echoService(): ServerServiceDefinition =
  ServerServiceDefinition.builder("EchoService")
    .addMethod(
      echoMethod,
      ServerCalls.asyncUnaryCall { request, responseObserver ->
        responseObserver.onNext("echo: $request")
        responseObserver.onCompleted()
      },
    )
    .build()

// A deadline keeps a hung handshake from blocking the suite: it fails as DEADLINE_EXCEEDED instead.
internal fun ManagedChannel.echo(request: String): String =
  ClientCalls.blockingUnaryCall(this, echoMethod, CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), request)

internal fun ManagedChannel.echoFailure(request: String): StatusRuntimeException =
  shouldThrow<StatusRuntimeException> { echo(request) }

// Bound to 127.0.0.1 rather than every interface: on a developer machine another app's 127.0.0.1 listener can
// shadow a wildcard listener on the same port.
internal fun loopbackServer(
  tlsContext: TlsContext,
  block: ServerBuilder<*>.() -> Unit,
): Server = GrpcDsl.server(port = 0, tlsContext = tlsContext, bindAddress = LOOPBACK, block = block)

// Runs block against a started server, shutting the server and every channel it opened down afterwards.
internal inline fun <T> Server.use(block: (Server) -> T): T {
  start()
  return try {
    block(this)
  } finally {
    shutdownNow()
    awaitTermination(5, TimeUnit.SECONDS)
  }
}

internal inline fun <T> ManagedChannel.use(block: (ManagedChannel) -> T): T =
  try {
    block(this)
  } finally {
    shutdownNow()
    awaitTermination(5, TimeUnit.SECONDS)
  }
