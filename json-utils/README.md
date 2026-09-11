# JSON Utils

Extensions for working with `kotlinx.serialization`'s `JsonElement`: typed value access, key and path
navigation, conversion helpers, and a set of pre-configured `Json` formats.

**Multiplatform** — this module targets JVM, JS, wasmJs and Native.

Type checks (`isObject`, `isArray`, `isString`, …) and simple accessors (`stringValue`, `intValue`, …) are
**properties**, so they are used without parentheses. The `vararg keys` forms are functions.

## Features

### Typed Value Access

- `stringValue`, `intValue`, `doubleValue`, `booleanValue`, `jsonObjectValue` as properties
- Matching `xxxValue(vararg keys)` / `xxxValueOrNull(vararg keys)` functions for nested lookups

### Navigation

- `get(vararg keys)` (the `[]` operator) — throws when a key is missing
- `getOrNull(vararg keys)` — returns `null` instead
- `getByPath("a/b/c")` — **slash-separated** path navigation

### Inspection & Conversion

- `keys`, `size`, `isEmpty()`, `isNotEmpty()`, `containsKeys(...)`
- `isObject`, `isArray`, `isPrimitive`, `isString`, `isNumber`
- `deepCopy()`, `toMap()`, `toJsonElementList()`, `forEachJsonObject { }`
- `toJsonString()`, `toJsonElement()`, `toFormattedString(indent)`

### Json Formats

- `JsonContentUtils.prettyFormat`, `rawFormat`, `lenientFormat`, `strictFormat`

## Usage Examples

### Type Checking and Simple Access

```kotlin
import com.pambrose.common.json.booleanValue
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

The `vararg` forms walk several keys at once. The plain forms throw `IllegalArgumentException` when a key
is missing; the `OrNull` forms return `null`.

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

`getByPath` splits on `/`, not `.`:

```kotlin
import com.pambrose.common.json.getByPath

val email = json.getByPath("user/profile/email")   // JsonElement?
```

Leading and repeated slashes are ignored, and the result is `null` if the path does not resolve.

### Arrays

```kotlin
import com.pambrose.common.json.forEachJsonObject
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
import com.pambrose.common.json.toFormattedString
import com.pambrose.common.json.toJsonElement
import com.pambrose.common.json.toJsonString
import com.pambrose.common.json.toMap

// String -> JsonElement -> pretty String
val pretty = """{"a":1}""".toJsonString()

// Any serializable value -> JSON
val asJson = myDataClass.toJsonString(prettyPrint = true)
val asElement = myDataClass.toJsonElement()

// JsonObject -> Map<String, Any?>; throws if the element is not an object
val map = json.toMap()

// Independent copy
val copy = json.deepCopy()

// Custom indent
println(json.toFormattedString(indent = "    "))
```

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

- `keys`, `size`, `stringValue`, `intValue`, `doubleValue`, `booleanValue`, `jsonObjectValue`
- `isObject`, `isArray`, `isPrimitive`, `isString`, `isNumber`

### Navigation

- `operator fun JsonElement.get(vararg keys: String): JsonElement` — throws `IllegalArgumentException` when a key is missing
- `fun JsonElement.getOrNull(vararg keys: String): JsonElement?`
- `fun JsonElement.getByPath(path: String): JsonElement?` — `/`-separated
- `fun JsonElement.containsKeys(vararg keys: String): Boolean`

### Nested Accessors

`stringValue`, `stringValueOrNull`, `intValue`, `intValueOrNull`, `doubleValue`, `doubleValueOrNull`,
`booleanValue`, `booleanValueOrNull`, `jsonObjectValue`, `jsonObjectValueOrNull`, `jsonElementList`,
`jsonElementListOrNull` — each taking `vararg keys: String`.

### Conversion & Iteration

- `fun JsonElement.toMap(): Map<String, Any?>`
- `fun JsonElement.toJsonElementList(): List<JsonElement>`
- `fun JsonElement.deepCopy(): JsonElement`
- `fun JsonElement.forEachJsonObject(action: (JsonObject) -> Unit)`
- `fun JsonElement.isEmpty(): Boolean` / `isNotEmpty(): Boolean`
- `fun JsonElement.toFormattedString(indent: String = "  "): String`
- `fun String.toJsonString(): String`
- `inline fun <reified T> T.toJsonString(prettyPrint: Boolean = true): String`
- `inline fun <reified T> T.toJsonElement(): JsonElement`
- `fun String.toJsonElement(verbose: Boolean = false): JsonElement`

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

- `get(...)` and the non-`OrNull` accessors throw `IllegalArgumentException` when a key is missing
- `toMap()` throws `IllegalArgumentException` unless the element is a `JsonObject`
- The typed properties throw if the underlying value is not of that type — prefer the `OrNull` variants for
  untrusted input

## License

Licensed under the Apache License, Version 2.0.
