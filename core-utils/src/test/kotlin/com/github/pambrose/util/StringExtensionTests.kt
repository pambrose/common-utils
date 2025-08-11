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

import com.github.pambrose.common.util.asRegex
import com.github.pambrose.common.util.firstLineNumberOf
import com.github.pambrose.common.util.isBracketed
import com.github.pambrose.common.util.isDouble
import com.github.pambrose.common.util.isDoubleQuoted
import com.github.pambrose.common.util.isFloat
import com.github.pambrose.common.util.isInt
import com.github.pambrose.common.util.isNotDouble
import com.github.pambrose.common.util.isNotFloat
import com.github.pambrose.common.util.isNotInt
import com.github.pambrose.common.util.isNotQuoted
import com.github.pambrose.common.util.isQuoted
import com.github.pambrose.common.util.isSingleQuoted
import com.github.pambrose.common.util.join
import com.github.pambrose.common.util.lastLineNumberOf
import com.github.pambrose.common.util.length
import com.github.pambrose.common.util.linesBetween
import com.github.pambrose.common.util.pluralize
import com.github.pambrose.common.util.singleToDoubleQuoted
import com.github.pambrose.common.util.toPath
import com.github.pambrose.common.util.toPattern
import com.github.pambrose.common.util.toRootPath
import com.github.pambrose.common.util.trimEnds
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StringExtensionTests {
  @Test
  fun lengthTests() {
    repeat(10_000_000) { i -> i.length shouldBe i.toString().length }
    for (i in Int.MAX_VALUE - 10000000..Int.MAX_VALUE) i.length shouldBe i.toString().length

    for (i in 0L..10000000L) i.length shouldBe i.toString().length
    for (i in Long.MAX_VALUE - 10000000L..Long.MAX_VALUE) i.length shouldBe i.toString().length
  }

  @Test
  fun quoteTests() {
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

  @Test
  fun isIntTest() {
    "".isInt() shouldBe false
    "a".isInt() shouldBe false
    "4".isInt() shouldBe true
    "4.0".isInt() shouldBe false

    "".isNotInt() shouldBe true
    "a".isNotInt() shouldBe true
    "4".isNotInt() shouldBe false
    "4.0".isNotInt() shouldBe true
  }

  @Test
  fun isFloatTest() {
    "".isFloat() shouldBe false
    "a".isFloat() shouldBe false
    "4.0".isFloat() shouldBe true
    "4".isFloat() shouldBe true

    "".isNotFloat() shouldBe true
    "a".isNotFloat() shouldBe true
    "4.0".isNotFloat() shouldBe false
    "4".isNotFloat() shouldBe false
  }

  @Test
  fun isDoubleTest() {
    "".isDouble() shouldBe false
    "a".isDouble() shouldBe false
    "4.0".isDouble() shouldBe true
    "4".isDouble() shouldBe true

    "".isNotDouble() shouldBe true
    "a".isNotDouble() shouldBe true
    "4.0".isNotDouble() shouldBe false
    "4".isNotDouble() shouldBe false
  }

  @Test
  fun convertTest() {
    "".singleToDoubleQuoted() shouldBe ""
    "'".singleToDoubleQuoted() shouldBe "'"
    "'test'".singleToDoubleQuoted() shouldBe """"test""""
    """'te"st'""".singleToDoubleQuoted() shouldBe """"te"st""""
    """"test"""".singleToDoubleQuoted() shouldBe """"test""""
  }

  @Test
  fun pluralTest() {
    "car".pluralize(0) shouldBe "cars"
    "car".pluralize(1) shouldBe "car"
    "car".pluralize(2) shouldBe "cars"

    "ski".pluralize(1, "es") shouldBe "ski"
    "ski".pluralize(2, "es") shouldBe "skies"
  }

  @Test
  fun testPaths() {
    listOf("a", "b", "c").join() shouldBe "a/b/c"
    listOf("a", "b", "c").toPath() shouldBe "/a/b/c/"
    listOf("a", "b", "c").toRootPath() shouldBe "/a/b/c"
    listOf("a", "b", "c").toRootPath(true) shouldBe "/a/b/c/"
    listOf("a", "b", "c").toPath(addPrefix = false, addTrailing = true) shouldBe "a/b/c/"
    listOf("a", "b", "c").toPath() shouldBe "/a/b/c/"
    listOf("/a", "/b", "c").toPath() shouldBe "/a/b/c/"
    listOf("/a", "/b", "c/").toPath() shouldBe "/a/b/c/"
    listOf("a", "/b", "c/").toPath() shouldBe "/a/b/c/"

    listOf("a", "b", "c").join() shouldBe "a/b/c"
    listOf("a/", "/b/", "c").join() shouldBe "a/b/c"
    listOf("/a/", "/b/", "c").join() shouldBe "/a/b/c"
    listOf("/a/", "/b/", "/c").join() shouldBe "/a/b/c"
  }

  @Test
  fun testLineIndexes() {
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

  @Test
  fun testLinesBetween() {
    val s =
      """
      aaa
      bbb
      ccc
      aaa
      bbb
      ccc
      """.trimIndent()

    s.linesBetween(Regex("aaa"), Regex("ccc")) shouldBe listOf("bbb", "ccc", "aaa", "bbb")
    s.linesBetween(Regex("ccc"), Regex("bbb")) shouldBe listOf("aaa")
    s.linesBetween(Regex("ccc"), Regex("aaa")) shouldBe listOf()
  }

  @Test
  fun bracketTest() {
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

  @Test
  fun trimEndsTest() {
    "  [fddsf]  ".trimEnds() shouldBe "fddsf"
    "  [fddsf]  ".trimEnds(2) shouldBe "dds"
  }

  @Test
  fun patternMatchTest() {
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
}
