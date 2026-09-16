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

import com.pambrose.common.util.asRegex
import com.pambrose.common.util.ensureLeadingSlash
import com.pambrose.common.util.ensurePrefix
import com.pambrose.common.util.ensureSuffix
import com.pambrose.common.util.firstLineNumberOf
import com.pambrose.common.util.isBracketed
import com.pambrose.common.util.isDouble
import com.pambrose.common.util.isDoubleQuoted
import com.pambrose.common.util.isFloat
import com.pambrose.common.util.isInt
import com.pambrose.common.util.isNotBracketed
import com.pambrose.common.util.isNotDouble
import com.pambrose.common.util.isNotDoubleQuoted
import com.pambrose.common.util.isNotFloat
import com.pambrose.common.util.isNotInt
import com.pambrose.common.util.isNotQuoted
import com.pambrose.common.util.isNotSingleQuoted
import com.pambrose.common.util.isQuoted
import com.pambrose.common.util.isSingleQuoted
import com.pambrose.common.util.join
import com.pambrose.common.util.lastLineNumberOf
import com.pambrose.common.util.length
import com.pambrose.common.util.linesBetween
import com.pambrose.common.util.maskUrlCredentials
import com.pambrose.common.util.maxLength
import com.pambrose.common.util.nullIfBlank
import com.pambrose.common.util.obfuscate
import com.pambrose.common.util.pathOf
import com.pambrose.common.util.pluralize
import com.pambrose.common.util.singleToDoubleQuoted
import com.pambrose.common.util.substringBetween
import com.pambrose.common.util.toDoubleQuoted
import com.pambrose.common.util.toPath
import com.pambrose.common.util.toPattern
import com.pambrose.common.util.toRootPath
import com.pambrose.common.util.toSingleQuoted
import com.pambrose.common.util.trimEnds
import com.pambrose.common.util.withLineNumbers
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotMatch

