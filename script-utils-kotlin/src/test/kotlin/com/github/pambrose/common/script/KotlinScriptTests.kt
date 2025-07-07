/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose.common.script

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import javax.script.ScriptException
import kotlin.reflect.typeOf

class IncClass(
  var i: Int = 0,
) {
  fun inc() {
    i++
  }
}

class KotlinScriptTests {
  @Test
  fun builtInTypesTest() {
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

  @Test
  fun valVarTest() {
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

  @Test
  fun userObjectTest() {
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

  @Test
  fun objectWithTypesTest() {
    val list = mutableListOf(1)
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

  @Test
  fun objectWithKTypeTest() {
    val list = mutableListOf(1)

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

  @Test
  fun nullObjectTest() {
    val list = mutableListOf<Int?>()

    KotlinScript().use {
      it.apply {
        add("list", list, typeOf<Int?>())

        val s = "list".toTempName()
        varDecls shouldBe "val list = bindings[\"$s\"] as java.util.ArrayList<Int?>"

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

  @Test
  fun listCompareTest() {
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

  @Test
  fun innerClassTest() {
    class InnerTest

    val inner = InnerTest()

    KotlinScript().use {
      it.apply {
        shouldThrow<ScriptException> { add("inner", inner) }
      }
    }
  }

  @Test
  fun unnecessaryParamsTest() {
    val value = 5

    KotlinScript().use {
      it.apply {
        shouldThrow<ScriptException> { add("value", value, typeOf<Int?>()) }
      }
    }
  }

  @Test
  fun unmatchedParamsTest() {
    val list = mutableListOf(1)

    KotlinScript().use {
      it.apply {
        shouldThrow<ScriptException> {
          add(
            name = "list",
            list,
            typeOf<Int?>(),
            typeOf<Int>(),
          )
        }
      }
    }
  }

  @Test
  fun missingCollectionTypeTest() {
    val list = mutableListOf(1)

    KotlinScript().use {
      it.apply {
        shouldThrow<ScriptException> { add("list", list) }
      }
    }
  }

  @Test
  fun invalidSyntaxTest() {
    KotlinScript().use {
      it.apply {
        shouldThrow<ScriptException> { eval("junk") }
      }
    }
  }

  @Test
  fun illegalCallsTest() {
    KotlinScript().use {
      it.apply {
        shouldThrow<ScriptException> { eval("System.exit(1)") }
        shouldThrow<ScriptException> { eval("com.lang.System.exit(1)") }
      }
    }
  }

  @Test
  fun exprEvaluator() {
    KotlinExprEvaluator()
      .apply {
        repeat(100) { i ->
          // println("Invocation1: $i")
          shouldThrow<ScriptException> { eval("$i == [wrong]") }
          shouldNotThrow<ScriptException> { eval("$i == $i") }
        }
      }
  }

  @Test
  fun computeEvaluator() {
    KotlinExprEvaluator()
      .apply {
        repeat(100) { i ->
          shouldNotThrow<ScriptException> { compute("$i * $i") }
          (compute("$i * $i") as Int) shouldBe (i * i)
        }
      }
  }

  @Test
  fun poolExprEvaluator() {
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
}
