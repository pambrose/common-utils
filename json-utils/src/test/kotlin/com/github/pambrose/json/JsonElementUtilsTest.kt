package com.github.pambrose.json

import com.github.pambrose.common.json.booleanValue
import com.github.pambrose.common.json.booleanValueOrNull
import com.github.pambrose.common.json.containsKeys
import com.github.pambrose.common.json.deepCopy
import com.github.pambrose.common.json.doubleValue
import com.github.pambrose.common.json.doubleValueOrNull
import com.github.pambrose.common.json.forEachJsonObject
import com.github.pambrose.common.json.getByPath
import com.github.pambrose.common.json.intValue
import com.github.pambrose.common.json.intValueOrNull
import com.github.pambrose.common.json.isArray
import com.github.pambrose.common.json.isEmpty
import com.github.pambrose.common.json.isNotEmpty
import com.github.pambrose.common.json.isNumber
import com.github.pambrose.common.json.isObject
import com.github.pambrose.common.json.isPrimitive
import com.github.pambrose.common.json.isString
import com.github.pambrose.common.json.jsonElementList
import com.github.pambrose.common.json.jsonElementListOrNull
import com.github.pambrose.common.json.jsonObjectValue
import com.github.pambrose.common.json.jsonObjectValueOrNull
import com.github.pambrose.common.json.keys
import com.github.pambrose.common.json.size
import com.github.pambrose.common.json.stringValue
import com.github.pambrose.common.json.stringValueOrNull
import com.github.pambrose.common.json.toJsonElement
import com.github.pambrose.common.json.toMap
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@Serializable
data class TestUser(
  val id: Int,
  val name: String,
  val email: String?,
  val active: Boolean,
  val score: Double,
  val tags: List<String> = emptyList(),
)

@Serializable
data class TestCompany(
  val name: String,
  val users: List<TestUser>,
  val metadata: Map<String, String> = emptyMap(),
)

class JsonElementUtilsTest {
  private val sampleUser = TestUser(
    id = 1,
    name = "John Doe",
    email = "john@example.com",
    active = true,
    score = 95.5,
    tags = listOf("developer", "kotlin"),
  )

  private val sampleCompany = TestCompany(
    name = "Tech Corp",
    users = listOf(sampleUser),
    metadata = mapOf("founded" to "2020", "size" to "startup"),
  )

  private val complexJsonString = """
        {
            "user": {
                "id": 123,
                "profile": {
                    "name": "Jane Smith",
                    "contact": {
                        "email": "jane@example.com",
                        "phone": "+1234567890"
                    }
                },
                "preferences": {
                    "theme": "dark",
                    "notifications": true,
                    "score": 87.3
                }
            },
            "metadata": {
                "version": "1.0",
                "features": ["auth", "api", "ui"],
                "config": {
                    "debug": false,
                    "maxRetries": 3
                }
            }
        }
    """.trimIndent()

  @Test
  fun `test basic type checking properties`() {
    val jsonString = """{"name": "test", "count": 42, "active": true, "items": [1,2,3]}"""
    val json = jsonString.toJsonElement() as JsonObject

    // Test root object
    json.isObject shouldBe true
    json.isArray shouldBe false
    json.isPrimitive shouldBe false

    // Test string field
    val nameElement = json["name"]!!
    nameElement.isObject shouldBe false
    nameElement.isArray shouldBe false
    nameElement.isPrimitive shouldBe true
    nameElement.isString shouldBe true
    nameElement.isNumber shouldBe false

    // Test number field
    val countElement = json["count"]!!
    countElement.isPrimitive shouldBe true
    countElement.isString shouldBe false
    countElement.isNumber shouldBe true

    // Test array field
    val itemsElement = json["items"]!!
    itemsElement.isObject shouldBe false
    itemsElement.isArray shouldBe true
    itemsElement.isPrimitive shouldBe false
  }

  @Test
  fun `test primitive value extraction`() {
    val userJson = sampleUser.toJsonElement()

    userJson.intValue("id") shouldBe 1
    userJson.stringValue("name") shouldBe "John Doe"
    userJson.booleanValue("active") shouldBe true
    userJson.doubleValue("score") shouldBe 95.5
  }

