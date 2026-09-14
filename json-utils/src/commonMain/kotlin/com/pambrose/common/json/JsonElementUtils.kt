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

package com.pambrose.common.json

import com.pambrose.common.json.JsonDefaults.json
import com.pambrose.common.util.simpleClassName
import io.github.oshai.kotlinlogging.KotlinLogging
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

private val logger = KotlinLogging.logger {}

/** Returns the set of keys if this [JsonElement] is a [JsonObject]. Extension property on [JsonElement]. */
val JsonElement.keys get() = jsonObject.keys

// Primitive values

/**
 * Returns the string content of this [JsonElement] as a [JsonPrimitive]. Extension property on [JsonElement].
 *
 * @throws IllegalArgumentException if this element is JSON `null` or not a primitive
 */
val JsonElement.stringValue get() = nonNullPrimitive().content

/**
 * Returns the integer value of this [JsonElement]'s primitive content. Extension property on [JsonElement].
 *
 * @throws IllegalArgumentException if this element is JSON `null`, not a primitive, or not an integer
 */
val JsonElement.intValue get() = nonNullPrimitive().content.toInt()

/**
 * Returns the double value of this [JsonElement]'s primitive content. Extension property on [JsonElement].
 *
 * @throws IllegalArgumentException if this element is JSON `null`, not a primitive, or not a number
 */
val JsonElement.doubleValue get() = nonNullPrimitive().content.toDouble()

/**
 * Returns the boolean value of this [JsonElement]'s primitive content. Extension property on [JsonElement].
 *
 * @throws IllegalArgumentException if this element is JSON `null`, not a primitive, or not `true` or `false`
 */
val JsonElement.booleanValue
  get() =
    nonNullPrimitive().content.let { content ->
      content.toBooleanStrictOrNull() ?: throw IllegalArgumentException("JSON value \"$content\" is not a boolean")
    }

// JSON null is a JsonPrimitive whose content is the string "null", so it has to be excluded explicitly.
private fun JsonElement.nonNullPrimitive(): JsonPrimitive {
  require(this !is JsonNull) { "JSON value is null" }
  return jsonPrimitive
}

private fun JsonElement.primitiveOrNull(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }

// Json object values

/** Returns this [JsonElement] as a [JsonObject]. Extension property on [JsonElement]. */
val JsonElement.jsonObjectValue: JsonObject get() = jsonObject

/** Returns `true` if this [JsonElement] is a [JsonObject]. Extension property on [JsonElement]. */
val JsonElement.isObject get() = this is JsonObject

/** Returns `true` if this [JsonElement] is a [JsonArray]. Extension property on [JsonElement]. */
val JsonElement.isArray get() = this is JsonArray

/** Returns `true` if this [JsonElement] is a [JsonPrimitive]. Extension property on [JsonElement]. */
val JsonElement.isPrimitive get() = this is JsonPrimitive

/** Returns `true` if this [JsonElement] is a string [JsonPrimitive]. Extension property on [JsonElement]. */
val JsonElement.isString get() = this is JsonPrimitive && jsonPrimitive.isString

/**
 * Returns `true` if this [JsonElement] is an unquoted numeric [JsonPrimitive]. Extension property on [JsonElement].
 *
 * A quoted numeric string such as `"42"` is a string, not a number, and JSON `null` is neither.
 */
val JsonElement.isNumber get() = this is JsonPrimitive && this !is JsonNull && !isString &&
  content.toDoubleOrNull() != null

// Keys are split on '.'; empty segments (from "a..b", a leading or trailing '.', or "") are ignored.
private fun pathSegments(keys: Array<out String>) = keys.flatMap { it.split(".") }.filter { it.isNotEmpty() }

/**
 * Traverses this [JsonElement] using a slash-delimited path (e.g., `"a/b/c"`).
 *
 * Extension function on [JsonElement]. Empty segments are ignored. Unlike [get], `.` is not a separator, so this
 * also reaches keys that contain a dot.
 *
 * @param path a `/`-separated path of object keys
 * @return the [JsonElement] at the given path, or `null` if any key is missing
 */