class StringExtensionTests : StringSpec() {
  init {
    "Int length is exact at every power-of-ten boundary" {
      0.length shouldBe 1
      var power = 1
      for (digits in 1..9) {
        power *= 10
        (power - 1).length shouldBe digits
        power.length shouldBe digits + 1
        (-(power - 1)).length shouldBe digits
      }
      Int.MAX_VALUE.length shouldBe 10
      Int.MIN_VALUE.length shouldBe 10
    }

    "quote tests" {
      "".isSingleQuoted() shouldBe false
      "".isDoubleQuoted() shouldBe false
      "".isQuoted() shouldBe false
      "".isNotQuoted() shouldBe true

      " ".isSingleQuoted() shouldBe false
      " ".isDoubleQuoted() shouldBe false
      " ".isQuoted() shouldBe false
      " ".isNotQuoted() shouldBe true

      "'".isSingleQuoted() shouldBe false
      "'".isDoubleQuoted() shouldBe false
      "'".isQuoted() shouldBe false
      "'".isNotQuoted() shouldBe true

      """ " """.isSingleQuoted() shouldBe false
      """ " """.isDoubleQuoted() shouldBe false
      """ " """.isQuoted() shouldBe false
      """ " """.isNotQuoted() shouldBe true

      """ "" """.isSingleQuoted() shouldBe false
      """ "" """.isDoubleQuoted() shouldBe true
      """ "" """.isQuoted() shouldBe true
      """ "" """.isNotQuoted() shouldBe false

      "''".isSingleQuoted() shouldBe true
      "''".isDoubleQuoted() shouldBe false
      "''".isQuoted() shouldBe true
      "''".isNotQuoted() shouldBe false
    }

    "is int test" {
      "".isInt() shouldBe false
      "a".isInt() shouldBe false
      "4".isInt() shouldBe true
      "4.0".isInt() shouldBe false

      "".isNotInt() shouldBe true
      "a".isNotInt() shouldBe true
      "4".isNotInt() shouldBe false
      "4.0".isNotInt() shouldBe true
    }

    "is float test" {
      "".isFloat() shouldBe false
      "a".isFloat() shouldBe false
      "4.0".isFloat() shouldBe true
      "4".isFloat() shouldBe true

      "".isNotFloat() shouldBe true
      "a".isNotFloat() shouldBe true
      "4.0".isNotFloat() shouldBe false
      "4".isNotFloat() shouldBe false
    }

    "is double test" {
      "".isDouble() shouldBe false
      "a".isDouble() shouldBe false
      "4.0".isDouble() shouldBe true
      "4".isDouble() shouldBe true

      "".isNotDouble() shouldBe true
      "a".isNotDouble() shouldBe true
      "4.0".isNotDouble() shouldBe false
      "4".isNotDouble() shouldBe false
    }

    "convert test" {
      "".singleToDoubleQuoted() shouldBe ""
      "'".singleToDoubleQuoted() shouldBe "'"
      "'test'".singleToDoubleQuoted() shouldBe """"test""""
      // Inner double quotes are backslash-escaped so the result is a well-formed double-quoted string
      """'te"st'""".singleToDoubleQuoted() shouldBe "\"te\\\"st\""
      """'a"b"c'""".singleToDoubleQuoted() shouldBe "\"a\\\"b\\\"c\""
      """"test"""".singleToDoubleQuoted() shouldBe """"test""""
    }

    // isSingleQuoted() looks at the trimmed string, so the quotes of a padded string are not its first and
    // last characters: the padding has to go before the quotes are stripped.
    "singleToDoubleQuoted ignores surrounding whitespace" {
      "  'x'  ".singleToDoubleQuoted() shouldBe "\"x\""
      "\t'a b'\n".singleToDoubleQuoted() shouldBe "\"a b\""
    }

    // Without escaping, a trailing backslash would escape the closing quote of the result.
    "singleToDoubleQuoted escapes backslashes as well as double quotes" {
      """'a\b'""".singleToDoubleQuoted() shouldBe """"a\\b""""
      """'a\'""".singleToDoubleQuoted() shouldBe """"a\\""""
      """'a\"b'""".singleToDoubleQuoted() shouldBe """"a\\\"b""""
    }

    "plural test" {
      "car".pluralize(0) shouldBe "cars"
      "car".pluralize(1) shouldBe "car"
      "car".pluralize(2) shouldBe "cars"

      "ski".pluralize(1, "es") shouldBe "ski"
      "ski".pluralize(2, "es") shouldBe "skies"
    }

    "test paths" {
      ["a", "b", "c"].join() shouldBe "a/b/c"
      ["a", "b", "c"].toPath() shouldBe "/a/b/c/"
      ["a", "b", "c"].toRootPath() shouldBe "/a/b/c"
      ["a", "b", "c"].toRootPath(true) shouldBe "/a/b/c/"
      ["a", "b", "c"].toPath(addPrefix = false, addTrailing = true) shouldBe "a/b/c/"
      ["a", "b", "c"].toPath() shouldBe "/a/b/c/"
      ["/a", "/b", "c"].toPath() shouldBe "/a/b/c/"
      ["/a", "/b", "c/"].toPath() shouldBe "/a/b/c/"
      ["a", "/b", "c/"].toPath() shouldBe "/a/b/c/"

      ["a", "b", "c"].join() shouldBe "a/b/c"
      ["a/", "/b/", "c"].join() shouldBe "a/b/c"
      ["/a/", "/b/", "c"].join() shouldBe "/a/b/c"
      ["/a/", "/b/", "/c"].join() shouldBe "/a/b/c"
    }

    "test line indexes" {
      val s =
        """
      aaa
      bbb
      ccc
      ddd
      eee
      aaa
      bbb
      ccc
      ddd
      eee
      """.trimIndent()

      s.firstLineNumberOf(Regex("zzz")) shouldBe -1
      s.firstLineNumberOf(Regex("bbb")) shouldBe 1

      s.lastLineNumberOf(Regex("zzz")) shouldBe -1
      s.lastLineNumberOf(Regex("bbb")) shouldBe 6
    }

    // Without the startIdx + 1 > endIdx guard, subList(fromIndex > toIndex) throws.
    "linesBetween is empty when the end boundary comes before or at the start boundary" {
      listOf("a", "b", "c").linesBetween(Regex("c"), Regex("a")) shouldBe emptyList()
      listOf("a", "b").linesBetween(Regex("a"), Regex("a")) shouldBe emptyList()
      "a\nb\nc".linesBetween(Regex("c"), Regex("a")) shouldBe emptyList()
    }

    "test lines between" {
      val s =
        """
      aaa
      bbb
      ccc
      aaa
      bbb
      ccc
      """.trimIndent()

      s.linesBetween(Regex("aaa"), Regex("ccc")) shouldBe ["bbb", "ccc", "aaa", "bbb"]
      s.linesBetween(Regex("ccc"), Regex("bbb")) shouldBe ["aaa"]
      s.linesBetween(Regex("ccc"), Regex("aaa")) shouldBe []
    }

    "bracket test" {
      "  [fddsf]  ".isBracketed() shouldBe true
      "[fddsf]".isBracketed() shouldBe true
      "[]".isBracketed() shouldBe true
      "[".isBracketed() shouldBe false
      "]".isBracketed() shouldBe false
      "".isBracketed() shouldBe false

      "{fddsf}".isBracketed('{', '}') shouldBe true
      "{}}".isBracketed('{', '}') shouldBe true
      "{".isBracketed('{', '}') shouldBe false
      "}".isBracketed('{', '}') shouldBe false
      "".isBracketed('{', '}') shouldBe false
    }

    "trim ends test" {
      "  [fddsf]  ".trimEnds() shouldBe "fddsf"
      "  [fddsf]  ".trimEnds(2) shouldBe "dds"
    }

    "not quoted variant test" {
      "'test'".isNotSingleQuoted() shouldBe false
      "  'test'  ".isNotSingleQuoted() shouldBe false
      "\"test\"".isNotSingleQuoted() shouldBe true
      "test".isNotSingleQuoted() shouldBe true
      "".isNotSingleQuoted() shouldBe true

      "\"test\"".isNotDoubleQuoted() shouldBe false
      "  \"test\"  ".isNotDoubleQuoted() shouldBe false
      "'test'".isNotDoubleQuoted() shouldBe true
      "test".isNotDoubleQuoted() shouldBe true
      "".isNotDoubleQuoted() shouldBe true
    }

    "to quoted test" {
      "test".toSingleQuoted() shouldBe "'test'"
      "".toSingleQuoted() shouldBe "''"
      "test".toDoubleQuoted() shouldBe "\"test\""
      "".toDoubleQuoted() shouldBe "\"\""
    }

    "ensure prefix test" {
      "path".ensurePrefix("/") shouldBe "/path"
      "/path".ensurePrefix("/") shouldBe "/path"
      "path".ensurePrefix("pa") shouldBe "path"
      "".ensurePrefix("/") shouldBe "/"

      "path".ensureLeadingSlash() shouldBe "/path"
      "/path".ensureLeadingSlash() shouldBe "/path"
    }

    "is not bracketed test" {
      "[fddsf]".isNotBracketed() shouldBe false
      "  [fddsf]  ".isNotBracketed() shouldBe false
      "fddsf".isNotBracketed() shouldBe true
      "[".isNotBracketed() shouldBe true
      "".isNotBracketed() shouldBe true

      "{fddsf}".isNotBracketed('{', '}') shouldBe false
      "[fddsf]".isNotBracketed('{', '}') shouldBe true
    }

    "pattern match test" {
      "*st*".toPattern shouldBe "^.*st.*$"
      "?.*".toPattern shouldBe "^.\\..*$"

      "Test.java".contains("*st*".asRegex()) shouldBe true
      "Test.java".contains("*.j".asRegex()) shouldBe false
      "Test.java".contains("*.java".asRegex()) shouldBe true
      "Test.java".contains("T?s?.java".asRegex()) shouldBe true
      "Test.java".contains("T?s?*java".asRegex()) shouldBe true
      "Test.java".contains("T?s?*jav?".asRegex()) shouldBe true
      "Test.java".contains("T?s?.*".asRegex()) shouldBe true
      "Test.java".contains("T?s?.*a".asRegex()) shouldBe true
      "Test.java".contains("t?s?.*a".asRegex()) shouldBe false
      "Test.java".contains("t?s?.*a".asRegex(true)) shouldBe true
    }

    "pattern match treats regex metacharacters in the glob literally" {
      "file(1).txt" shouldMatch "file(1).txt".asRegex()
      "file1.txt" shouldNotMatch "file(1).txt".asRegex()
      "a+b" shouldMatch "a+b".asRegex()
      "aab" shouldNotMatch "a+b".asRegex()
      "[abc" shouldMatch "[abc".asRegex()
      $$"a$b^c{1}|d\\e" shouldMatch $$"a$b^c{1}|d\\e".asRegex()
      "a__SINGLE__DOT__b".toPattern shouldBe "^a__SINGLE__DOT__b$"
    }

    "Long length is exact at every power-of-ten boundary" {
      var power = 1L
      for (digits in 1..18) {
        power *= 10
        (power - 1).length shouldBe digits
        power.length shouldBe digits + 1
        (-(power - 1)).length shouldBe digits
      }
      Long.MAX_VALUE.length shouldBe 19
      Long.MIN_VALUE.length shouldBe 19
    }

    "join and toPath strip a whole multi-character separator and skip empty elements" {
      ["a", "::b"].join("::") shouldBe "a::b"
      ["a::", "::b"].join("::") shouldBe "a::b"
      ["a", "", "b"].join() shouldBe "a/b"
      ["", "a", "", "b"].toRootPath() shouldBe "/a/b"
      ["a", "", "b"].toPath() shouldBe "/a/b/"
    }

    // Parsing is the platform's own. The JVM and Kotlin/Native also accept a trailing f/d and hexadecimal
    // floating-point literals, JS accepts hexadecimal integers, and wasmJs accepts neither.
    "isFloat and isDouble follow the platform's number parsing" {
      val suffixAndHexFloat = testPlatform == TestPlatform.JVM || testPlatform == TestPlatform.NATIVE
      val hexInteger = testPlatform == TestPlatform.JS
      for (isNumber in [String::isFloat, String::isDouble]) {
        isNumber("4f") shouldBe suffixAndHexFloat
        isNumber("4d") shouldBe suffixAndHexFloat
        isNumber("0x1p3") shouldBe suffixAndHexFloat
        isNumber("0x10") shouldBe hexInteger
      }
    }

    "isFloat and isDouble agree across platforms on signs, exponents, padding, NaN and Infinity" {
      for (s in [" 4 ", "+4", "-4", ".5", "5.", "1e3", "NaN", "Infinity", "-Infinity"]) {
        s.isFloat() shouldBe true
        s.isDouble() shouldBe true
      }
      for (s in ["1_000", "4.0.0", "e3", "--4"]) {
        s.isFloat() shouldBe false
        s.isDouble() shouldBe false
      }
    }

    // JS's native substring swaps or clamps indices that the other platforms reject, so without explicit checks
    // these returned a wrong result on JS and threw on the JVM.
    "trimEnds rejects a length that does not fit the trimmed string" {
      "  ab  ".trimEnds() shouldBe ""
      shouldThrow<IllegalArgumentException> { "a".trimEnds() }
      shouldThrow<IllegalArgumentException> { " [x] ".trimEnds(2) }
      shouldThrow<IllegalArgumentException> { "abc".trimEnds(-1) }
    }

    // An emoji outside the Basic Multilingual Plane is a surrogate pair; cutting or masking half of it leaves an
    // invalid string that prints as '?'.
    "maxLength does not split a surrogate pair" {
      "a\uD83D\uDE00b".maxLength(2) shouldBe "a"
      "a\uD83D\uDE00b".maxLength(3) shouldBe "a\uD83D\uDE00"
      "\uD83D\uDE00".maxLength(1) shouldBe ""
    }

    "obfuscate masks or keeps a surrogate pair as a whole" {
      "\uD83D\uDE00x".obfuscate() shouldBe "*x"
      "x\uD83D\uDE00y".obfuscate() shouldBe "*\uD83D\uDE00*"
    }

    "maxLength rejects a negative length" {
      "abc".maxLength(0) shouldBe ""
      shouldThrow<IllegalArgumentException> { "abc".maxLength(-1) }
    }

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

    "substring between test" {
      "hello [world] test".substringBetween("[", "]") shouldBe "world"
      "<tag>content</tag>".substringBetween("<tag>", "</tag>") shouldBe "content"
      "no markers here".substringBetween("[", "]") shouldBe "no markers here"
    }

    "with line numbers test" {
      "line1\nline2\nline3".withLineNumbers() shouldBe "1 : line1\n2 : line2\n3 : line3"

      // Numbers are left-aligned and padded to the width of the largest line number.
      val numbered = (1..10).joinToString("\n") { "l$it" }.withLineNumbers().lines()
      numbered.first() shouldBe "1  : l1"
      numbered.last() shouldBe "10 : l10"
      "a\nb".withLineNumbers(separator = '|') shouldBe "1 | a\n2 | b"
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

    "mask url credentials only treats an @ inside the authority as a credential separator" {
      // An @ in the path, query, or fragment must not be mistaken for userinfo.
      "https://api.example.com/users?email=bob@corp.com".maskUrlCredentials() shouldBe
        "https://api.example.com/users?email=bob@corp.com"
      "https://example.com/a@b".maskUrlCredentials() shouldBe "https://example.com/a@b"
      "https://example.com#section@2".maskUrlCredentials() shouldBe "https://example.com#section@2"
      "https://u:p@host.com/a@b".maskUrlCredentials() shouldBe "https://*****:*****@host.com/a@b"
      "https://u:p@host.com?next=me@x.com".maskUrlCredentials() shouldBe "https://*****:*****@host.com?next=me@x.com"
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
