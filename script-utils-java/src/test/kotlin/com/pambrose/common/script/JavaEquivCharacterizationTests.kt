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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.reflect.typeOf

/**
 * Pins the exact generated type-parameter strings produced via the private `KType.javaEquiv`
 * (exercised through `varDecls`/`params`). Guards both the `Int`/`Int?` -> `Integer` mapping and
 * the `else` branch's `removePrefix("kotlin.")` / `replace("?", "")` transformation.
 */
class JavaEquivCharacterizationTests : StringSpec() {
  init {
    "typeOf<Int>() renders as Integer in a generic type parameter" {
      JavaScript().use {
        it.add("list", mutableListOf(1), typeOf<Int>())
        it.varDecls.trim() shouldBe "public ArrayList<Integer> list;"
      }
    }

    "typeOf<Int?>() also renders as Integer (nullable Int maps to the boxed type)" {
      JavaScript().use {
        it.add("list", mutableListOf<Int?>(), typeOf<Int?>())
        it.varDecls.trim() shouldBe "public ArrayList<Integer> list;"
      }
    }

    "non-Int type args go through the else branch (String, Integer)" {
      JavaScript().use {
        it.add("map", mutableMapOf("k" to 1), typeOf<String>(), typeOf<Int>())
        it.varDecls.trim() shouldBe "public LinkedHashMap<String, Integer> map;"
      }
    }

    "nullable non-Int type arg has its '?' stripped in the else branch (String? -> String)" {
      JavaScript().use {
        it.add("list", mutableListOf<String?>(), typeOf<String?>())
        it.varDecls.trim() shouldBe "public ArrayList<String> list;"
      }
    }
  }
}
