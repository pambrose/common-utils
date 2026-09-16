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
      val list: MutableList<Int> = [1]
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
      val list: MutableList<Int> = [1]

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
      val list: MutableList<Int?> = []

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

    "Python exit calls are rejected before evaluation" {
      // Each call sits in a function that is never called, so without the guard the definition would just succeed.
      // Run, the calls would raise SystemExit, which the engine also reports as a ScriptException, so only the
      // guard's own message shows that the guard rejected them.
      PythonScript().use { script ->
        listOf(
          "def stop():\n  import sys\n  sys.exit(1)" to "Illegal call to sys.exit()",
          "def stop():\n  exit(1)" to "Illegal call to exit()",
          "def stop():\n  exit (1)" to "Illegal call to exit()",
          "def stop():\n  quit(1)" to "Illegal call to quit()",
        ).forEach { (code, message) ->
          shouldThrow<ScriptException> { script.eval(code) }.message shouldBe message
        }
      }
    }

    "raise SystemExit is rejected but referencing the type is allowed" {
      PythonScript().use {
        it.apply {
          // `raise SystemExit` is exactly what sys.exit()/exit()/quit() do under the hood. The guard
          // rejects it before evaluation, with its own message rather than Jython's SystemExit error.
          listOf("raise SystemExit", "raise SystemExit(0)", "raise SystemExit('bye')").forEach { code ->
            shouldThrow<ScriptException> { eval("def stop():\n  $code") }.message shouldBe "Illegal 'raise SystemExit'"
          }
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

    "expr evaluator keeps working after failed expressions" {
      PythonExprEvaluator().use { evaluator ->
        repeat(ITERATIONS) { i ->
          shouldThrow<ScriptException> { evaluator.eval("$i == [wrong]") }
          evaluator.eval("$i == $i") shouldBe true
          evaluator.eval("$i == ${i + 1}") shouldBe false
        }
      }
    }

    "pool expr evaluator keeps working after failed expressions" {
      PythonExprEvaluatorPool(2).use { pool ->
        repeat(ITERATIONS) { i ->
          shouldThrow<ScriptException> { pool.blockingEval("$i == [wrong]") }
          pool.blockingEval("$i == $i") shouldBe true
          pool.blockingEval("$i == ${i + 1}") shouldBe false
        }
      }
    }

    "expr evaluator names the type of a result that is not a Boolean" {
      PythonExprEvaluator().use { evaluator ->
        evaluator.compute("1 + 2") shouldBe 3
        shouldThrow<IllegalArgumentException> { evaluator.eval("1 + 2") }.message shouldBe
          "Expression did not evaluate to Boolean, got Integer"
        shouldThrow<IllegalArgumentException> { evaluator.eval("'s'") }.message shouldBe
          "Expression did not evaluate to Boolean, got String"
        shouldThrow<IllegalArgumentException> { evaluator.eval("None") }.message shouldBe
          "Expression did not evaluate to Boolean, got null"
      }
    }

    "JVM termination through java.lang is rejected" {
      PythonScript().use {
        it.apply {
          // Each call is inside a function that is never called, so an unguarded script returns instead of
          // terminating the test JVM.
          shouldThrow<ScriptException> {
            eval(
              """
              def shutdown():
                from java.lang import System
                System.exit(0)
              """.trimIndent(),
            )
          }.message shouldContain JVM_EXIT_MESSAGE
          shouldThrow<ScriptException> {
            eval(
              """
              def stop():
                from java.lang import Runtime
                Runtime.getRuntime().halt(0)
              """.trimIndent(),
            )
          }.message shouldContain JVM_EXIT_MESSAGE
        }
      }
    }

    "methods named exit or quit can be defined" {
      PythonScript().use {
        it.apply {
          shouldNotThrow<ScriptException> {
            eval(
              """
              class Door:
                def exit(self):
                  return 1
                def quit(self):
                  return 2
              """.trimIndent(),
            )
          }
          eval("Door().exit() + Door().quit()") shouldBe 3
        }
      }
    }

    "a variable added after an evaluation is bound for the next one" {
      PythonScript().use {
        it.apply {
          add("a", 1)
          eval("a") shouldBe 1
          add("b", 2)
          eval("a + b") shouldBe 3
        }
      }
    }

    "a variable added again replaces the earlier value" {
      PythonScript().use {
        it.apply {
          add("x", 1)
          eval("x") shouldBe 1
          add("x", "s")
          eval("x") shouldBe "s"
        }
      }
    }

    "variable names must be valid Python identifiers" {
      PythonScript().use { script ->
        ["my-var", "print", "x = 1"].forEach { name ->
          shouldThrow<ScriptException> { script.add(name, 5) }
        }
      }
    }

    "the Python evaluator rejects literal termination calls" {
      PythonExprEvaluator().use { evaluator ->
        // The lambdas are never called, so an unguarded evaluator returns True instead of exiting.
        shouldThrow<ScriptException> { evaluator.eval("(lambda: System.exit(0)) is not None") }.message shouldContain
          JVM_EXIT_MESSAGE
        shouldThrow<ScriptException> { evaluator.eval("(lambda: sys.exit(0)) is not None") }.message shouldBe
          "Illegal call to sys.exit()"
      }
    }
  }

  companion object {
    private const val JVM_EXIT_MESSAGE = "Illegal call to a JVM termination method"

    // A few iterations are enough to show that failed expressions leave the engine usable.
    private const val ITERATIONS = 5
  }
}
