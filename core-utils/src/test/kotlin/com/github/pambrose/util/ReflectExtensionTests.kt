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

import com.github.pambrose.common.util.typeParameterCount
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReflectExtensionTests {
  @Test
  fun tyeParamCountTest() {
    4.typeParameterCount shouldBe 0
    "dd".typeParameterCount shouldBe 0
    listOf(3).typeParameterCount shouldBe 1
    mapOf(3 to "d").typeParameterCount shouldBe 2
    mutableMapOf("k1" to 1).typeParameterCount shouldBe 2
    arrayOf(4).typeParameterCount shouldBe 1
    (IntArray(1) { 2 }).typeParameterCount shouldBe 0
  }
}
