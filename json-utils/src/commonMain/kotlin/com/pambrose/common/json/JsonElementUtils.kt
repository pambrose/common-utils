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
import com.pambrose.common.util.toDoubleQuoted
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Created lazily: it is only used on the verbose parse-failure path, so accessors should not start up logging.
private val logger by lazy { KotlinLogging.logger {} }

/** Returns the set of keys if this [JsonElement] is a [JsonObject]. Extension property on [JsonElement]. */
val JsonElement.keys get() = jsonObject.keys

// Primitive values

/**
 * Returns the string content of this [JsonElement] as a [JsonPrimitive]. Extension property on [JsonElement].
 *
 * @throws IllegalArgumentException if this element is JSON `null` or not a primitive
 */
val JsonElement.stringValue get() = nonNullContent

/**
 * Returns the integer value of this [JsonElement]'s primitive content. Extension property on [JsonElement].
 *
 * The content, quoted or not, must be a JSON integer (`-?(0|[1-9][0-9]*)`), so `007`, `+5` and non-ASCII digits are
 * rejected on every platform, as [isNumber] and [doubleValue] reject them.
 *
 * @throws IllegalArgumentException if this element is JSON `null`, not a primitive, or not an integer in range
 */
val JsonElement.intValue: Int
  get() = nonNullContent.let { it.toJsonIntOrNull() ?: throw NumberFormatException("JSON value \"$it\" is not an Int") }

/**
 * Returns the [Long] value of this [JsonElement]'s primitive content, for values such as IDs and millisecond
 * timestamps that exceed [Int]. Extension property on [JsonElement].
 *
 * The content, quoted or not, must be a JSON integer (`-?(0|[1-9][0-9]*)`), as for [intValue].
 *
 * @throws IllegalArgumentException if this element is JSON `null`, not a primitive, or not an integer in range
 */
val JsonElement.longValue: Long
  get() = nonNullContent.let {
    it.toJsonLongOrNull() ?: throw NumberFormatException("JSON value \"$it\" is not a Long")
  }

/**
 * Returns the double value of this [JsonElement]'s primitive content. Extension property on [JsonElement].
 *
 * The content, quoted or not, must follow JSON number syntax or be `NaN`, `Infinity` or `-Infinity`, so every
 * platform gives the same answer.
 *
 * @throws IllegalArgumentException if this element is JSON `null`, not a primitive, or not a number
 */
val JsonElement.doubleValue: Double
  get() = nonNullContent.let {
    it.toJsonDoubleOrNull() ?: throw NumberFormatException("JSON value \"$it\" is not a number")
  }

/**
 * Returns the boolean value of this [JsonElement]'s primitive content. Extension property on [JsonElement].
 *
 * @throws IllegalArgumentException if this element is JSON `null`, not a primitive, or not `true` or `false`
 */
val JsonElement.booleanValue
  get() = nonNullContent.let { requireNotNull(it.toBooleanStrictOrNull()) { "JSON value \"$it\" is not a boolean" } }

// kotlinx's contentOrNull is null for JSON null, whose content is otherwise the string "null".
private val JsonElement.nonNullContent: String
  get() = requireNotNull(jsonPrimitive.contentOrNull) { "JSON value is null" }

private val JsonElement.primitiveContentOrNull: String?
  get() = (this as? JsonPrimitive)?.contentOrNull

// The JSON number grammar (RFC 8259, section 6), plus the tokens kotlinx writes for non-finite doubles. Each
// platform's toDouble accepts more than this, and not the same more: all of them take " 1.5 ", "+1" and ".5", the
// JVM and Apple native targets also take "1.5f" and hex floats, and JS takes "0x10". Screening first makes every
// platform agree.
private val jsonNumber = Regex("""-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?|NaN|-?Infinity""")

private fun String.toJsonDoubleOrNull(): Double? = if (jsonNumber.matches(this)) toDouble() else null

// The integer part of the JSON number grammar. toIntOrNull alone also takes "007", "+5" and non-ASCII digits.
private val jsonInteger = Regex("""-?(?:0|[1-9][0-9]*)""")

private fun String.toJsonIntOrNull(): Int? = if (jsonInteger.matches(this)) toIntOrNull() else null

private fun String.toJsonLongOrNull(): Long? = if (jsonInteger.matches(this)) toLongOrNull() else null

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
 * A quoted numeric string such as `"42"` is a string, not a number, and JSON `null` is neither. The content must
 * follow JSON number syntax or be `NaN`, `Infinity` or `-Infinity`, so every platform gives the same answer.
 */
val JsonElement.isNumber get() = this is JsonPrimitive && !isString && jsonNumber.matches(content)

// Paths are split on [separator]; empty segments (from "a..b", a leading or trailing separator, or "") are ignored.
private fun pathSegments(
  paths: Array<out String>,
  separator: Char = '.',
) = paths.flatMap { it.split(separator) }.filter { it.isNotEmpty() }

