# JSON Utils

Extensions for working with `kotlinx.serialization`'s `JsonElement`: typed value access, key and path
navigation, conversion helpers, and a set of pre-configured `Json` formats.

**Multiplatform** — this module targets JVM, JS, wasmJs and Native.

Type checks (`isObject`, `isArray`, `isString`, …) and simple accessors (`stringValue`, `intValue`, …) are
**properties**, so they are used without parentheses. The `vararg keys` forms are functions.

## Features

### Typed Value Access

- `stringValue`, `intValue`, `longValue`, `doubleValue`, `booleanValue`, `jsonObjectValue` as properties
- Matching `xxxValue(vararg keys)` / `xxxValueOrNull(vararg keys)` functions for nested lookups

### Navigation

- `get(vararg keys)` (the `[]` operator) — throws when a key is missing
- `getOrNull(vararg keys)` — returns `null` instead
- `getByPath("a/b/c")` — **slash-separated** path navigation

### Inspection & Conversion

- `keys`, `size`, `isEmpty()`, `isNotEmpty()`, `containsKeys(...)`
- `isObject`, `isArray`, `isPrimitive`, `isString`, `isNumber`
- `deepCopy()`, `toMap()`, `toJsonElementList()`, `forEachJsonObject { }`
- `parseJson()` (JSON text), `toJsonString()` (objects), `reformatJson(prettyPrint)` (JSON strings),
  `toJsonElement()` (objects), `toFormattedString(indent)`

### Json Formats

- `JsonContentUtils.prettyFormat`, `rawFormat`, `lenientFormat`, `strictFormat`

## Usage Examples

### Type Checking and Simple Access

```kotlin
import com.pambrose.common.json.booleanValue
import com.pambrose.common.json.get
import com.pambrose.common.json.intValue
import com.pambrose.common.json.isArray
import com.pambrose.common.json.isObject
import com.pambrose.common.json.keys
import com.pambrose.common.json.stringValue
import kotlinx.serialization.json.Json

val jsonElement = Json.parseToJsonElement("""{"name": "Alice", "age": 30, "active": true}""")

if (jsonElement.isObject) {
  println(jsonElement.keys)  // [name, age, active]

  // Properties, not functions — no parentheses
  val name = jsonElement["name"].stringValue
  val age = jsonElement["age"].intValue
  val active = jsonElement["active"].booleanValue
}
```

There is no `isBoolean` property; use `isPrimitive` or read the value with `booleanValue`.

### Nested Access

