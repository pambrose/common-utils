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

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import javax.script.ScriptException
import kotlin.reflect.typeOf

class IncClass(
  var i: Int = 0,
) {
  fun inc() {
    i++
  }
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

          varDecls shouldBe "val list = bindings[\"list_tmp\"] as java.util.ArrayList<kotlin.Int?>"

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

    "illegal calls" {
      // ScriptGuards rejects each of these before the engine runs, so the JVM is never terminated.
      KotlinScript().use {
        it.apply {
          shouldThrow<ScriptException> { eval("System.exit(1)") }
          shouldThrow<ScriptException> { eval("java.lang.System.exit(1)") }
          shouldThrow<ScriptException> { eval("exitProcess(0)") }
          shouldThrow<ScriptException> { eval("kotlin.system.exitProcess(0)") }
          shouldThrow<ScriptException> { eval("Runtime.getRuntime().exit(0)") }
          shouldThrow<ScriptException> { eval("Runtime.getRuntime().halt(0)") }
        }
      }
    }

    "expr evaluator" {
      KotlinExprEvaluator()
        .apply {
          repeat(100) { i ->
            // println("Invocation1: $i")
            shouldThrow<ScriptException> { eval("$i == [wrong]") }
            shouldNotThrow<ScriptException> { eval("$i == $i") }
          }
        }
    }

    "compute evaluator" {
      KotlinExprEvaluator()
        .apply {
          repeat(100) { i ->
            shouldNotThrow<ScriptException> { compute("$i * $i") }
            (compute("$i * $i") as Int) shouldBe (i * i)
          }
        }
    }

    "pool expr evaluator" {
      val pool = KotlinExprEvaluatorPool(5)
      repeat(100) { i ->
        pool
          .apply {
            // println("Invocation2: $i")
            shouldThrow<ScriptException> { blockingEval("$i == [wrong]") }
            shouldNotThrow<ScriptException> { blockingEval("$i == $i") }
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
}
