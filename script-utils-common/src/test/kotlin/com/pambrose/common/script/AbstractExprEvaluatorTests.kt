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
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptException

// Keeps the default code check, which FakeEvaluator replaces.
private class GuardedFakeEvaluator : AbstractExprEvaluator(FAKE_EXTENSION) {
  val fakeEngine get() = scriptEngine as FakeScriptEngine
}

class AbstractExprEvaluatorTests : StringSpec() {
  init {
    "eval returns a Boolean result" {
      FakeEvaluator().use { evaluator ->
        evaluator.eval("true") shouldBe true
        evaluator.eval("false") shouldBe false
      }
    }

    "eval names the type of a result that is not a Boolean" {
      FakeEvaluator().use { evaluator ->
        shouldThrow<IllegalArgumentException> { evaluator.eval("null") }.message shouldBe
          "Expression did not evaluate to Boolean, got null"
        shouldThrow<IllegalArgumentException> { evaluator.eval("3") }.message shouldBe
          "Expression did not evaluate to Boolean, got Integer"
        shouldThrow<IllegalArgumentException> { evaluator.eval("\"true\"") }.message shouldBe
          "Expression did not evaluate to Boolean, got String"
      }
    }

    "compute returns any result, including null" {
      FakeEvaluator().use { evaluator ->
        evaluator.compute("3") shouldBe 3
        evaluator.compute("\"s\"") shouldBe "s"
        evaluator.compute("null") shouldBe null
      }
    }

    "the default code check rejects JVM termination calls before evaluating them" {
      GuardedFakeEvaluator().use { evaluator ->
        shouldThrow<ScriptException> { evaluator.compute("System.exit(0)") }.message shouldBe
          "Illegal call to a JVM termination method (System.exit / exitProcess / Runtime.exit / Runtime.halt)"
        shouldThrow<ScriptException> { evaluator.eval("Runtime.getRuntime().halt(0)") }
        evaluator.fakeEngine.evaluated shouldBe []
        evaluator.eval("true") shouldBe true
      }
    }

    "each evaluator has global bindings of its own, which resetContext can null" {
      GuardedFakeEvaluator().use { first ->
        GuardedFakeEvaluator().use { second ->
          val firstGlobals = first.fakeEngine.getBindings(GLOBAL_SCOPE)
          firstGlobals shouldNotBe null
          (firstGlobals === second.fakeEngine.getBindings(GLOBAL_SCOPE)) shouldBe false

          first.resetContext(nullGlobalContext = true)
          first.fakeEngine.getBindings(GLOBAL_SCOPE) shouldBe null
          first.resetContext()
          first.fakeEngine.getBindings(GLOBAL_SCOPE) shouldNotBe null
        }
      }
    }

    "an evaluator pool resets each evaluator it gets back" {
      FakeEvaluatorPool(size = 1).use { pool ->
        val evaluator = pool.created.single()
        val before = evaluator.fakeEngine.context
        pool.blockingEval("true") shouldBe true
        (evaluator.fakeEngine.context === before) shouldBe false
      }
    }
  }
}
