package com.github.pambrose.common.json.json

import com.github.pambrose.common.json.common.JsonContentUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val JsonElement.keys get() = jsonObject.keys

// Primitive values
val JsonElement.stringValue get() = jsonPrimitive.content
val JsonElement.intValue get() = jsonPrimitive.content.toInt()
val JsonElement.doubleValue get() = jsonPrimitive.content.toDouble()
val JsonElement.booleanValue get() = jsonPrimitive.content.toBoolean()

// Json object values
val JsonElement.jsonObjectValue: JsonObject get() = jsonObject

operator fun JsonElement.get(vararg keys: String): JsonElement =
  keys.flatMap { it.split(".") }
    .fold(this) { acc, key -> acc.element(key) }

fun JsonElement.getOrNull(vararg keys: String): JsonElement? = if (containsKey(*keys)) get(*keys) else null

// Primitive values
fun JsonElement.stringValue(vararg keys: String) = get(*keys).stringValue

fun JsonElement.stringValueOrNull(vararg keys: String) = getOrNull(*keys)?.stringValue

fun JsonElement.intValue(vararg keys: String) = get(*keys).intValue

fun JsonElement.intValueOrNull(vararg keys: String) = getOrNull(*keys)?.intValue

fun JsonElement.doubleValue(vararg keys: String) = get(*keys).doubleValue

fun JsonElement.doubleValueOrNull(vararg keys: String) = getOrNull(*keys)?.doubleValue

fun JsonElement.booleanValue(vararg keys: String) = get(*keys).booleanValue

fun JsonElement.booleanValueOrNull(vararg keys: String) = getOrNull(*keys)?.booleanValue

// Object values
fun JsonElement.jsonObjectValue(vararg keys: String): JsonObject = get(*keys).jsonObjectValue

fun JsonElement.jsonObjectValueOrNull(vararg keys: String) = getOrNull(*keys)?.jsonObjectValue

// Array values
fun JsonElement.jsonElementList(vararg keys: String) = get(*keys).toJsonElementList()

fun JsonElement.jsonElementListOrNull(vararg keys: String) = getOrNull(*keys)?.toJsonElementList()

fun JsonElement.containsKey(vararg keys: String): Boolean {
  val ks = keys.flatMap { it.split(".") }
  var currElement: JsonElement = this
  for (k in ks) {
    if (currElement is JsonObject && k in currElement.keys)
      currElement = (currElement as JsonElement)[k]
    else
      return false
  }
  return true
}

val JsonElement.size get() = jsonObject.size

fun JsonElement.isEmpty() = if (this is JsonPrimitive) true else jsonObject.isEmpty()

fun JsonElement.isNotEmpty() = !isEmpty()

fun String.toJsonString() = toJsonElement().toJsonString(true)

inline fun <reified T> T.toJsonString(prettyPrint: Boolean = true) =
  (if (prettyPrint) JsonContentUtils.prettyFormat else JsonContentUtils.rawFormat).encodeToString(this)

inline fun <reified T> T.toJsonElement() = Json.encodeToJsonElement(this)

fun String.toJsonElement() = Json.parseToJsonElement(this)

fun JsonElement.toJsonElementList() = jsonArray.toList()

fun JsonElement.toMap(): Map<String, Any?> {
  require(this is JsonObject) { "Can only convert JsonObject to Map, not a ${this.javaClass.simpleName}" }

  return entries.associate { (key, value) ->
    key to when (value) {
      is JsonPrimitive -> value.content
      is JsonArray -> value.map {
        when (it) {
          is JsonPrimitive -> it.content
          else -> it.toMap()
        }
      }

      is JsonObject -> value.toMap()
      JsonNull -> null
    }
  }
}

internal fun JsonElement.element(key: String) =
  elementOrNull(key) ?: throw IllegalArgumentException("""JsonElement key "$key" not found""")

private fun JsonElement.elementOrNull(key: String) = jsonObject.get(key)
