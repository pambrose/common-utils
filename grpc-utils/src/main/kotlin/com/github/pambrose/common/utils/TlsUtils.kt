package com.github.pambrose.common.utils

import io.grpc.netty.GrpcSslContexts
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import java.io.File
import javax.net.ssl.SSLException

object TlsUtils {
    @Throws(SSLException::class)
    fun buildClientSslContext(certChainFilePath: String = "",
                              privateKeyFilePath: String = "",
                              trustCertCollectionFilePath: String = ""): SslContext =
        GrpcSslContexts.forClient()
            .let { builder ->
                if (trustCertCollectionFilePath.isNotEmpty()) {
                    File(trustCertCollectionFilePath)
                        .also { file ->
                            require(file.exists()) { "File $trustCertCollectionFilePath does not exist" }
                            builder.trustManager(file)
                        }
                }

                if (certChainFilePath.isNotEmpty())
                    require(privateKeyFilePath.isNotEmpty()) { "privateKeyFilePath required if certChainFilePath specified" }

                if (privateKeyFilePath.isNotEmpty())
                    require(certChainFilePath.isNotEmpty()) { "certChainFilePath required if privateKeyFilePath specified" }

                if (certChainFilePath.isNotEmpty() && privateKeyFilePath.isNotEmpty()) {
                    val cert = File(certChainFilePath)
                        .apply { require(exists()) { "File $certChainFilePath does not exist" } }
                    val key = File(privateKeyFilePath)
                        .apply { require(exists()) { "File $privateKeyFilePath does not exist" } }
                    builder.keyManager(cert, key)
                }

                builder.build()
            }

    @Throws(SSLException::class)
    fun buildServerSslContext(certChainFilePath: String,
                              privateKeyFilePath: String,
                              trustCertCollectionFilePath: String = ""): SslContext {
        require(certChainFilePath.isNotEmpty()) { "certChainFilePath cannot be empty" }
        require(privateKeyFilePath.isNotEmpty()) { "privateKeyFilePath cannot be empty" }

        val cert = File(certChainFilePath)
            .apply { require(exists()) { "File $certChainFilePath does not exist" } }
        val key = File(privateKeyFilePath)
            .apply { require(exists()) { "File $privateKeyFilePath does not exist" } }

        return SslContextBuilder.forServer(cert, key)
            .let { builder ->
                if (trustCertCollectionFilePath.isNotEmpty()) {
                    File(trustCertCollectionFilePath)
                        .also { file ->
                            require(file.exists()) { "File $trustCertCollectionFilePath does not exist" }
                            builder.trustManager(file)
                        }
                    builder.clientAuth(ClientAuth.REQUIRE)
                }

                GrpcSslContexts.configure(builder).build()
            }
    }
}