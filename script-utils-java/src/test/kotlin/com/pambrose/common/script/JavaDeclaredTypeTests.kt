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
import java.util.function.IntSupplier
import java.util.function.Supplier
import java.util.stream.IntStream
import kotlin.reflect.typeOf

// Java source cannot name an inner class of a generic class with type arguments of its own on the raw outer class.
class GenericHolder<T> {
  inner class Answer : IntSupplier {
    override fun getAsInt() = 42
  }

  inner class Wrapped<U>(
    private val item: U,
  ) : Supplier<U> {
    override fun get() = item
  }
}

/** Tests, on the real Java engine, that script-utils-common declares a value as a type Java source can name. */
class JavaDeclaredTypeTests : StringSpec() {
  init {
    declaredTypeTests()
  }

  private fun declaredTypeTests() {
    // IntStream.of(1, 2) is a package-private IntPipeline.Head<E_IN>, so adding it without a type argument used to
    // throw.
    "an IntStream is declared as an IntStream" {
      JavaScript().use { script ->
        script.add("ints", IntStream.of(1, 2))
        script.varDecls shouldBe "  public java.util.stream.IntStream ints;"
        script.eval("ints.sum()") shouldBe 3
      }
    }

    // Wrapped used to be declared as GenericHolder.Wrapped<java.lang.String>, which javac rejects on a raw outer
    // type.
    "an inner class of a generic class is declared as a supertype Java source can name" {
      JavaScript().use { script ->
        script.add("answer", GenericHolder<Int>().Answer())
        script.add("wrapped", GenericHolder<Int>().Wrapped("abc"), typeOf<String>())
        script.eval("answer.getAsInt() + wrapped.get().length()") shouldBe 45
        script.varDecls shouldBe
          """
          |  public java.util.function.IntSupplier answer;
          |  public java.util.function.Supplier<java.lang.String> wrapped;
          """.trimMargin()
      }
    }
  }
}
