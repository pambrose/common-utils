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

class IncClass(
  var i: Int = 0,
) {
  fun inc() {
    i++
  }
}

class PythonScriptTests {
  @Test
  fun builtInTypesTest() {
    val boolVal = true
    val intVal = 0
    val longVal = 0L
    val doubleVal = 0.0
    val floatVal = 0.0F
    val strVal = "A String"

    PythonScript().use {
      it.apply {
        add("boolVal", boolVal)
        add("intVal", intVal)
        add("longVal", longVal)
        add("doubleVal", doubleVal)
        add("floatVal", floatVal)
        add("strVal", strVal)

        boolVal shouldBe eval("boolVal")
        !boolVal shouldBe eval("not boolVal")

        intVal shouldBe eval("intVal")
        intVal + 1 shouldBe eval("intVal + 1")

        longVal.toBigInteger() shouldBe eval("longVal")
        (longVal + 1).toBigInteger() shouldBe eval("longVal + 1")

        doubleVal shouldBe eval("doubleVal")
        doubleVal + 1 shouldBe eval("doubleVal + 1")

        floatVal.toDouble() shouldBe eval("floatVal")
        floatVal.toDouble() + 1 shouldBe eval("floatVal + 1")

        strVal shouldBe eval("strVal")
        strVal.length shouldBe eval("len(strVal)")
      }
    }
  }

  @Test
  fun userObjectTest() {
    val aux = IncClass()

    PythonScript().use {
      it.apply {
        add("aux", aux)

        aux.i shouldBe eval("aux.i")

        eval(
          """
                for i in range(100):
                  aux.inc()
          """.trimIndent(),
        )

        aux.i shouldBe 100
      }
    }
  }

  @Test
  fun objectWithClassTest() {
    val list = mutableListOf(1)
    val map = mutableMapOf("k1" to 1)

    PythonScript().use {
      it.apply {
        add("list", list)
        add("map", map)

        list.size shouldBe eval("len(list)")
        map.size shouldBe eval("len(map)")

        eval(
          """
                map["k2"] = 10
                for i in range(100):
                  list.add(i)
          """.trimIndent(),
        )

        list.size shouldBe 101
        list.size shouldBe eval("len(list)")

        map.size shouldBe eval("len(map)")
        map.size shouldBe 2
        map["k2"] shouldBe 10
      }
    }
  }

  @Test
  fun listCompareTest() {
    PythonScript().use {
      it.apply {
        eval("[True] == [True]") shouldBe true
      }
    }
  }

  @Test
  fun objectWithTypeTest() {
    val list = mutableListOf(1)

    PythonScript().use {
      it.apply {
        add("list", list)

        list.size shouldBe eval("len(list)")

        eval(
          """
                for i in range(100):
                  list.add(i)
          """.trimIndent(),
        )

        list.size shouldBe 101
        list.size shouldBe eval("len(list)")
      }
    }
  }

  @Test
  fun nullObjectTest() {
    val list = mutableListOf<Int?>()

    PythonScript().use {
      it.apply {
        add("list", list)

        list.size shouldBe eval("len(list)")

        eval(
          """
                for i in range(100):
                  list.add(None)
          """.trimIndent(),
        )

        list.size shouldBe 100
        list.size shouldBe eval("len(list)")
      }
    }
  }

  @Test
  fun invalidSyntaxTest() {
    PythonScript().use {
      it.apply {
        shouldThrow<ScriptException> { eval("junk") }
      }
    }
  }

  @Test
  fun illegalCallsTest() {
    PythonScript().use {
      it.apply {
        shouldThrow<ScriptException> { eval("sys.exit(1)") }
        shouldThrow<ScriptException> { eval("exit(1)") }
        shouldThrow<ScriptException> { eval("quit(1)") }
      }
    }
  }

  @Test
  fun exprEvaluator() {
    PythonExprEvaluator()
      .apply {
        repeat(200) { i ->
          // println("Invocation: $i")
          shouldThrow<ScriptException> { eval("$i == [wrong]") }
          shouldNotThrow<ScriptException> { eval("$i == $i") }
        }
      }
  }

  @Test
  fun poolExprEvaluator() {
    val pool = PythonExprEvaluatorPool(5)
    repeat(200) { i ->
      pool
        .apply {
          // println("Invocation: $i")
          shouldThrow<ScriptException> { blockingEval("$i == [wrong]") }
          shouldNotThrow<ScriptException> { blockingEval("$i == $i") }
        }
    }
  }
}
