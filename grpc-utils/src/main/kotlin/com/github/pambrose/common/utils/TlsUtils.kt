package com.github.pambrose.common.utils

import com.github.pambrose.common.util.doubleQuoted
import io.grpc.netty.GrpcSslContexts
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import javax.net.ssl.SSLException

data class TlsDetails(val sslContext: SslContext? = null, val mutualAuth: Boolean = false) {
  fun desc() =
    if (sslContext == null)
      "plaintext"
    else
      "TLS " + if (mutualAuth) "with mutual auth" else "(no mutual auth)"
}

object TlsUtils {
  private fun String.doesNotExistMsg() = "File ${doubleQuoted()} does not exist"

  @Throws(SSLException::class)
  fun buildClientSslContext(certChainFilePath: String = "",
                            privateKeyFilePath: String = "",
                            trustCertCollectionFilePath: String = ""): SslContext =
    clientSslContext(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath).build()

  @Throws(SSLException::class)
  fun clientSslContext(certChainFilePath: String = "",
                       privateKeyFilePath: String = "",
                       trustCertCollectionFilePath: String = ""): SslContextBuilder =
    GrpcSslContexts.forClient()
      .also { builder ->
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
      }

  @Throws(SSLException::class)
  fun buildServerSslContext(certChainFilePath: String,
                            privateKeyFilePath: String,
                            trustCertCollectionFilePath: String = ""): SslContext {
    val builder = serverSslContext(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath)
    return GrpcSslContexts.configure(builder).build()
  }

  @Throws(SSLException::class)
  fun serverSslContext(certChainFilePath: String,
                       privateKeyFilePath: String,
                       trustCertCollectionFilePath: String = ""): SslContextBuilder {
    val certPath = certChainFilePath.trim()
    val keyPath = privateKeyFilePath.trim()
    val trustPath = trustCertCollectionFilePath.trim()

    require(certPath.isNotEmpty()) { "Server certChainFilePath is required for TLS" }
    require(keyPath.isNotEmpty()) { "Server privateKeyFilePath is required for TLS" }

    val certFile = File(certPath).apply { require(exists() && isFile()) { certPath.doesNotExistMsg() } }
    val keyFile = File(keyPath).apply { require(exists() && isFile()) { keyPath.doesNotExistMsg() } }

    return SslContextBuilder.forServer(certFile, keyFile)
      .also { builder ->
        if (trustPath.isNotEmpty()) {
          File(trustPath)
            .also { file ->
              require(file.exists() && file.isFile()) { trustPath.doesNotExistMsg() }
              builder.trustManager(file)
            }
          builder.clientAuth(ClientAuth.REQUIRE)
        }
      }
  }
}