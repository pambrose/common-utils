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

import com.github.pambrose.common.util.isDoubleQuoted
import com.github.pambrose.common.util.isQuoted
import com.github.pambrose.common.util.isSingleQuoted
import com.github.pambrose.common.util.length
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Test

class MiscFuncTests {

  @Test
  fun lengthTests() {
    repeat(10000000) { i -> i.length shouldEqual i.toString().length }
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
}