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

// DEPRECATION: reads the deprecated public engine to compare global bindings.
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "DEPRECATION")

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptException

private const val JVM_EXIT_MESSAGE = "Illegal call to a JVM termination method"

class ExprEvaluatorTests : StringSpec() {
  init {
    // Each termination call sits in a lambda that is never invoked, so an unguarded evaluator returns instead of
    // terminating the test JVM.
    "evaluators reject literal JVM termination calls" {
      val evaluator = KotlinExprEvaluator()
      shouldThrow<ScriptException> { evaluator.eval("{ kotlin.system.exitProcess(0) } != null") }.message shouldContain
        JVM_EXIT_MESSAGE
      shouldThrow<ScriptException> { evaluator.compute("{ System.exit(0) }") }.message shouldContain JVM_EXIT_MESSAGE
      KotlinExprEvaluatorPool(1).use { pool ->
        shouldThrow<ScriptException> {
          pool.blockingEval("{ Runtime.getRuntime().halt(0) } != null")
        }.message shouldContain
          JVM_EXIT_MESSAGE
      }
    }

    "resetting an evaluator discards its REPL history" {
      KotlinExprEvaluator().use { evaluator ->
        evaluator.compute("val y = 1")
        evaluator.compute("y") shouldBe 1
        evaluator.resetContext()
        shouldThrow<ScriptException> { evaluator.compute("y") }
      }
    }

    "each evaluator has its own global bindings" {
      val first = KotlinExprEvaluator()
      val second = KotlinExprEvaluator()
      (first.engine.getBindings(GLOBAL_SCOPE) === second.engine.getBindings(GLOBAL_SCOPE)) shouldBe false
    }
  }
}
