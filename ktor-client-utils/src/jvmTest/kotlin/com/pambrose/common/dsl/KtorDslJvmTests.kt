@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.pambrose.common.dsl.KtorDsl.httpClient
import com.pambrose.common.dsl.KtorDsl.newHttpClient
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class KtorDslJvmTests : StringSpec() {
  init {
    "newHttpClient creates a working client" {
      val client = newHttpClient()
      client shouldNotBe null
      client.close()
    }

    "withHttpClient creates client when null passed" {
      val result =
        withHttpClient {
          this shouldNotBe null
          "created"
        }
      result shouldBe "created"
    }

    "httpClient creates client when null passed" {
      val result =
        httpClient { client ->
          client shouldNotBe null
          "created"
        }
      result shouldBe "created"
    }
  }
}
