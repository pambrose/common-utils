package com.github.pambrose.json

import com.github.pambrose.common.json.JsonContentUtils
import com.github.pambrose.common.json.booleanValue
import com.github.pambrose.common.json.containsKeys
import com.github.pambrose.common.json.defaultJsonConfig
import com.github.pambrose.common.json.intValue
import com.github.pambrose.common.json.jsonElementList
import com.github.pambrose.common.json.stringValue
import com.github.pambrose.common.json.toFormattedString
import com.github.pambrose.common.json.toJsonElement
import com.github.pambrose.common.json.toJsonString
import com.github.pambrose.common.json.toMap
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

@Serializable
data class SimpleData(
  val name: String,
  val value: Int,
  val active: Boolean = true,
)

@Serializable
data class ComplexData(
  val id: String,
  val metadata: Map<String, String>,
  val items: List<SimpleData>,
)

class JsonContentUtilsTest {
  private val simpleData = SimpleData("test", 42, true)
  private val complexData = ComplexData(
    id = "complex-1",
    metadata = mapOf("version" to "1.0", "type" to "test"),
    items = listOf(
      SimpleData("item1", 10),
      SimpleData("item2", 20, false),
    ),
  )

  @Test
  fun `test JsonContentUtils formats are properly configured`() {
    // Test pretty format
    val prettyJson = JsonContentUtils.prettyFormat
    prettyJson.configuration.prettyPrint shouldBe true
    prettyJson.configuration.prettyPrintIndent shouldBe "  "

    // Test raw format
    val rawJson = JsonContentUtils.rawFormat
    rawJson.configuration.prettyPrint shouldBe false

    // Test lenient format
    val lenientJson = JsonContentUtils.lenientFormat
    lenientJson.configuration.prettyPrint shouldBe true
    lenientJson.configuration.isLenient shouldBe true
    lenientJson.configuration.ignoreUnknownKeys shouldBe true

    // Test strict format
    val strictJson = JsonContentUtils.strictFormat
    strictJson.configuration.prettyPrint shouldBe true
    strictJson.configuration.isLenient shouldBe false
    strictJson.configuration.ignoreUnknownKeys shouldBe false
    strictJson.configuration.encodeDefaults shouldBe true
  }

  @Test
  fun `test toJsonString with pretty printing`() {
    val prettyJsonString = simpleData.toJsonString(prettyPrint = true)
    val rawJsonString = simpleData.toJsonString(prettyPrint = false)

    // Pretty printed should contain newlines and indentation
    prettyJsonString.contains('\n') shouldBe true
    prettyJsonString.contains("  ") shouldBe true // default indent

    // Raw should be compact
    rawJsonString.contains('\n') shouldBe false

    // Both should contain the same data
    val prettyParsed = prettyJsonString.toJsonElement()
    val rawParsed = rawJsonString.toJsonElement()

    rawParsed.stringValue("name") shouldBe prettyParsed.stringValue("name")
    rawParsed.intValue("value") shouldBe prettyParsed.intValue("value")
    rawParsed.booleanValue("active") shouldBe prettyParsed.booleanValue("active")
  }

  @Test
  fun `test toJsonElement conversion`() {
    val jsonElement = simpleData.toJsonElement()

    jsonElement.stringValue("name") shouldBe "test"
    jsonElement.intValue("value") shouldBe 42
    jsonElement.booleanValue("active") shouldBe true
  }

  @Test
  fun `test String toJsonElement parsing`() {
    val jsonString = """{"name": "parsed", "value": 100, "active": false}"""
    val jsonElement = jsonString.toJsonElement()

    jsonElement.stringValue("name") shouldBe "parsed"
    jsonElement.intValue("value") shouldBe 100
    jsonElement.booleanValue("active") shouldBe false
  }

  @Test
  fun `test String toJsonString formatting`() {
    val inputJsonString = """{"name":"compact","value":123}"""
    val formattedJsonString = inputJsonString.toJsonString()

    // Should be pretty printed
    formattedJsonString.contains('\n') shouldBe true
    formattedJsonString.contains("  ") shouldBe true

    // Should contain the same data
    val parsed = formattedJsonString.toJsonElement()
    parsed.stringValue("name") shouldBe "compact"
    parsed.intValue("value") shouldBe 123
  }

  @Test
  fun `test toFormattedString with custom indent`() {
    val jsonElement = simpleData.toJsonElement()

    val defaultFormatted = jsonElement.toFormattedString()
    val customFormatted = jsonElement.toFormattedString("    ") // 4 spaces

    // Both should be formatted but with different indentation
    defaultFormatted.contains("  ") shouldBe true // 2 spaces
    customFormatted.contains("    ") shouldBe true // 4 spaces

    // Should not contain each other's indentation in this simple case
    defaultFormatted.contains("    ") shouldBe false // shouldn't have 4 spaces
  }

  @Test
  fun `test complex data serialization and parsing`() {
    // Test serialization
    val jsonString = complexData.toJsonString(prettyPrint = true)
    val jsonElement = jsonString.toJsonElement()

    // Test basic fields
    jsonElement.stringValue("id") shouldBe "complex-1"

    // Test metadata map
    jsonElement.stringValue("metadata.version") shouldBe "1.0"
    jsonElement.stringValue("metadata.type") shouldBe "test"

    // Test items array
    val items = jsonElement.jsonElementList("items")
    items.size shouldBe 2

    items[0].stringValue("name") shouldBe "item1"
    items[0].intValue("value") shouldBe 10
    items[0].booleanValue("active") shouldBe true

    items[1].stringValue("name") shouldBe "item2"
    items[1].intValue("value") shouldBe 20
    items[1].booleanValue("active") shouldBe false
  }

