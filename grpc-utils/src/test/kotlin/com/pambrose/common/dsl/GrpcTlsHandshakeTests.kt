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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.pambrose.common.utils.TlsContext
import com.pambrose.common.utils.TlsContext.Companion.PLAINTEXT_CONTEXT
import com.pambrose.common.utils.TlsUtils
import com.pambrose.common.utils.tlsResourcePath
import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

private const val NO_TLS = "plaintext"
private const val NO_CLIENT_CERTIFICATE = "no client certificate"

// server-cert.pem is self-signed for localhost, and client-cert.pem is self-signed for test-client, so each
// certificate is also the CA that the other side has to trust.
private fun serverTls(trustClients: Boolean = false) =
  TlsUtils.buildServerTlsContext(
    certChainFilePath = tlsResourcePath("server-cert.pem"),
    privateKeyFilePath = tlsResourcePath("server-key.pem"),
    trustCertCollectionFilePath = if (trustClients) tlsResourcePath("client-cert.pem") else "",
  )

private fun clientTls(
  certName: String = "client",
  presentCertificate: Boolean = true,
  trustName: String = "server",
) = TlsUtils.buildClientTlsContext(
  certChainFilePath = if (presentCertificate) tlsResourcePath("$certName-cert.pem") else "",
  privateKeyFilePath = if (presentCertificate) tlsResourcePath("$certName-key.pem") else "",
  trustCertCollectionFilePath = tlsResourcePath("$trustName-cert.pem"),
)

// The server certificate names localhost but the channel dials 127.0.0.1, so overrideAuthority supplies the host
// name that TLS verifies.
private fun Server.channel(
  tlsContext: TlsContext,
  overrideAuthority: String = "localhost",
): ManagedChannel =
  GrpcDsl.channel(
    hostName = LOOPBACK,
    port = port,
    tlsContext = tlsContext,
    overrideAuthority = overrideAuthority,
  ) {}

private fun SSLSession.clientSubject(): String =
  try {
    (peerCertificates.first() as X509Certificate).subjectX500Principal.name
  } catch (_: SSLPeerUnverifiedException) {
    NO_CLIENT_CERTIFICATE
  }

// Records, for each call, the subject of the client certificate the server verified.
private class PeerRecorder : ServerInterceptor {
  val peers: MutableList<String> = CopyOnWriteArrayList()

  override fun <ReqT, RespT> interceptCall(
    call: ServerCall<ReqT, RespT>,
    headers: Metadata,
    next: ServerCallHandler<ReqT, RespT>,
  ): ServerCall.Listener<ReqT> {
    peers += call.attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION)?.clientSubject() ?: NO_TLS
    return next.startCall(call, headers)
  }
}

private fun echoServer(
  tlsContext: TlsContext,
  recorder: PeerRecorder = PeerRecorder(),
) = loopbackServer(tlsContext) { addService(ServerInterceptors.intercept(echoService(), recorder)) }

private fun ManagedChannel.echoFailureCode(): Status.Code = use { Status.fromThrowable(it.echoFailure("hi")).code }

// Real handshakes over loopback, on the OpenSSL (BoringSSL) provider that netty-tcnative supplies (OpenSslTests
// checks that it loads).
class GrpcTlsHandshakeTests : StringSpec() {
  init {
    "a mutual-TLS client and server complete the handshake and the server sees the client certificate" {
      val recorder = PeerRecorder()
      echoServer(serverTls(trustClients = true), recorder).use { server ->
        server.channel(clientTls()).use { it.echo("hello") } shouldBe "echo: hello"
      }
      recorder.peers shouldBe ["CN=test-client"]
    }

    "a TLS server without client auth serves a client that presents no certificate" {
      val recorder = PeerRecorder()
      echoServer(serverTls(), recorder).use { server ->
        server.channel(clientTls(presentCertificate = false)).use { it.echo("hello") } shouldBe "echo: hello"
      }
      recorder.peers shouldBe [NO_CLIENT_CERTIFICATE]
    }

    "a mutual-TLS server rejects a client that presents no certificate" {
      val recorder = PeerRecorder()
      echoServer(serverTls(trustClients = true), recorder).use { server ->
        server.channel(clientTls(presentCertificate = false)).echoFailureCode() shouldBe Status.Code.UNAVAILABLE
      }
      recorder.peers.shouldBeEmpty()
    }

    "a mutual-TLS server rejects a client certificate it does not trust" {
      echoServer(serverTls(trustClients = true)).use { server ->
        // The server certificate is valid, but the server trusts only test-client.
        server.channel(clientTls(certName = "server")).echoFailureCode() shouldBe Status.Code.UNAVAILABLE
      }
    }

    "a client that trusts a different CA rejects the server" {
      echoServer(serverTls(trustClients = true)).use { server ->
        server.channel(clientTls(trustName = "client")).echoFailureCode() shouldBe Status.Code.UNAVAILABLE
      }
    }

    "a plaintext client cannot call a TLS server" {
      val recorder = PeerRecorder()
      echoServer(serverTls(), recorder).use { server ->
        server.channel(PLAINTEXT_CONTEXT, overrideAuthority = "").echoFailureCode() shouldBe Status.Code.UNAVAILABLE
      }
      recorder.peers.shouldBeEmpty()
    }

    "a TLS client cannot call a plaintext server" {
      echoServer(PLAINTEXT_CONTEXT).use { server ->
        server.channel(clientTls(presentCertificate = false)).echoFailureCode() shouldBe Status.Code.UNAVAILABLE
      }
    }

    "the client verifies the server host name, which overrideAuthority supplies" {
      echoServer(serverTls()).use { server ->
        // The certificate names localhost only, so dialing 127.0.0.1 without an override fails verification.
        server.channel(clientTls(presentCertificate = false), overrideAuthority = "").echoFailureCode() shouldBe
          Status.Code.UNAVAILABLE
        server.channel(clientTls(presentCertificate = false)).use { it.echo("hello") } shouldBe "echo: hello"
      }
    }

    // Netty checks neither while configuring the builder nor in build(), with the OpenSSL or the JDK provider, that
    // the key belongs to the certificate, so the mismatch surfaces only when a client connects.
    "a server key that belongs to a different certificate builds, but fails the handshake" {
      val mismatched =
        TlsUtils.buildServerTlsContext(
          certChainFilePath = tlsResourcePath("server-cert.pem"),
          privateKeyFilePath = tlsResourcePath("client-key.pem"),
        )

      echoServer(mismatched).use { server ->
        server.channel(clientTls(presentCertificate = false)).echoFailureCode() shouldBe Status.Code.UNAVAILABLE
      }
    }
  }
}
