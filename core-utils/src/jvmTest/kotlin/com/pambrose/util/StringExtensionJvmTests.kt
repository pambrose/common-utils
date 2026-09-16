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

package com.pambrose.util

import com.pambrose.common.util.asText
import com.pambrose.common.util.decode
import com.pambrose.common.util.encode
import com.pambrose.common.util.md5
import com.pambrose.common.util.md5Of
import com.pambrose.common.util.newByteArraySalt
import com.pambrose.common.util.newStringSalt
import com.pambrose.common.util.sha256
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

// The common string helpers are covered on every platform by StringExtensionTests.
class StringExtensionJvmTests : StringSpec() {
  init {
    "encode decode test" {
      val original = "hello world"
      val encoded = original.encode()
      encoded shouldBe "hello+world"
      encoded.decode() shouldBe original

      val specialChars = "a=b&c=d"
      val encodedSpecial = specialChars.encode()
      encodedSpecial.decode() shouldBe specialChars
    }

    "md5 test" {
      val hash1 = "hello".md5()
      val hash2 = "hello".md5()
      hash1 shouldBe hash2
      hash1 shouldHaveLength 32 // MD5 produces 128 bits = 32 hex chars

      // Different input should produce different hash
      val hash3 = "world".md5()
      hash3 shouldNotBe hash1
    }

    "sha256 test" {
      val hash1 = "hello".sha256()
      val hash2 = "hello".sha256()
      hash1 shouldBe hash2
      hash1 shouldHaveLength 64 // SHA-256 produces 256 bits = 64 hex chars

      // Different input should produce different hash
      val hash3 = "world".sha256()
      hash3 shouldNotBe hash1
    }

    "md5 with salt test" {
      val hash1 = "hello".md5("salt1")
      val hash2 = "hello".md5("salt2")
      hash1 shouldNotBe hash2 // Different salt should produce different hash
    }

    "md5 known answer test" {
      "hello".md5() shouldBe "5d41402abc4b2a76b9719d911017c592"
    }

    "sha256 known answer test" {
      "hello".sha256() shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }

    "md5 with byte array salt test" {
      // An empty byte array salt is a no-op, so the result matches the unsalted hash
      "hello".md5(ByteArray(0)) shouldBe "5d41402abc4b2a76b9719d911017c592"

      // The salt bytes are prepended to the input bytes before hashing
      "hello".md5("abc".toByteArray()) shouldBe "abchello".md5()

      // Deterministic for the same salt, different across salts and vs unsalted
      val salt = "salt1".toByteArray()
      "hello".md5(salt) shouldBe "hello".md5(salt)
      "hello".md5(salt) shouldNotBe "hello".md5("salt2".toByteArray())
      "hello".md5(salt) shouldNotBe "hello".md5()
      "hello".md5(salt) shouldHaveLength 32
    }

    "sha256 with byte array salt test" {
      // An empty byte array salt is a no-op, so the result matches the unsalted hash
      "hello".sha256(ByteArray(0)) shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

      // The salt bytes are prepended to the input bytes before hashing
      "hello".sha256("abc".toByteArray()) shouldBe "abchello".sha256()

      // Deterministic for the same salt, different across salts and vs unsalted
      val salt = "salt1".toByteArray()
      "hello".sha256(salt) shouldBe "hello".sha256(salt)
      "hello".sha256(salt) shouldNotBe "hello".sha256("salt2".toByteArray())
      "hello".sha256(salt) shouldNotBe "hello".sha256()
      "hello".sha256(salt) shouldHaveLength 64
    }

    "byte array as text test" {
      ByteArray(0).asText shouldBe ""
      // Negative bytes must render as unsigned two-digit lowercase hex
      byteArrayOf(0x00, 0x0f, 0x10, 0x7f, -0x80, -0x01).asText shouldBe "000f107f80ff"
    }

    "new byte array salt test" {
      newByteArraySalt().size shouldBe 16 // Default length
      newByteArraySalt(32).size shouldBe 32
      // Two independently generated 128-bit salts should differ
      newByteArraySalt().contentEquals(newByteArraySalt()) shouldBe false
    }

    "new string salt test" {
      newStringSalt() shouldHaveLength 16 // Default length
      newStringSalt(8) shouldHaveLength 8
      newStringSalt() shouldMatch Regex("[a-zA-Z0-9]+")
      // Two independently generated salts should differ
      newStringSalt() shouldNotBe newStringSalt()
    }

    "md5 of keys test" {
      // Keys are stringified, joined with the default "|" separator, and MD5 hashed
      md5Of("a", 1, true) shouldBe "a|1|true".md5()
      md5Of("hello") shouldBe "5d41402abc4b2a76b9719d911017c592"
      md5Of("a", "b", separator = "-") shouldBe "a-b".md5()
      md5Of("a", "b") shouldNotBe md5Of("a", "c")
    }
  }
}