  @Test
  fun `test primitive value extraction with null safety`() {
    val userJson = sampleUser.toJsonElement()

    userJson.intValueOrNull("id") shouldBe 1
    userJson.stringValueOrNull("name") shouldBe "John Doe"
    userJson.booleanValueOrNull("active") shouldBe true
    userJson.doubleValueOrNull("score") shouldBe 95.5

    // Test missing keys
    userJson.stringValueOrNull("missing") shouldBe null
    userJson.intValueOrNull("missing") shouldBe null
    userJson.booleanValueOrNull("missing") shouldBe null
    userJson.doubleValueOrNull("missing") shouldBe null
  }

  @Test
  fun `test nested path access using dot notation`() {
    val complexJson = complexJsonString.toJsonElement()

    complexJson.intValue("user.id") shouldBe 123
    complexJson.stringValue("user.profile.name") shouldBe "Jane Smith"
    complexJson.stringValue("user.profile.contact.email") shouldBe "jane@example.com"
    complexJson.stringValue("user.preferences.theme") shouldBe "dark"
    complexJson.booleanValue("user.preferences.notifications") shouldBe true
    complexJson.doubleValue("user.preferences.score") shouldBe 87.3
    complexJson.stringValue("metadata.version") shouldBe "1.0"
    complexJson.booleanValue("metadata.config.debug") shouldBe false
    complexJson.intValue("metadata.config.maxRetries") shouldBe 3
  }

  @Test
  fun `test path access using forward slash notation`() {
    val complexJson = complexJsonString.toJsonElement()

    complexJson.getByPath("user/profile/name")?.stringValue shouldBe "Jane Smith"
    complexJson.getByPath("user/profile/contact/email")?.stringValue shouldBe "jane@example.com"
    complexJson.getByPath("user/preferences/theme")?.stringValue shouldBe "dark"
    complexJson.getByPath("user/missing/path") shouldBe null
    complexJson.getByPath("missing/path") shouldBe null
  }

  @Test
  fun `test containsKey functionality`() {
    val userJson = sampleUser.toJsonElement()
    val complexJson = complexJsonString.toJsonElement()

    // Simple keys
    userJson.containsKeys("id") shouldBe true
    userJson.containsKeys("name") shouldBe true
    userJson.containsKeys("email") shouldBe true
    userJson.containsKeys("missing") shouldBe false

    // Nested keys with dot notation
    complexJson.containsKeys("user.id") shouldBe true
    complexJson.containsKeys("user.profile.name") shouldBe true
    complexJson.containsKeys("user.profile.contact.email") shouldBe true
    complexJson.containsKeys("metadata.config.debug") shouldBe true
    complexJson.containsKeys("user.missing") shouldBe false
    complexJson.containsKeys("user.profile.missing") shouldBe false
    complexJson.containsKeys("missing.path") shouldBe false
  }

  @Test
  fun `test array operations`() {
    val userJson = sampleUser.toJsonElement()
    val complexJson = complexJsonString.toJsonElement()

    // Test list extraction
    val tags = userJson.jsonElementList("tags")
    tags.size shouldBe 2
    tags[0].stringValue shouldBe "developer"
    tags[1].stringValue shouldBe "kotlin"

    // Test nested array
    val features = complexJson.jsonElementList("metadata.features")
    features.size shouldBe 3
    features[0].stringValue shouldBe "auth"
    features[1].stringValue shouldBe "api"
    features[2].stringValue shouldBe "ui"

    // Test null safety
    userJson.jsonElementListOrNull("missing") shouldBe null
  }

  @Test
  fun `test object operations`() {
    val complexJson = complexJsonString.toJsonElement()

    val userObject = complexJson.jsonObjectValue("user")
    userObject.containsKeys("id") shouldBe true
    userObject.containsKeys("profile") shouldBe true

    val profileObject = complexJson.jsonObjectValue("user.profile")
    profileObject.containsKeys("name") shouldBe true
    profileObject.containsKeys("contact") shouldBe true

    // Test null safety
    complexJson.jsonObjectValueOrNull("missing") shouldBe null
    complexJson.jsonObjectValueOrNull("user.missing") shouldBe null
  }

