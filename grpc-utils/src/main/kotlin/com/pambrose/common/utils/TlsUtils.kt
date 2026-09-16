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

package com.pambrose.common.utils

import com.pambrose.common.util.isNull
import com.pambrose.common.util.toDoubleQuoted
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.netty.GrpcSslContexts
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import javax.net.ssl.SSLException

/**
 * Wraps an [SslContextBuilder] together with a flag indicating whether mutual authentication is configured.
 *
 * The builder already carries gRPC's ALPN configuration, so a context built from it is accepted by
 * `NettyServerBuilder.sslContext` and `NettyChannelBuilder.sslContext`.
 *
 * @property builder the Netty [SslContextBuilder] being configured
 * @property mutualAuth `true` if mutual (client + server) authentication is enabled
 */
data class TlsContextBuilder(
  val builder: SslContextBuilder,
  val mutualAuth: Boolean,
)

/**
 * Holds a fully-built [SslContext] (or `null` for plaintext) along with a mutual-auth indicator.
 *
 * @property sslContext the Netty [SslContext], or `null` if the connection uses plaintext
 * @property mutualAuth `true` if mutual authentication is enabled
 */
data class TlsContext(
  val sslContext: SslContext?,
  val mutualAuth: Boolean,
) {
  /**
   * Returns a human-readable description of the TLS mode (e.g., "plaintext" or "TLS with mutual auth").
   *
   * @return a description string suitable for logging
   */
  fun desc() =
    if (sslContext.isNull())
      "plaintext"
    else
      "TLS ${if (mutualAuth) "with mutual auth" else "(no mutual auth)"}"

  companion object {
    /** A [TlsContext] representing a plaintext (non-TLS) connection. */
    val PLAINTEXT_CONTEXT = TlsContext(null, false)
  }
}

/** Factory methods for building gRPC client and server TLS contexts from certificate files. */
object TlsUtils {
  private val logger = KotlinLogging.logger {}

  private fun String.doesNotExistMsg() = "File ${toDoubleQuoted()} does not exist"

  // Resolves a configured path to a file that has to exist, naming the setting it came from in the log line.
  private fun existingFile(
    path: String,
    setting: String,
  ): File =
    File(path).also {
      require(it.exists() && it.isFile) { path.doesNotExistMsg() }
      logger.info { "Reading $setting: ${path.toDoubleQuoted()}" }
    }

  /**
   * Builds a complete client-side [TlsContext] ready for use with a gRPC channel.
   *
   * Every path is optional; see [clientTlsContextBuilder] for what each one does.
   *
   * @param certChainFilePath path to the client certificate chain file (for mutual auth)
   * @param privateKeyFilePath path to the client private key file (for mutual auth)
   * @param trustCertCollectionFilePath path to the trusted CA certificates file; when empty, the JVM's
   *   default trust store is used
   * @return a [TlsContext] containing the built [SslContext]
   * @throws SSLException if SSL context creation fails
   */
  @Throws(SSLException::class)
  fun buildClientTlsContext(
    certChainFilePath: String = "",
    privateKeyFilePath: String = "",
    trustCertCollectionFilePath: String = "",
  ): TlsContext =
    clientTlsContextBuilder(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath)
      .run {
        TlsContext(builder.build(), mutualAuth)
      }

