/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TlsContextTests : StringSpec() {
  init {
    "plaintext context" {
      val context = TlsContext.PLAINTEXT_CONTEXT
      context.sslContext shouldBe null
      context.mutualAuth shouldBe false
    }

    "plaintext desc" {
      val context = TlsContext.PLAINTEXT_CONTEXT
      context.desc() shouldBe "plaintext"
    }

    "tls context with mutual auth desc" {
      // We can't easily create a real SslContext without files, but we can test the desc logic
      val context = TlsContext(null, true)
      // With null sslContext, it should show plaintext regardless of mutualAuth
      context.desc() shouldBe "plaintext"
    }

    "tls context builder data class" {
      // Test the TlsContextBuilder data class
      val builder = TlsContextBuilder(
        builder = io.netty.handler.ssl.SslContextBuilder.forClient(),
        mutualAuth = true,
      )
      builder.mutualAuth shouldBe true
    }
  }
}
