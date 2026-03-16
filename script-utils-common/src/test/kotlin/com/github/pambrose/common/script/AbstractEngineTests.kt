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
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import javax.script.ScriptException

class AbstractEngineTests : StringSpec() {
  init {
    "invalid extension throws exception" {
      val exception = shouldThrow<ScriptException> {
        object : AbstractEngine("invalid_extension_xyz") {}
      }
      exception.message shouldContain "Unrecognized script extension"
    }

    "another invalid extension" {
      val exception = shouldThrow<ScriptException> {
        object : AbstractEngine("") {}
      }
      exception.message shouldContain "Unrecognized script extension"
    }

    "yet another invalid extension" {
      val exception = shouldThrow<ScriptException> {
        object : AbstractEngine("foobar123") {}
      }
      exception.message shouldContain "Unrecognized script extension"
    }
  }
}