// Walks the path, returning null as soon as a segment is missing or a non-object is reached. A JSON null at the end
// is returned as JsonNull, so callers can tell "present but null" from "missing".
private fun JsonElement.findOrNull(segments: List<String>): JsonElement? {
  var current: JsonElement = this
  for (key in segments) {
    current = (current as? JsonObject)?.get(key) ?: return null
  }
  return current
}

private fun JsonElement.findOrNull(keys: Array<out String>) = findOrNull(pathSegments(keys))

/**
 * Traverses this [JsonElement] using a slash-delimited path (e.g., `"a/b/c"`).
 *
 * Extension function on [JsonElement]. Empty segments are ignored. Unlike [get], `.` is not a separator, so this
 * also reaches keys that contain a dot.
 *
 * @param path a `/`-separated path of object keys
 * @return the [JsonElement] at the given path, or `null` if any key is missing or the path runs into a value that is
 *   not an object. A key that is present with a JSON `null` value is returned as [JsonNull].
 */
fun JsonElement.getByPath(path: String): JsonElement? = findOrNull(pathSegments(arrayOf(path), separator = '/'))

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
fun JsonElement.getOrNull(vararg keys: String): JsonElement? = findOrNull(keys)?.takeIf { it != JsonNull }

// Primitive values

/** Navigates to the nested element at [keys] and returns its string value. Extension function on [JsonElement]. */
fun JsonElement.stringValue(vararg keys: String) = get(*keys).stringValue

/**
 * Navigates to the nested element at [keys] and returns its string value, or `null` if it is missing, JSON `null`,
 * or not a primitive. Extension function on [JsonElement].
 */
fun JsonElement.stringValueOrNull(vararg keys: String) = getOrNull(*keys)?.primitiveContentOrNull

/** Navigates to the nested element at [keys] and returns its integer value. Extension function on [JsonElement]. */
fun JsonElement.intValue(vararg keys: String) = get(*keys).intValue

/**
 * Navigates to the nested element at [keys] and returns its integer value, or `null` if it is missing, JSON `null`,
 * or not an integer. Extension function on [JsonElement].
 */
fun JsonElement.intValueOrNull(vararg keys: String) = getOrNull(*keys)?.primitiveContentOrNull?.toJsonIntOrNull()

/** Navigates to the nested element at [keys] and returns its [Long] value. Extension function on [JsonElement]. */
fun JsonElement.longValue(vararg keys: String) = get(*keys).longValue

/**
 * Navigates to the nested element at [keys] and returns its [Long] value, or `null` if it is missing, JSON `null`,
 * or not an integer in range. Extension function on [JsonElement].
 */
fun JsonElement.longValueOrNull(vararg keys: String) = getOrNull(*keys)?.primitiveContentOrNull?.toJsonLongOrNull()

/** Navigates to the nested element at [keys] and returns its double value. Extension function on [JsonElement]. */
fun JsonElement.doubleValue(vararg keys: String) = get(*keys).doubleValue

/**
 * Navigates to the nested element at [keys] and returns its double value, or `null` if it is missing, JSON `null`,
 * or not a number. Extension function on [JsonElement].
 */
fun JsonElement.doubleValueOrNull(vararg keys: String) = getOrNull(*keys)?.primitiveContentOrNull?.toJsonDoubleOrNull()

/** Navigates to the nested element at [keys] and returns its boolean value. Extension function on [JsonElement]. */
fun JsonElement.booleanValue(vararg keys: String) = get(*keys).booleanValue

/**
 * Navigates to the nested element at [keys] and returns its boolean value, or `null` if it is missing, JSON `null`,
 * or not `true` or `false`. Extension function on [JsonElement].
 */
fun JsonElement.booleanValueOrNull(vararg keys: String) =
  getOrNull(*keys)?.primitiveContentOrNull?.toBooleanStrictOrNull()

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
fun JsonElement.containsKeys(vararg keys: String): Boolean = findOrNull(keys) != null

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
    is JsonPrimitive -> contentOrNull.isNullOrEmpty()
  }

/** Returns `true` if this [JsonElement] is not empty. Extension function on [JsonElement]. */
fun JsonElement.isNotEmpty() = !isEmpty()

private fun prettyPrint(indent: String) =
  Json {
    prettyPrint = true
    prettyPrintIndent = indent
  }

// Cache the common formatters (two spaces, four spaces, a tab) so they are not rebuilt on every call.
private val cachedPrettyFormats by lazy { listOf("  ", "    ", "\t").associateWith { prettyPrint(it) } }

/**
 * Encodes this [JsonElement] as a pretty-printed JSON string.
 *
 * Extension function on [JsonElement].
 *
 * @param indent the indentation string to use (defaults to two spaces); it may contain only spaces, tabs, `\r` and
 *   `\n`, as kotlinx.serialization requires
 * @return the formatted JSON string
 * @throws IllegalArgumentException if [indent] contains any other character
 */
