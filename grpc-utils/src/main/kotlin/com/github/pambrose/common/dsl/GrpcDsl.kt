/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.dsl

import com.github.pambrose.common.delegate.SingleAssignVar.singleAssign
import io.grpc.Attributes
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.internal.AbstractManagedChannelImplBuilder
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import io.netty.handler.ssl.SslContext
import mu.KLogging

object GrpcDsl : KLogging() {

    private fun desc(sslContext: SslContext?) = if (sslContext != null) "TLS" else "plaintext"

    fun channel(hostName: String = "",
                port: Int = -1,
                sslContext: SslContext? = null,
                overrideAuthority: String = "",
                inProcessServerName: String = "",
                block: AbstractManagedChannelImplBuilder<*>.() -> Unit): ManagedChannel =
        when {
            inProcessServerName.isEmpty() -> {
                logger.info { "Connecting with ${desc(sslContext)} to gRPC server on port $port" }
                NettyChannelBuilder.forAddress(hostName, port)
                    .also { builder ->
                        if (overrideAuthority.isNotEmpty())
                            builder.overrideAuthority(overrideAuthority)
                        if (sslContext != null)
                            builder.sslContext(sslContext)
                        else
                            builder.usePlaintext()
                    }
            }
            else -> {
                logger.info { "Connecting to gRPC server with in-process server name $inProcessServerName" }
                InProcessChannelBuilder.forName(inProcessServerName)
                    .also { builder ->
                        builder.usePlaintext()
                    }
            }
        }
            .run {
                block(this)
                build()
            }

    fun server(port: Int = -1,
               sslContext: SslContext? = null,
               inProcessServerName: String = "",
               block: ServerBuilder<*>.() -> Unit): Server =
        when {
            inProcessServerName.isEmpty() -> {
                logger.info { "Listening for ${desc(sslContext)} gRPC traffic on port $port" }
                NettyServerBuilder.forPort(port)
                    .also { builder ->
                        if (sslContext != null)
                            builder.sslContext(sslContext)
                    }
            }
            else -> {
                logger.info { "Listening for gRPC traffic with in-process server name $inProcessServerName" }
                InProcessServerBuilder.forName(inProcessServerName)
            }
        }
            .run {
                block(this)
                build()
            }

    fun attributes(block: Attributes.Builder.() -> Unit): Attributes =
        Attributes.newBuilder()
            .run {
                block(this)
                build()
            }

    fun <T> streamObserver(init: StreamObserverHelper<T>.() -> Unit) =
        StreamObserverHelper<T>().apply { init() }

    class StreamObserverHelper<T> : StreamObserver<T> {
        private var onNextBlock: ((T) -> Unit)? by singleAssign()
        private var onErrorBlock: ((Throwable) -> Unit)? by singleAssign()
        private var completedBlock: (() -> Unit)? by singleAssign()

        override fun onNext(response: T) {
            onNextBlock?.invoke(response)
        }

        override fun onError(t: Throwable) {
            onErrorBlock?.invoke(t)
        }

        override fun onCompleted() {
            completedBlock?.invoke()
        }

        fun onNext(block: (T) -> Unit) {
            onNextBlock = block
        }

        fun onError(block: (Throwable) -> Unit) {
            onErrorBlock = block
        }

        fun onCompleted(block: () -> Unit) {
            completedBlock = block
        }
    }
}