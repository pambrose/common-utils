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

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Properties
import javax.script.ScriptException
import kotlin.reflect.typeOf

// How a generated declaration reads its variable back from the ScriptVariables holder.
private const val HOLDER = """(bindings["__variables"] as com.pambrose.common.script.ScriptVariables)"""

class IncClass(
  var i: Int = 0,
) {
  fun inc() {
    i++
  }
}

// Two type parameters, and no public supertype with two, so a script can only cast it to Any. Being internal, it is
// not a class a script can name either.
internal class InternalPair<A, B>(
  val first: A,
  val second: B,
) : AbstractList<A>() {
  override val size get() = 1

  override fun get(index: Int) = first
}

class KotlinScriptTests : StringSpec() {
  init {
    "built in types" {
      val boolVal = true
      val intVal = 0
      val longVal = 0L
      val doubleVal = 0.0
      val floatVal = 0.0F
      val strVal = "A String"

      KotlinScript().use {
        it.apply {
          add("boolVal", boolVal)
          add("intVal", intVal)
          add("longVal", longVal)
          add("doubleVal", doubleVal)
          add("floatVal", floatVal)
          add("strVal", strVal)

          boolVal shouldBe eval("boolVal")
          !boolVal shouldBe eval("!boolVal")

          intVal shouldBe eval("intVal")
          intVal + 1 shouldBe eval("intVal + 1")

          longVal shouldBe eval("longVal")
          longVal + 1 shouldBe eval("longVal + 1")

          doubleVal shouldBe eval("doubleVal")
          doubleVal + 1 shouldBe eval("doubleVal + 1")

          floatVal shouldBe eval("floatVal")
          floatVal + 1 shouldBe eval("floatVal + 1")

          strVal shouldBe eval("strVal")
          strVal.length shouldBe eval("strVal.length")
        }
      }
    }

    @Suppress("VarCouldBeVal")
    "val var" {
      val intVal = 0
      var intVar = 0

      KotlinScript().use {
        it.apply {
          add("intVal", intVal)
          add("intVar", intVar)

          intVal shouldBe eval("intVal")
          intVal + 1 shouldBe eval("intVal + 1")
          intVal shouldBe 0

          intVar shouldBe eval("intVar")
          intVar + 1 shouldBe eval("intVar + 1")
          intVar shouldBe 0
        }
      }
    }

    "user object" {
      val aux = IncClass()

      KotlinScript().use {
        it.apply {
          add("aux", aux)

          aux.i shouldBe eval("aux.i")

          val incEd =
            eval(
              """
                repeat(100) { aux.inc() }
                aux
              """,
            ) as IncClass

          aux.i shouldBe 100
          aux.i shouldBe incEd.i
        }
      }
    }

    "object with types" {
      val list: MutableList<Int> = [1]
      val map = mutableMapOf("k1" to 1)

      KotlinScript().use {
        it.apply {
          add("list", list, typeOf<Int>())
          add("map", map, typeOf<String>(), typeOf<Int>())

          list.size shouldBe eval("list.size")
          map.size shouldBe eval("map.size")

          val incEd =
            eval(
              """
                map["k2"] = 10
                repeat(100) { list.add(it) }
                list
              """,
            ) as List<*>

          list.size shouldBe 101
          list.size shouldBe eval("list.size")
          list.size shouldBe incEd.size

          map.size shouldBe eval("map.size")
          map.size shouldBe 2
          map["k2"] shouldBe 10
        }
      }
    }

    "object with KType" {
      val list: MutableList<Int> = [1]

      KotlinScript().use {
        it.apply {
          add("list", list, typeOf<Int>())

          list.size shouldBe eval("list.size")

          val incEd =
            eval(
              """
                repeat(100) { list.add(it) }
                list
              """,
            ) as List<*>

          list.size shouldBe 101
          list.size shouldBe eval("list.size")
          list.size shouldBe incEd.size
        }
      }
    }

    "null object" {
      val list: MutableList<Int?> = []

      KotlinScript().use {
        it.apply {
          add("list", list, typeOf<Int?>())

          varDecls shouldBe
            "val list = $HOLDER[\"list\"] as java.util.ArrayList<kotlin.Int?>"

          list.size shouldBe eval("list.size")

          val incEd =
            eval(
              """
                repeat(100) { list.add(null) }
                list
              """,
            ) as List<*>

          list.size shouldBe 100
          list.size shouldBe eval("list.size")
          list.size shouldBe incEd.size
        }
      }
    }

    "list compare" {
      KotlinScript().use {
        it.apply {
          eval("listOf(1,2,3) == listOf(1, 2, 3)") shouldBe true
          eval("listOf(1,2) == listOf(1, 2, 3)") shouldBe false

          eval("listOf(true,true) == listOf(true, true)") shouldBe true
          eval("listOf(true,false) == listOf(true, true)") shouldBe false

          eval("""listOf("aaa","bbb") == listOf("aaa", "bbb")""") shouldBe true
          eval("""listOf("aaa","bbb") == listOf("aaa", "aaa")""") shouldBe false
        }
      }
    }

    "inner class" {
      class InnerTest

      val inner = InnerTest()

      KotlinScript().use { script ->
        shouldThrow<ScriptException> { script.add("inner", inner) }
      }
    }

    "unnecessary params" {
      val value = 5

      KotlinScript().use { script ->
        shouldThrow<ScriptException> { script.add("value", value, typeOf<Int?>()) }
      }
    }

    "unmatched params" {
      val list: MutableList<Int> = [1]

      KotlinScript().use { script ->
        shouldThrow<ScriptException> {
          script.add(name = "list", list, typeOf<Int?>(), typeOf<Int>())
        }
      }
    }

    "missing collection type" {
      val list: MutableList<Int> = [1]

      KotlinScript().use { script ->
        shouldThrow<ScriptException> { script.add("list", list) }
      }
    }

    "invalid syntax" {
      KotlinScript().use { script ->
        shouldThrow<ScriptException> { script.eval("junk") }
      }
    }

    "JVM termination calls are rejected before evaluation" {
      // Each call sits in a lambda that is never invoked, so without the guard the script would return the lambda,
      // failing the test instead of terminating the test JVM. The guard's own message tells its rejection apart from
      // a compile error, such as the unresolved reference a bare exitProcess would otherwise be.
      KotlinScript().use { script ->
        listOf(
          "{ System.exit(1) }",
          "{ java.lang.System.exit(1) }",
          "{ exitProcess(0) }",
          "{ kotlin.system.exitProcess(0) }",
          "{ Runtime.getRuntime().exit(0) }",
          "{ Runtime.getRuntime().halt(0) }",
        ).forEach { code ->
          shouldThrow<ScriptException> { script.eval(code) }.message shouldContain JVM_EXIT_MESSAGE
        }
      }
    }

    "expr evaluator keeps working after failed expressions" {
      KotlinExprEvaluator().use { evaluator ->
        repeat(ITERATIONS) { i ->
          shouldThrow<ScriptException> { evaluator.eval("$i == [wrong]") }
          evaluator.eval("$i == $i") shouldBe true
          evaluator.eval("$i == ${i + 1}") shouldBe false
        }
      }
    }

    "compute evaluator" {
      KotlinExprEvaluator().use { evaluator ->
        repeat(ITERATIONS) { i ->
          evaluator.compute("$i * $i") shouldBe i * i
        }
      }
    }

    "pool expr evaluator keeps working after failed expressions" {
      KotlinExprEvaluatorPool(2).use { pool ->
        repeat(ITERATIONS) { i ->
          shouldThrow<ScriptException> { pool.blockingEval("$i == [wrong]") }
          pool.blockingEval("$i == $i") shouldBe true
          pool.blockingEval("$i == ${i + 1}") shouldBe false
        }
      }
    }

    "java.lang.System stays usable in scripts" {
      KotlinScript().use { it.eval("System.currentTimeMillis() > 0") shouldBe true }
    }

    "values whose runtime class is private, internal, or outside kotlin can be bound" {
      KotlinScript().use {
        it.apply {
          add("regex", Regex("a+"))
          add("fixed", listOf(1, 2), typeOf<Int>())
          add("nested", mapOf("k" to listOf(1, 2)), typeOf<String>(), typeOf<List<Int>>())
          add("empty", emptyList<Int>())

          eval("""regex.matches("aaa")""") shouldBe true
          eval("fixed.size") shouldBe 2
          eval("""nested.getValue("k").sum()""") shouldBe 3
          eval("empty.size") shouldBe 0
        }
      }
    }

    "a variable added after an evaluation is bound for the next one" {
      KotlinScript().use {
        it.apply {
          add("a", 1)
          eval("a") shouldBe 1
          add("b", 2)
          eval("a + b") shouldBe 3
        }
      }
    }

    "a variable added again is redeclared with its new value and type" {
      KotlinScript().use {
        it.apply {
          add("x", 1)
          eval("x") shouldBe 1
          add("x", "s")
          eval("x.length") shouldBe 1
        }
      }
    }

    // ArrayPrimitive: an Array<Int> is the case under test.
    @Suppress("ArrayPrimitive")
    "arrays can be bound, including arrays of generic and primitive elements" {
      KotlinScript().use {
        it.apply {
          // Integer[] used to be cast to kotlin.Any<kotlin.Int>, which does not compile.
          add("ints", arrayOf(1, 2), typeOf<Int>())
          add("lists", arrayOf(listOf(1, 2, 3)), typeOf<List<Int>>())
          add("primitives", intArrayOf(1, 2, 3, 4))
          varDecls shouldBe
            """
            |val ints = $HOLDER["ints"] as kotlin.Array<kotlin.Int>
            |val lists = $HOLDER["lists"] as kotlin.Array<kotlin.collections.List<kotlin.Int>>
            |val primitives = $HOLDER["primitives"] as kotlin.IntArray
            """.trimMargin()
          eval("ints.size + ints[1]") shouldBe 4
          eval("lists[0].size") shouldBe 3
          eval("primitives.sum()") shouldBe 10
        }
      }
    }

    "a value with no nameable class for its type arguments is bound as Any" {
      KotlinScript().use {
        it.apply {
          add("pair", InternalPair(1, "a"), typeOf<Int>(), typeOf<String>())
          // It used to be cast to kotlin.Any<kotlin.Int, kotlin.String>, which does not compile.
          varDecls shouldBe
            """val pair = $HOLDER["pair"] as kotlin.Any"""
          eval("pair.toString()") shouldBe "[1]"
          eval("0") shouldBe 0
        }
      }
    }

    "a non-generic subclass of a generic class is cast to itself" {
      KotlinScript().use {
        it.apply {
          add("props", Properties().apply { setProperty("k", "v") })
          varDecls shouldBe
            """val props = $HOLDER["props"] as java.util.Properties"""
          eval("""props.getProperty("k")""") shouldBe "v"
        }
      }
    }

    // Each value used to get its own "<name>_tmp" engine binding, which the engine also exposes as a script property.
    // A user variable could then collide with one, and values of some classes broke every later evaluation.
    "a variable named like another's former temporary binding keeps its own value" {
      KotlinScript().use {
        it.apply {
          add("a_tmp", 1)
          add("a", "s")
          eval("a_tmp") shouldBe 1
          eval("a") shouldBe "s"
        }
      }
    }

    "binding a lambda or a JDK-internal class leaves later evaluations working" {
      KotlinScript().use {
        it.apply {
          val increment: (Int) -> Int = { x -> x + 1 }
          add("increment", increment)
          add("order", String.CASE_INSENSITIVE_ORDER)
          eval("1 + 1") shouldBe 2
          eval("order === order") shouldBe true
        }
      }
    }

    "the names the generated declarations read from are reserved" {
      KotlinScript().use { script ->
        listOf("bindings", "__variables").forEach { name ->
          shouldThrow<ScriptException> { script.add(name, 1) }.message.orEmpty() shouldContain "not a valid identifier"
        }
      }
    }

    "a value whose class is in an unexported JDK package is declared as an exported supertype" {
      KotlinScript().use {
        it.apply {
          add("cs", Charsets.UTF_8)
          eval("cs.name()") shouldBe "UTF-8"
        }
      }
    }

    "a comparator or a lambda can be declared with its type arguments and called" {
      KotlinScript().use {
        it.apply {
          add("cmp", Comparator.naturalOrder<String>(), typeOf<String>())
          val increment: (Int) -> Int = { x -> x + 1 }
          add("increment", increment, typeOf<Int>(), typeOf<Int>())
          eval("""cmp.compare("a", "b") < 0""") shouldBe true
          eval("increment(2)") shouldBe 3
        }
      }
    }

    "variable names must be valid identifiers" {
      KotlinScript().use { script ->
        ["my-var", "class", "x = 1; val injected", ""].forEach { name ->
          shouldThrow<ScriptException> { script.add(name, 5) }
        }
      }
    }

    "the public engine property is deprecated" {
      // Kotlin keeps a property's annotations on the property, not its getter, so read them with Kotlin reflection.
      AbstractEngine::class.members.single { it.name == "engine" }.annotations.any { it is Deprecated } shouldBe true
    }
  }

  companion object {
    private const val JVM_EXIT_MESSAGE = "Illegal call to a JVM termination method"

    // Each iteration compiles REPL snippets, so a few are enough to show that failed ones leave the engine usable.
    private const val ITERATIONS = 5
  }
}
