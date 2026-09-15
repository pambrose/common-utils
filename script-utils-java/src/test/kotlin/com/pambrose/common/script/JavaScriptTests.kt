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

// DEPRECATION: some tests read the deprecated public engine.
@file:Suppress("DEPRECATION")

package com.pambrose.common.script

import ch.obermuhlner.scriptengine.java.Isolation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptException
import kotlin.reflect.typeOf
import kotlinx.coroutines.withTimeout

class IncClass(
  var i: Int = 0,
) {
  fun inc() {
    i++
  }
}

private fun Any.isolation() = javaClass.getDeclaredField("isolation").apply { isAccessible = true }.get(this)

class JavaScriptTests : StringSpec() {
  init {
    "built in types" {
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

    "user object" {
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

    "object with types" {
      val list: MutableList<Int> = [1]
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

    "object with KType" {
      val list: MutableList<Int> = [1]

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

    "null object" {
      val list: MutableList<Int?> = []

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

    "unnecessary params" {
      val value = 5

      JavaScript().use { script ->
        shouldThrow<ScriptException> { script.add("value", value, typeOf<Int?>()) }
      }
    }

    "unmatched params" {
      val list: MutableList<Int> = [1]

      JavaScript().use { script ->
        shouldThrow<ScriptException> { script.add("list", list, typeOf<Int?>(), typeOf<Int>()) }
      }
    }

    "missing collection type" {
      val list: MutableList<Int> = [1]
      JavaScript().use { script ->
        shouldThrow<ScriptException> { script.add("list", list) }
      }
    }

    "invalid syntax" {
      JavaScript().use { script ->
        shouldThrow<ScriptException> { script.eval("junk") }
      }
    }

    "illegal calls" {
      // Java-syntax termination calls, rejected by the guard rather than by a compile error.
      JavaScript().use {
        it.apply {
          shouldThrow<ScriptException> { eval("0", "java.lang.System.exit(1);") }
          shouldThrow<ScriptException> { eval("0", "Runtime.getRuntime().exit(1);") }
        }
      }
    }

    "JVM termination calls are rejected before evaluation" {
      // ScriptGuards rejects each of these before the engine compiles/runs, so the JVM is never killed.
      JavaScript().use {
        it.apply {
          shouldThrow<ScriptException> { eval("System.exit(0)") }
          // Previously the guard scanned only the expression, so a call in the action block slipped through.
          shouldThrow<ScriptException> { eval("0", "System.exit(0);") }
          shouldThrow<ScriptException> { eval("Runtime.getRuntime().halt(0)") }
          // evalScript previously had no guard at all.
          shouldThrow<ScriptException> { evalScript("System.exit(0);") }
        }
      }
    }

    // Bug #5 (Java): JavaScriptPool's nullGlobalContext is ignored because Java binds variables
    // into the engine scope, never the global scope. The global context must stay non-null on both
    // the initial population and after recycling, even when the pool is created with true.
    "pool keeps non-null global context regardless of nullGlobalContext" {
      val pool = JavaScriptPool(2, nullGlobalContext = true)
      // Three evals on a pool of size two force a recycle between borrows.
      repeat(3) {
        pool.eval { engine.getBindings(GLOBAL_SCOPE) } shouldNotBe null
      }
    }

    "a variable added after an evaluation is bound for the next one" {
      JavaScript().use {
        it.apply {
          add("a", 1)
          eval("a") shouldBe 1
          add("b", 2)
          eval("b") shouldBe 2
        }
      }
    }

    "Char, Any, and nested generic type arguments compile" {
      JavaScript().use {
        it.apply {
          add("chars", mutableListOf('a'), typeOf<Char>())
          add("anys", mutableListOf<Any>(1), typeOf<Any>())
          add("nested", mutableMapOf("k" to listOf(1, 2)), typeOf<String>(), typeOf<List<Int>>())
          eval("""chars.size() + anys.size() + nested.get("k").size()""") shouldBe 4
        }
      }
    }

    "a value whose runtime class is private binds as a public supertype" {
      JavaScript().use {
        it.apply {
          add("fixed", listOf(1, 2), typeOf<Int>())
          import(ArrayList::class.java)
          eval("fixed.size()") shouldBe 2
        }
      }
    }

    "a binding that does not fit the script's field is reported as a ScriptException" {
      JavaScript().use {
        it.apply {
          add("count", "not a number")
          shouldThrow<ScriptException> {
            evalScript("public class Main { public int count; public Object getValue() { return count; } }")
          }
        }
      }
    }

    "a pooled instance does not keep the previous borrower's imports or isolation" {
      withTimeout(60_000) {
        val pool = JavaScriptPool(1)
        pool.eval {
          import(ArrayList::class.java)
          assignIsolation(Isolation.IsolatedClassLoader)
        }
        pool.eval {
          importDecls shouldBe ""
          engine.isolation() shouldBe Isolation.CallerClassLoader
        }
      }
    }

    "variable names must be valid Java identifiers" {
      JavaScript().use { script ->
        ["my-var", "new", "x; int injected"].forEach { name ->
          shouldThrow<ScriptException> { script.add(name, 5) }
        }
      }
    }
  }
}
