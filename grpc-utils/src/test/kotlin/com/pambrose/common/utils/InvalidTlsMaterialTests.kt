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

package com.pambrose.common.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.netty.handler.ssl.OpenSsl
import java.io.File
import javax.net.ssl.SSLException

private const val NOT_CERTIFICATES = "does not contain valid certificates"
private const val NOT_A_KEY = "does not contain valid private key"

// Files that exist but hold the wrong content. Netty rejects most of them while TlsUtils configures the builder, so
// they fail as IllegalArgumentException before build() runs; a chain that does not link up gets past the builder
// and fails in build() as SSLException. The tests run on the JDK TLS provider (see GrpcTlsHandshakeTests).
class InvalidTlsMaterialTests : StringSpec() {
  init {
    fun pemFile(content: String): String = tempfile(suffix = ".pem").apply { writeText(content) }.absolutePath

    val garbageCertificate = "-----BEGIN CERTIFICATE-----\nnot base64 at all!\n-----END CERTIFICATE-----\n"

    "a server certificate and key given in each other's place are rejected by the builder" {
      val ex =
        shouldThrow<IllegalArgumentException> {
          TlsUtils.serverTlsContext(
            certChainFilePath = tlsResourcePath("server-key.pem"),
            privateKeyFilePath = tlsResourcePath("server-cert.pem"),
          )
        }
      ex.message shouldContain NOT_CERTIFICATES
      ex.message shouldContain "server-key.pem"
    }

    "a client certificate and key given in each other's place are rejected by the builder" {
      val ex =
        shouldThrow<IllegalArgumentException> {
          TlsUtils.clientTlsContextBuilder(
            certChainFilePath = tlsResourcePath("client-key.pem"),
            privateKeyFilePath = tlsResourcePath("client-cert.pem"),
            trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"),
          )
        }
      ex.message shouldContain NOT_CERTIFICATES
      ex.message shouldContain "client-key.pem"
    }

    "a garbage certificate chain is rejected by the server builder" {
      val path = pemFile(garbageCertificate)
      val ex =
        shouldThrow<IllegalArgumentException> {
          TlsUtils.serverTlsContext(certChainFilePath = path, privateKeyFilePath = tlsResourcePath("server-key.pem"))
        }
      ex.message shouldContain NOT_CERTIFICATES
      ex.message shouldContain path
    }

    "a garbage trust collection is rejected by the server and client builders" {
      val path = pemFile(garbageCertificate)

      shouldThrow<IllegalArgumentException> {
        TlsUtils.serverTlsContext(
          certChainFilePath = tlsResourcePath("server-cert.pem"),
          privateKeyFilePath = tlsResourcePath("server-key.pem"),
          trustCertCollectionFilePath = path,
        )
      }.message shouldContain NOT_CERTIFICATES

      shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder(trustCertCollectionFilePath = path)
      }.message shouldContain NOT_CERTIFICATES
    }

    "a file without any PEM block is rejected as a certificate and as a key" {
      val path = pemFile("hello, world\n")

      shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder(trustCertCollectionFilePath = path)
      }.message shouldContain NOT_CERTIFICATES

      val ex =
        shouldThrow<IllegalArgumentException> {
          TlsUtils.serverTlsContext(certChainFilePath = tlsResourcePath("server-cert.pem"), privateKeyFilePath = path)
        }
      ex.message shouldContain NOT_A_KEY
      ex.message shouldContain path
    }

    // The API takes no key password, so an encrypted key can never be read. The error names the file, not the
    // missing password.
    "an encrypted private key is rejected with an error naming the file" {
      val encryptedKey = tlsResourcePath("client-key-encrypted.pem")
      File(encryptedKey).readLines().first() shouldBe "-----BEGIN ENCRYPTED PRIVATE KEY-----"

      val clientError =
        shouldThrow<IllegalArgumentException> {
          TlsUtils.buildClientTlsContext(
            certChainFilePath = tlsResourcePath("client-cert.pem"),
            privateKeyFilePath = encryptedKey,
          )
        }
      clientError.message shouldBe "File does not contain valid private key: $encryptedKey"
      clientError.cause.shouldNotBeNull()

      shouldThrow<IllegalArgumentException> {
        TlsUtils.serverTlsContext(
          certChainFilePath = tlsResourcePath("client-cert.pem"),
          privateKeyFilePath = encryptedKey,
        )
      }.message shouldBe "File does not contain valid private key: $encryptedKey"
    }

    // The documented @Throws(SSLException) path: the certificates parse, so the builder accepts them, but the JDK
    // key store refuses a chain in which one certificate is not issued by the next.
    "a certificate chain that does not link up passes the builder but fails build() with SSLException" {
      withClue("OpenSSL is loaded: re-check how it treats a broken chain before relying on this test") {
        OpenSsl.isAvailable() shouldBe false
      }
      val serverCert = File(tlsResourcePath("server-cert.pem")).readText()
      val clientCert = File(tlsResourcePath("client-cert.pem")).readText()
      val serverChain = pemFile(serverCert + clientCert)
      val clientChain = pemFile(clientCert + serverCert)

      TlsUtils.serverTlsContext(serverChain, tlsResourcePath("server-key.pem")).mutualAuth shouldBe false

      shouldThrow<SSLException> {
        TlsUtils.buildServerTlsContext(serverChain, tlsResourcePath("server-key.pem"))
      }.message shouldContain "server-side SSL context"

      shouldThrow<SSLException> {
        TlsUtils.buildClientTlsContext(
          certChainFilePath = clientChain,
          privateKeyFilePath = tlsResourcePath("client-key.pem"),
          trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"),
        )
      }.message shouldContain "client-side SSL context"
    }
  }
}
