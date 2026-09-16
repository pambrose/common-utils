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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "UndocumentedPublicProperty")

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import javax.script.ScriptContext.ENGINE_SCOPE
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptException
import kotlin.reflect.typeOf
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

// A public generic interface that generated code can name.
interface Box<T> {
  val item: T
}

// A public class with the same single type parameter.
open class BaseBox<T>(
  override val item: T,
) : Box<T>

// Private, so generated code cannot name it; its nearest nameable supertype is Box.
private class HiddenBox<T>(
  override val item: T,
) : Box<T>

// Two type parameters, and no nameable supertype with two. It reaches Box twice, through BaseBox and directly.
@Suppress("RedundantSuperType")
private class HiddenPair<A, B>(
  item: A,
  val other: B,
) : BaseBox<A>(item),
  Box<A>

private class HiddenRunnable : Runnable {
  override fun run() {
    // Never run.
  }
}

// Public, but enclosed in a private class, so generated code still cannot name it.
private class PrivateOuter {
  class PublicNested<T>(
    override val item: T,
  ) : Box<T>
}

class AbstractScriptTests : StringSpec() {
  init {
    "accessibleClass returns the value's own class when it is public" {
      FakeScript().use { script ->
        val regex = Regex("a+")
        script.add("regex", regex)
        script.accessibleClassOf("regex", regex) shouldBe Regex::class
      }
    }

    "accessibleClass returns the nearest public supertype with the registered number of type parameters" {
      FakeScript().use { script ->
        val box = HiddenBox(1)
        script.add("box", box, typeOf<Int>())
        script.accessibleClassOf("box", box) shouldBe Box::class

        val nested = PrivateOuter.PublicNested("a")
        script.add("nested", nested, typeOf<String>())
        script.accessibleClassOf("nested", nested) shouldBe Box::class
      }
    }

    "accessibleClass takes the first public supertype when no type parameters were registered" {
      FakeScript().use { script ->
        val runnable = HiddenRunnable()
        script.add("runnable", runnable)
        script.accessibleClassOf("runnable", runnable) shouldBe Runnable::class
      }
    }

    "accessibleClass falls back to Any when no public supertype has the registered number of type parameters" {
      FakeScript().use { script ->
        val pair = HiddenPair(1, "a")
        script.add("pair", pair, typeOf<Int>(), typeOf<String>())
        script.accessibleClassOf("pair", pair) shouldBe Any::class
      }
    }

    // ArrayPrimitive: an Array<Int> is the case under test.
    @Suppress("ArrayPrimitive")
    "accessibleClass returns an array's own class, whatever its element class" {
      FakeScript().use { script ->
        // Integer[] has none of the one type parameter registered for it, so this used to fall back to Any.
        val ints = arrayOf(1, 2)
        script.add("ints", ints, typeOf<Int>())
        script.accessibleClassOf("ints", ints) shouldBe Array<Int>::class

        val boxes = arrayOf(HiddenBox(1))
        script.add("boxes", boxes, typeOf<Box<Int>>())
        script.accessibleClassOf("boxes", boxes).qualifiedName shouldBe "kotlin.Array"
        script.accessibleClassOf("boxes", boxes).java shouldBe boxes.javaClass

        val primitives = intArrayOf(1, 2)
        script.add("primitives", primitives)
        script.accessibleClassOf("primitives", primitives) shouldBe IntArray::class
      }
    }

    "the default reserves no names and binds each variable in the engine scope" {
      FakeScript().use { script ->
        script.reserved("class") shouldBe false
        script.add("class", 1)
        script.eval("class") shouldBe 1
        script.fakeEngine.getBindings(ENGINE_SCOPE)["class"] shouldBe 1
      }
    }

    "a variable whose binding failed is bound by the next evaluation" {
      FakeScript().use { script ->
        val bound: MutableList<Set<String>> = []
        var failures = 1
        script.onBind = { variables ->
          bound += variables.keys
          if (failures-- > 0) throw ScriptException("binding failed")
        }
        script.add("x", 1)

        shouldThrow<ScriptException> { script.eval("x") }.message shouldContain "binding failed"
        script.eval("x") shouldBe 1
        // Nothing is left to bind, so a later evaluation binds nothing.
        script.eval("x") shouldBe 1
        bound shouldBe [setOf("x"), setOf("x")]
      }
    }

    "a variable added again replaces the earlier value" {
      FakeScript().use { script ->
        script.add("x", 1)
        script.eval("x") shouldBe 1
        script.add("x", "s")
        script.eval("x") shouldBe "s"
      }
    }

    "code is checked before variables are bound or anything is evaluated" {
      FakeScript().use { script ->
        val bound: MutableList<Set<String>> = []
        script.onBind = { bound += it.keys }
        script.add("x", 1)
        shouldThrow<ScriptException> { script.eval("System.exit(0)") }.message shouldContain
          "Illegal call to a JVM termination method"
        bound shouldBe []
        script.fakeEngine.evaluated shouldBe []
      }
    }

    "resetContext drops the variables and gives the engine a fresh context" {
      FakeScript().use { script ->
        script.add("x", 1)
        script.eval("x") shouldBe 1
        val globals = script.fakeEngine.getBindings(GLOBAL_SCOPE)

        script.resetContext(false)
        script.fakeEngine.getBindings(ENGINE_SCOPE).isEmpty() shouldBe true
        (script.fakeEngine.getBindings(GLOBAL_SCOPE) === globals) shouldBe false
        // x is no longer registered either, so it is not bound again.
        shouldThrow<ScriptException> { script.eval("x") }

        script.resetContext(true)
        script.fakeEngine.getBindings(GLOBAL_SCOPE) shouldBe null
      }
    }

    "the deprecated initialized property tracks whether an evaluation was prepared" {
      FakeScript().use { script ->
        script.preparedFlag shouldBe false
        script.eval("1") shouldBe 1
        script.preparedFlag shouldBe true
        script.resetContext(false)
        script.preparedFlag shouldBe false
        script.preparedFlag = true
        script.preparedFlag shouldBe true
      }
    }

    "a script pool resets each returned instance, honoring nullGlobalContext" {
      withTimeout(30.seconds) {
        FakeScriptPool(size = 1, nullGlobalContext = true).use { pool ->
          pool.eval {
            fakeEngine.getBindings(GLOBAL_SCOPE) shouldBe null
            add("x", 1)
            eval("x")
          } shouldBe 1
          // The same instance, recycled: its variables are gone and its global scope is still null.
          pool.eval {
            fakeEngine.getBindings(GLOBAL_SCOPE) shouldBe null
            shouldThrow<ScriptException> { eval("x") }
          }
        }

        FakeScriptPool(size = 1, nullGlobalContext = false).use { pool ->
          repeat(2) {
            pool.eval { fakeEngine.getBindings(GLOBAL_SCOPE) } shouldNotBe null
          }
        }
      }
    }
  }
}
