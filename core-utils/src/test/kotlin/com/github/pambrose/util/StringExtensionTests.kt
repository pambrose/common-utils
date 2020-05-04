/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class StringExtensionTests {

  @Test
  fun lengthTests() {
    repeat(10_000_000) { i -> i.length shouldBeEqualTo i.toString().length }
    for (i in Int.MAX_VALUE - 10000000..Int.MAX_VALUE) i.length shouldBeEqualTo i.toString().length

    for (i in 0L..10000000L) i.length shouldBeEqualTo i.toString().length
    for (i in Long.MAX_VALUE - 10000000L..Long.MAX_VALUE) i.length shouldBeEqualTo i.toString().length
  }

  @Test
  fun quoteTests() {
    "".isSingleQuoted() shouldBeEqualTo false
    "".isDoubleQuoted() shouldBeEqualTo false
    "".isQuoted() shouldBeEqualTo false

    " ".isSingleQuoted() shouldBeEqualTo false
    " ".isDoubleQuoted() shouldBeEqualTo false
    " ".isQuoted() shouldBeEqualTo false

    "'".isSingleQuoted() shouldBeEqualTo false
    "'".isDoubleQuoted() shouldBeEqualTo false
    "'".isQuoted() shouldBeEqualTo false

    """ " """.isSingleQuoted() shouldBeEqualTo false
    """ " """.isDoubleQuoted() shouldBeEqualTo false
    """ " """.isQuoted() shouldBeEqualTo false

    """ "" """.isSingleQuoted() shouldBeEqualTo false
    """ "" """.isDoubleQuoted() shouldBeEqualTo true
    """ "" """.isQuoted() shouldBeEqualTo true

    "''".isSingleQuoted() shouldBeEqualTo true
    "''".isDoubleQuoted() shouldBeEqualTo false
    "''".isQuoted() shouldBeEqualTo true
  }

  @Test
  fun convertTest() {
    "".singleToDoubleQuoted() shouldBeEqualTo ""
    "'".singleToDoubleQuoted() shouldBeEqualTo "'"
    "'test'".singleToDoubleQuoted() shouldBeEqualTo """"test""""
    """'te"st'""".singleToDoubleQuoted() shouldBeEqualTo """"te"st""""
    """"test"""".singleToDoubleQuoted() shouldBeEqualTo """"test""""
  }

  @Test
  fun pluralTest() {
    "car".pluralize(0) shouldBeEqualTo "cars"
    "car".pluralize(1) shouldBeEqualTo "car"
    "car".pluralize(2) shouldBeEqualTo "cars"

    "ski".pluralize(1, "es") shouldBeEqualTo "ski"
    "ski".pluralize(2, "es") shouldBeEqualTo "skies"
  }

  @Test
  fun testPaths() {

    listOf("a", "b", "c").toPath() shouldBeEqualTo "a/b/c/"
    listOf("/a", "/b", "c").toPath() shouldBeEqualTo "/a/b/c/"

    listOf("a", "b", "c").toPath(false) shouldBeEqualTo "a/b/c"
    listOf("a/", "/b/", "c").toPath(false) shouldBeEqualTo "a/b/c"
    listOf("/a/", "/b/", "c").toPath(false) shouldBeEqualTo "/a/b/c"
    listOf("/a/", "/b/", "/c").toPath(false) shouldBeEqualTo "/a/b/c"
  }

  @Test
  fun testLineIndexes() {
    val s = """
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

    s.firstLineNumberOf(Regex("zzz")) shouldBeEqualTo -1
    s.firstLineNumberOf(Regex("bbb")) shouldBeEqualTo 1

    s.lastLineNumberOf(Regex("zzz")) shouldBeEqualTo -1
    s.lastLineNumberOf(Regex("bbb")) shouldBeEqualTo 6
  }

  @Test
  fun testLinesBetween() {
    val s = """
      aaa
      bbb
      ccc
      aaa
      bbb
      ccc
    """.trimIndent()

    s.linesBetween(Regex("aaa"), Regex("ccc")) shouldBeEqualTo listOf("bbb", "ccc", "aaa", "bbb")
    s.linesBetween(Regex("ccc"), Regex("bbb")) shouldBeEqualTo listOf("aaa")
    s.linesBetween(Regex("ccc"), Regex("aaa")) shouldBeEqualTo listOf()
  }

  @Test
  fun bracketTest() {

    "  [fddsf]  ".isBracketed() shouldBeEqualTo true
    "[fddsf]".isBracketed() shouldBeEqualTo true
    "[]".isBracketed() shouldBeEqualTo true
    "[".isBracketed() shouldBeEqualTo false
    "]".isBracketed() shouldBeEqualTo false
    "".isBracketed() shouldBeEqualTo false

    "{fddsf}".isBracketed('{', '}') shouldBeEqualTo true
    "{}}".isBracketed('{', '}') shouldBeEqualTo true
    "{".isBracketed('{', '}') shouldBeEqualTo false
    "}".isBracketed('{', '}') shouldBeEqualTo false
    "".isBracketed('{', '}') shouldBeEqualTo false
  }

  @Test
  fun trimEndsTest() {
    "  [fddsf]  ".trimEnds() shouldBeEqualTo "fddsf"
    "  [fddsf]  ".trimEnds(2) shouldBeEqualTo "dds"
  }

}