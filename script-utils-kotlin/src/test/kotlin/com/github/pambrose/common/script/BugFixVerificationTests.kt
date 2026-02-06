@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import javax.script.ScriptException

class BugFixVerificationTests {
  // Bug #6: resetContext() did not clear valueMap/typeMap, leaking state between pool users
  // Before fix: resetContext only reset initialized flag and engine bindings
  // After fix: resetContext also clears valueMap and typeMap

  @Test
  fun resetContextClearsVariables() {
    KotlinScript().use { script ->
      script.apply {
        // Add a variable and verify it works
        add("x", 42)
        eval("x") shouldBe 42

        // Reset the context — should clear valueMap
        resetContext(false)

        // After reset, "x" should no longer be bound
        // Evaluating code that references "x" should fail
        shouldThrow<ScriptException> { eval("x") }
      }
    }
  }

  @Test
  fun resetContextAllowsNewVariablesAfterReset() {
    KotlinScript().use { script ->
      script.apply {
        add("a", 10)
        eval("a") shouldBe 10

        resetContext(false)

        // Add a different variable after reset
        add("b", 20)
        eval("b") shouldBe 20

        // Old variable should not be accessible
        shouldThrow<ScriptException> { eval("a") }
      }
    }
  }
}
