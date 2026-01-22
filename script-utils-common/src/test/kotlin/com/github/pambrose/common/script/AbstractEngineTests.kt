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

package com.github.pambrose.common.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import javax.script.ScriptException

class AbstractEngineTests {
  @Test
  fun invalidExtensionThrowsExceptionTest() {
    val exception = shouldThrow<ScriptException> {
      object : AbstractEngine("invalid_extension_xyz") {}
    }
    exception.message shouldContain "Unrecognized script extension"
  }

  @Test
  fun anotherInvalidExtensionTest() {
    val exception = shouldThrow<ScriptException> {
      object : AbstractEngine("") {}
    }
    exception.message shouldContain "Unrecognized script extension"
  }

  @Test
  fun yetAnotherInvalidExtensionTest() {
    val exception = shouldThrow<ScriptException> {
      object : AbstractEngine("foobar123") {}
    }
    exception.message shouldContain "Unrecognized script extension"
  }
}
