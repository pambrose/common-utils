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

  /**
   * Builds a complete client-side [TlsContext] ready for use with a gRPC channel.
   *
   * @param certChainFilePath path to the client certificate chain file (for mutual auth)
   * @param privateKeyFilePath path to the client private key file (for mutual auth)
   * @param trustCertCollectionFilePath path to the trusted CA certificates file (required)
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
   * @param certChainFilePath path to the client certificate chain file (for mutual auth)
   * @param privateKeyFilePath path to the client private key file (for mutual auth)
   * @param trustCertCollectionFilePath path to the trusted CA certificates file (required)
   * @return a [TlsContextBuilder] wrapping the configured [SslContextBuilder]
   * @throws SSLException if SSL context builder creation fails
   */
  @Throws(SSLException::class)
  fun clientTlsContextBuilder(
    certChainFilePath: String = "",
    privateKeyFilePath: String = "",
    trustCertCollectionFilePath: String = "",
  ): TlsContextBuilder =
    GrpcSslContexts.forClient()
      .let { builder ->
        val certPath = certChainFilePath.trim()
        val keyPath = privateKeyFilePath.trim()
        val trustPath = trustCertCollectionFilePath.trim()

        require(trustPath.isNotEmpty()) { "Client trustCertCollectionFilePath is required for TLS" }

        File(trustPath)
          .also { file ->
            require(file.exists() && file.isFile) { trustPath.doesNotExistMsg() }
            logger.info { "Reading trustCertCollectionFilePath: ${trustPath.toDoubleQuoted()}" }
            builder.trustManager(file)
          }

        if (certPath.isNotEmpty())
          require(keyPath.isNotEmpty()) {
            "privateKeyFilePath required if certChainFilePath specified"
          }

        if (keyPath.isNotEmpty())
          require(certPath.isNotEmpty()) {
            "certChainFilePath required if privateKeyFilePath specified"
          }

        if (certPath.isNotEmpty() && keyPath.isNotEmpty()) {
          val certFile =
            File(certPath).apply { require(exists() && isFile) { certPath.doesNotExistMsg() } }
          val keyFile =
            File(keyPath).apply { require(exists() && isFile) { keyPath.doesNotExistMsg() } }

          logger.info { "Reading certChainFilePath: ${certPath.toDoubleQuoted()}" }
          logger.info { "Reading privateKeyFilePath: ${keyPath.toDoubleQuoted()}" }

          builder.keyManager(certFile, keyFile)
        }

        TlsContextBuilder(builder, certPath.isNotEmpty() && keyPath.isNotEmpty())
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
        TlsContext(GrpcSslContexts.configure(builder).build(), mutualAuth)
      }

  /**
   * Creates a server-side [TlsContextBuilder] that can be further customized before building.
   *
   * If [trustCertCollectionFilePath] is provided, mutual authentication (client cert required) is enabled.
   *
   * @param certChainFilePath path to the server certificate chain file (required)
   * @param privateKeyFilePath path to the server private key file (required)
   * @param trustCertCollectionFilePath path to the trusted client CA certificates (enables mutual auth)
   * @return a [TlsContextBuilder] wrapping the configured [SslContextBuilder]
   * @throws SSLException if SSL context builder creation fails
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

    val certFile =
      File(certPath).apply { require(exists() && isFile) { certPath.doesNotExistMsg() } }
    val keyFile = File(keyPath).apply { require(exists() && isFile) { keyPath.doesNotExistMsg() } }

    logger.info { "Reading certChainFilePath: ${certPath.toDoubleQuoted()}" }
    logger.info { "Reading privateKeyFilePath: ${keyPath.toDoubleQuoted()}" }

    return SslContextBuilder.forServer(certFile, keyFile)
      .let { builder ->
        if (trustPath.isNotEmpty()) {
          File(trustPath)
            .also { file ->
              require(file.exists() && file.isFile) { trustPath.doesNotExistMsg() }
              logger.info { "Reading trustCertCollectionFilePath: ${trustPath.toDoubleQuoted()}" }
              builder.trustManager(file)
              builder.clientAuth(ClientAuth.REQUIRE)
            }
        }
        TlsContextBuilder(builder, trustPath.isNotEmpty())
      }
  }
}
