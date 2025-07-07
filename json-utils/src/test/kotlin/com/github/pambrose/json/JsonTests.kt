package com.github.pambrose.json

import com.github.pambrose.common.json.booleanValue
import com.github.pambrose.common.json.booleanValueOrNull
import com.github.pambrose.common.json.containsKeys
import com.github.pambrose.common.json.doubleValue
import com.github.pambrose.common.json.doubleValueOrNull
import com.github.pambrose.common.json.intValue
import com.github.pambrose.common.json.intValueOrNull
import com.github.pambrose.common.json.jsonElementList
import com.github.pambrose.common.json.jsonObjectValue
import com.github.pambrose.common.json.jsonObjectValueOrNull
import com.github.pambrose.common.json.stringValue
import com.github.pambrose.common.json.stringValueOrNull
import com.github.pambrose.common.json.toJsonElement
import com.github.pambrose.common.json.toJsonString
import com.github.pambrose.common.json.toMap
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_BOOLEAN
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_BOOLEAN_LIST
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_DOUBLE
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_DOUBLE_LIST
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_INT
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_INT_LIST
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_STRING
import com.github.pambrose.json.BasicObject2.Companion.DEFAULT_STRING_LIST
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Test

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
    json1.containsKeys("boolVal") shouldBe true
    json1.containsKeys("missing") shouldBe false

    json2.containsKeys("boolVal") shouldBe true
    json2.containsKeys("missing") shouldBe false
  }

  @Test
  fun `Primitive types`() {
    json1.booleanValue("boolVal") shouldBe DEFAULT_BOOLEAN
    json1.stringValue("strVal") shouldBe DEFAULT_STRING
    json1.intValue("intVal") shouldBe DEFAULT_INT
    json1.doubleValue("doubleVal") shouldBe DEFAULT_DOUBLE

    json2.booleanValue("boolVal") shouldBe DEFAULT_BOOLEAN
    json2.stringValue("strVal") shouldBe DEFAULT_STRING
    json2.intValue("intVal") shouldBe DEFAULT_INT
    json2.doubleValue("doubleVal") shouldBe DEFAULT_DOUBLE
  }

  @Test
  fun `Invalid primitive types`() {
    json1.jsonObjectValueOrNull("missing") shouldBe null
    json1.booleanValueOrNull("missing") shouldBe null
    json1.stringValueOrNull("missing") shouldBe null
    json1.intValueOrNull("missing") shouldBe null
    json1.doubleValueOrNull("missing") shouldBe null

    json2.jsonObjectValueOrNull("missing") shouldBe null
    json2.booleanValueOrNull("missing") shouldBe null
    json2.stringValueOrNull("missing") shouldBe null
    json2.intValueOrNull("missing") shouldBe null
    json2.doubleValueOrNull("missing") shouldBe null
  }

  @Test
  fun `Primitive types via maps`() {
    val map1 = json1.toMap()
    map1["boolVal"].toString().toBoolean() shouldBe DEFAULT_BOOLEAN
    map1["strVal"] shouldBe DEFAULT_STRING
    map1["intVal"].toString().toInt() shouldBe DEFAULT_INT
    map1["doubleVal"].toString().toDouble() shouldBe DEFAULT_DOUBLE

    val map2 = json2.toMap()
    map2["boolVal"].toString().toBoolean() shouldBe DEFAULT_BOOLEAN
    map2["strVal"] shouldBe DEFAULT_STRING
    map2["intVal"].toString().toInt() shouldBe DEFAULT_INT
    map2["doubleVal"].toString().toDouble() shouldBe DEFAULT_DOUBLE
  }

  @Test
  fun `Object types`() {
    listOf(json1, json2).forEach { json ->
      val objVal = json.jsonObjectValue("objectVal")
      objVal.booleanValue("boolVal") shouldBe DEFAULT_BOOLEAN
      objVal.stringValue("strVal") shouldBe DEFAULT_STRING
      objVal.intValue("intVal") shouldBe DEFAULT_INT
      objVal.doubleValue("doubleVal") shouldBe DEFAULT_DOUBLE
    }
  }

  @Test
  fun `List types`() {
    listOf(json1, json2).forEach { json ->
      json.jsonElementList("boolList").map { it.booleanValue } shouldBe DEFAULT_BOOLEAN_LIST
      json.jsonElementList("strList").map { it.stringValue } shouldBe DEFAULT_STRING_LIST
      json.jsonElementList("intList").map { it.intValue } shouldBe DEFAULT_INT_LIST
      json.jsonElementList("doubleList").map { it.doubleValue } shouldBe DEFAULT_DOUBLE_LIST
    }
  }

  @Test
  fun `Embedded object types`() {
    listOf(json1, json2).forEach { json ->
      json.booleanValue("objectVal.boolVal") shouldBe DEFAULT_BOOLEAN
      json.stringValue("objectVal.strVal") shouldBe DEFAULT_STRING
      json.intValue("objectVal.intVal") shouldBe DEFAULT_INT
      json.doubleValue("objectVal.doubleVal") shouldBe DEFAULT_DOUBLE
    }
  }
}