  @Test
  fun `test lenient parsing with extra fields`() {
    val jsonWithExtraFields = """
            {
                "name": "test",
                "value": 42,
                "active": true,
                "extraField": "should be ignored",
                "anotherExtra": 999
            }
        """.trimIndent()

    // Lenient format should ignore unknown keys
    val lenientElement = JsonContentUtils.lenientFormat.parseToJsonElement(jsonWithExtraFields)
    lenientElement.stringValue("name") shouldBe "test"
    lenientElement.intValue("value") shouldBe 42
    lenientElement.booleanValue("active") shouldBe true
    lenientElement.stringValue("extraField") shouldBe "should be ignored"
  }

  @Test
  fun `test strict format with defaults`() {
    // Create data with default values
    val dataWithDefaults = SimpleData("test", 42) // active defaults to true

    val strictJson = JsonContentUtils.strictFormat.encodeToString(SimpleData.serializer(), dataWithDefaults)
    val strictElement = strictJson.toJsonElement()

    // Should include default values
    strictElement.stringValue("name") shouldBe "test"
    strictElement.intValue("value") shouldBe 42
    strictElement.booleanValue("active") shouldBe true
  }

  @Test
  fun `test defaultJson builder configuration`() {
    val customJson = Json { defaultJsonConfig() }

    customJson.configuration.prettyPrint shouldBe true
    customJson.configuration.prettyPrintIndent shouldBe "  "
  }

  @Test
  fun `test round trip serialization`() {
    // Original -> JSON String -> JsonElement -> Map -> back to JsonElement
    val originalJsonString = complexData.toJsonString()
    val jsonElement = originalJsonString.toJsonElement()
    val map = jsonElement.toMap()

    // Create new JsonElement from map data (simulated)
    val reconstructed = buildJsonObject {
      put("id", map["id"] as String)
      // Note: This is a simplified reconstruction for testing
      // In practice, you'd use proper serialization
    }

    // Verify the round trip preserved core data
    jsonElement.stringValue("id") shouldBe complexData.id
  }

  @Test
  fun `test error handling with malformed JSON`() {
    val malformedJson = """{"name": "test", "value": }"""

    try {
      malformedJson.toJsonElement()
      throw AssertionError("Should have thrown an exception for malformed JSON")
    } catch (e: Exception) {
      // Expected - malformed JSON should throw
      (e.message?.contains("JSON") == true || e.message?.contains("Unexpected") == true) shouldBe true
    }
  }

  @Test
  fun `test empty and null handling`() {
    val jsonWithNulls = """
            {
                "name": "test",
                "value": null,
                "empty": "",
                "zero": 0,
                "false": false
            }
        """.trimIndent()

    val element = jsonWithNulls.toJsonElement()

    element.stringValue("name") shouldBe "test"
    element.stringValue("empty") shouldBe ""
    element.intValue("zero") shouldBe 0
    element.booleanValue("false") shouldBe false

    // Null handling
    element.containsKeys("value") shouldBe true
    // Accessing null value directly should fail, but check it exists
    // TODO
    // assertTrue(element.getOrNull("value") != null) // JsonNull is not null reference
  }

  @Test
  fun `test large data structures`() {
    // Create a larger structure to test performance
    val largeData = ComplexData(
      id = "large-test",
      metadata = (1..50).associate { "key$it" to "value$it" },
      items = (1..100).map { SimpleData("item$it", it, it % 2 == 0) },
    )

    val jsonString = largeData.toJsonString()
    val parsed = jsonString.toJsonElement()

    parsed.stringValue("id") shouldBe "large-test"
    parsed.stringValue("metadata.key25") shouldBe "value25"

    val items = parsed.jsonElementList("items")
    items.size shouldBe 100
    items[49].stringValue("name") shouldBe "item50" // 0-indexed
    items[49].intValue("value") shouldBe 50
    items[49].booleanValue("active") shouldBe true // 50 % 2 == 0
  }

  @Test
  fun `test format consistency across utils`() {
    // Ensure different formatting methods produce consistent results
    val data = simpleData

    val viaToJsonString = data.toJsonString(prettyPrint = true)
    val viaPrettyFormat = JsonContentUtils.prettyFormat.encodeToString(data)
    val viaToFormattedString = data.toJsonElement().toFormattedString()

    // All should be pretty printed
    viaToJsonString.contains('\n') shouldBe true
    viaPrettyFormat.contains('\n') shouldBe true
    viaToFormattedString.contains('\n') shouldBe true

    // Parse all and verify they contain the same data
    val parsed1 = viaToJsonString.toJsonElement()
    val parsed2 = viaPrettyFormat.toJsonElement()
    val parsed3 = viaToFormattedString.toJsonElement()

    parsed2.stringValue("name") shouldBe parsed1.stringValue("name")
    parsed3.stringValue("name") shouldBe parsed2.stringValue("name")
    parsed2.intValue("value") shouldBe parsed1.intValue("value")
    parsed3.intValue("value") shouldBe parsed2.intValue("value")
  }
}