  @Test
  fun `test toMap conversion`() {
    val userJson = sampleUser.toJsonElement()
    val map = userJson.toMap()

    (map["id"] as String).toInt() shouldBe 1
    map["name"] shouldBe "John Doe"
    map["email"] shouldBe "john@example.com"
    (map["active"] as String).toBoolean() shouldBe true
    (map["score"] as String).toDouble() shouldBe 95.5

    @Suppress("UNCHECKED_CAST")
    val tags = map["tags"] as List<String>
    tags.size shouldBe 2
    tags[0] shouldBe "developer"
    tags[1] shouldBe "kotlin"
  }

  @Test
  fun `test toMap conversion with nested objects`() {
    val complexJson = complexJsonString.toJsonElement()
    val map = complexJson.toMap()

    @Suppress("UNCHECKED_CAST")
    val userMap = map["user"] as Map<String, Any?>
    (userMap["id"] as String).toInt() shouldBe 123

    @Suppress("UNCHECKED_CAST")
    val profileMap = userMap["profile"] as Map<String, Any?>
    profileMap["name"] shouldBe "Jane Smith"

    @Suppress("UNCHECKED_CAST")
    val contactMap = profileMap["contact"] as Map<String, Any?>
    contactMap["email"] shouldBe "jane@example.com"
  }

  @Test
  fun `test isEmpty and isNotEmpty`() {
    val emptyObject = JsonObject(emptyMap())
    val nonEmptyObject = sampleUser.toJsonElement()
    val primitiveElement = JsonPrimitive("test")

    emptyObject.isEmpty() shouldBe true
    emptyObject.isNotEmpty() shouldBe false

    nonEmptyObject.isEmpty() shouldBe false
    nonEmptyObject.isNotEmpty() shouldBe true

    // Non-empty primitive strings are not empty
    primitiveElement.isEmpty() shouldBe false
    primitiveElement.isNotEmpty() shouldBe true

    // Empty primitive strings are empty
    val emptyPrimitive = JsonPrimitive("")
    emptyPrimitive.isEmpty() shouldBe true
    emptyPrimitive.isNotEmpty() shouldBe false
  }

  @Test
  fun `test size property`() {
    val userJson = sampleUser.toJsonElement()
    userJson.size shouldBe 6 // id, name, email, active, score, tags

    val emptyObject = JsonObject(emptyMap())
    emptyObject.size shouldBe 0
  }

  @Test
  fun `test keys property`() {
    val userJson = sampleUser.toJsonElement()
    val keys = userJson.keys

    keys.contains("id") shouldBe true
    keys.contains("name") shouldBe true
    keys.contains("email") shouldBe true
    keys.contains("active") shouldBe true
    keys.contains("score") shouldBe true
    keys.contains("tags") shouldBe true
    keys.size shouldBe 6
  }

  @Test
  fun `test deepCopy functionality`() {
    val original = sampleUser.toJsonElement()
    val copy = original.deepCopy()

    copy.toString() shouldBe original.toString()
    copy.stringValue("name") shouldBe original.stringValue("name")
    copy.intValue("id") shouldBe original.intValue("id")

    // Verify it's a true deep copy (though JsonElement is immutable anyway)
    (original !== copy) shouldBe true
  }

  @Test
  fun `test forEachJsonObject with object`() {
    val userJson = sampleUser.toJsonElement() as JsonObject
    var callCount = 0

    userJson.forEachJsonObject { obj ->
      callCount++
      obj.containsKeys("id") shouldBe true
    }

    callCount shouldBe 1
  }

  @Test
  fun `test forEachJsonObject with array of objects`() {
    val companyJson = sampleCompany.toJsonElement()
    val usersArray = companyJson.jsonElementList("users")

    // Create a JsonArray from the list
    val jsonArray = JsonArray(usersArray)
    var callCount = 0

    jsonArray.forEachJsonObject { obj ->
      callCount++
      obj.containsKeys("id") shouldBe true
    }

    callCount shouldBe 1 // One user object
  }

  @Test
  fun `test error handling for missing keys`() {
    val userJson = sampleUser.toJsonElement()

    assertThrows<IllegalArgumentException> {
      userJson.stringValue("missing")
    }

    assertThrows<IllegalArgumentException> {
      userJson.intValue("missing")
    }

    assertThrows<IllegalArgumentException> {
      userJson.booleanValue("missing")
    }

    assertThrows<IllegalArgumentException> {
      userJson.doubleValue("missing")
    }
  }

