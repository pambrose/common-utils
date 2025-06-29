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

package com.github.pambrose.common.delegate

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object AtomicDelegates {
  fun <T : Any> nonNullableReference(initValue: T? = null): ReadWriteProperty<Any?, T> =
    NonNullableAtomicReferenceDelegate(initValue)

//  fun <T : Any?> nullableReference(initValue: T? = null): ReadWriteProperty<Any?, T> =
//    NullableAtomicReferenceDelegate(initValue)

  fun <T : Any?> singleSetReference(
    initValue: T? = null,
    compareValue: T? = null,
  ): ReadWriteProperty<Any?, T?> = SingleSetAtomicReferenceDelegate(initValue, compareValue)

  fun atomicBoolean(initValue: Boolean = false): ReadWriteProperty<Any?, Boolean> = AtomicBooleanDelegate(initValue)

  fun atomicInteger(initValue: Int = -1): ReadWriteProperty<Any?, Int> = AtomicIntegerDelegate(initValue)

  fun atomicLong(initValue: Long = -1L): ReadWriteProperty<Any?, Long> = AtomicLongDelegate(initValue)
}

private class NonNullableAtomicReferenceDelegate<T : Any>(
  initValue: T? = null,
) : ReadWriteProperty<Any?, T> {
  private val atomicVal = AtomicReference(initValue)

  override operator fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ) = atomicVal.load() ?: error("Property ${property.name} must be initialized first")

  override operator fun setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: T,
  ) = atomicVal.store(value)
}

// private class NullableAtomicReferenceDelegate<T : Any?>(
//   initValue: T? = null,
// ) : ReadWriteProperty<Any?, T> {
//   private val atomicVal = AtomicReference(initValue)
//
//   override operator fun getValue(
//     thisRef: Any?,
//     property: KProperty<*>,
//   ): T = atomicVal.load()
//
//   override operator fun setValue(
//     thisRef: Any?,
//     property: KProperty<*>,
//     value: T,
//   ) = atomicVal.store(value)
// }

private class SingleSetAtomicReferenceDelegate<T : Any?>(
  initValue: T?,
  private val compareValue: T?,
) : ReadWriteProperty<Any?, T?> {
  private val atomicVal = AtomicReference(initValue)

  override operator fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ): T? = atomicVal.load()

  override operator fun setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: T?,
  ) {
    atomicVal.compareAndSet(compareValue, value)
  }
}

private class AtomicBooleanDelegate(
  initValue: Boolean,
) : ReadWriteProperty<Any?, Boolean> {
  private val atomicVal = AtomicBoolean(initValue)

  override operator fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ) = atomicVal.load()

  override operator fun setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: Boolean,
  ) {
    atomicVal.store(value)
  }
}

private class AtomicIntegerDelegate(
  initValue: Int,
) : ReadWriteProperty<Any?, Int> {
  private val atomicVal = AtomicInt(initValue)

  override operator fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ) = atomicVal.load()

  override operator fun setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: Int,
  ) {
    atomicVal.store(value)
  }
}

private class AtomicLongDelegate(
  initValue: Long,
) : ReadWriteProperty<Any?, Long> {
  private val atomicVal = AtomicLong(initValue)

  override operator fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ) = atomicVal.load()

  override operator fun setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: Long,
  ) {
    atomicVal.store(value)
  }
}
