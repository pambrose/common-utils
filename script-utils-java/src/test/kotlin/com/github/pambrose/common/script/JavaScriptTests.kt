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
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import javax.script.ScriptException
import kotlin.reflect.typeOf

class IncClass(var i: Int = 0) {
  fun inc() {
    i++
  }
}

class JavaScriptTests {

  @Test
  fun builtInTypesTest() {
    val boolVal = true
    val intVal = 0
    val longVal = 0L
    val doubleVal = 0.0
    val floatVal = 0.0F
    val strVal = "A String"

    JavaScript().use {
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
        (longVal + 1) shouldBeEqualTo eval("longVal + 1")

        doubleVal shouldBeEqualTo eval("doubleVal")
        doubleVal + 1 shouldBeEqualTo eval("doubleVal + 1")

        floatVal shouldBeEqualTo eval("floatVal")
        floatVal + 1 shouldBeEqualTo eval("floatVal + 1")

        strVal shouldBeEqualTo eval("strVal")
        strVal.length shouldBeEqualTo eval("strVal.length()")
      }
    }
  }

  @Test
  fun userObjectTest() {
    val aux = IncClass()

    JavaScript().use {
      it.apply {
        add("aux", aux)
        import(IncClass::class.java)

        aux.i shouldBeEqualTo eval("aux.getI()")

        val retval =
          eval(
            "aux.getI()",
            """
                for (int i = 0; i < 100; i++)
                  aux.inc();
              """.trimIndent()
          )

        retval shouldBeEqualTo 100
        aux.i shouldBeEqualTo 100
      }
    }
  }

  @Test
  fun objectWithTypesTest() {
    val list = mutableListOf(1)
    val map = mutableMapOf("k1" to 1)

    JavaScript().use {
      it.apply {
        add("list", list, typeOf<Int>())
        add("map", map, typeOf<String>(), typeOf<Int>())
        import(ArrayList::class.java)
        import(LinkedHashMap::class.java)

        list.size shouldBeEqualTo eval("list.size()")
        map.size shouldBeEqualTo eval("map.size()")

        val retval =
          eval(
            "map.size()",
            """
                map.put("k2", 10);
                for (int i = 0; i < 100; i++)
                  list.add(i);
              """.trimIndent()
          )

        retval shouldBeEqualTo map.size

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("list.size()")

        map.size shouldBeEqualTo eval("map.size()")
        map.size shouldBeEqualTo 2
        map["k2"] shouldBeEqualTo 10
      }
    }
  }

  @Test
  fun objectWithKTypeTest() {
    val list = mutableListOf(1)

    JavaScript().use {
      it.apply {
        add("list", list, typeOf<Int>())
        import(ArrayList::class.java)

        list.size shouldBeEqualTo eval("list.size()")

        eval(
          "0",
          """
            for (int i = 0; i < 100; i++)
              list.add(i);
              """.trimIndent()
        )

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("list.size()")
      }
    }
  }

  @Test
  fun nullObjectTest() {
    val list = mutableListOf<Int?>()

    JavaScript().use {
      it.apply {
        add("list", list, typeOf<Int?>())
        import(ArrayList::class.java)

        list.size shouldBeEqualTo eval("list.size()")

        eval(
          "0",
          """
                for (int i = 0; i < 100; i++)
                  list.add(null);
              """.trimIndent()
        )

        list.size shouldBeEqualTo 100
        list.size shouldBeEqualTo eval("list.size()")
      }
    }
  }

  @Test
  fun unnecessaryParamsTest() {
    val value = 5

    JavaScript().use {
      it.apply {
        invoking { add("value", value, typeOf<Int?>()) } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun unmatchedParamsTest() {
    val list = mutableListOf(1)

    JavaScript().use {
      it.apply {
        invoking { add("list", list, typeOf<Int?>(), typeOf<Int>()) } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun missingCollectionTypeTest() {
    val list = mutableListOf(1)
    JavaScript().use {
      it.apply {
        invoking { add("list", list) } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun invalidSyntaxTest() {
    JavaScript().use {
      it.apply {
        invoking { eval("junk") } shouldThrow ScriptException::class
      }
    }
  }

  @Test
  fun illegalCallsTest() {
    JavaScript().use {
      it.apply {
        invoking { eval("sys.exit(1)") } shouldThrow ScriptException::class
        invoking { eval("exit(1)") } shouldThrow ScriptException::class
        invoking { eval("quit(1)") } shouldThrow ScriptException::class
      }
    }
  }
}