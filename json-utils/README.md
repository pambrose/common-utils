# JSON Utils

Utilities for JSON processing using kotlinx.serialization, providing extensions for JSON manipulation, content loading,
and element utilities.

## Features

### JSON Content Loading

- **JsonContentUtils**: Load and parse JSON from various sources (files, URLs, resources)
- **Content Source Integration**: Work with different content sources seamlessly

### JSON Element Utilities

- **JsonElement Extensions**: Type checking, conversion, and manipulation utilities
- **Path-based Access**: Navigate JSON structures using dot notation
- **Type Safety**: Safe conversion between JSON types

## Usage Examples

### Loading JSON Content

```kotlin
import com.github.pambrose.common.json.toJsonElement
import com.github.pambrose.common.json.toJsonObject

// Load from file
val jsonFromFile = FileContentSource("config.json").toJsonElement()

// Load from URL
val jsonFromUrl = UrlContentSource("https://api.example.com/data.json").toJsonObject()

// Load from classpath resource
val jsonFromResource = ClasspathContentSource("default-config.json").toJsonElement()
```

### JSON Element Utilities

```kotlin
import com.github.pambrose.common.json.*

val jsonString = """
{
    "name": "John Doe",
    "age": 30,
    "email": "john@example.com",
    "address": {
        "street": "123 Main St",
        "city": "New York"
    },
    "hobbies": ["reading", "coding", "hiking"]
}
"""

val jsonElement = Json.parseToJsonElement(jsonString)

// Type checking
if (jsonElement.isJsonObject()) {
    val jsonObject = jsonElement.jsonObject

    // Safe property access
    val name = jsonObject["name"]?.contentOrNull() // "John Doe"
    val age = jsonObject["age"]?.intOrNull() // 30

    // Check property types
    if (jsonObject["email"]?.isString == true) {
        println("Email is a string")
    }
}

// Path-based access
val city = jsonElement.getByPath("address.city")?.contentOrNull() // "New York"
val firstHobby = jsonElement.getByPath("hobbies.0")?.contentOrNull() // "reading"
```

### JSON Manipulation

```kotlin
import com.github.pambrose.common.json.*

// Convert to Map
val jsonMap = jsonElement.toMap()
println(jsonMap["name"]) // "John Doe"

// Deep copy
val copy = jsonElement.deepCopy()

// Check if element is a specific type
val isNumber = jsonElement.isNumber
val isString = jsonElement.isString
val isBoolean = jsonElement.isBoolean
```

### Working with Arrays

```kotlin
val jsonArray = Json.parseToJsonElement("""["apple", "banana", "orange"]""")

if (jsonArray.isJsonArray()) {
    val fruits = jsonArray.jsonArray
    fruits.forEach { fruit ->
        if (fruit.isString) {
            println("Fruit: ${fruit.contentOrNull()}")
        }
    }
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- Kotlinx Serialization JSON
- Core Utils (for content sources)

## Installation

### Gradle

```kotlin
dependencies {
    implementation("com.github.pambrose.common-utils:json-utils:2.4.4")
}
```

### Maven

```xml
<dependency>
    <groupId>com.github.pambrose.common-utils</groupId>
    <artifactId>json-utils</artifactId>
    <version>2.4.4</version>
</dependency>
```

## API Reference

### JsonContentUtils

- `ContentSource.toJsonElement()`: Parse content source as JsonElement
- `ContentSource.toJsonObject()`: Parse content source as JsonObject
- `ContentSource.toJsonArray()`: Parse content source as JsonArray

### JsonElement Extensions

- `isJsonObject()`, `isJsonArray()`, `isJsonPrimitive()`: Type checking
- `isString`, `isNumber`, `isBoolean`: Primitive type checking
- `contentOrNull()`: Safe content extraction
- `intOrNull()`, `doubleOrNull()`, `booleanOrNull()`: Type-safe conversions
- `getByPath(path: String)`: Navigate JSON structure using dot notation
- `toMap()`: Convert JsonElement to Map
- `deepCopy()`: Create deep copy of JsonElement

## Error Handling

- All conversion functions return null on failure rather than throwing exceptions
- Type checking functions return false for invalid types
- Path-based access returns null for non-existent paths
- Content loading may throw exceptions for network or file system errors

## Performance Notes

- `deepCopy()` creates a string representation and re-parses it, which is inefficient for large objects
- `toMap()` performs recursive type checking and conversion
- Path-based access splits strings and traverses the JSON structure

## Thread Safety

- All utility functions are thread-safe
- JsonElement instances are immutable
- Content loading operations are thread-safe

## Best Practices

1. **Error Handling**: Always check for null returns from conversion functions
2. **Type Safety**: Use type checking functions before casting
3. **Path Access**: Use dot notation for nested property access
4. **Performance**: Cache frequently accessed JSON elements
5. **Memory**: Be aware that `deepCopy()` is expensive for large objects

## Common Patterns

### Safe JSON Navigation

```kotlin
fun getNestedValue(json: JsonElement, path: String): String? {
    return json.getByPath(path)?.takeIf { it.isString }?.contentOrNull()
}

val email = getNestedValue(jsonElement, "user.contact.email")
```

### JSON Validation

```kotlin
fun validateUser(json: JsonElement): Boolean {
    return json.isJsonObject() &&
           json.getByPath("name")?.isString == true &&
           json.getByPath("age")?.isNumber == true &&
           json.getByPath("email")?.isString == true
}
```

## Known Issues

⚠️ **Note**: There are inverted boolean logic issues in the current implementation of `isString` and `isNumber`
properties. These will be fixed in a future version.

## License

Licensed under the Apache License, Version 2.0.
