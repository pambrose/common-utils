/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.dsl

import com.github.pambrose.common.delegate.SingleAssignVar.singleAssign
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.pambrose.common.utils.TlsContext
import com.github.pambrose.common.utils.TlsContext.Companion.PLAINTEXT_CONTEXT
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.*
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver

object GrpcDsl {
  private val logger = KotlinLogging.logger {}

  fun channel(
    hostName: String = "",
    port: Int = -1,
    enableRetry: Boolean = false,
    maxRetryAttempts: Int = 5,
    tlsContext: TlsContext,
    overrideAuthority: String = "",
    inProcessServerName: String = "",
    block: ManagedChannelBuilder<*>.() -> Unit,
  ): ManagedChannel {
    val channelBuilder =
      if (inProcessServerName.isEmpty())
        createNettyChannel(hostName, port, tlsContext, overrideAuthority, enableRetry, maxRetryAttempts)
      else
        createInProcessChannel(inProcessServerName)

    return channelBuilder.run {
      block(this)
      build()
    }
  }

  private fun createInProcessChannel(inProcessServerName: String): InProcessChannelBuilder {
    logger.info { "Creating connection for gRPC server with in-process server name $inProcessServerName" }
    return InProcessChannelBuilder.forName(inProcessServerName).also { builder ->
      builder.usePlaintext()
    }
  }

  private fun createNettyChannel(
    hostName: String,
    port: Int,
    tlsContext: TlsContext,
    overrideAuthority: String,
    enableRetry: Boolean,
    maxRetryAttempts: Int,
  ): NettyChannelBuilder {
    logger.info { "Creating connection for gRPC server at $hostName:$port using ${tlsContext.desc()}" }
    return NettyChannelBuilder
      .forAddress(hostName, port)
      .also { builder ->
        val override = overrideAuthority.trim()
        if (override.isNotEmpty()) {
          logger.info { "Assigning overrideAuthority: ${override.toDoubleQuoted()}" }
          builder.overrideAuthority(override)
        }

        if (tlsContext.sslContext.isNotNull())
          builder.sslContext(tlsContext.sslContext)
        else
          builder.usePlaintext()

        if (enableRetry)
          builder.enableRetry()

        if (maxRetryAttempts > -1)
          builder.maxRetryAttempts(maxRetryAttempts)
      }
  }

  fun server(
    port: Int = -1,
    tlsContext: TlsContext = PLAINTEXT_CONTEXT,
    inProcessServerName: String = "",
    block: ServerBuilder<*>.() -> Unit,
  ): Server {
    val serverBuilder =
      if (inProcessServerName.isEmpty())
        createNettyServer(port, tlsContext)
      else
        createInProcessServer(inProcessServerName)

    return serverBuilder.run {
      block(this)
      build()
    }
  }

  private fun createNettyServer(
    port: Int,
    tlsContext: TlsContext,
  ): NettyServerBuilder {
    logger.info { "Listening for gRPC traffic on port $port using ${tlsContext.desc()}" }
    return NettyServerBuilder.forPort(port).also { builder ->
      if (tlsContext.sslContext.isNotNull())
        builder.sslContext(tlsContext.sslContext)
    }
  }

  private fun createInProcessServer(inProcessServerName: String): InProcessServerBuilder {
    logger.info { "Listening for gRPC traffic with in-process server name $inProcessServerName" }
    return InProcessServerBuilder.forName(inProcessServerName)
  }

  fun attributes(block: Attributes.Builder.() -> Unit): Attributes =
    Attributes
      .newBuilder()
      .run {
        block(this)
        build()
      }

  fun <T> streamObserver(init: StreamObserverHelper<T>.() -> Unit) = StreamObserverHelper<T>().apply { init() }

  class StreamObserverHelper<T> : StreamObserver<T> {
    private var onNextBlock: ((T) -> Unit)? by singleAssign()
    private var onErrorBlock: ((Throwable) -> Unit)? by singleAssign()
    private var completedBlock: (() -> Unit)? by singleAssign()

    override fun onNext(response: T) {
      onNextBlock?.invoke(response)
    }

    override fun onError(t: Throwable) {
      onErrorBlock?.invoke(t)
    }

    override fun onCompleted() {
      completedBlock?.invoke()
    }

    fun onNext(block: (T) -> Unit) {
      onNextBlock = block
    }

    fun onError(block: (Throwable) -> Unit) {
      onErrorBlock = block
    }

    fun onCompleted(block: () -> Unit) {
      completedBlock = block
    }
  }
}
