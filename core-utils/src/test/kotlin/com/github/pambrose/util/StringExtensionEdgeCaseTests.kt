/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.util

import com.github.pambrose.common.util.decode
import com.github.pambrose.common.util.encode
import com.github.pambrose.common.util.ensureSuffix
import com.github.pambrose.common.util.maskUrlCredentials
import com.github.pambrose.common.util.maxLength
import com.github.pambrose.common.util.md5
import com.github.pambrose.common.util.nullIfBlank
import com.github.pambrose.common.util.obfuscate
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.sha256
import com.github.pambrose.common.util.substringBetween
import com.github.pambrose.common.util.withLineNumbers
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldHaveLength
import org.junit.jupiter.api.Test

class StringExtensionEdgeCaseTests {
  @Test
  fun nullIfBlankTest() {
    "".nullIfBlank() shouldBe null
    "   ".nullIfBlank() shouldBe null
    "hello".nullIfBlank() shouldBe "hello"
    " hello ".nullIfBlank() shouldBe " hello "
  }

  @Test
  fun ensureSuffixTest() {
    "file".ensureSuffix(".txt") shouldBe "file.txt"
    "file.txt".ensureSuffix(".txt") shouldBe "file.txt"
    "".ensureSuffix("/") shouldBe "/"
  }

  @Test
  fun encodeDecodeTest() {
    val original = "hello world"
    val encoded = original.encode()
    encoded shouldBe "hello+world"
    encoded.decode() shouldBe original

    val specialChars = "a=b&c=d"
    val encodedSpecial = specialChars.encode()
    encodedSpecial.decode() shouldBe specialChars
  }

  @Test
  fun substringBetweenTest() {
    "hello [world] test".substringBetween("[", "]") shouldBe "world"
    "<tag>content</tag>".substringBetween("<tag>", "</tag>") shouldBe "content"
    "no markers here".substringBetween("[", "]") shouldBe "no markers here"
  }

  @Test
  fun withLineNumbersTest() {
    val input = "line1\nline2\nline3"
    val numbered = input.withLineNumbers()
    numbered shouldContain "1"
    numbered shouldContain "2"
    numbered shouldContain "3"
    numbered shouldContain "line1"
    numbered shouldContain "line2"
    numbered shouldContain "line3"
  }

  @Test
  fun md5Test() {
    val hash1 = "hello".md5()
    val hash2 = "hello".md5()
    hash1 shouldBe hash2
    hash1 shouldHaveLength 32 // MD5 produces 128 bits = 32 hex chars

    // Different input should produce different hash
    val hash3 = "world".md5()
    hash3 shouldNotBe hash1
  }

  @Test
  fun sha256Test() {
    val hash1 = "hello".sha256()
    val hash2 = "hello".sha256()
    hash1 shouldBe hash2
    hash1 shouldHaveLength 64 // SHA-256 produces 256 bits = 64 hex chars

    // Different input should produce different hash
    val hash3 = "world".sha256()
    hash3 shouldNotBe hash1
  }

  @Test
  fun md5WithSaltTest() {
    val hash1 = "hello".md5("salt1")
    val hash2 = "hello".md5("salt2")
    hash1 shouldNotBe hash2 // Different salt should produce different hash
  }

  @Test
  fun pathOfTest() {
    pathOf("a", "b", "c") shouldBe "a/b/c"
    pathOf("a", "", "c") shouldBe "a/c" // Empty elements filtered
    pathOf("") shouldBe ""
    pathOf("single") shouldBe "single"
  }

  @Test
  fun maskUrlCredentialsTest() {
    "https://user:pass@example.com/path".maskUrlCredentials() shouldBe "https://*****:*****@example.com/path"
    "http://admin:secret@localhost:8080".maskUrlCredentials() shouldBe "http://*****:*****@localhost:8080"
    "https://example.com/path".maskUrlCredentials() shouldBe "https://example.com/path" // No credentials
    "not a url".maskUrlCredentials() shouldBe "not a url"
  }

  @Test
  fun obfuscateTest() {
    // obfuscate replaces characters at positions where index % freq == 0
    "hello".obfuscate() shouldBe "*e*l*" // freq=2: positions 0,2,4 replaced
    "hello".obfuscate(3) shouldBe "*el*o" // freq=3: positions 0,3 replaced
    "ab".obfuscate() shouldBe "*b"
    "".obfuscate() shouldBe ""
  }

  @Test
  fun maxLengthTest() {
    "hello world".maxLength(5) shouldBe "hello"
    "hello".maxLength(10) shouldBe "hello"
    "hello".maxLength(5) shouldBe "hello"
    "".maxLength(5) shouldBe ""
  }
}
