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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.script

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.Properties
import javax.script.ScriptException
import kotlin.reflect.typeOf

/**
 * Characterization tests pinning the exact ScriptException / IllegalStateException messages produced
 * by [AbstractScript.add] and [AbstractScript.params], exercised through [FakeScript], which keeps the base class's
 * default rendering and starts no compiler. The engine modules' tests already prove each branch THROWS; these lock the
 * message text and singular/plural wording so a regression in message content or branch routing is caught.
 */
class AbstractScriptAddMessageTests : StringSpec() {
  init {
    "add rejects a local class with a local/anonymous-class message" {
      class Local // local class -> qualifiedName is null

      FakeScript().use { script ->
        val e = shouldThrow<ScriptException> { script.add("x", Local()) }
        e.message shouldContain "is a local or an anonymous class"
        e.message shouldContain "\"x\"" // qname is the name double-quoted
      }
    }

    "add reports missing type parameters with singular wording" {
      FakeScript().use { script ->
        val e = shouldThrow<ScriptException> { script.add("list", mutableListOf(1)) }
        // pluralize(1) keeps the singular "parameter"
        e.message shouldContain "Expected 1 type parameter to be specified for"
        e.message shouldContain "\"list\""
      }
    }

    "add reports an unexpected single type parameter with the formatted type and singular wording" {
      FakeScript().use { script ->
        val e = shouldThrow<ScriptException> { script.add("value", 5, typeOf<Int>()) }
        // params() renders fully-qualified names -> <kotlin.Int>; one type -> "parameter"
        e.message shouldContain "Invalid type parameter <kotlin.Int> specified for"
        e.message shouldContain "\"value\""
      }
    }

    "add reports unexpected multiple type parameters with plural wording" {
      FakeScript().use { script ->
        val e = shouldThrow<ScriptException> { script.add("value", 5, typeOf<Int>(), typeOf<String>()) }
        // two types -> "parameters"
        e.message shouldContain "Invalid type parameters <kotlin.Int, kotlin.String> specified for"
      }
    }

    "add reports a type-count mismatch with the expected and found counts" {
      FakeScript().use { script ->
        val e =
          shouldThrow<ScriptException> {
            script.add("list", mutableListOf(1), typeOf<Int?>(), typeOf<Int>()) // 2 types vs 1 expected
          }
        // paramCnt == 1 drives the singular "parameter"; the found count and the rendered type list are both pinned.
        e.message shouldContain "Expected 1 type parameter for"
        e.message shouldContain "\"list\""
        e.message shouldContain "but found 2: <kotlin.Int?, kotlin.Int>"
      }
    }

    "params raises an IllegalStateException when no types are registered for the name" {
      FakeScript().use { script ->
        val e = shouldThrow<IllegalStateException> { script.params("never") }
        e.message shouldBe "No type parameters registered for never"
      }
    }

    "add requires type parameters for a generic class whose superclass is not generic" {
      FakeScript().use { script ->
        // Pair extends Object, so its own two type parameters used to go uncounted.
        val e = shouldThrow<ScriptException> { script.add("pair", 1 to "a") }
        e.message shouldContain "Expected 2 type parameters to be specified for"
      }
    }

    "add accepts a non-generic subclass of a generic class without type parameters" {
      FakeScript().use { script ->
        // Properties extends Hashtable<Object, Object> but declares no type parameters of its own.
        val props = Properties()
        shouldNotThrowAny { script.add("props", props) }
        (script.eval("props") === props) shouldBe true
      }
    }
  }
}
