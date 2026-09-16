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
import io.kotest.matchers.string.shouldContain
import java.util.Properties
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptException
import kotlin.reflect.typeOf
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

class IncClass(
  var i: Int = 0,
) {
  fun inc() {
    i++
  }
}

private const val JVM_EXIT_MESSAGE = "Illegal call to a JVM termination method"

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

    "JVM termination calls are rejected before evaluation" {
      // Each call compiles but never runs: it sits in a lambda that is not invoked, behind if (false), or in a method
      // nothing calls. Without the guard, each evaluation would return normally, failing the test instead of
      // terminating the test JVM. The guard's own message tells its rejection apart from a compile error.
      JavaScript().use {
        it.apply {
          listOf(
            { eval("(Runnable) () -> System.exit(0)") },
            { eval("(Runnable) () -> Runtime.getRuntime().halt(0)") },
            // Previously the guard scanned only the expression, so a call in the action block slipped through.
            { eval("0", "if (false) java.lang.System.exit(1);") },
            { eval("0", "if (false) Runtime.getRuntime().exit(1);") },
            // evalScript previously had no guard at all. Only getValue is public and takes no arguments, so it is
            // the method java-scriptengine calls.
            {
              evalScript(
                """
                public class Main {
                  public Object getValue() { return 0; }
                  static void shutdown(int status) { System.exit(status); }
                }
                """.trimIndent(),
              )
            },
          ).forEach { evaluation ->
            shouldThrow<ScriptException> { evaluation() }.message shouldContain JVM_EXIT_MESSAGE
          }
          eval("0") shouldBe 0
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
          eval("a + b") shouldBe 3
        }
      }
    }

    "a variable added again is redeclared with its new value and type" {
      JavaScript().use {
        it.apply {
          add("x", 1)
          eval("x") shouldBe 1
          add("x", "s")
          eval("x.length()") shouldBe 1
        }
      }
    }

    // ArrayPrimitive: an Array<Int> is the case under test.
    @Suppress("ArrayPrimitive")
    "arrays can be bound, including arrays of generic and primitive elements" {
      JavaScript().use {
        it.apply {
          // Integer[] used to be declared as java.lang.Object<java.lang.Integer>, which does not compile.
          add("ints", arrayOf(1, 2), typeOf<Int>())
          add("lists", arrayOf(listOf(1, 2, 3)), typeOf<List<Int>>())
          add("primitives", intArrayOf(1, 2, 3, 4))
          eval("ints.length + ints[1]") shouldBe 4
          eval("lists[0].size()") shouldBe 3
          eval("primitives.length") shouldBe 4
        }
      }
    }

    "a star-projected array type argument compiles" {
      JavaScript().use {
        it.apply {
          // The field used to be declared as java.util.HashMap<java.lang.String, ?[]>.
          add("map", hashMapOf("k" to arrayOf<Any>(1, "a")), typeOf<String>(), typeOf<Array<*>>())
          eval("""map.get("k").length""") shouldBe 2
        }
      }
    }

    "a value with no nameable class for its type arguments is bound as an Object" {
      JavaScript().use {
        it.apply {
          add("pair", InternalPair(1, "a"), typeOf<Int>(), typeOf<String>())
          // The field used to be declared as java.lang.Object<java.lang.Integer, java.lang.String>.
          eval("pair.toString()") shouldBe "[1]"
          eval("0") shouldBe 0
        }
      }
    }

    "a non-generic subclass of a generic class is declared as itself" {
      JavaScript().use {
        it.apply {
          add("props", Properties().apply { setProperty("k", "v") })
          eval("""props.getProperty("k")""") shouldBe "v"
        }
      }
    }

    "evalScript assigns each variable to its field and prepends the imports" {
      JavaScript().use {
        it.apply {
          add("count", 41)
          add("names", mutableListOf("a", "b"), typeOf<String>())
          import(ArrayList::class.java)
          evalScript(
            """
            public class Main {
              public int count;
              public ArrayList<String> names;

              public Object getValue() {
                return count + names.size();
              }
            }
            """.trimIndent(),
          ) shouldBe 43
          importDecls shouldBe "import java.util.ArrayList;"
        }
      }
    }

    "verbose evaluation logs the generated source and returns the same result" {
      JavaScript().use {
        it.apply {
          add("x", 20)
          eval("x + 1", verbose = true) shouldBe 21
          eval("x + 2", "x = x * 2;", verbose = true) shouldBe 42
          val script = "public class Main { public int x; public Object getValue() { return x; } }"
          evalScript(script, verbose = true) shouldBe 40
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

    "a pooled instance does not keep the previous borrower's variables" {
      withTimeout(60_000.milliseconds) {
        JavaScriptPool(1).use { pool ->
          pool.eval {
            add("x", 99)
            eval("x")
          } shouldBe 99
          pool.eval {
            varDecls shouldBe ""
            shouldThrow<ScriptException> { eval("x") }
          }
        }
      }
    }

    "a pooled instance does not keep the previous borrower's imports or isolation" {
      withTimeout(60_000.milliseconds) {
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