fun JsonElement.getByPath(path: String): JsonElement? =
  path.split("/")
    .filter { it.isNotEmpty() }
    .fold(this as JsonElement?) { acc, key ->
      acc?.jsonObject?.get(key)
    }

/**
 * Navigates into nested [JsonObject] children using dot-separated key strings.
 *
 * Extension operator on [JsonElement]. Each key in [keys] may contain dots to traverse multiple levels. Empty
 * segments are ignored, so `get("a..b")` is the same as `get("a.b")` and an empty path returns this element. A key
 * that itself contains a dot cannot be reached this way; use [getByPath], which splits on `/`.
 *
 * @param keys one or more dot-separated key paths
 * @return the [JsonElement] found by following all keys in sequence
 * @throws IllegalArgumentException if any key is not found
 */
operator fun JsonElement.get(vararg keys: String): JsonElement =
  pathSegments(keys).fold(this) { acc, key -> acc.element(key) }

/**
 * Navigates into nested children like [get], but returns `null` if any key is missing or the value is [JsonNull].
 *
 * Extension function on [JsonElement]. Empty segments are ignored, as in [get].
 *
 * @param keys one or more dot-separated key paths
 * @return the [JsonElement] at the path, or `null` if not found or [JsonNull]
 */
fun JsonElement.getOrNull(vararg keys: String): JsonElement? {
  // Walk the path once and short-circuit on the first missing key, instead of traversing twice
  // (containsKeys then get).
  var current: JsonElement? = this
  for (key in pathSegments(keys)) {
    current = (current as? JsonObject)?.get(key) ?: return null
  }
  return current?.takeIf { it != JsonNull }
}

// Primitive values

/** Navigates to the nested element at [keys] and returns its string value. Extension function on [JsonElement]. */
fun JsonElement.stringValue(vararg keys: String) = get(*keys).stringValue

/**
 * Navigates to the nested element at [keys] and returns its string value, or `null` if it is missing, JSON `null`,
 * or not a primitive. Extension function on [JsonElement].
 */
fun JsonElement.stringValueOrNull(vararg keys: String) = getOrNull(*keys)?.primitiveOrNull()?.content

/** Navigates to the nested element at [keys] and returns its integer value. Extension function on [JsonElement]. */
fun JsonElement.intValue(vararg keys: String) = get(*keys).intValue

/**
 * Navigates to the nested element at [keys] and returns its integer value, or `null` if it is missing, JSON `null`,
 * or not an integer. Extension function on [JsonElement].
 */
fun JsonElement.intValueOrNull(vararg keys: String) = getOrNull(*keys)?.primitiveOrNull()?.content?.toIntOrNull()

/** Navigates to the nested element at [keys] and returns its double value. Extension function on [JsonElement]. */
fun JsonElement.doubleValue(vararg keys: String) = get(*keys).doubleValue

/**
 * Navigates to the nested element at [keys] and returns its double value, or `null` if it is missing, JSON `null`,
 * or not a number. Extension function on [JsonElement].
 */
fun JsonElement.doubleValueOrNull(vararg keys: String) = getOrNull(*keys)?.primitiveOrNull()?.content?.toDoubleOrNull()

/** Navigates to the nested element at [keys] and returns its boolean value. Extension function on [JsonElement]. */
fun JsonElement.booleanValue(vararg keys: String) = get(*keys).booleanValue

/**
 * Navigates to the nested element at [keys] and returns its boolean value, or `null` if it is missing, JSON `null`,
 * or not `true` or `false`. Extension function on [JsonElement].
 */
fun JsonElement.booleanValueOrNull(vararg keys: String) =
  getOrNull(*keys)?.primitiveOrNull()?.content?.toBooleanStrictOrNull()

// Object values

/** Navigates to the nested element at [keys] and returns it as a [JsonObject]. Extension function on [JsonElement]. */
fun JsonElement.jsonObjectValue(vararg keys: String): JsonObject = get(*keys).jsonObjectValue

/**
 * Navigates to the nested element at [keys] and returns it as a [JsonObject], or `null` if it is missing, JSON `null`,
 * or not an object. Extension function on [JsonElement].
 */
fun JsonElement.jsonObjectValueOrNull(vararg keys: String) = getOrNull(*keys) as? JsonObject

