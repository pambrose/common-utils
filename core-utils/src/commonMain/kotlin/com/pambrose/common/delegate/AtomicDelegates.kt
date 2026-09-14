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

package com.pambrose.common.delegate

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Factory object for creating thread-safe atomic property delegates. */
object AtomicDelegates {
  /**
   * Creates a thread-safe delegate for a non-nullable property backed by an [AtomicReference].
   *
   * Reading the property before it has been set throws [IllegalStateException].
   *
   * @param T the property type
   * @param initValue optional initial value (default `null`, meaning uninitialized)
   * @return a [ReadWriteProperty] delegate
   */
  fun <T : Any> nonNullableReference(initValue: T? = null): ReadWriteProperty<Any?, T> =
    NonNullableAtomicReferenceDelegate(initValue)

  /**
   * Creates a thread-safe delegate that can only be set once.
   *
   * The property starts at [initValue]. The first assignment, including an assignment of `null`, sets it;
   * any later assignment throws [IllegalStateException].
   *
   * @param T the property type
   * @param initValue optional initial value (default `null`)
   * @param compareValue the value the property holds before its one assignment; defaults to [initValue] and
   *   must equal it (compared with `==`)
   * @return a [ReadWriteProperty] delegate
   * @throws IllegalArgumentException if [compareValue] is not equal to [initValue]
   */
  fun <T> singleSetReference(
    initValue: T? = null,
    compareValue: T? = initValue,
  ): ReadWriteProperty<Any?, T?> {
    // The value only changes through the one assignment, so a compareValue that differs from initValue
    // could never match and would leave the property permanently unsettable.
    require(initValue == compareValue) { "compareValue ($compareValue) must equal initValue ($initValue)" }
    return SingleSetAtomicReferenceDelegate(initValue)
  }

  /**
   * Creates a thread-safe [Boolean] property delegate backed by [AtomicBoolean].
   *
   * @param initValue the initial value (default `false`)
   * @return a [ReadWriteProperty] delegate
   */
  fun atomicBoolean(initValue: Boolean = false): ReadWriteProperty<Any?, Boolean> = AtomicBooleanDelegate(initValue)

  /**
   * Creates a thread-safe [Int] property delegate backed by [AtomicInt].
   *
   * @param initValue the initial value (default `0`)
   * @return a [ReadWriteProperty] delegate
   */
  fun atomicInteger(initValue: Int = 0): ReadWriteProperty<Any?, Int> = AtomicIntegerDelegate(initValue)

  /**
   * Creates a thread-safe [Long] property delegate backed by [AtomicLong].
   *
   * @param initValue the initial value (default `0L`)
   * @return a [ReadWriteProperty] delegate
   */
  fun atomicLong(initValue: Long = 0L): ReadWriteProperty<Any?, Long> = AtomicLongDelegate(initValue)
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

private class SingleSetAtomicReferenceDelegate<T>(
  private val initValue: T?,
) : ReadWriteProperty<Any?, T?> {
  // Null until the one assignment. Wrapping the value lets an assigned null count as set, and a single
  // compare-and-set both claims the assignment and publishes the value.
  private val assigned = AtomicReference<Assigned<T>?>(null)

  override operator fun getValue(
    thisRef: Any?,
    property: KProperty<*>,
  ): T? = assigned.load().let { if (it == null) initValue else it.value }

  override operator fun setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: T?,
  ) = check(assigned.compareAndSet(null, Assigned(value))) { "Property ${property.name} has already been set" }

  private class Assigned<T>(
    val value: T?,
  )
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
