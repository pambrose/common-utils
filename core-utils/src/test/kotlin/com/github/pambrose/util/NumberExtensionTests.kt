/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.util.times
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class NumberExtensionTests {
  @Test
  fun shortTest() {
    val cnt: Short = 1000.toShort()
    var runs: Short = 0
    var index: Short = 0
    cnt times {
      index shouldBeEqualTo it
      index++
      runs++
    }
    cnt shouldBeEqualTo runs
  }

  @Test
  fun intTest() {
    val cnt = 100000
    var runs = 0
    var index = 0
    cnt times {
      index shouldBeEqualTo it
      index++
      runs++
    }
    cnt shouldBeEqualTo runs
  }

  @Test
  fun longTest() {
    val cnt = 100000L
    var runs = 0L
    var index = 0L
    cnt times {
      index shouldBeEqualTo it
      index++
      runs++
    }
    cnt shouldBeEqualTo runs
  }
}
