package com.github.pambrose.common.script

import org.amshove.kluent.invoking
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import javax.script.ScriptException

class AuxClass(var i: Int = 0) {
  fun inc() {
    i++
  }
}

class KtsScriptTests {

  @Test
  fun builtInTypesTest() {
    val intVal = 0
    val longVal = 0L
    val doubleVal = 0.0
    val floatVal = 0.0F
    val strVal = "A String"

    KtsScript()
      .apply {
        add("intVal", intVal)
        add("longVal", longVal)
        add("doubleVal", doubleVal)
        add("floatVal", floatVal)
        add("strVal", strVal)

        intVal shouldEqual eval("intVal")
        intVal + 1 shouldEqual eval("intVal + 1")

        longVal shouldEqual eval("longVal")
        longVal + 1 shouldEqual eval("longVal + 1")

        doubleVal shouldEqual eval("doubleVal")
        doubleVal + 1 shouldEqual eval("doubleVal + 1")

        floatVal shouldEqual eval("floatVal")
        floatVal + 1 shouldEqual eval("floatVal + 1")

        strVal shouldEqual eval("strVal")
        strVal.length shouldEqual eval("strVal.length")
      }
  }

  @Test
  fun userObjectTest() {
    val aux = AuxClass()

    KtsScript()
      .apply {
        add("aux", aux)

        aux.i shouldEqual eval("aux.i")

        val inc_ed =
          eval(
              """
          repeat(100) { aux.inc() }
          aux
        """
          ) as AuxClass

        aux.i shouldEqualTo 100
        aux.i shouldEqualTo inc_ed.i
      }
  }

  @Test
  fun jdkObjectTest() {
    val list = mutableListOf(1)

    KtsScript()
      .apply {
        add("list", list, Int::class)

        list.size shouldEqual eval("list.size")

        val inc_ed =
          eval(
              """
          repeat(100) { list.add(it) }
          list
        """
          ) as List<Int>

        list.size shouldEqual 101
        list.size shouldEqual eval("list.size")
        list.size shouldEqual inc_ed.size
      }
  }

  @Test
  fun missingCollectionTypeTest() {
    val list = mutableListOf(1)

    KtsScript()
      .apply {
        add("list", list)

        invoking { list.size shouldEqual eval("list.size") } shouldThrow ScriptException::class
      }
  }


  @Test
  fun invalidSyntaxTest() {
    KtsScript()
      .apply {
        invoking { eval("junk") } shouldThrow ScriptException::class
      }
  }

  @Test
  fun illegalCallsTest() {
    KtsScript()
      .apply {
        invoking { eval("System.exit(1)") } shouldThrow ScriptException::class
      }
  }
}