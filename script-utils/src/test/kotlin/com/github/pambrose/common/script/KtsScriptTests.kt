package com.github.pambrose.common.script

import org.amshove.kluent.invoking
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import javax.script.ScriptException
import kotlin.reflect.typeOf

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
  fun valVarTest() {
    val intVal = 0
    val intVar = 0

    KtsScript()
      .apply {
        add("intVal", intVal)
        add("intVar", intVar)

        intVal shouldEqual eval("intVal")
        intVal + 1 shouldEqual eval("intVal + 1")
        intVal shouldEqual 0

        intVar shouldEqual eval("intVar")
        intVar + 1 shouldEqual eval("intVar + 1")
        intVar shouldEqual 0
      }
  }

  @Test
  fun userObjectTest() {
    val aux = AuxClass()

    KtsScript()
      .apply {

        add("aux", aux)

        aux.i shouldEqual eval("aux.i")

        val incEd =
          eval(
              """
          repeat(100) { aux.inc() }
          aux
        """
          ) as AuxClass

        aux.i shouldEqualTo 100
        aux.i shouldEqualTo incEd.i
      }
  }

  @Test
  fun objectWithKClassTest() {
    val list = mutableListOf(1)
    val map = mutableMapOf("k1" to 1)

    KtsScript()
      .apply {
        add("list", list, typeOf<Int>())
        add("map", map, typeOf<String>(), typeOf<Int>())

        list.size shouldEqual eval("list.size")
        map.size shouldEqual eval("map.size")

        val incEd =
          eval("""
          map["k2"] = 10
          repeat(100) { list.add(it) }
          list
        """
          ) as List<*>

        list.size shouldEqual 101
        list.size shouldEqual eval("list.size")
        list.size shouldEqual incEd.size

        map.size shouldEqual eval("map.size")
        map.size shouldEqual 2
        map["k2"] shouldEqual 10
      }
  }

  @Test
  fun objectWithKTypeTest() {
    val list = mutableListOf(1)

    KtsScript()
      .apply {
        add("list", list, typeOf<Int>())

        list.size shouldEqual eval("list.size")

        val incEd =
          eval(
              """
          repeat(100) { list.add(it) }
          list
        """
          ) as List<*>

        list.size shouldEqual 101
        list.size shouldEqual eval("list.size")
        list.size shouldEqual incEd.size
      }
  }

  @Test
  fun nullObjectTest() {
    val list = mutableListOf<Int?>()

    KtsScript()
      .apply {
        add("list", list, typeOf<Int?>())

        varDecls shouldEqual "val list = bindings[\"list\"] as java.util.ArrayList<Int?>"

        list.size shouldEqual eval("list.size")

        val incEd =
          eval(
              """
          repeat(100) { list.add(null) }
          list
        """
          ) as List<*>

        list.size shouldEqual 100
        list.size shouldEqual eval("list.size")
        list.size shouldEqual incEd.size
      }
  }

  @Test
  fun innerClassTest() {
    class InnerTest

    val inner = InnerTest()

    KtsScript()
      .apply {
        invoking { add("inner", inner) } shouldThrow ScriptException::class
      }
  }

  @Test
  fun unnecesssaryParamsTest() {
    val value = 5

    KtsScript()
      .apply {
        invoking { add("value", value, typeOf<Int?>()) } shouldThrow ScriptException::class
      }
  }

  @Test
  fun unmatchedParamsTest() {
    val list = mutableListOf(1)

    KtsScript()
      .apply {
        invoking { add("list", list, typeOf<Int?>(), typeOf<Int>()) } shouldThrow ScriptException::class
      }
  }

  @Test
  fun missingCollectionTypeTest() {
    val list = mutableListOf(1)

    KtsScript()
      .apply {
        invoking { add("list", list) } shouldThrow ScriptException::class
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
        invoking { eval("com.lang.System.exit(1)") } shouldThrow ScriptException::class
      }
  }
}