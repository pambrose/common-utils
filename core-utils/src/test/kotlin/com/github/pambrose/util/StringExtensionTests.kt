/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.util

import com.github.pambrose.common.util.*
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class StringExtensionTests {

  @Test
  fun lengthTests() {
    repeat(10_000_000) { i -> i.length shouldEqual i.toString().length }
    for (i in Int.MAX_VALUE - 10000000..Int.MAX_VALUE) i.length shouldEqual i.toString().length

    for (i in 0L..10000000L) i.length shouldEqual i.toString().length
    for (i in Long.MAX_VALUE - 10000000L..Long.MAX_VALUE) i.length shouldEqual i.toString().length
  }

  @Test
  fun quoteTests() {
    "".isSingleQuoted() shouldEqual false
    "".isDoubleQuoted() shouldEqual false
    "".isQuoted() shouldEqual false

    " ".isSingleQuoted() shouldEqual false
    " ".isDoubleQuoted() shouldEqual false
    " ".isQuoted() shouldEqual false

    "'".isSingleQuoted() shouldEqual false
    "'".isDoubleQuoted() shouldEqual false
    "'".isQuoted() shouldEqual false

    """ " """.isSingleQuoted() shouldEqual false
    """ " """.isDoubleQuoted() shouldEqual false
    """ " """.isQuoted() shouldEqual false

    """ "" """.isSingleQuoted() shouldEqual false
    """ "" """.isDoubleQuoted() shouldEqual true
    """ "" """.isQuoted() shouldEqual true

    "''".isSingleQuoted() shouldEqual true
    "''".isDoubleQuoted() shouldEqual false
    "''".isQuoted() shouldEqual true
  }

  @Test
  fun pluralTest() {
    "car".pluralize(0) shouldEqual "cars"
    "car".pluralize(1) shouldEqual "car"
    "car".pluralize(2) shouldEqual "cars"
  }

  @Test
  fun zipTest() {
    val s =
      "kjwkjfhwekfjhwwewewerrr cdsc  ##444445 wekfnkfn ew fwefwejfewkjfwef  qweqweqweqwe wef wef w ef wefwef ezzzzxdweere"
    val builder = StringBuilder()
    repeat(100_000) { builder.append(s) }
    val g = builder.toString()
    g.zip().unzip() shouldEqual g

    invoking { "".zip() } shouldThrow IllegalArgumentException::class
    invoking { ByteArray(0).unzip() } shouldThrow IllegalArgumentException::class
  }
}