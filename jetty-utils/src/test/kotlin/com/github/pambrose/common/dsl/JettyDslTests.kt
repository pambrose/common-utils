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

package com.github.pambrose.common.dsl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class JettyDslTests {
  @Test
  fun serverCreationTest() {
    val server =
      JettyDsl.server(8080) {
        // Configuration block
      }

    server shouldNotBe null
    // Server is created but not started
    server.isStarted shouldBe false
  }

  @Test
  fun servletContextHandlerCreationTest() {
    val handler =
      JettyDsl.servletContextHandler {
        contextPath = "/api"
      }

    handler shouldNotBe null
    handler.contextPath shouldBe "/api"
  }

  @Test
  fun serverWithHandlerTest() {
    val handler =
      JettyDsl.servletContextHandler {
        contextPath = "/test"
      }

    // Port 0 for ephemeral port
    val server =
      JettyDsl.server(0) {
        this.handler = handler
      }

    server.handler shouldBe handler
  }
}
