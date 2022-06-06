/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotThrow
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import javax.script.ScriptException
import kotlin.reflect.typeOf

class IncClass(var i: Int = 0) {
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

        boolVal shouldBeEqualTo eval("boolVal")
        !boolVal shouldBeEqualTo eval("!boolVal")

        intVal shouldBeEqualTo eval("intVal")
        intVal + 1 shouldBeEqualTo eval("intVal + 1")

        longVal shouldBeEqualTo eval("longVal")
        longVal + 1 shouldBeEqualTo eval("longVal + 1")

        doubleVal shouldBeEqualTo eval("doubleVal")
        doubleVal + 1 shouldBeEqualTo eval("doubleVal + 1")

        floatVal shouldBeEqualTo eval("floatVal")
        floatVal + 1 shouldBeEqualTo eval("floatVal + 1")

        strVal shouldBeEqualTo eval("strVal")
        strVal.length shouldBeEqualTo eval("strVal.length")
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

        intVal shouldBeEqualTo eval("intVal")
        intVal + 1 shouldBeEqualTo eval("intVal + 1")
        intVal shouldBeEqualTo 0

        intVar shouldBeEqualTo eval("intVar")
        intVar + 1 shouldBeEqualTo eval("intVar + 1")
        intVar shouldBeEqualTo 0
      }
    }
  }

  @Test
  fun userObjectTest() {
    val aux = IncClass()

    KotlinScript().use {
      it.apply {

        add("aux", aux)

        aux.i shouldBeEqualTo eval("aux.i")

        val incEd =
          eval(
            """
                repeat(100) { aux.inc() }
                aux
              """
          ) as IncClass

        aux.i shouldBeEqualTo 100
        aux.i shouldBeEqualTo incEd.i
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

        list.size shouldBeEqualTo eval("list.size")
        map.size shouldBeEqualTo eval("map.size")

        val incEd =
          eval(
            """
                map["k2"] = 10
                repeat(100) { list.add(it) }
                list
              """
          ) as List<*>

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("list.size")
        list.size shouldBeEqualTo incEd.size

        map.size shouldBeEqualTo eval("map.size")
        map.size shouldBeEqualTo 2
        map["k2"] shouldBeEqualTo 10
      }
    }
  }

  @Test
  fun objectWithKTypeTest() {
    val list = mutableListOf(1)

    KotlinScript().use {
      it.apply {
        add("list", list, typeOf<Int>())

        list.size shouldBeEqualTo eval("list.size")

        val incEd =
          eval(
            """
                repeat(100) { list.add(it) }
                list
              """
          ) as List<*>

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("list.size")
        list.size shouldBeEqualTo incEd.size
      }
    }
  }

  @Test
  fun nullObjectTest() {
    val list = mutableListOf<Int?>()

    KotlinScript().use {
      it.apply {
        add("list", list, typeOf<Int?>())

        varDecls shouldBeEqualTo "val list = bindings[\"${"list".toTempName()}\"] as java.util.ArrayList<Int?>"

        list.size shouldBeEqualTo eval("list.size")

        val incEd =
          eval(
            """
                repeat(100) { list.add(null) }
                list
              """
          ) as List<*>

        list.size shouldBeEqualTo 100
        list.size shouldBeEqualTo eval("list.size")
        list.size shouldBeEqualTo incEd.size
      }
    }
  }

  @Test
  fun listCompareTest() {
    KotlinScript().use {
      it.apply {
        eval("listOf(1,2,3) == listOf(1, 2, 3)") shouldBeEqualTo true
        eval("listOf(1,2) == listOf(1, 2, 3)") shouldBeEqualTo false

        eval("listOf(true,true) == listOf(true, true)") shouldBeEqualTo true
        eval("listOf(true,false) == listOf(true, true)") shouldBeEqualTo false

        eval("""listOf("aaa","bbb") == listOf("aaa", "bbb")""") shouldBeEqualTo true
        eval("""listOf("aaa","bbb") == listOf("aaa", "aaa")""") shouldBeEqualTo false
      }
    }
  }

  @Test
  fun innerClassTest() {
    class InnerTest

    val inner = InnerTest()

    KotlinScript().use {
      it.apply {
        invoking { add("inner", inner) } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun unnecessaryParamsTest() {
    val value = 5

    KotlinScript().use {
      it.apply {
        invoking { add("value", value, typeOf<Int?>()) } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun unmatchedParamsTest() {
    val list = mutableListOf(1)

    KotlinScript().use {
      it.apply {
        invoking { add("list", list, typeOf<Int?>(), typeOf<Int>()) } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun missingCollectionTypeTest() {
    val list = mutableListOf(1)

    KotlinScript().use {
      it.apply {
        invoking { add("list", list) } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun invalidSyntaxTest() {
    KotlinScript().use {
      it.apply {
        invoking { eval("junk") } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun illegalCallsTest() {
    KotlinScript().use {
      it.apply {
        invoking { eval("System.exit(1)") } shouldThrow ScriptException::class
        invoking { eval("com.lang.System.exit(1)") } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun exprEvaluator() {
    KotlinExprEvaluator()
      .apply {
        repeat(100) { i ->
          //println("Invocation1: $i")
          invoking { eval("$i == [wrong]") } shouldThrow ScriptException::class
          invoking { eval("$i == $i") } shouldNotThrow ScriptException::class
        }
      }
  }

  @Test
  fun computeEvaluator() {
    KotlinExprEvaluator()
      .apply {
        repeat(100) { i ->
          invoking { compute("$i * $i") } shouldNotThrow ScriptException::class
          (compute("$i * $i") as Int) shouldBeEqualTo (i * i)
        }
      }
  }

  @Test
  fun poolExprEvaluator() {
    val pool = KotlinExprEvaluatorPool(5)
    repeat(100) { i ->
      pool
        .apply {
          //println("Invocation2: $i")
          invoking { blockingEval("$i == [wrong]") } shouldThrow ScriptException::class
          invoking { blockingEval("$i == $i") } shouldNotThrow ScriptException::class
        }
    }
  }
}