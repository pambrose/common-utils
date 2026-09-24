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

import com.pambrose.common.json.get
import com.pambrose.common.json.intValue
import com.pambrose.common.json.intValueOrNull
import com.pambrose.common.json.longValue
import com.pambrose.common.json.longValueOrNull
import com.pambrose.common.json.parseJson
import com.pambrose.common.json.reformatJson
import com.pambrose.common.json.toFormattedString
import com.pambrose.common.json.toJsonElement
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive

// Pins the strict-parsing and integer-accessor rules on every platform. kotlinx's tree parser accepts any unquoted
// token as a literal, and each platform's number parsing accepts different extras, so without these screens the same
// input gave different results on the JVM, JS and native.
class JsonStrictnessTests : StringSpec() {
  init {
    "parseJson rejects unquoted tokens that are not JSON literals" {
      listOf("hello", "[1, two, 3]", """{"b": 007}""", "[+3]", "[0x10]", "[.5]", "[1.5f]").forEach { text ->
        shouldThrow<SerializationException> { text.parseJson() }
      }
    }

    "parseJson accepts true, false, null, JSON numbers and the non-finite tokens kotlinx writes" {
      """[true, false, null, 0, -12, 1.5e3, "007"]""".parseJson().toString() shouldBe
        """[true,false,null,0,-12,1.5e3,"007"]"""
      "[NaN, Infinity, -Infinity]".parseJson().toString() shouldBe "[NaN,Infinity,-Infinity]"
    }

    "reformatJson throws for invalid JSON instead of rewriting it" {
      shouldThrow<SerializationException> { "hello".reformatJson(false) }
      shouldThrow<SerializationException> { "[1, two, +3]".reformatJson(false) }
      """[1, "two", 3]""".reformatJson(false) shouldBe """[1,"two",3]"""
    }

    "parseJson parses JSON text, while the generic toJsonElement serializes a String as a JSON string" {
      "42".parseJson() shouldBe JsonPrimitive(42)
      "42".toJsonElement<String>() shouldBe JsonPrimitive("42")
    }

    @Suppress("DEPRECATION")
    "the deprecated String.toJsonElement still parses JSON text" {
      "42".toJsonElement() shouldBe JsonPrimitive(42)
    }

    "intValue and intValueOrNull accept only JSON integers" {
      val json = """{"n": -12, "q": "42", "lead": "007", "plus": "+5", "arabic": "١٢", "big": 3000000000}""".parseJson()
      json.intValue("n") shouldBe -12
      json.intValue("q") shouldBe 42
      listOf("lead", "plus", "arabic", "big").forEach { key ->
        shouldThrow<NumberFormatException> { json.intValue(key) }
        json.intValueOrNull(key) shouldBe null
      }
    }

    "longValue reads integers beyond Int and beyond a double's exact range" {
      val json =
        """{"id": 9007199254740993, "ts": "1700000000000", "lead": "007", "over": 9223372036854775808}""".parseJson()
      json.longValue("id") shouldBe 9_007_199_254_740_993L
      json["ts"].longValue shouldBe 1_700_000_000_000L
      json.longValueOrNull("id") shouldBe 9_007_199_254_740_993L
      json.longValueOrNull("lead") shouldBe null
      json.longValueOrNull("over") shouldBe null
      json.longValueOrNull("missing") shouldBe null
      shouldThrow<NumberFormatException> { json.longValue("over") }
    }

    "a missing key names the key and the keys present, never the document's content" {
      val json = """{"a": {"secret": "s3cr3t-value", "other": 1}}""".parseJson()
      val message = shouldThrow<IllegalArgumentException> { json["a", "missing"] }.message.orEmpty()
      message shouldContain "\"missing\""
      message shouldContain "\"secret\""
      message shouldNotContain "s3cr3t-value"
    }

    "toFormattedString accepts whitespace indents and rejects others" {
      val json = """{"a": 1}""".parseJson()
      json.toFormattedString("\t") shouldBe "{\n\t\"a\": 1\n}"
      json.toFormattedString("    ") shouldBe "{\n    \"a\": 1\n}"
      shouldThrow<IllegalArgumentException> { json.toFormattedString("--") }
    }
  }
}
