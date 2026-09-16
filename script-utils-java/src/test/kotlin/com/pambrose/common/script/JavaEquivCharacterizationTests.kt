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
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

// Two type parameters, and no public supertype with two, so generated code can only declare it as Object. Being
// internal, it is not a class generated code can name either.
internal class InternalPair<A, B>(
  val first: A,
  val second: B,
) : AbstractList<A>() {
  override val size get() = 1

  override fun get(index: Int) = first
}

/**
 * Pins the exact generated field declarations produced via `varDecls`/`params`: fully-qualified Java class names,
 * with Kotlin type arguments mapped to their boxed Java types and nullability dropped.
 */
class JavaEquivCharacterizationTests : StringSpec() {
  init {
    "typeOf<Int>() renders as Integer in a generic type parameter" {
      JavaScript().use {
        it.add("list", mutableListOf(1), typeOf<Int>())
        it.varDecls.trim() shouldBe "public java.util.ArrayList<java.lang.Integer> list;"
      }
    }

    "typeOf<Int?>() also renders as Integer (nullable Int maps to the boxed type)" {
      JavaScript().use {
        it.add("list", mutableListOf<Int?>(), typeOf<Int?>())
        it.varDecls.trim() shouldBe "public java.util.ArrayList<java.lang.Integer> list;"
      }
    }

    "non-Int type args go through the else branch (String, Integer)" {
      JavaScript().use {
        it.add("map", mutableMapOf("k" to 1), typeOf<String>(), typeOf<Int>())
        it.varDecls.trim() shouldBe "public java.util.LinkedHashMap<java.lang.String, java.lang.Integer> map;"
      }
    }

    "nullable non-Int type arg has its '?' stripped in the else branch (String? -> String)" {
      JavaScript().use {
        it.add("list", mutableListOf<String?>(), typeOf<String?>())
        it.varDecls.trim() shouldBe "public java.util.ArrayList<java.lang.String> list;"
      }
    }

    "arrays, star projections, and type parameters render as Java types" {
      JavaScript().use {
        it.params("x", arrayOf(typeOf<Array<Int>>())) shouldBe "<java.lang.Integer[]>"
        it.params("x", arrayOf(typeOf<Array<Array<String?>>>())) shouldBe "<java.lang.String[][]>"
        it.params("x", arrayOf(typeOf<IntArray>())) shouldBe "<int[]>"
        it.params("x", arrayOf(typeOf<List<*>>())) shouldBe "<java.util.List<?>>"
        // Java has no array of ?, so Array<*> is erased to Object[]; it used to render as ?[].
        it.params("x", arrayOf(typeOf<Array<*>>())) shouldBe "<java.lang.Object[]>"
        it.params("x", arrayOf(typeOf<Map<String, Array<*>>>())) shouldBe
          "<java.util.Map<java.lang.String, java.lang.Object[]>>"
        it.params("x", arrayOf(List::class.typeParameters.single().createType())) shouldBe "<Object>"
      }
    }

    // ArrayPrimitive: an Array<Int> is the case under test.
    @Suppress("ArrayPrimitive")
    "an object array is declared as an array of its registered element type" {
      JavaScript().use {
        it.add("ints", arrayOf(1, 2), typeOf<Int>())
        it.add("lists", arrayOf(listOf(1)), typeOf<List<Int>>())
        it.add("primitives", intArrayOf(1, 2))
        it.varDecls shouldBe
          """
          |  public java.lang.Integer[] ints;
          |  public java.util.List<java.lang.Integer>[] lists;
          |  public int[] primitives;
          """.trimMargin()
      }
    }

    "a value with no nameable class for its type arguments is declared as a plain Object" {
      JavaScript().use {
        it.add("pair", InternalPair(1, "a"), typeOf<Int>(), typeOf<String>())
        it.varDecls.trim() shouldBe "public java.lang.Object pair;"
      }
    }
  }
}
