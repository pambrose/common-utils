/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import javax.script.ScriptException
import kotlin.reflect.typeOf

class PyScriptTests {

  @Test
  fun builtInTypesTest() {
    val boolVal = true
    val intVal = 0
    val longVal = 0L
    val doubleVal = 0.0
    val floatVal = 0.0F
    val strVal = "A String"

    PyScript()
      .apply {
        add("boolVal", boolVal)
        add("intVal", intVal)
        add("longVal", longVal)
        add("doubleVal", doubleVal)
        add("floatVal", floatVal)
        add("strVal", strVal)

        boolVal shouldBeEqualTo eval("boolVal")
        !boolVal shouldBeEqualTo eval("not boolVal")

        intVal shouldBeEqualTo eval("intVal")
        intVal + 1 shouldBeEqualTo eval("intVal + 1")

        longVal.toBigInteger() shouldBeEqualTo eval("longVal")
        (longVal + 1).toBigInteger() shouldBeEqualTo eval("longVal + 1")

        doubleVal shouldBeEqualTo eval("doubleVal")
        doubleVal + 1 shouldBeEqualTo eval("doubleVal + 1")

        floatVal.toDouble() shouldBeEqualTo eval("floatVal")
        floatVal.toDouble() + 1 shouldBeEqualTo eval("floatVal + 1")

        strVal shouldBeEqualTo eval("strVal")
        strVal.length shouldBeEqualTo eval("len(strVal)")
      }
  }

  @Test
  fun valVarTest() {
    val intVal = 0
    val intVar = 0

    PyScript()
      .apply {
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

  @Test
  fun userObjectTest() {
    val aux = AuxClass()

    PyScript()
      .apply {

        add("aux", aux)

        aux.i shouldBeEqualTo eval("aux.i")

        eval("""
                for i in range(100):
                  aux.inc()
              """.trimIndent()
        )

        aux.i shouldBeEqualTo 100
      }
  }

  @Test
  fun objectWithKClassTest() {
    val list = mutableListOf(1)
    val map = mutableMapOf("k1" to 1)

    PyScript()
      .apply {
        add("list", list, typeOf<Int>())
        add("map", map, typeOf<String>(), typeOf<Int>())

        list.size shouldBeEqualTo eval("len(list)")
        map.size shouldBeEqualTo eval("len(map)")

        eval("""
                map["k2"] = 10
                for i in range(100):
                  list.add(i)
              """.trimIndent()
        )

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("len(list)")

        map.size shouldBeEqualTo eval("len(map)")
        map.size shouldBeEqualTo 2
        map["k2"] shouldBeEqualTo 10
      }
  }

  @Test
  fun objectWithKTypeTest() {
    val list = mutableListOf(1)

    PyScript()
      .apply {
        add("list", list, typeOf<Int>())

        list.size shouldBeEqualTo eval("len(list)")

        eval("""
                for i in range(100):
                  list.add(i)
              """.trimIndent()
        )

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("len(list)")
      }
  }

  @Test
  fun nullObjectTest() {
    val list = mutableListOf<Int?>()

    PyScript()
      .apply {
        add("list", list, typeOf<Int?>())

        list.size shouldBeEqualTo eval("len(list)")

        eval("""
                for i in range(100):
                  list.add(None)
              """.trimIndent()
        )

        list.size shouldBeEqualTo 100
        list.size shouldBeEqualTo eval("len(list)")
      }
  }

  @Test
  fun innerClassTest() {
    class InnerTest

    val inner = InnerTest()

    PyScript()
      .apply {
        invoking { add("inner", inner) } shouldThrow ScriptException::class
      }
  }

  @Test
  fun unnecessaryParamsTest() {
    val value = 5

    PyScript()
      .apply {
        invoking { add("value", value, typeOf<Int?>()) } shouldThrow ScriptException::class
      }
  }

  @Test
  fun unmatchedParamsTest() {
    val list = mutableListOf(1)

    PyScript()
      .apply {
        invoking { add("list", list, typeOf<Int?>(), typeOf<Int>()) } shouldThrow ScriptException::class
      }
  }

  @Test
  fun missingCollectionTypeTest() {
    val list = mutableListOf(1)
    PyScript()
      .apply {
        invoking { add("list", list) } shouldThrow ScriptException::class
      }
  }

  @Test
  fun invalidSyntaxTest() {
    PyScript()
      .apply {
        invoking { eval("junk") } shouldThrow ScriptException::class
      }
  }

  @Test
  fun illegalCallsTest() {
    PyScript()
      .apply {
        invoking { eval("sys.exit(1)") } shouldThrow ScriptException::class
        invoking { eval("exit(1)") } shouldThrow ScriptException::class
        invoking { eval("quit(1)") } shouldThrow ScriptException::class
      }
  }
}