@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.dsl

import com.pambrose.common.dsl.KtorDsl.withHttpClient
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf

class KtorDslNativeTests : StringSpec() {
  init {
    // Kotlin/Native wraps Ktor's IllegalStateException in an (internal) file-initialization error, and only the
    // first call carries it as the cause; later calls rethrow the wrapper alone. So keep this the only test in the
    // native binary that makes a client, which is why the common client-creating tests are disabled here.
    "withHttpClient fails without a client engine dependency" {
      var ran = false
      val e =
        shouldThrowAny {
          withHttpClient { ran = true }
        }
      val rootCause = generateSequence(e) { it.cause }.last()
      rootCause.shouldBeInstanceOf<IllegalStateException>()
      rootCause.message shouldStartWith "Failed to find HTTP client engine implementation"
      ran shouldBe false
    }
  }
}
