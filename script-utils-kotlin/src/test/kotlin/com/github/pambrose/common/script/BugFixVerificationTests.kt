@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

  // Bug #15: AbstractExprEvaluator.eval() unconditionally cast to Boolean
  // Before fix: engine.eval(expr) as Boolean threw ClassCastException for non-Boolean results
  // After fix: safe cast with IllegalArgumentException and meaningful error message

  @Test
  fun evalThrowsMeaningfulErrorForNonBooleanResult() {
    val evaluator = KotlinExprEvaluator()
    val exception = shouldThrow<IllegalArgumentException> {
      evaluator.eval("1 + 2")
    }
    exception.message shouldContain "Boolean"
  }

  @Test
  fun evalThrowsMeaningfulErrorForStringResult() {
    val evaluator = KotlinExprEvaluator()
    val exception = shouldThrow<IllegalArgumentException> {
      evaluator.eval("\"hello\"")
    }
    exception.message shouldContain "Boolean"
  }

  @Test
  fun evalStillWorksForBooleanExpressions() {
    val evaluator = KotlinExprEvaluator()
    evaluator.eval("true") shouldBe true
    evaluator.eval("false") shouldBe false
    evaluator.eval("1 > 0") shouldBe true
    evaluator.eval("1 < 0") shouldBe false
  }
}
