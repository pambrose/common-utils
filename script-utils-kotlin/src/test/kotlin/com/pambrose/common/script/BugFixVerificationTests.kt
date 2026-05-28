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

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptException

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #6: resetContext() did not clear valueMap/typeMap, leaking state between pool users
    // Before fix: resetContext only reset initialized flag and engine bindings
    // After fix: resetContext also clears valueMap and typeMap

    "reset context clears variables" {
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

    "reset context allows new variables after reset" {
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

    "eval throws meaningful error for non boolean result" {
      val evaluator = KotlinExprEvaluator()
      val exception = shouldThrow<IllegalArgumentException> {
        evaluator.eval("1 + 2")
      }
      exception.message shouldContain "Boolean"
    }

    "eval throws meaningful error for string result" {
      val evaluator = KotlinExprEvaluator()
      val exception = shouldThrow<IllegalArgumentException> {
        evaluator.eval("\"hello\"")
      }
      exception.message shouldContain "Boolean"
    }

    "eval still works for boolean expressions" {
      val evaluator = KotlinExprEvaluator()
      evaluator.eval("true") shouldBe true
      evaluator.eval("false") shouldBe false
      evaluator.eval("1 > 0") shouldBe true
      evaluator.eval("1 < 0") shouldBe false
    }

    // Bug #3: AbstractExprEvaluator.compute() unconditionally cast to non-null Any
    // Before fix: engine.eval(expr) as Any threw when the expression evaluated to null
    // After fix: compute() returns Any? and propagates a null result

    "compute returns null for a null-evaluating expression" {
      val evaluator = KotlinExprEvaluator()
      evaluator.compute("null") shouldBe null
    }

    "compute returns non-null results" {
      val evaluator = KotlinExprEvaluator()
      evaluator.compute("1 + 2") shouldBe 3
      evaluator.compute("\"hello\"") shouldBe "hello"
    }

    // Bug #4: AbstractExprEvaluatorPool.eval()/blockingEval() were generic (<R> ... as R)
    // even though the underlying evaluator only ever returns Boolean.
    // Before fix: blockingEval<String>(expr) compiled but threw ClassCastException at the call site.
    // After fix: eval()/blockingEval() return Boolean, so the result is usable directly.

    "pool blockingEval returns boolean results" {
      val pool = KotlinExprEvaluatorPool(2)
      val result: Boolean = pool.blockingEval("1 > 0")
      result shouldBe true
      pool.blockingEval("1 < 0") shouldBe false
    }

    // Bug #5: script pools created their instances with KotlinScript() instead of
    // KotlinScript(nullGlobalContext), so the pool's nullGlobalContext was ignored for the
    // initial population (only honored later, on recycle).
    // Before fix: a pool created with nullGlobalContext=true still had non-null global bindings
    // on the first borrow. After fix: the initial instances honor the flag.

    "pool honors nullGlobalContext for initial population" {
      runBlocking {
        KotlinScriptPool(2, nullGlobalContext = true).eval {
          engine.getBindings(GLOBAL_SCOPE)
        } shouldBe null

        KotlinScriptPool(2, nullGlobalContext = false).eval {
          engine.getBindings(GLOBAL_SCOPE)
        } shouldNotBe null
      }
    }
  }
}
