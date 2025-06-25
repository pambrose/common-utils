package com.github.pambrose.json

import com.github.pambrose.common.json.json.booleanValue
import com.github.pambrose.common.json.json.booleanValueOrNull
import com.github.pambrose.common.json.json.containsKey
import com.github.pambrose.common.json.json.doubleValue
import com.github.pambrose.common.json.json.doubleValueOrNull
import com.github.pambrose.common.json.json.intValue
import com.github.pambrose.common.json.json.intValueOrNull
import com.github.pambrose.common.json.json.jsonElementList
import com.github.pambrose.common.json.json.jsonObjectValue
import com.github.pambrose.common.json.json.jsonObjectValueOrNull
import com.github.pambrose.common.json.json.stringValue
import com.github.pambrose.common.json.json.stringValueOrNull
import com.github.pambrose.common.json.json.toJsonElement
import com.github.pambrose.common.json.json.toJsonString
import com.github.pambrose.common.json.json.toMap
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_BOOLEAN
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_BOOLEAN_LIST
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_DOUBLE
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_DOUBLE_LIST
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_INT
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_INT_LIST
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_STRING
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_STRING_LIST
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
class BasicObject1(
  val boolVal: Boolean,
  val strVal: String,
  val intVal: Int,
  val doubleVal: Double,
)

@Serializable
class BasicObject2(
  val boolVal: Boolean,
  val strVal: String,
  val intVal: Int,
  val doubleVal: Double,
  val objectVal: BasicObject1,
  val boolList: List<Boolean>,
  val strList: List<String>,
  val intList: List<Int>,
  val doubleList: List<Double>,
  // val objList: List<BasicObject1>,
) {
  companion object {
    val DEFAULT_BOOLEAN = true
    val DEFAULT_STRING = "Default str value"
    val DEFAULT_INT = 1234
    val DEFAULT_DOUBLE = 5678.9
    val DEFAULT_BOOLEAN_LIST = List(5) { DEFAULT_BOOLEAN }
    val DEFAULT_STRING_LIST = List(5) { DEFAULT_STRING }
    val DEFAULT_INT_LIST = List(5) { DEFAULT_INT }
    val DEFAULT_DOUBLE_LIST = List(5) { DEFAULT_DOUBLE }
  }
}

val l = List(5) { it -> it }

class JsonTests {
  val obj = BasicObject2(
    DEFAULT_BOOLEAN,
    DEFAULT_STRING,
    DEFAULT_INT,
    DEFAULT_DOUBLE,
    BasicObject1(DEFAULT_BOOLEAN, DEFAULT_STRING, DEFAULT_INT, DEFAULT_DOUBLE),
    DEFAULT_BOOLEAN_LIST,
    DEFAULT_STRING_LIST,
    DEFAULT_INT_LIST,
    DEFAULT_DOUBLE_LIST,
  )
  val json1: JsonElement = obj.toJsonString(true).toJsonElement()
  val json2: JsonElement = obj.toJsonElement()

  @Test
  fun `Check for keys`() {
    assertEquals(true, json1.containsKey("boolVal"))
    assertEquals(false, json1.containsKey("missing"))

    assertEquals(true, json2.containsKey("boolVal"))
    assertEquals(false, json2.containsKey("missing"))
  }

  @Test
  fun `Primitive types`() {
    assertEquals(DEFAULT_BOOLEAN, json1.booleanValue("boolVal"))
    assertEquals(DEFAULT_STRING, json1.stringValue("strVal"))
    assertEquals(DEFAULT_INT, json1.intValue("intVal"))
    assertEquals(DEFAULT_DOUBLE, json1.doubleValue("doubleVal"))

    assertEquals(DEFAULT_BOOLEAN, json2.booleanValue("boolVal"))
    assertEquals(DEFAULT_STRING, json2.stringValue("strVal"))
    assertEquals(DEFAULT_INT, json2.intValue("intVal"))
    assertEquals(DEFAULT_DOUBLE, json2.doubleValue("doubleVal"))
  }

  @Test
  fun `Invalid primitive types`() {
    assertEquals(null, json1.jsonObjectValueOrNull("missing"))
    assertEquals(null, json1.booleanValueOrNull("missing"))
    assertEquals(null, json1.stringValueOrNull("missing"))
    assertEquals(null, json1.intValueOrNull("missing"))
    assertEquals(null, json1.doubleValueOrNull("missing"))

    assertEquals(null, json2.jsonObjectValueOrNull("missing"))
    assertEquals(null, json2.booleanValueOrNull("missing"))
    assertEquals(null, json2.stringValueOrNull("missing"))
    assertEquals(null, json2.intValueOrNull("missing"))
    assertEquals(null, json2.doubleValueOrNull("missing"))
  }

  @Test
  fun `Primitive types via maps`() {
    val map1 = json1.toMap()
    assertEquals(DEFAULT_BOOLEAN, map1["boolVal"].toString().toBoolean())
    assertEquals(DEFAULT_STRING, map1["strVal"])
    assertEquals(DEFAULT_INT, map1["intVal"].toString().toInt())
    assertEquals(DEFAULT_DOUBLE, map1["doubleVal"].toString().toDouble())

    val map2 = json2.toMap()
    assertEquals(DEFAULT_BOOLEAN, map2["boolVal"].toString().toBoolean())
    assertEquals(DEFAULT_STRING, map2["strVal"])
    assertEquals(DEFAULT_INT, map2["intVal"].toString().toInt())
    assertEquals(DEFAULT_DOUBLE, map2["doubleVal"].toString().toDouble())
  }

  @Test
  fun `Object types`() {
    listOf(json1, json2).forEach { json ->
      val objVal = json.jsonObjectValue("objectVal")
      assertEquals(DEFAULT_BOOLEAN, objVal.booleanValue("boolVal"))
      assertEquals(DEFAULT_STRING, objVal.stringValue("strVal"))
      assertEquals(DEFAULT_INT, objVal.intValue("intVal"))
      assertEquals(DEFAULT_DOUBLE, objVal.doubleValue("doubleVal"))
    }
  }

  @Test
  fun `List types`() {
    listOf(json1, json2).forEach { json ->
      assertEquals(DEFAULT_BOOLEAN_LIST, json.jsonElementList("boolList").map { it.booleanValue })
      assertEquals(DEFAULT_STRING_LIST, json.jsonElementList("strList").map { it.stringValue })
      assertEquals(DEFAULT_INT_LIST, json.jsonElementList("intList").map { it.intValue })
      assertEquals(DEFAULT_DOUBLE_LIST, json.jsonElementList("doubleList").map { it.doubleValue })
    }
  }

  @Test
  fun `Embedded object types`() {
    listOf(json1, json2).forEach { json ->
      assertEquals(DEFAULT_BOOLEAN, json.booleanValue("objectVal.boolVal"))
      assertEquals(DEFAULT_STRING, json.stringValue("objectVal.strVal"))
      assertEquals(DEFAULT_INT, json.intValue("objectVal.intVal"))
      assertEquals(DEFAULT_DOUBLE, json.doubleValue("objectVal.doubleVal"))
    }
  }
}
