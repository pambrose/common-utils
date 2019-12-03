package com.github.pambrose.common.utils

import com.github.pambrose.common.util.doubleQuoted
import io.grpc.netty.GrpcSslContexts
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import javax.net.ssl.SSLException

data class TlsContextBuilder(val builder: SslContextBuilder, val mutualAuth: Boolean)

data class TlsContext(val sslContext: SslContext? = null, val mutualAuth: Boolean = false) {
  fun desc() =
    if (sslContext == null)
      "plaintext"
    else
      "TLS " + if (mutualAuth) "with mutual auth" else "(no mutual auth)"
}

object TlsUtils {
  private fun String.doesNotExistMsg() = "File ${doubleQuoted()} does not exist"

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
            require(file.exists() && file.isFile()) { trustPath.doesNotExistMsg() }
            builder.trustManager(file)
          }

        if (certPath.isNotEmpty())
          require(keyPath.isNotEmpty()) { "privateKeyFilePath required if certChainFilePath specified" }

        if (keyPath.isNotEmpty())
          require(certPath.isNotEmpty()) { "certChainFilePath required if privateKeyFilePath specified" }

        if (certPath.isNotEmpty() && keyPath.isNotEmpty()) {
          val certFile = File(certPath).apply { require(exists() && isFile()) { certPath.doesNotExistMsg() } }
          val keyFile = File(keyPath).apply { require(exists() && isFile()) { keyPath.doesNotExistMsg() } }
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

    val certFile = File(certPath).apply { require(exists() && isFile()) { certPath.doesNotExistMsg() } }
    val keyFile = File(keyPath).apply { require(exists() && isFile()) { keyPath.doesNotExistMsg() } }

    return SslContextBuilder.forServer(certFile, keyFile)
      .let { builder ->
        if (trustPath.isNotEmpty()) {
          File(trustPath)
            .also { file ->
              require(file.exists() && file.isFile()) { trustPath.doesNotExistMsg() }
              builder.trustManager(file)
            }
        }
        TlsContextBuilder(builder.clientAuth(ClientAuth.REQUIRE), trustPath.isNotEmpty())
      }
  }
}