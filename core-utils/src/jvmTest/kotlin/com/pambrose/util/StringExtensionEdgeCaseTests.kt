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
import com.pambrose.common.util.ensureSuffix
import com.pambrose.common.util.maskUrlCredentials
import com.pambrose.common.util.maxLength
import com.pambrose.common.util.md5
import com.pambrose.common.util.md5Of
import com.pambrose.common.util.newByteArraySalt
import com.pambrose.common.util.newStringSalt
import com.pambrose.common.util.nullIfBlank
import com.pambrose.common.util.obfuscate
import com.pambrose.common.util.pathOf
import com.pambrose.common.util.sha256
import com.pambrose.common.util.substringBetween
import com.pambrose.common.util.withLineNumbers
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

class StringExtensionEdgeCaseTests : StringSpec() {
  init {
    "null if blank test" {
      "".nullIfBlank() shouldBe null
      "   ".nullIfBlank() shouldBe null
      "hello".nullIfBlank() shouldBe "hello"
      " hello ".nullIfBlank() shouldBe " hello "
    }

    "ensure suffix test" {
      "file".ensureSuffix(".txt") shouldBe "file.txt"
      "file.txt".ensureSuffix(".txt") shouldBe "file.txt"
      "".ensureSuffix("/") shouldBe "/"
    }

    "encode decode test" {
      val original = "hello world"
      val encoded = original.encode()
      encoded shouldBe "hello+world"
      encoded.decode() shouldBe original

      val specialChars = "a=b&c=d"
      val encodedSpecial = specialChars.encode()
      encodedSpecial.decode() shouldBe specialChars
    }

    "substring between test" {
      "hello [world] test".substringBetween("[", "]") shouldBe "world"
      "<tag>content</tag>".substringBetween("<tag>", "</tag>") shouldBe "content"
      "no markers here".substringBetween("[", "]") shouldBe "no markers here"
    }

    "with line numbers test" {
      val input = "line1\nline2\nline3"
      val numbered = input.withLineNumbers()
      numbered shouldContain "1"
      numbered shouldContain "2"
      numbered shouldContain "3"
      numbered shouldContain "line1"
      numbered shouldContain "line2"
      numbered shouldContain "line3"
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

    "path of test" {
      pathOf("a", "b", "c") shouldBe "a/b/c"
      pathOf("a", "", "c") shouldBe "a/c" // Empty elements filtered
      pathOf("") shouldBe ""
      pathOf("single") shouldBe "single"
    }

    "mask url credentials test" {
      "https://user:pass@example.com/path".maskUrlCredentials() shouldBe "https://*****:*****@example.com/path"
      "http://admin:secret@localhost:8080".maskUrlCredentials() shouldBe "http://*****:*****@localhost:8080"
      "https://example.com/path".maskUrlCredentials() shouldBe "https://example.com/path" // No credentials
      "not a url".maskUrlCredentials() shouldBe "not a url"
    }

    "obfuscate test" {
      // obfuscate replaces characters at positions where index % freq == 0
      "hello".obfuscate() shouldBe "*e*l*" // freq=2: positions 0,2,4 replaced
      "hello".obfuscate(3) shouldBe "*el*o" // freq=3: positions 0,3 replaced
      "ab".obfuscate() shouldBe "*b"
      "".obfuscate() shouldBe ""
      "abc".obfuscate(1) shouldBe "***" // freq=1: every position replaced
      // freq must be positive; a non-positive freq previously threw ArithmeticException (i % 0).
      shouldThrow<IllegalArgumentException> { "abc".obfuscate(0) }
      shouldThrow<IllegalArgumentException> { "abc".obfuscate(-1) }
    }

    "max length test" {
      "hello world".maxLength(5) shouldBe "hello"
      "hello".maxLength(10) shouldBe "hello"
      "hello".maxLength(5) shouldBe "hello"
      "".maxLength(5) shouldBe ""
    }
  }
}
