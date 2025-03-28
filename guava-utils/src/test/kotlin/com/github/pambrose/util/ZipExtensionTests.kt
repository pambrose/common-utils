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

package com.github.pambrose.util

import com.github.pambrose.common.util.unzip
import com.github.pambrose.common.util.zip
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class ZipExtensionTests {
  @Test
  fun shortStringZipTest() {
    val s = "kjwkjfhwekfjhwwewewerrr\nwdfwefwefwef\n"
    s.zip().unzip() shouldBeEqualTo s

    val t = "kjwkjfhwekfjhwwewewerrr\nwdfwefwefwef\n"
    t.zip().unzip() shouldBeEqualTo t
  }

  @Test
  fun longStringZipTest() {
    val s =
      "kjwkjfhwekf cdsc  ##444445 wekfnkfn ew fwefwejfewkjfwef  qweqweqweqwe wef w ef wefwef ezzzzxdweere\n"
    val g = buildString { repeat(100_000) { append(s) } }
    g.zip().unzip() shouldBeEqualTo g
  }

  @Test
  fun empyStringZipTest() {
    "".zip() shouldBeEqualTo ByteArray(0)
    "".zip().unzip() shouldBeEqualTo ""
    ByteArray(0).unzip() shouldBeEqualTo ""
  }
}
