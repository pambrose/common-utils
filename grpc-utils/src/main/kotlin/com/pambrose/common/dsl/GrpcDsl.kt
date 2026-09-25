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

package com.pambrose.common.dsl

import com.pambrose.common.delegate.AtomicDelegates.singleSetReference
import com.pambrose.common.util.isNotNull
import com.pambrose.common.util.toDoubleQuoted
import com.pambrose.common.utils.TlsContext
import com.pambrose.common.utils.TlsContext.Companion.PLAINTEXT_CONTEXT
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.Attributes
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import java.net.InetSocketAddress

/**
 * DSL entry point for building gRPC channels, servers, attributes, and stream observers.
 *
 * Supports both Netty-based (network) and in-process transports, with optional TLS and retry configuration.
 */
object GrpcDsl {
  private val logger = KotlinLogging.logger {}
  private const val MAX_PORT = 65535

  /**
   * Builds a [ManagedChannel] using either a Netty transport or an in-process transport.
   *
   * When [inProcessServerName] is non-empty, an in-process channel is created, ignoring [hostName], [port]
   * and [tlsContext]. The retry and authority options apply to both transports.
   *
   * @param hostName the target server hostname (Netty transport only)
   * @param port the target server port (Netty transport only)
   * @param enableRetry whether gRPC retry is enabled on the channel. `true` (the default) matches grpc-java's own
   *   default: RPCs get transparent retries (e.g. on a refused stream during a server restart), and any retry
   *   policy in the channel's service config applies. `false` calls `disableRetry()`, which turns off transparent
   *   retries as well. Configured retries need a retry policy, supplied in [block] through
   *   `defaultServiceConfig(...)`; this flag alone does not add one.
   * @param maxRetryAttempts the maximum number of retry attempts per RPC; a negative value leaves grpc's own
   *   default in place
   * @param tlsContext the TLS configuration for the channel; defaults to [PLAINTEXT_CONTEXT]
   * @param overrideAuthority overrides the authority used for TLS hostname verification
   * @param inProcessServerName if non-empty, creates an in-process channel with this name
   * @param block a configuration block applied to the channel builder before building
   * @return the built [ManagedChannel]
   * @throws IllegalArgumentException if the Netty transport is chosen and [hostName] is blank or [port] is not in
   *   `0..65535`
   */
  fun channel(
    hostName: String = "",
    port: Int = -1,
    enableRetry: Boolean = true,
    maxRetryAttempts: Int = 5,
    tlsContext: TlsContext = PLAINTEXT_CONTEXT,
    overrideAuthority: String = "",
    inProcessServerName: String = "",
    block: ManagedChannelBuilder<*>.() -> Unit,
  ): ManagedChannel {
    val channelBuilder =
      if (inProcessServerName.isEmpty())
        createNettyChannel(hostName, port, tlsContext)
      else
        createInProcessChannel(inProcessServerName)

    return channelBuilder.run {
      applyChannelOptions(this, overrideAuthority, enableRetry, maxRetryAttempts)
      block(this)
      build()
    }
  }

  // Every option here lives on ManagedChannelBuilder itself, so it is applied to whichever transport was
  // chosen; setting them in the Netty branch alone is what left the in-process channel ignoring them.
  private fun applyChannelOptions(
    builder: ManagedChannelBuilder<*>,
    overrideAuthority: String,
    enableRetry: Boolean,
    maxRetryAttempts: Int,
  ) {
    val override = overrideAuthority.trim()
    if (override.isNotEmpty()) {
      logger.info { "Assigning overrideAuthority: ${override.toDoubleQuoted()}" }
      builder.overrideAuthority(override)
    }

    // grpc-java turns retry on by default, so the flag has to say so in both directions.
    if (enableRetry)
      builder.enableRetry()
    else
      builder.disableRetry()

    if (maxRetryAttempts > -1)
      builder.maxRetryAttempts(maxRetryAttempts)
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
  ): NettyChannelBuilder {
    require(hostName.isNotBlank()) {
      "hostName is required for a Netty channel; set inProcessServerName for an in-process one"
    }
    require(port in 0..MAX_PORT) { "port must be in 0..$MAX_PORT for a Netty channel, but was $port" }
    logger.info { "Creating connection for gRPC server at $hostName:$port using ${tlsContext.desc()}" }
    return NettyChannelBuilder
      .forAddress(hostName, port)
      .also { builder ->
        if (tlsContext.sslContext.isNotNull())
          builder.sslContext(tlsContext.sslContext)
        else
          builder.usePlaintext()
      }
  }

