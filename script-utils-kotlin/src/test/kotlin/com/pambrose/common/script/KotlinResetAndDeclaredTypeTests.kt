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

// DEPRECATION: the REPL-state test reads the deprecated public engine.
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "DEPRECATION")

package com.pambrose.common.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.lang.ref.WeakReference
import java.util.function.IntSupplier
import java.util.function.Supplier
import java.util.stream.IntStream
import javax.script.ScriptContext.ENGINE_SCOPE
import kotlin.reflect.typeOf

// How a generated declaration reads its variable back from the ScriptVariables holder.
private const val VARIABLES = """(bindings["__variables"] as com.pambrose.common.script.ScriptVariables)"""

// The engine-scope binding that holds the Kotlin engine's REPL state.
private const val REPL_STATE_BINDING = "kotlin.script.state"

private const val GC_ATTEMPTS = 100
private const val GC_PAUSE_MILLIS = 50L

// A script cannot name an inner class of a generic class without the outer class's type argument.
class GenericHolder<T> {
  inner class Answer : IntSupplier {
    override fun getAsInt() = 42
  }

  inner class Wrapped<U>(
    private val item: U,
  ) : Supplier<U> {
    override fun get() = item
  }
}

// Requests garbage collection until this reference is cleared, and returns whether it was.
@Suppress("ExplicitGarbageCollectionCall")
private fun WeakReference<*>.isClearedAfterGc(): Boolean {
  repeat(GC_ATTEMPTS) {
    System.gc()
    if (get() == null) return true
    Thread.sleep(GC_PAUSE_MILLIS)
  }
  return false
}

// Binds a new object in script and evaluates it, returning only a weak reference to it, so that no frame of the caller
// keeps it reachable.
private fun bindAndEvaluate(script: KotlinScript): WeakReference<Any> {
  val variable = StringBuilder("held")
  script.add("variable", variable)
  script.eval("variable.length") shouldBe 4
  return WeakReference(variable)
}

// Evaluates an expression, returning a weak reference to the REPL state it ran in.
private fun replStateAfterEvaluating(evaluator: KotlinExprEvaluator): WeakReference<Any> {
  evaluator.eval("1 < 2") shouldBe true
  return WeakReference(evaluator.engine.getBindings(ENGINE_SCOPE)[REPL_STATE_BINDING].shouldNotBeNull())
}

/**
 * Tests, on the real Kotlin engine, for script-utils-common's context reset and declared types: a reset releases what
 * the replaced context held, and a value is declared as a type a script can name.
 */
class KotlinResetAndDeclaredTypeTests : StringSpec() {
  init {
    releaseTests()
    declaredTypeTests()
  }

  private fun releaseTests() {
    // The engine keeps the context of its last evaluation until the next one, so a reset used to leave the previous
    // variables, and the REPL state that declared them, reachable for as long as the script sat idle.
    "resetContext releases the variables of the context it replaces" {
      KotlinScript().use { script ->
        val variable = bindAndEvaluate(script)
        script.resetContext(false)
        variable.isClearedAfterGc() shouldBe true
        script.eval("1 + 1") shouldBe 2
      }
    }

    "an evaluator's resetContext releases the REPL state of the context it replaces" {
      KotlinExprEvaluator().use { evaluator ->
        val state = replStateAfterEvaluating(evaluator)
        evaluator.resetContext()
        state.isClearedAfterGc() shouldBe true
        evaluator.eval("2 > 1") shouldBe true
      }
    }
  }

  private fun declaredTypeTests() {
    // IntStream.of(1, 2) is a package-private IntPipeline.Head<E_IN>, so adding it without a type argument used to
    // throw.
    "an IntStream is declared as an IntStream" {
      KotlinScript().use { script ->
        script.add("ints", IntStream.of(1, 2))
        script.varDecls shouldBe """val ints = $VARIABLES["ints"] as java.util.stream.IntStream"""
        script.eval("ints.sum()") shouldBe 3
      }
    }

    // It used to be cast to GenericHolder.Answer, which does not compile without GenericHolder's type argument.
    "an inner class of a generic class is declared as a supertype the script can name" {
      KotlinScript().use { script ->
        script.add("answer", GenericHolder<Int>().Answer())
        script.add("wrapped", GenericHolder<Int>().Wrapped("abc"), typeOf<String>())
        script.eval("answer.asInt + wrapped.get().length") shouldBe 45
        script.varDecls shouldBe
          """
          |val answer = $VARIABLES["answer"] as java.util.function.IntSupplier
          |val wrapped = $VARIABLES["wrapped"] as java.util.function.Supplier<kotlin.String>
          """.trimMargin()
      }
    }
  }
}
