package com.github.pambrose.common.utils

import com.github.pambrose.common.util.doubleQuoted
import io.grpc.netty.GrpcSslContexts
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import javax.net.ssl.SSLException

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
        val certFile = certChainFilePath.trim()
        val keyFile = privateKeyFilePath.trim()
        val trustFile = trustCertCollectionFilePath.trim()

        if (trustFile.isNotEmpty()) {
          File(trustFile)
            .also { file ->
              require(file.exists() && file.isFile()) { trustFile.doesNotExistMsg() }
              builder.trustManager(file)
            }
        }

        if (certFile.isNotEmpty())
          require(keyFile.isNotEmpty()) { "privateKeyFilePath required if certChainFilePath specified" }

        if (keyFile.isNotEmpty())
          require(certFile.isNotEmpty()) { "certChainFilePath required if privateKeyFilePath specified" }

        if (certFile.isNotEmpty() && keyFile.isNotEmpty()) {
          val cert = File(certFile).apply { require(exists() && isFile()) { certFile.doesNotExistMsg() } }
          val key = File(keyFile).apply { require(exists() && isFile()) { keyFile.doesNotExistMsg() } }
          builder.keyManager(cert, key)
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
    val certFile = certChainFilePath.trim()
    val keyFile = privateKeyFilePath.trim()
    val trustFile = trustCertCollectionFilePath.trim()

    require(certFile.isNotEmpty()) { "certChainFilePath cannot be empty" }
    require(keyFile.isNotEmpty()) { "privateKeyFilePath cannot be empty" }

    val cert = File(certFile).apply { require(exists() && isFile()) { certFile.doesNotExistMsg() } }
    val key = File(keyFile).apply { require(exists() && isFile()) { keyFile.doesNotExistMsg() } }

    return SslContextBuilder.forServer(cert, key)
      .also { builder ->
        if (trustFile.isNotEmpty()) {
          File(trustFile)
            .also { file ->
              require(file.exists() && file.isFile()) { trustFile.doesNotExistMsg() }
              builder.trustManager(file)
            }
          builder.clientAuth(ClientAuth.REQUIRE)
        }
      }
  }
}