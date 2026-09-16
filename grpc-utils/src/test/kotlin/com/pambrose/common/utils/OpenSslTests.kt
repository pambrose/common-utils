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

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.netty.handler.ssl.OpenSsl
import io.netty.handler.ssl.OpenSslContext

// netty-tcnative-boringssl-static ships its native libraries as per-platform classifier jars, which its POM declares
// as dependencies on itself. Gradle drops those, so grpc-utils declares them explicitly. Without them OpenSSL never
// loads and Netty silently falls back to the JDK TLS provider.
class OpenSslTests : StringSpec() {
  init {
    "netty-tcnative's BoringSSL loads on this platform" {
      withClue({ "OpenSSL is unavailable: ${OpenSsl.unavailabilityCause()}" }) {
        OpenSsl.isAvailable() shouldBe true
      }
    }

    "the TLS contexts TlsUtils builds use OpenSSL" {
      val server =
        TlsUtils.buildServerTlsContext(
          certChainFilePath = tlsResourcePath("server-cert.pem"),
          privateKeyFilePath = tlsResourcePath("server-key.pem"),
        )
      server.sslContext.shouldBeInstanceOf<OpenSslContext>()

      val client = TlsUtils.buildClientTlsContext(trustCertCollectionFilePath = tlsResourcePath("server-cert.pem"))
      client.sslContext.shouldBeInstanceOf<OpenSslContext>()
    }
  }
}