The `vararg` forms walk several keys at once; see [Error Handling](#error-handling) for how the plain and `OrNull`
forms treat a missing key, JSON `null`, or a value of the wrong type. Keys are split on `.`, and empty segments
are ignored, so `get("a..b")` is the same as `get("a.b")`.

```kotlin
import com.pambrose.common.json.get
import com.pambrose.common.json.getOrNull
import com.pambrose.common.json.stringValue
import com.pambrose.common.json.stringValueOrNull

val json = Json.parseToJsonElement("""{"user": {"profile": {"email": "a@example.com"}}}""")

// Throws if any key is absent
val email = json.stringValue("user", "profile", "email")

// Returns null if any key is absent
val missing = json.stringValueOrNull("user", "profile", "phone")

// The [] operator is the same lookup, returning a JsonElement
val profile = json["user", "profile"]
val maybeProfile = json.getOrNull("user", "settings")
```

### Path Navigation

`getByPath` splits on `/`, not `.`, so it can also reach a key that contains a dot:

```kotlin
import com.pambrose.common.json.getByPath

val email = json.getByPath("user/profile/email")   // JsonElement?
```

Leading and repeated slashes are ignored. The result is `null` if a key is missing or the path runs into a value
that is not an object, and `JsonNull` if the last key is present with a `null` value.

### Arrays

```kotlin
import com.pambrose.common.json.forEachJsonObject
import com.pambrose.common.json.get
import com.pambrose.common.json.isArray
import com.pambrose.common.json.jsonElementList
import com.pambrose.common.json.size
import com.pambrose.common.json.stringValue
import com.pambrose.common.json.toJsonElementList

val fruits = Json.parseToJsonElement("""["apple", "banana", "orange"]""")

if (fruits.isArray) {
  println(fruits.size)
  fruits.toJsonElementList().forEach { println(it.stringValue) }
}

val payload = Json.parseToJsonElement("""{"users": [{"name": "Alice"}, {"name": "Bob"}]}""")

// Pull a nested array out by key
payload.jsonElementList("users").forEach { println(it.stringValue("name")) }

// Or iterate the objects directly
payload["users"].forEachJsonObject { obj -> println(obj["name"]) }
```

### Checking Keys

```kotlin
import com.pambrose.common.json.containsKeys

// True only if the whole nested chain exists
if (json.containsKeys("user", "profile", "email"))
  println("email present")
```

### Conversion

```kotlin
import com.pambrose.common.json.deepCopy
import com.pambrose.common.json.reformatJson
import com.pambrose.common.json.toFormattedString
import com.pambrose.common.json.toJsonElement
import com.pambrose.common.json.toJsonString
import com.pambrose.common.json.toMap

// JSON text -> JsonElement, and JSON text -> pretty String
val element = """{"a":1}""".parseJson()
val pretty = """{"a":1}""".reformatJson()

// Any serializable value -> JSON
val asJson = myDataClass.toJsonString(prettyPrint = true)
val asElement = myDataClass.toJsonElement()

// JsonObject -> Map<String, Any?>; throws if the element is not an object
val map = json.toMap()

// Independent copy
val copy = json.deepCopy()

// Custom indent: spaces, tabs, \r and \n only (kotlinx.serialization rejects anything else)
println(json.toFormattedString(indent = "    "))
```

`parseJson` (and `reformatJson`, which uses it) accepts only valid JSON. kotlinx.serialization's tree parser takes
any unquoted token as a literal, so `hello`, `007`, `+3` and `0x10` would otherwise be accepted and then re-encoded
differently on each platform; they throw `SerializationException` instead. `NaN`, `Infinity` and `-Infinity`, which
kotlinx writes for non-finite doubles, are accepted.

`parseJson` was called `toJsonElement` on a `String`, which shadowed the generic `T.toJsonElement()`: `"42"` parsed
to the number 42 on a `String` but serialized to the string `"42"` in generic code. The old name still works, and is
deprecated.

### Json Formats

```kotlin
import com.pambrose.common.json.JsonContentUtils

JsonContentUtils.prettyFormat   // pretty-printed, 2-space indent, encodeDefaults
JsonContentUtils.rawFormat      // compact, encodeDefaults
JsonContentUtils.lenientFormat  // pretty, isLenient, ignoreUnknownKeys
JsonContentUtils.strictFormat   // pretty, strict parsing, rejects unknown keys

val text = JsonContentUtils.rawFormat.encodeToString(myValue)
```

`defaultJsonConfig()` is available as a `JsonBuilder` extension if you want the same pretty-print defaults
in your own `Json { }` block.

## API Reference

### Properties

- `keys`, `size`, `stringValue`, `intValue`, `longValue`, `doubleValue`, `booleanValue`, `jsonObjectValue`
- `intValue` and `longValue` accept only JSON integers (`-?(0|[1-9][0-9]*)`, quoted or not), so `007`, `+5` and
  non-ASCII digits are rejected on every platform
- `isObject`, `isArray`, `isPrimitive`, `isString`, `isNumber`

### Navigation

- `operator fun JsonElement.get(vararg keys: String): JsonElement` — throws `IllegalArgumentException` when a key is missing
- `fun JsonElement.getOrNull(vararg keys: String): JsonElement?`
- `fun JsonElement.getByPath(path: String): JsonElement?` — `/`-separated
- `fun JsonElement.containsKeys(vararg keys: String): Boolean`

### Nested Accessors

`stringValue`, `stringValueOrNull`, `intValue`, `intValueOrNull`, `longValue`, `longValueOrNull`, `doubleValue`,
`doubleValueOrNull`,
`booleanValue`, `booleanValueOrNull`, `jsonObjectValue`, `jsonObjectValueOrNull`, `jsonElementList`,
`jsonElementListOrNull` — each taking `vararg keys: String`.

### Conversion & Iteration

- `fun JsonElement.toMap(): Map<String, Any?>`
- `fun JsonElement.toJsonElementList(): List<JsonElement>`
- `fun JsonElement.deepCopy(): JsonElement`
- `fun JsonElement.forEachJsonObject(action: (JsonObject) -> Unit)`
- `fun JsonElement.isEmpty(): Boolean` / `isNotEmpty(): Boolean`
- `fun JsonElement.toFormattedString(indent: String = "  "): String`
- `fun String.reformatJson(prettyPrint: Boolean = true): String`
- `fun String.toJsonString(): String` (deprecated; use `reformatJson`)
- `inline fun <reified T> T.toJsonString(prettyPrint: Boolean = true): String`
- `inline fun <reified T> T.toJsonElement(): JsonElement` — serializes a value; a `String` becomes a JSON string
- `fun String.parseJson(verbose: Boolean = false): JsonElement` — parses JSON text; throws `SerializationException`
  if it is not valid JSON
- `fun String.toJsonElement(verbose: Boolean = false): JsonElement` (deprecated; use `parseJson`)

### Formats

- `JsonContentUtils.prettyFormat` / `rawFormat` / `lenientFormat` / `strictFormat`
- `fun JsonBuilder.defaultJsonConfig()`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Kotlinx Serialization JSON

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/json-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/json-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:json-utils:LATEST_VERSION")
}
```

### Maven

Maven consumers must depend on the `-jvm` artifact, since this is a multiplatform module:

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>json-utils-jvm</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Error Handling

- `get(...)` throws `IllegalArgumentException` when a key is missing.
- The typed accessors (`stringValue`, `intValue`, `doubleValue`, `booleanValue`) throw
  `IllegalArgumentException` when the value is JSON `null` or has the wrong type. `booleanValue` accepts only
  `true` and `false`.
- `doubleValue`, `doubleValueOrNull` and `isNumber` accept only JSON number syntax, plus `NaN`, `Infinity` and
  `-Infinity`, so every platform gives the same answer. Text such as `1.5f`, `0x10`, ` 1.5 ` or `.5` is not a
  number. A quoted number such as `"2.5"` is still read by `doubleValue`, but `isNumber` is `false` for it.
- The `OrNull` accessors never throw for a missing key, JSON `null`, or a type mismatch; they return `null`.
  Prefer them for untrusted input.
- `size` works on objects and arrays and throws for primitives. `isEmpty()` is `true` for JSON `null`.
- `toMap()` throws `IllegalArgumentException` unless the element is a `JsonObject`. It returns each primitive's
  content, which for parsed JSON is the source text. A number built in code carries the platform's rendering:
  `JsonPrimitive(1.0)` becomes `"1.0"` on the JVM but `"1"` on JS.
- To reformat a JSON **string**, call `reformatJson()`. On a `String`, `toJsonString(prettyPrint = …)` serializes
  the string itself as a JSON string literal.

## License

Licensed under the Apache License, Version 2.0.
