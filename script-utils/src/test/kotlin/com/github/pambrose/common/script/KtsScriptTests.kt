package com.github.pambrose.common.script

import org.amshove.kluent.invoking
import org.amshove.kluent.shouldBeEqualTo
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

        intVal shouldBeEqualTo eval("intVal")
        intVal + 1 shouldBeEqualTo eval("intVal + 1")

        longVal shouldBeEqualTo eval("longVal")
        longVal + 1 shouldBeEqualTo eval("longVal + 1")

        doubleVal shouldBeEqualTo eval("doubleVal")
        doubleVal + 1 shouldBeEqualTo eval("doubleVal + 1")

        floatVal shouldBeEqualTo eval("floatVal")
        floatVal + 1 shouldBeEqualTo eval("floatVal + 1")

        strVal shouldBeEqualTo eval("strVal")
        strVal.length shouldBeEqualTo eval("strVal.length")
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

    KtsScript()
      .apply {

        add("aux", aux)

        aux.i shouldBeEqualTo eval("aux.i")

        val incEd =
          eval(
              """
          repeat(100) { aux.inc() }
          aux
        """
          ) as AuxClass

        aux.i shouldBeEqualTo 100
        aux.i shouldBeEqualTo incEd.i
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

        list.size shouldBeEqualTo eval("list.size")
        map.size shouldBeEqualTo eval("map.size")

        val incEd =
          eval("""
          map["k2"] = 10
          repeat(100) { list.add(it) }
          list
        """
          ) as List<*>

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("list.size")
        list.size shouldBeEqualTo incEd.size

        map.size shouldBeEqualTo eval("map.size")
        map.size shouldBeEqualTo 2
        map["k2"] shouldBeEqualTo 10
      }
  }

  @Test
  fun objectWithKTypeTest() {
    val list = mutableListOf(1)

    KtsScript()
      .apply {
        add("list", list, typeOf<Int>())

        list.size shouldBeEqualTo eval("list.size")

        val incEd =
          eval(
              """
          repeat(100) { list.add(it) }
          list
        """
          ) as List<*>

        list.size shouldBeEqualTo 101
        list.size shouldBeEqualTo eval("list.size")
        list.size shouldBeEqualTo incEd.size
      }
  }

  @Test
  fun nullObjectTest() {
    val list = mutableListOf<Int?>()

    KtsScript()
      .apply {
        add("list", list, typeOf<Int?>())

        varDecls shouldBeEqualTo "val list = bindings[\"list\"] as java.util.ArrayList<Int?>"

        list.size shouldBeEqualTo eval("list.size")

        val incEd =
          eval(
              """
          repeat(100) { list.add(null) }
          list
        """
          ) as List<*>

        list.size shouldBeEqualTo 100
        list.size shouldBeEqualTo eval("list.size")
        list.size shouldBeEqualTo incEd.size
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