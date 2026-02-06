@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.json

import com.github.pambrose.common.json.isEmpty
import com.github.pambrose.common.json.isNotEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.jupiter.api.Test

class BugFixVerificationTests {
  // Bug #12: isEmpty() returned true for all primitives and crashed on arrays
  // Before fix: all JsonPrimitive values returned true; JsonArray threw exception
  // After fix: checks content.isEmpty() for primitives; handles arrays correctly

  @Test
  fun isEmptyReturnsFalseForNonEmptyPrimitives() {
    JsonPrimitive("hello").isEmpty() shouldBe false
    JsonPrimitive(42).isEmpty() shouldBe false
    JsonPrimitive(true).isEmpty() shouldBe false
    JsonPrimitive(3.14).isEmpty() shouldBe false
  }

  @Test
  fun isEmptyReturnsTrueForEmptyPrimitiveString() {
    JsonPrimitive("").isEmpty() shouldBe true
  }

  @Test
  fun isEmptyWorksForJsonArrays() {
    val emptyArray = JsonArray(emptyList())
    emptyArray.isEmpty() shouldBe true

    val nonEmptyArray =
      buildJsonArray {
        add(JsonPrimitive(1))
        add(JsonPrimitive(2))
      }
    nonEmptyArray.isEmpty() shouldBe false
  }

  @Test
  fun isEmptyWorksForJsonObjects() {
    val emptyObject = JsonObject(emptyMap())
    emptyObject.isEmpty() shouldBe true

    val nonEmptyObject = JsonObject(mapOf("key" to JsonPrimitive("value")))
    nonEmptyObject.isEmpty() shouldBe false
  }

  @Test
  fun isNotEmptyIsConsistent() {
    JsonPrimitive("hello").isNotEmpty() shouldBe true
    JsonPrimitive("").isNotEmpty() shouldBe false
    JsonArray(emptyList()).isNotEmpty() shouldBe false
    JsonObject(emptyMap()).isNotEmpty() shouldBe false
  }
}