// Array values

/** Navigates to the nested element at [keys] and returns it as a list of [JsonElement]. Extension function on [JsonElement]. */
fun JsonElement.jsonElementList(vararg keys: String) = get(*keys).toJsonElementList()

/**
 * Navigates to the nested element at [keys] and returns it as a list of [JsonElement], or `null` if it is missing,
 * JSON `null`, or not an array. Extension function on [JsonElement].
 */
fun JsonElement.jsonElementListOrNull(vararg keys: String) = (getOrNull(*keys) as? JsonArray)?.toList()

/**
 * Checks whether this [JsonElement] contains the nested path specified by [keys].
 *
 * Extension function on [JsonElement]. Each key may contain dots to traverse multiple levels; empty segments are
 * ignored, as in [get].
 *
 * @param keys one or more dot-separated key paths
 * @return `true` if all keys exist along the path
 */
fun JsonElement.containsKeys(vararg keys: String): Boolean {
  var currElement: JsonElement = this
  for (k in pathSegments(keys)) {
    if (currElement is JsonObject && k in currElement.keys)
      currElement = (currElement as JsonElement)[k]
    else
      return false
  }
  return true
}

/**
 * Iterates over [JsonObject] elements within this [JsonElement].
 *
 * Extension function on [JsonElement]. If this element is a [JsonObject], [action] is invoked once.
 * If it is a [JsonArray], [action] is invoked for each [JsonObject] element in the array.
 *
 * @param action the action to perform on each [JsonObject]
 * @throws IllegalArgumentException if this element is neither a [JsonObject] nor a [JsonArray]
 */
fun JsonElement.forEachJsonObject(action: (JsonObject) -> Unit) {
  when (this) {
    is JsonObject -> action(this)
    is JsonArray -> forEach { if (it is JsonObject) action(it) }
    else -> throw IllegalArgumentException("Not an object or array")
  }
}

/**
 * Creates a deep copy of this [JsonElement], rebuilding every nested object and array. Primitives are immutable, so
 * they are shared. Extension function on [JsonElement].
 */
fun JsonElement.deepCopy(): JsonElement =
  when (this) {
    is JsonObject -> JsonObject(mapValues { (_, value) -> value.deepCopy() })
    is JsonArray -> JsonArray(map { it.deepCopy() })
    is JsonPrimitive -> this
  }

/**
 * Returns the number of entries in a [JsonObject] or elements in a [JsonArray]. Extension property on [JsonElement].
 *
 * @throws IllegalArgumentException if this element is a primitive or JSON `null`
 */
val JsonElement.size: Int
  get() =
    when (this) {
      is JsonObject -> jsonObject.size
      is JsonArray -> jsonArray.size
      is JsonPrimitive -> throw IllegalArgumentException("A $simpleClassName has no size; only objects and arrays do")
    }

/**
 * Returns `true` if this [JsonElement] is empty: an object with no keys, an array with no elements, JSON `null`, or
 * a primitive whose content is the empty string. A whitespace-only string is not empty.
 *
 * Extension function on [JsonElement].
 */
fun JsonElement.isEmpty() =
  when (this) {
    is JsonObject -> jsonObject.isEmpty()
    is JsonArray -> jsonArray.isEmpty()
    JsonNull -> true
    is JsonPrimitive -> content.isEmpty()
  }

/** Returns `true` if this [JsonElement] is not empty. Extension function on [JsonElement]. */
fun JsonElement.isNotEmpty() = !isEmpty()

private fun prettyPrint(indent: String) =
  Json {
    prettyPrint = true
    prettyPrintIndent = indent
  }

// Cache the default (two-space) formatter so the common path doesn't rebuild a Json on every call.
private val defaultPrettyFormat by lazy { prettyPrint("  ") }

/**
 * Encodes this [JsonElement] as a pretty-printed JSON string.
 *
 * Extension function on [JsonElement].
 *
 * @param indent the indentation string to use (defaults to two spaces)
 * @return the formatted JSON string
 */
fun JsonElement.toFormattedString(indent: String = "  "): String =
  if (indent == "  ") defaultPrettyFormat.encodeToString(this) else prettyPrint(indent).encodeToString(this)