  @Test
  fun `test error handling for type mismatches`() {
    val jsonString = """{"name": "test", "count": 42}"""
    val json = jsonString.toJsonElement()

    // These should throw exceptions due to type mismatches
    assertThrows<NumberFormatException> {
      json.intValue("name") // "test" is not a number
    }

    assertThrows<IllegalArgumentException> {
      json.stringValue("count", "missing") // nested path on primitive
    }
  }

  @Test
  fun `test null handling`() {
    val jsonString = """{"name": "test", "value": null, "count": 42}"""
    val json = jsonString.toJsonElement()

    // Null values should be handled gracefully
    json.containsKeys("value") shouldBe true

    json.booleanValueOrNull("value") shouldBe null
    json.stringValueOrNull("value") shouldBe null
    json.intValueOrNull("value") shouldBe null
    json.doubleValueOrNull("value") shouldBe null

    json.booleanValueOrNull("value2") shouldBe null
    json.stringValueOrNull("value2") shouldBe null
    json.intValueOrNull("value2") shouldBe null
    json.doubleValueOrNull("value2") shouldBe null

    assertThrows<java.lang.IllegalArgumentException> { json.stringValue("value2") }
  }

  @Test
  fun `test complex nested operations`() {
    val nestedJson = """
            {
                "level1": {
                    "level2": {
                        "level3": {
                            "value": "deep",
                            "number": 42,
                            "array": [1, 2, 3]
                        }
                    }
                }
            }
        """.trimIndent().toJsonElement()

    nestedJson.stringValue("level1.level2.level3.value") shouldBe "deep"
    nestedJson.intValue("level1.level2.level3.number") shouldBe 42

    val deepArray = nestedJson.jsonElementList("level1.level2.level3.array")
    deepArray.size shouldBe 3
    deepArray[0].intValue shouldBe 1
    deepArray[1].intValue shouldBe 2
    deepArray[2].intValue shouldBe 3
  }

  @Test
  fun `test edge cases with empty strings and arrays`() {
    val edgeCaseJson = """
            {
                "emptyString": "",
                "emptyArray": [],
                "emptyObject": {},
                "whitespace": "   "
            }
        """.trimIndent().toJsonElement()

    edgeCaseJson.stringValue("emptyString") shouldBe ""
    edgeCaseJson.stringValue("whitespace") shouldBe "   "

    val emptyArray = edgeCaseJson.jsonElementList("emptyArray")
    emptyArray.size shouldBe 0

    val emptyObj = edgeCaseJson.jsonObjectValue("emptyObject")
    emptyObj.size shouldBe 0
  }

  @Test
  fun `test performance with large nested structure`() {
    // Create a moderately complex structure to test performance
    val json = buildJsonObject {
      repeat(10) { i ->
        put(
          "section$i",
          buildJsonObject {
            put("id", i)
            put("name", "Section $i")
            put(
              "items",
              buildJsonArray {
                repeat(5) { j ->
                  add(
                    buildJsonObject {
                      put("itemId", j)
                      put("value", "Item $j")
                      put("active", j % 2 == 0)
                    },
                  )
                }
              },
            )
          },
        )
      }
    }

    json.containsKeys("section5.items") shouldBe true
    json.jsonElementList("section5.items").size shouldBe 5
    json.stringValue("section3.name") shouldBe "Section 3"
  }

  @Test
  fun `test toJsonElement with valid JSON`() {
    val validJson = """{"name": "test", "value": 42}"""

    // Should work with verbose = false (default)
    val result1 = validJson.toJsonElement()
    result1.stringValue("name") shouldBe "test"
    result1.intValue("value") shouldBe 42

    // Should work with verbose = true
    val result2 = validJson.toJsonElement(verbose = true)
    result2.stringValue("name") shouldBe "test"
    result2.intValue("value") shouldBe 42
  }

  @Test
  fun `test toJsonElement with invalid JSON throws exception`() {
    val invalidJson = "not valid json {"

    // Should throw with verbose = false (default)
    assertThrows<kotlinx.serialization.SerializationException> {
      invalidJson.toJsonElement()
    }

    // Should throw with verbose = true (logs warning before throwing)
    assertThrows<kotlinx.serialization.SerializationException> {
      invalidJson.toJsonElement(verbose = true)
    }
  }
}
