package com.github.pambrose.common.dsl

import io.grpc.netty.GrpcSslContexts
import io.netty.handler.ssl.SslContext
import java.io.File
import javax.net.ssl.SSLException

object TlsSupport {
    @Throws(SSLException::class)
    private fun buildSslContext(trustCertCollectionFilePath: String = "",
                                clientCertChainFilePath: String = "",
                                clientPrivateKeyFilePath: String = ""): SslContext =
        GrpcSslContexts.forClient().let { builder ->
            if (trustCertCollectionFilePath.isNotEmpty())
                builder.trustManager(File(trustCertCollectionFilePath))

            if (clientCertChainFilePath.isNotEmpty() && clientPrivateKeyFilePath.isNotEmpty())
                builder.keyManager(File(clientCertChainFilePath), File(clientPrivateKeyFilePath))

            builder.build()
        }
}