  /**
   * Creates a client-side [TlsContextBuilder] that can be further customized before building.
   *
   * Supplying [trustCertCollectionFilePath] pins the servers to trust to that CA; leaving it empty keeps
   * Netty's default trust manager, which verifies against the JVM trust store, as a server with a public-CA
   * certificate needs. Supplying [certChainFilePath] and [privateKeyFilePath] together enables mutual auth;
   * neither one is valid without the other.
   *
   * @param certChainFilePath path to the client certificate chain file (for mutual auth)
   * @param privateKeyFilePath path to the client private key file (for mutual auth)
   * @param trustCertCollectionFilePath path to the trusted CA certificates file; when empty, the JVM's
   *   default trust store is used
   * @return a [TlsContextBuilder] wrapping the configured [SslContextBuilder]
   * @throws SSLException if SSL context builder creation fails
   * @throws IllegalArgumentException if a given file does not exist, or only one of the cert/key pair is given
   */
  @Throws(SSLException::class)
  fun clientTlsContextBuilder(
    certChainFilePath: String = "",
    privateKeyFilePath: String = "",
    trustCertCollectionFilePath: String = "",
  ): TlsContextBuilder {
    val certPath = certChainFilePath.trim()
    val keyPath = privateKeyFilePath.trim()
    val trustPath = trustCertCollectionFilePath.trim()
    val builder = GrpcSslContexts.forClient()

    if (trustPath.isNotEmpty())
      builder.trustManager(existingFile(trustPath, "trustCertCollectionFilePath"))
    else
      logger.info { "No trustCertCollectionFilePath given; using the JVM default trust store" }

    if (certPath.isNotEmpty())
      require(keyPath.isNotEmpty()) {
        "privateKeyFilePath required if certChainFilePath specified"
      }

    if (keyPath.isNotEmpty())
      require(certPath.isNotEmpty()) {
        "certChainFilePath required if privateKeyFilePath specified"
      }

    val mutualAuth = certPath.isNotEmpty() && keyPath.isNotEmpty()
    if (mutualAuth)
      builder.keyManager(
        existingFile(certPath, "certChainFilePath"),
        existingFile(keyPath, "privateKeyFilePath"),
      )

    return TlsContextBuilder(builder, mutualAuth)
  }

  /**
   * Builds a complete server-side [TlsContext] ready for use with a gRPC server.
   *
   * @param certChainFilePath path to the server certificate chain file (required)
   * @param privateKeyFilePath path to the server private key file (required)
   * @param trustCertCollectionFilePath path to the trusted client CA certificates (enables mutual auth)
   * @return a [TlsContext] containing the built [SslContext]
   * @throws SSLException if SSL context creation fails
   */
  @Throws(SSLException::class)
  fun buildServerTlsContext(
    certChainFilePath: String,
    privateKeyFilePath: String,
    trustCertCollectionFilePath: String = "",
  ): TlsContext =
    serverTlsContext(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath)
      .run {
        TlsContext(builder.build(), mutualAuth)
      }

  /**
   * Creates a server-side [TlsContextBuilder] that can be further customized before building.
   *
   * If [trustCertCollectionFilePath] is provided, mutual authentication (client cert required) is enabled.
   *
   * The builder comes from [GrpcSslContexts], so it already has the ALPN configuration that
   * `NettyServerBuilder.sslContext` requires: a context built straight from it is accepted by
   * [com.pambrose.common.dsl.GrpcDsl.server].
   *
   * @param certChainFilePath path to the server certificate chain file (required)
   * @param privateKeyFilePath path to the server private key file (required)
   * @param trustCertCollectionFilePath path to the trusted client CA certificates (enables mutual auth)
   * @return a [TlsContextBuilder] wrapping the configured [SslContextBuilder]
   * @throws SSLException if SSL context builder creation fails
   * @throws IllegalArgumentException if a required path is empty, or a given file does not exist
   */
  @Throws(SSLException::class)
  fun serverTlsContext(
    certChainFilePath: String,
    privateKeyFilePath: String,
    trustCertCollectionFilePath: String = "",
  ): TlsContextBuilder {
    val certPath = certChainFilePath.trim()
    val keyPath = privateKeyFilePath.trim()
    val trustPath = trustCertCollectionFilePath.trim()

    require(certPath.isNotEmpty()) { "Server certChainFilePath is required for TLS" }
    require(keyPath.isNotEmpty()) { "Server privateKeyFilePath is required for TLS" }

    val builder =
      GrpcSslContexts.forServer(
        existingFile(certPath, "certChainFilePath"),
        existingFile(keyPath, "privateKeyFilePath"),
      )

    if (trustPath.isNotEmpty()) {
      builder.trustManager(existingFile(trustPath, "trustCertCollectionFilePath"))
      builder.clientAuth(ClientAuth.REQUIRE)
    }

    return TlsContextBuilder(builder, trustPath.isNotEmpty())
  }
}
