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
                if (trustCertCollectionFilePath.isNotEmpty())
                    builder.trustManager(File(trustCertCollectionFilePath))

                if (certChainFilePath.isNotEmpty() && privateKeyFilePath.isNotEmpty())
                    builder.keyManager(File(certChainFilePath), File(privateKeyFilePath))

                builder.build()
            }

    @Throws(SSLException::class)
    fun buildServerSslContext(certChainFilePath: String,
                              privateKeyFilePath: String,
                              trustCertCollectionFilePath: String = ""): SslContext =
        SslContextBuilder.forServer(File(certChainFilePath), File(privateKeyFilePath))
            .let { builder ->
                if (trustCertCollectionFilePath.isNotEmpty()) {
                    builder.trustManager(File(trustCertCollectionFilePath))
                    builder.clientAuth(ClientAuth.REQUIRE)
                }

                GrpcSslContexts.configure(builder).build()
            }
}