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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.json

import com.pambrose.common.json.isEmpty
import com.pambrose.common.json.isNotEmpty
import com.pambrose.common.json.toMap
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.collections.get
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #12: isEmpty() returned true for all primitives and crashed on arrays
    // Before fix: all JsonPrimitive values returned true; JsonArray threw exception
    // After fix: checks content.isEmpty() for primitives; handles arrays correctly

    "is empty returns false for non-empty primitives" {
      JsonPrimitive("hello").isEmpty() shouldBe false
      JsonPrimitive(42).isEmpty() shouldBe false
      JsonPrimitive(true).isEmpty() shouldBe false
      JsonPrimitive(3.14).isEmpty() shouldBe false
    }

    "is empty returns true for empty primitive string" {
      JsonPrimitive("").isEmpty() shouldBe true
    }

    "is empty works for json arrays" {
      val emptyArray = JsonArray(emptyList())
      emptyArray.isEmpty() shouldBe true

      val nonEmptyArray =
        buildJsonArray {
          add(JsonPrimitive(1))
          add(JsonPrimitive(2))
        }
      nonEmptyArray.isEmpty() shouldBe false
    }

    "is empty works for json objects" {
      val emptyObject = JsonObject(emptyMap())
      emptyObject.isEmpty() shouldBe true

      val nonEmptyObject = JsonObject(mapOf("key" to JsonPrimitive("value")))
      nonEmptyObject.isEmpty() shouldBe false
    }

    "is not empty is consistent" {
      JsonPrimitive("hello").isNotEmpty() shouldBe true
      JsonPrimitive("").isNotEmpty() shouldBe false
      JsonArray(emptyList()).isNotEmpty() shouldBe false
      JsonObject(emptyMap()).isNotEmpty() shouldBe false
    }

    // Bug #13: toMap() crashed on nested arrays
    // Before fix: inner JsonArray hit "else -> it.toMap()" which only handles JsonObject
    // After fix: recursive toAny() helper handles all JsonElement types

    "to map handles nested arrays" {
      val json =
        JsonObject(
          mapOf(
            "matrix" to JsonArray(
              listOf(
                JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))),
                JsonArray(listOf(JsonPrimitive(3), JsonPrimitive(4))),
              ),
            ),
          ),
        )

      val map = json.toMap()
      val matrix = map["matrix"] as List<*>
      matrix.size shouldBe 2

      val row1 = matrix[0] as List<*>
      row1 shouldBe listOf("1", "2")

      val row2 = matrix[1] as List<*>
      row2 shouldBe listOf("3", "4")
    }

    "to map handles mixed nested arrays" {
      val json =
        JsonObject(
          mapOf(
            "items" to JsonArray(
              listOf(
                JsonObject(mapOf("name" to JsonPrimitive("a"))),
                JsonArray(listOf(JsonPrimitive("nested"))),
                JsonPrimitive("plain"),
                JsonNull,
              ),
            ),
          ),
        )

      val map = json.toMap()
      val items = map["items"] as List<*>
      items.size shouldBe 4
      (items[0] as Map<*, *>)["name"] shouldBe "a"
      items[1] shouldBe listOf("nested")
      items[2] shouldBe "plain"
      items[3] shouldBe null
    }

    "to map handles deeply nested arrays" {
      val json =
        JsonObject(
          mapOf(
            "deep" to JsonArray(
              listOf(
                JsonArray(
                  listOf(
                    JsonArray(listOf(JsonPrimitive("innermost"))),
                  ),
                ),
              ),
            ),
          ),
        )

      val map = json.toMap()
      val deep = map["deep"] as List<*>
      val mid = (deep[0] as List<*>)
      val inner = (mid[0] as List<*>)
      inner shouldBe listOf("innermost")
    }
  }
}
