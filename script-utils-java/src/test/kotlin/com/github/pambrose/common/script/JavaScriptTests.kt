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

        boolVal shouldBe eval("boolVal")
        !boolVal shouldBe eval("!boolVal")

        intVal shouldBe eval("intVal")
        intVal + 1 shouldBe eval("intVal + 1")

        longVal shouldBe eval("longVal")
        (longVal + 1) shouldBe eval("longVal + 1")

        doubleVal shouldBe eval("doubleVal")
        doubleVal + 1 shouldBe eval("doubleVal + 1")

        floatVal shouldBe eval("floatVal")
        floatVal + 1 shouldBe eval("floatVal + 1")

        strVal shouldBe eval("strVal")
        strVal.length shouldBe eval("strVal.length()")
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

        aux.i shouldBe eval("aux.getI()")

        val retval =
          eval(
            "aux.getI()",
            """
                for (int i = 0; i < 100; i++)
                  aux.inc();
            """.trimIndent(),
          )

        retval shouldBe 100
        aux.i shouldBe 100
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

        list.size shouldBe eval("list.size()")
        map.size shouldBe eval("map.size()")

        val retval =
          eval(
            "map.size()",
            """
                map.put("k2", 10);
                for (int i = 0; i < 100; i++)
                  list.add(i);
            """.trimIndent(),
          )

        retval shouldBe map.size

        list.size shouldBe 101
        list.size shouldBe eval("list.size()")

        map.size shouldBe eval("map.size()")
        map.size shouldBe 2
        map["k2"] shouldBe 10
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

        list.size shouldBe eval("list.size()")

        eval(
          "0",
          """
            for (int i = 0; i < 100; i++)
              list.add(i);
          """.trimIndent(),
        )

        list.size shouldBe 101
        list.size shouldBe eval("list.size()")
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

        list.size shouldBe eval("list.size()")

        eval(
          "0",
          """
                for (int i = 0; i < 100; i++)
                  list.add(null);
          """.trimIndent(),
        )

        list.size shouldBe 100
        list.size shouldBe eval("list.size()")
      }
    }
  }

  @Test
  fun unnecessaryParamsTest() {
    val value = 5

    JavaScript().use {
      it.apply {
        shouldThrow<ScriptException> { add("value", value, typeOf<Int?>()) }
      }
    }
  }

  @Test
  fun unmatchedParamsTest() {
    val list = mutableListOf(1)

    JavaScript().use {
      it.apply {
        shouldThrow<ScriptException> {
          add(
            "list",
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
    JavaScript().use {
      it.apply {
        shouldThrow<ScriptException> { add("list", list) }
      }
    }
  }

  @Test
  fun invalidSyntaxTest() {
    JavaScript().use {
      it.apply {
        shouldThrow<ScriptException> { eval("junk") }
      }
    }
  }

  @Test
  fun illegalCallsTest() {
    JavaScript().use {
      it.apply {
        shouldThrow<ScriptException> { eval("sys.exit(1)") }
        shouldThrow<ScriptException> { eval("exit(1)") }
        shouldThrow<ScriptException> { eval("quit(1)") }
      }
    }
  }
}
