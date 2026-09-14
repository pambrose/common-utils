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

package com.pambrose.json

import com.pambrose.common.json.JsonContentUtils
import com.pambrose.common.json.booleanValue
import com.pambrose.common.json.containsKeys
import com.pambrose.common.json.getOrNull
import com.pambrose.common.json.defaultJsonConfig
import com.pambrose.common.json.intValue
import com.pambrose.common.json.jsonElementList
import com.pambrose.common.json.reformatJson
import com.pambrose.common.json.stringValue
import com.pambrose.common.json.stringValueOrNull
import com.pambrose.common.json.toFormattedString
import com.pambrose.common.json.toJsonElement
import com.pambrose.common.json.toJsonString
import com.pambrose.common.json.toMap
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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

class JsonContentUtilsTest : StringSpec() {
  init {
    val simpleData = SimpleData("test", 42, true)
    val complexData = ComplexData(
      id = "complex-1",
      metadata = mapOf("version" to "1.0", "type" to "test"),
      items = [
        SimpleData("item1", 10),
        SimpleData("item2", 20, false),
      ],
    )

    "JsonContentUtils formats are properly configured" {
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

    "to json string with pretty printing" {
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

    "to json element conversion" {
      val jsonElement = simpleData.toJsonElement()

      jsonElement.stringValue("name") shouldBe "test"
      jsonElement.intValue("value") shouldBe 42
      jsonElement.booleanValue("active") shouldBe true
    }

    "string to json element parsing" {
      val jsonString = """{"name": "parsed", "value": 100, "active": false}"""
      val jsonElement = jsonString.toJsonElement()

      jsonElement.stringValue("name") shouldBe "parsed"
      jsonElement.intValue("value") shouldBe 100
      jsonElement.booleanValue("active") shouldBe false
    }

    "string to json string formatting" {
      val inputJsonString = """{"name":"compact","value":123}"""
      val formattedJsonString = inputJsonString.reformatJson()

      // Should be pretty printed
      formattedJsonString.contains('\n') shouldBe true
      formattedJsonString.contains("  ") shouldBe true

      // Should contain the same data
      val parsed = formattedJsonString.toJsonElement()
      parsed.stringValue("name") shouldBe "compact"
      parsed.intValue("value") shouldBe 123
    }

    "to formatted string with custom indent" {
      val jsonElement = simpleData.toJsonElement()

      val defaultFormatted = jsonElement.toFormattedString()
      val customFormatted = jsonElement.toFormattedString("    ") // 4 spaces

      // Both should be formatted but with different indentation
      defaultFormatted.contains("  ") shouldBe true // 2 spaces
      customFormatted.contains("    ") shouldBe true // 4 spaces

      // Should not contain each other's indentation in this simple case
      defaultFormatted.contains("    ") shouldBe false // shouldn't have 4 spaces
    }

    "complex data serialization and parsing" {
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

    "lenient format ignores unknown keys that the strict format rejects" {
      // Unknown keys only matter when decoding into a class; parsing to a JsonElement accepts any object.
      val jsonWithExtraFields = """{"name": "test", "value": 42, "active": true, "extraField": "unknown"}"""

      JsonContentUtils.lenientFormat.decodeFromString(SimpleData.serializer(), jsonWithExtraFields) shouldBe
        SimpleData("test", 42, true)
      shouldThrow<SerializationException> {
        JsonContentUtils.strictFormat.decodeFromString(SimpleData.serializer(), jsonWithExtraFields)
      }
    }

    "lenient format accepts unquoted keys and strings that the strict format rejects" {
      val unquoted = """{name: test, value: 42, active: true}"""

      JsonContentUtils.lenientFormat.decodeFromString(SimpleData.serializer(), unquoted) shouldBe
        SimpleData("test", 42, true)
      shouldThrow<SerializationException> {
        JsonContentUtils.strictFormat.decodeFromString(SimpleData.serializer(), unquoted)
      }
    }

    "strict format with defaults" {
      // Create data with default values
      val dataWithDefaults = SimpleData("test", 42) // active defaults to true

      val strictJson = JsonContentUtils.strictFormat.encodeToString(SimpleData.serializer(), dataWithDefaults)
      val strictElement = strictJson.toJsonElement()

      // Should include default values
      strictElement.stringValue("name") shouldBe "test"
      strictElement.intValue("value") shouldBe 42
      strictElement.booleanValue("active") shouldBe true
    }

    "default json builder configuration" {
      val customJson = Json { defaultJsonConfig() }

      customJson.configuration.prettyPrint shouldBe true
      customJson.configuration.prettyPrintIndent shouldBe "  "
    }

    "round trip serialization" {
      // Original -> JSON String -> JsonElement -> Map -> back to JsonElement
      val originalJsonString = complexData.toJsonString()
      val jsonElement = originalJsonString.toJsonElement()
      val map = jsonElement.toMap()

      // Verify the round trip preserved core data
      map["id"] shouldBe complexData.id
      jsonElement.stringValue("id") shouldBe complexData.id
    }

    "error handling with malformed JSON" {
      shouldThrow<SerializationException> {
        """{"name": "test", "value": }""".toJsonElement()
      }
    }

    "empty and null handling" {
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

      // A JSON null key exists, reads as null through the OrNull accessors, and is rejected by the plain ones.
      element.containsKeys("value") shouldBe true
      element.getOrNull("value") shouldBe null
      element.stringValueOrNull("value") shouldBe null
      shouldThrow<IllegalArgumentException> { element.stringValue("value") }
    }

    "large data structures" {
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

    "format consistency across utils" {
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
}