fun JsonElement.toFormattedString(indent: String = "  "): String =
  (cachedPrettyFormats[indent] ?: prettyPrint(indent)).encodeToString(this)

/**
 * Parses this [String] as JSON and re-encodes it. Extension function on [String].
 *
 * @param prettyPrint if `true` (the default), formats the output with indentation; otherwise outputs compact JSON
 * @return the re-encoded JSON string
 * @throws kotlinx.serialization.SerializationException if this string is not valid JSON, as for [parseJson]
 */
fun String.reformatJson(prettyPrint: Boolean = true): String = parseJson().toJsonString(prettyPrint)

/**
 * Parses this [String] as JSON and re-encodes it as a pretty-printed JSON string. Extension function on [String].
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
 * A [String] receiver is serialized as a JSON string, so `"42".toJsonElement()` inside generic code is the string
 * `"42"`. To parse JSON text, use [parseJson].
 *
 * @param T the type to serialize (must be `@Serializable`)
 * @return the [JsonElement] representation
 */
inline fun <reified T> T.toJsonElement() = json.encodeToJsonElement(this)

/**
 * Parses this [String] as JSON text into a [JsonElement].
 *
 * Extension function on [String]. The input must be valid JSON: kotlinx.serialization's tree parser accepts any
 * unquoted token (`hello`, `007`, `+3`, `0x10`) as a literal, which would then be re-encoded differently on each
 * platform, so every unquoted value other than `true`, `false`, `null` and a JSON number is rejected. `NaN`,
 * `Infinity` and `-Infinity`, which kotlinx writes for non-finite doubles, are accepted.
 *
 * @param verbose if `true`, logs the full raw input string at WARN level on parse failure. Avoid this
 *   for sensitive payloads, since the input may contain PII or secrets.
 * @return the parsed [JsonElement]
 * @throws SerializationException if this string is not valid JSON
 */
fun String.parseJson(verbose: Boolean = false): JsonElement =
  runCatching { json.parseToJsonElement(this).also { it.requireJsonLiterals() } }
    .onFailure {
      if (verbose)
        logger.warn { "Error parsing JSON: <<\n$this\n>>" }
    }
    .getOrThrow()

/**
 * Parses this [String] as JSON text into a [JsonElement]; renamed [parseJson].
 *
 * The name shadowed the generic [toJsonElement], so the same call parsed `"42"` into the number 42 on a [String]
 * but serialized it as the string `"42"` in generic code.
 */
@Deprecated(
  "Parses JSON text, unlike the generic toJsonElement, which serializes a String as a JSON string. Use parseJson.",
  ReplaceWith("parseJson(verbose)"),
)
fun String.toJsonElement(verbose: Boolean = false) = parseJson(verbose)

// Rejects the unquoted tokens kotlinx's tree parser lets through; JsonNull is a primitive too, and is valid.
private fun JsonElement.requireJsonLiterals() {
  when (this) {
    is JsonObject -> {
      values.forEach { it.requireJsonLiterals() }
    }

    is JsonArray -> {
      forEach { it.requireJsonLiterals() }
    }

    JsonNull -> {}

    is JsonPrimitive -> {
      if (!isString && content != "true" && content != "false" && !jsonNumber.matches(content))
        throw SerializationException("Unexpected unquoted token '${content.take(20)}' in JSON input")
    }
  }
}

/** Converts this [JsonElement] (which must be a [JsonArray]) to a [List] of [JsonElement]. Extension function on [JsonElement]. */
fun JsonElement.toJsonElementList() = jsonArray.toList()

/**
 * Converts this [JsonObject] element to a [Map] of string keys to Kotlin values.
 *
 * Extension function on [JsonElement]. Primitives become strings, nested objects become nested maps,
 * arrays become lists, and [JsonNull] becomes `null`.
 *
 * A primitive's string is its content. For parsed JSON that is the source text, so `1.0` stays `"1.0"`. A number
 * built in code carries the platform's rendering instead: `JsonPrimitive(1.0)` is `"1.0"` on the JVM but `"1"` on JS.
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

// The message names the key and a few of the keys that are present, never the document's content: serializing it
// costs time on every miss, and it may hold personal data that would end up in logs.
internal fun JsonElement.element(key: String) =
  elementOrNull(key) ?: throw IllegalArgumentException(
    jsonObject.keys.let { keys ->
      val shown = keys.joinToString(", ", "[", "]", MAX_KEYS_SHOWN, "... (${keys.size} keys)") { it.toDoubleQuoted() }
      """JsonElement key "$key" not found; available keys: $shown"""
    },
  )

private const val MAX_KEYS_SHOWN = 10

private fun JsonElement.elementOrNull(key: String) = jsonObject[key]

/** Formerly the internal logger holder for JSON element utilities. */
@Deprecated("An internal logger that was never meant to be public API; it will be removed in a future release.")
object JsonElementUtils {
  /** The logger formerly used by the JSON element utilities. */
  val logger = KotlinLogging.logger {}
}