/**
 * Parses this [String] as JSON and re-encodes it. Extension function on [String].
 *
 * @param prettyPrint if `true` (the default), formats the output with indentation; otherwise outputs compact JSON
 * @return the re-encoded JSON string
 * @throws kotlinx.serialization.SerializationException if this string is not valid JSON
 */
fun String.reformatJson(prettyPrint: Boolean = true): String = toJsonElement().toJsonString(prettyPrint)

/**
 * Parses this [String] as JSON and re-encodes it as a pretty-printed JSON string. Extension function on [String].
 *
 * Deprecated because passing any argument, as in `toJsonString(prettyPrint = true)`, selects the generic
 * [toJsonString] overload instead, which serializes the string as a quoted JSON string literal.
 */
@Deprecated(
  "Passing an argument to toJsonString on a String serializes it as a JSON string literal. Use reformatJson.",
  ReplaceWith("reformatJson()"),
)
fun String.toJsonString() = reformatJson()

/**
 * Serializes this value to a JSON string.
 *
 * @param T the type to serialize (must be `@Serializable` or a [JsonElement])
 * @param prettyPrint if `true`, formats the output with indentation; otherwise outputs compact JSON
 * @return the JSON string representation
 */
inline fun <reified T> T.toJsonString(prettyPrint: Boolean = true) =
  (if (prettyPrint) JsonContentUtils.prettyFormat else JsonContentUtils.rawFormat).encodeToString(this)

/** Default [Json] configuration used by the conversion utilities in this file. */
object JsonDefaults {
  /** A [Json] instance with defaults encoded, pretty-printing enabled, and two-space indentation. */
  val json = Json {
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
  }
}

/**
 * Converts this value to a [JsonElement] tree using kotlinx.serialization.
 *
 * @param T the type to serialize (must be `@Serializable`)
 * @return the [JsonElement] representation
 */
inline fun <reified T> T.toJsonElement() = json.encodeToJsonElement(this)

/**
 * Parses this [String] into a [JsonElement].
 *
 * Extension function on [String].
 *
 * @param verbose if `true`, logs the full raw input string at WARN level on parse failure. Avoid this
 *   for sensitive payloads, since the input may contain PII or secrets.
 * @return the parsed [JsonElement]
 * @throws kotlinx.serialization.SerializationException if parsing fails
 */
fun String.toJsonElement(verbose: Boolean = false) =
  runCatching { json.parseToJsonElement(this) }
    .onFailure {
      if (verbose)
        logger.warn { "Error parsing JSON: <<\n$this\n>>" }
    }
    .getOrThrow()

/** Converts this [JsonElement] (which must be a [JsonArray]) to a [List] of [JsonElement]. Extension function on [JsonElement]. */
fun JsonElement.toJsonElementList() = jsonArray.toList()

/**
 * Converts this [JsonObject] element to a [Map] of string keys to Kotlin values.
 *
 * Extension function on [JsonElement]. Primitives become strings, nested objects become nested maps,
 * arrays become lists, and [JsonNull] becomes `null`.
 *
 * @return a [Map] representation of this JSON object
 * @throws IllegalArgumentException if this element is not a [JsonObject]
 */
fun JsonElement.toMap(): Map<String, Any?> {
  require(this is JsonObject) { "Can only convert JsonObject to Map, not a $simpleClassName" }

  return entries.associate { (key, value) ->
    key to value.toAny()
  }
}

private fun JsonElement.toAny(): Any? =
  when (this) {
    JsonNull -> null
    is JsonPrimitive -> content
    is JsonObject -> toMap()
    is JsonArray -> map { it.toAny() }
  }

internal fun JsonElement.element(key: String) =
  elementOrNull(key) ?: throw IllegalArgumentException(
    """JsonElement key "$key" not found in ${this.toString().take(100)}...""",
  )

private fun JsonElement.elementOrNull(key: String) = jsonObject[key]

/** Formerly the internal logger holder for JSON element utilities. */
@Deprecated("An internal logger that was never meant to be public API; it will be removed in a future release.")
object JsonElementUtils {
  /** The logger formerly used by the JSON element utilities. */
  val logger = KotlinLogging.logger {}
}
