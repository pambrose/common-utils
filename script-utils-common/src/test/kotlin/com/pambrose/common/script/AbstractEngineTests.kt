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

// DEPRECATION: one test reads the deprecated public engine.
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "DEPRECATION")

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import javax.script.ScriptException

class AbstractEngineTests : StringSpec() {
  init {
    "an extension with no registered engine throws a ScriptException naming it" {
      ["invalid_extension_xyz", "", "foobar123"].forEach { extension ->
        shouldThrow<ScriptException> { object : AbstractEngine(extension) {} }.message shouldBe
          "Unrecognized script extension: $extension"
      }
    }

    "a registered extension resolves its engine, which the deprecated engine property exposes" {
      val engine =
        object : AbstractEngine(FAKE_EXTENSION) {
          val scripted get() = scriptEngine
        }
      engine.scripted.shouldBeInstanceOf<FakeScriptEngine>()
      (engine.engine === engine.scripted) shouldBe true
    }

    "close does nothing by default, however often it is called" {
      val engine = object : AbstractEngine(FAKE_EXTENSION) {}
      shouldNotThrowAny {
        engine.close()
        engine.close()
      }
    }
  }
}
