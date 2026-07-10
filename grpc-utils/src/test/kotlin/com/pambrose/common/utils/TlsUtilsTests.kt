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
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class TlsUtilsTests : StringSpec() {
  init {
    "buildClientTlsContext requires non-empty trustCertCollectionFilePath" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.buildClientTlsContext()
      }
      ex.message shouldContain "trustCertCollectionFilePath is required"
    }

    "buildClientTlsContext rejects missing trust file" {
      val missing = File("/tmp/does-not-exist-${System.nanoTime()}.pem").absolutePath
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.buildClientTlsContext(trustCertCollectionFilePath = missing)
      }
      ex.message shouldContain "does not exist"
    }

    "clientTlsContextBuilder requires non-empty trustCertCollectionFilePath" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder()
      }
      ex.message shouldContain "trustCertCollectionFilePath is required"
    }

    "buildServerTlsContext requires certChainFilePath" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.buildServerTlsContext(
          certChainFilePath = "",
          privateKeyFilePath = "",
        )
      }
      ex.message shouldContain "certChainFilePath is required"
    }

    "buildServerTlsContext requires privateKeyFilePath" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.buildServerTlsContext(
          certChainFilePath = "/nope/cert.pem",
          privateKeyFilePath = "",
        )
      }
      ex.message shouldContain "privateKeyFilePath is required"
    }

    "serverTlsContext rejects missing cert file" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.serverTlsContext(
          certChainFilePath = "/tmp/missing-cert-${System.nanoTime()}.pem",
          privateKeyFilePath = "/tmp/missing-key-${System.nanoTime()}.pem",
        )
      }
      ex.message shouldContain "does not exist"
    }

    "serverTlsContext requires both cert and key when blank-trimmed values are passed" {
      val whitespaceOnly = "   "
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.serverTlsContext(
          certChainFilePath = whitespaceOnly,
          privateKeyFilePath = whitespaceOnly,
        )
      }
      ex.message shouldContain "certChainFilePath is required"
    }

    "buildClientTlsContext builds a TLS context from a trust cert file" {
      val context = TlsUtils.buildClientTlsContext(trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"))
      context.sslContext.shouldNotBeNull().isClient shouldBe true
      context.mutualAuth shouldBe false
      context.desc() shouldBe "TLS (no mutual auth)"
    }

    "buildClientTlsContext with cert and key enables mutual auth" {
      val context = TlsUtils.buildClientTlsContext(
        certChainFilePath = tlsResourcePath("client-cert.pem"),
        privateKeyFilePath = tlsResourcePath("client-key.pem"),
        trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"),
      )
      context.sslContext.shouldNotBeNull().isClient shouldBe true
      context.mutualAuth shouldBe true
      context.desc() shouldBe "TLS with mutual auth"
    }

    "clientTlsContextBuilder returns a buildable builder without mutual auth for trust-only config" {
      val result = TlsUtils.clientTlsContextBuilder(trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"))
      result.mutualAuth shouldBe false
      result.builder.build().isClient shouldBe true
    }

    "clientTlsContextBuilder requires privateKeyFilePath when certChainFilePath is specified" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder(
          certChainFilePath = tlsResourcePath("client-cert.pem"),
          trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"),
        )
      }
      ex.message shouldContain "privateKeyFilePath required if certChainFilePath specified"
    }

    "clientTlsContextBuilder requires certChainFilePath when privateKeyFilePath is specified" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder(
          privateKeyFilePath = tlsResourcePath("client-key.pem"),
          trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"),
        )
      }
      ex.message shouldContain "certChainFilePath required if privateKeyFilePath specified"
    }

    "clientTlsContextBuilder rejects missing certChain file" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder(
          certChainFilePath = "/tmp/missing-cert-${System.nanoTime()}.pem",
          privateKeyFilePath = tlsResourcePath("client-key.pem"),
          trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"),
        )
      }
      ex.message shouldContain "does not exist"
    }

    "clientTlsContextBuilder rejects missing privateKey file" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder(
          certChainFilePath = tlsResourcePath("client-cert.pem"),
          privateKeyFilePath = "/tmp/missing-key-${System.nanoTime()}.pem",
          trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"),
        )
      }
      ex.message shouldContain "does not exist"
    }

    "clientTlsContextBuilder rejects a directory as trust path" {
      val directory = File(tlsResourcePath("server-cert.pem")).parentFile.absolutePath
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.clientTlsContextBuilder(trustCertCollectionFilePath = directory)
      }
      ex.message shouldContain "does not exist"
    }

    "buildServerTlsContext builds a TLS context without mutual auth" {
      val context = TlsUtils.buildServerTlsContext(
        certChainFilePath = tlsResourcePath("server-cert.pem"),
        privateKeyFilePath = tlsResourcePath("server-key.pem"),
      )
      context.sslContext.shouldNotBeNull().isServer shouldBe true
      context.mutualAuth shouldBe false
      context.desc() shouldBe "TLS (no mutual auth)"
    }

    "buildServerTlsContext with trust collection enables mutual auth" {
      val context = TlsUtils.buildServerTlsContext(
        certChainFilePath = tlsResourcePath("server-cert.pem"),
        privateKeyFilePath = tlsResourcePath("server-key.pem"),
        trustCertCollectionFilePath = tlsResourcePath("client-cert.pem"),
      )
      context.sslContext.shouldNotBeNull().isServer shouldBe true
      context.mutualAuth shouldBe true
      context.desc() shouldBe "TLS with mutual auth"
    }

    "serverTlsContext returns a buildable builder without mutual auth when no trust file is given" {
      val result = TlsUtils.serverTlsContext(
        certChainFilePath = tlsResourcePath("server-cert.pem"),
        privateKeyFilePath = tlsResourcePath("server-key.pem"),
      )
      result.mutualAuth shouldBe false
      result.builder.build().isServer shouldBe true
    }

    "serverTlsContext rejects missing privateKey file" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.serverTlsContext(
          certChainFilePath = tlsResourcePath("server-cert.pem"),
          privateKeyFilePath = "/tmp/missing-key-${System.nanoTime()}.pem",
        )
      }
      ex.message shouldContain "does not exist"
    }

    "serverTlsContext rejects missing trust file" {
      val ex = shouldThrow<IllegalArgumentException> {
        TlsUtils.serverTlsContext(
          certChainFilePath = tlsResourcePath("server-cert.pem"),
          privateKeyFilePath = tlsResourcePath("server-key.pem"),
          trustCertCollectionFilePath = "/tmp/missing-trust-${System.nanoTime()}.pem",
        )
      }
      ex.message shouldContain "does not exist"
    }

    "TlsContext desc reflects mutual-auth flag for non-null sslContext" {
      val mutual = TlsContext(sslContext = stubSslContext(), mutualAuth = true)
      mutual.desc() shouldBe "TLS with mutual auth"

      val noMutual = TlsContext(sslContext = stubSslContext(), mutualAuth = false)
      noMutual.desc() shouldBe "TLS (no mutual auth)"
    }

    "TlsContext desc returns plaintext when sslContext is null regardless of mutualAuth" {
      TlsContext(null, false).desc() shouldBe "plaintext"
      TlsContext(null, true).desc() shouldBe "plaintext"
    }

    "PLAINTEXT_CONTEXT singleton equality" {
      TlsContext.PLAINTEXT_CONTEXT shouldBe TlsContext(null, false)
    }

    "TlsContext copy preserves invariants" {
      val original = TlsContext.PLAINTEXT_CONTEXT
      val copy = original.copy(mutualAuth = true)
      copy.sslContext shouldBe null
      copy.mutualAuth shouldBe true
      original.mutualAuth shouldBe false
    }
  }
}

private fun stubSslContext(): io.netty.handler.ssl.SslContext = io.mockk.mockk(relaxed = true)

private fun tlsResourcePath(name: String): String {
  val url = TlsUtilsTests::class.java.classLoader.getResource("tls/$name")
    ?: error("Missing test resource: tls/$name")
  return File(url.toURI()).absolutePath
}
