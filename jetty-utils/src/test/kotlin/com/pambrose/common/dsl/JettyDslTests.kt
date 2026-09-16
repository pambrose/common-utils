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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.eclipse.jetty.server.ServerConnector

class JettyDslTests : StringSpec() {
  init {
    "server creation configures the port without starting the server" {
      val server = JettyDsl.server(8080)

      server.connectors.single().shouldBeInstanceOf<ServerConnector>().port shouldBe 8080
      server.isStarted shouldBe false
    }

    "servlet context handler creation" {
      val handler =
        JettyDsl.servletContextHandler {
          contextPath = "/api"
        }

      handler.contextPath shouldBe "/api"
    }

    "server with handler" {
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

    "the builders need no block" {
      JettyDsl.server(0).handler shouldBe null
      JettyDsl.servletContextHandler().contextPath shouldBe "/"
    }
  }
}
