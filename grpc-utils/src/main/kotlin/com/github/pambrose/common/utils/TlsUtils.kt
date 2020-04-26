/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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

package com.github.pambrose.common.utils

import com.github.pambrose.common.util.toDoubleQuoted
import io.grpc.netty.GrpcSslContexts
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import mu.KLogging
import java.io.File
import javax.net.ssl.SSLException

data class TlsContextBuilder(val builder: SslContextBuilder, val mutualAuth: Boolean)

data class TlsContext(val sslContext: SslContext?, val mutualAuth: Boolean) {
  fun desc() =
    if (sslContext == null)
      "plaintext"
    else
      "TLS ${if (mutualAuth) "with mutual auth" else "(no mutual auth)"}"

  companion object {
    val PLAINTEXT_CONTEXT = TlsContext(null, false)
  }
}

object TlsUtils : KLogging() {
  private fun String.doesNotExistMsg() = "File ${toDoubleQuoted()} does not exist"

  @Throws(SSLException::class)
  fun buildClientTlsContext(certChainFilePath: String = "",
                            privateKeyFilePath: String = "",
                            trustCertCollectionFilePath: String = ""): TlsContext =
    clientTlsContextBuilder(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath)
      .run {
        TlsContext(builder.build(), mutualAuth)
      }

  @Throws(SSLException::class)
  fun clientTlsContextBuilder(certChainFilePath: String = "",
                              privateKeyFilePath: String = "",
                              trustCertCollectionFilePath: String = ""): TlsContextBuilder =
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
          require(keyPath.isNotEmpty()) { "privateKeyFilePath required if certChainFilePath specified" }

        if (keyPath.isNotEmpty())
          require(certPath.isNotEmpty()) { "certChainFilePath required if privateKeyFilePath specified" }

        if (certPath.isNotEmpty() && keyPath.isNotEmpty()) {
          val certFile = File(certPath).apply { require(exists() && isFile) { certPath.doesNotExistMsg() } }
          val keyFile = File(keyPath).apply { require(exists() && isFile) { keyPath.doesNotExistMsg() } }

          logger.info { "Reading certChainFilePath: ${certPath.toDoubleQuoted()}" }
          logger.info { "Reading privateKeyFilePath: ${keyPath.toDoubleQuoted()}" }

          builder.keyManager(certFile, keyFile)
        }

        TlsContextBuilder(builder, certPath.isNotEmpty() && keyPath.isNotEmpty())
      }

  @Throws(SSLException::class)
  fun buildServerTlsContext(certChainFilePath: String,
                            privateKeyFilePath: String,
                            trustCertCollectionFilePath: String = ""): TlsContext =
    serverTlsContext(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath)
      .run {
        TlsContext(GrpcSslContexts.configure(builder).build(), mutualAuth)
      }

  @Throws(SSLException::class)
  fun serverTlsContext(certChainFilePath: String,
                       privateKeyFilePath: String,
                       trustCertCollectionFilePath: String = ""): TlsContextBuilder {
    val certPath = certChainFilePath.trim()
    val keyPath = privateKeyFilePath.trim()
    val trustPath = trustCertCollectionFilePath.trim()

    require(certPath.isNotEmpty()) { "Server certChainFilePath is required for TLS" }
    require(keyPath.isNotEmpty()) { "Server privateKeyFilePath is required for TLS" }

    val certFile = File(certPath).apply { require(exists() && isFile) { certPath.doesNotExistMsg() } }
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