  /**
   * Builds a gRPC [Server] using either a Netty transport or an in-process transport.
   *
   * When [inProcessServerName] is non-empty, an in-process server is created (ignoring port, TLS and bind
   * address). Otherwise a Netty server is created listening on [port], at [bindAddress] or on every interface.
   *
   * @param port the port to listen on (Netty transport only); `0` lets the OS choose one
   * @param tlsContext the TLS configuration for the server; defaults to [PLAINTEXT_CONTEXT]
   * @param inProcessServerName if non-empty, creates an in-process server with this name
   * @param bindAddress the address to listen on, such as `"127.0.0.1"` (Netty transport only); `null`, the
   *   default, listens on every interface
   * @param block a configuration block applied to the server builder before building
   * @return the built [Server]
   * @throws IllegalArgumentException if the Netty transport is chosen and [port] is not in `0..65535`
   */
  fun server(
    port: Int = -1,
    tlsContext: TlsContext = PLAINTEXT_CONTEXT,
    inProcessServerName: String = "",
    bindAddress: String? = null,
    block: ServerBuilder<*>.() -> Unit,
  ): Server {
    val serverBuilder =
      if (inProcessServerName.isEmpty())
        createNettyServer(port, bindAddress, tlsContext)
      else
        createInProcessServer(inProcessServerName)

    return serverBuilder.run {
      block(this)
      build()
    }
  }

  // The pre-bindAddress signature, kept for binary compatibility; hidden, so source callers resolve to the one above.
  @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
  fun server(
    port: Int = -1,
    tlsContext: TlsContext = PLAINTEXT_CONTEXT,
    inProcessServerName: String = "",
    block: ServerBuilder<*>.() -> Unit,
  ): Server = server(port, tlsContext, inProcessServerName, null, block)

  private fun createNettyServer(
    port: Int,
    bindAddress: String?,
    tlsContext: TlsContext,
  ): NettyServerBuilder {
    require(port in 0..MAX_PORT) {
      "port must be in 0..$MAX_PORT for a Netty server, but was $port; set inProcessServerName for an in-process one"
    }
    val where = if (bindAddress == null) "port $port" else "$bindAddress:$port"
    logger.info { "Listening for gRPC traffic on $where using ${tlsContext.desc()}" }
    val nettyBuilder =
      if (bindAddress == null)
        NettyServerBuilder.forPort(port)
      else
        NettyServerBuilder.forAddress(InetSocketAddress(bindAddress, port))
    return nettyBuilder.also { builder ->
      if (tlsContext.sslContext.isNotNull())
        builder.sslContext(tlsContext.sslContext)
    }
  }

  private fun createInProcessServer(inProcessServerName: String): InProcessServerBuilder {
    logger.info { "Listening for gRPC traffic with in-process server name $inProcessServerName" }
    return InProcessServerBuilder.forName(inProcessServerName)
  }

  /**
   * Builds gRPC [Attributes] using a DSL-style configuration block.
   *
   * @param block a configuration block applied to [Attributes.Builder]
   * @return the built [Attributes] instance
   */
  fun attributes(block: Attributes.Builder.() -> Unit): Attributes =
    Attributes
      .newBuilder()
      .run {
        block(this)
        build()
      }

  /**
   * Creates a [StreamObserver] using a DSL-style builder.
   *
   * Every callback is optional, and each one may be registered **at most once**: a second
   * [StreamObserverHelper.onNext], [StreamObserverHelper.onError] or [StreamObserverHelper.onCompleted] in the
   * same block throws [IllegalStateException].
   *
   * @param T the response element type
   * @param init a configuration block for registering [StreamObserverHelper.onNext], [StreamObserverHelper.onError],
   *   and [StreamObserverHelper.onCompleted] callbacks
   * @return a [StreamObserver] that delegates to the registered callbacks
   */
  fun <T> streamObserver(init: StreamObserverHelper<T>.() -> Unit): StreamObserver<T> =
    StreamObserverHelper<T>().apply { init() }

  /**
   * A DSL-friendly [StreamObserver] implementation that delegates to user-supplied lambda callbacks.
   *
   * Each callback is single-assignment; registering one twice throws [IllegalStateException]. An unregistered
   * callback does nothing.
   *
   * @param T the response element type
   */
  class StreamObserverHelper<T> : StreamObserver<T> {
    private var onNextBlock: ((T) -> Unit)? by singleSetReference()
    private var onErrorBlock: ((Throwable) -> Unit)? by singleSetReference()
    private var completedBlock: (() -> Unit)? by singleSetReference()

    override fun onNext(response: T) {
      onNextBlock?.invoke(response)
    }

    override fun onError(t: Throwable) {
      onErrorBlock?.invoke(t)
    }

    override fun onCompleted() {
      completedBlock?.invoke()
    }

    /** Registers a callback invoked for each response element. Can be called only once. */
    fun onNext(block: (T) -> Unit) {
      onNextBlock = block
    }

    /** Registers a callback invoked when the stream encounters an error. Can be called only once. */
    fun onError(block: (Throwable) -> Unit) {
      onErrorBlock = block
    }

    /** Registers a callback invoked when the stream completes successfully. Can be called only once. */
    fun onCompleted(block: () -> Unit) {
      completedBlock = block
    }
  }
}
