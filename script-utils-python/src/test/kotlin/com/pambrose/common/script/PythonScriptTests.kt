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
import io.kotest.matchers.string.shouldContain
import javax.script.ScriptException

class IncClass(
  var i: Int = 0,
) {
  fun inc() {
    i++
  }
}

class Exitable {
  var calls = 0
    private set

  fun exit() {
    calls++
  }

  fun quit() {
    calls++
  }
}

class PythonScriptTests : StringSpec() {
  init {
    "built in types" {
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

    "user object" {
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

    "object with class" {
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

    "list compare" {
      PythonScript().use {
        it.apply {
          eval("[True] == [True]") shouldBe true
        }
      }
    }

    "object with type" {
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

    "null object" {
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

    "invalid syntax" {
      PythonScript().use { script ->
        shouldThrow<ScriptException> { script.eval("junk") }
      }
    }

    "illegal calls" {
      PythonScript().use {
        it.apply {
          shouldThrow<ScriptException> { eval("sys.exit(1)") }
          shouldThrow<ScriptException> { eval("exit(1)") }
          shouldThrow<ScriptException> { eval("quit(1)") }
          shouldThrow<ScriptException> { eval("exit (1)") }
        }
      }
    }

    "raise SystemExit is rejected but referencing the type is allowed" {
      PythonScript().use {
        it.apply {
          // `raise SystemExit` is exactly what sys.exit()/exit()/quit() do under the hood. The guard
          // rejects it before evaluation; its message starts with "Illegal" (vs. a Jython runtime error).
          shouldThrow<ScriptException> { eval("raise SystemExit") }.message shouldContain "Illegal"
          shouldThrow<ScriptException> { eval("raise SystemExit(0)") }
          shouldThrow<ScriptException> { eval("raise SystemExit('bye')") }
          // Catching it (no `raise`) is legitimate and must not be flagged.
          shouldNotThrow<ScriptException> { eval("try:\n  pass\nexcept SystemExit:\n  pass") }
        }
      }
    }

    "exit guards do not match identifiers containing exit/quit substrings" {
      PythonScript().use {
        it.apply {
          shouldNotThrow<ScriptException> {
            eval(
              """
                  def my_exit(x):
                    return x

                  def quit_handler(x):
                    return x + 1

                  def sys_exit_wrapper(x):
                    return x
              """.trimIndent(),
            )
          }
          eval("my_exit(7)") shouldBe 7
          eval("quit_handler(10)") shouldBe 11
          eval("sys_exit_wrapper(3)") shouldBe 3
        }
      }
    }

    "exit guards allow exit/quit method calls on bound objects" {
      // A method call via `.` (e.g. obj.exit()) is a user method, not the Python builtin, so it must
      // not be rejected. Previously the (?<!\w) lookbehind wrongly matched it because `.` is not a word char.
      val widget = Exitable()
      PythonScript().use {
        it.apply {
          add("widget", widget)
          shouldNotThrow<ScriptException> { eval("widget.exit()") }
          shouldNotThrow<ScriptException> { eval("widget.quit()") }
        }
      }
      // Both calls were allowed through the guard and actually executed on the bound object.
      widget.calls shouldBe 2
    }

    "expr evaluator" {
      PythonExprEvaluator()
        .apply {
          repeat(200) { i ->
            // println("Invocation: $i")
            shouldThrow<ScriptException> { eval("$i == [wrong]") }
            shouldNotThrow<ScriptException> { eval("$i == $i") }
          }
        }
    }

    "pool expr evaluator" {
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
}
