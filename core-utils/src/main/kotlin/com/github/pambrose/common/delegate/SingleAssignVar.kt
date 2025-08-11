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

import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

object SingleAssignVar {
  /**
   * Returns a property delegate for a read/write property that can be assigned only once.
   * This implementation is thread-safe and prevents race conditions.
   *
   * @throws IllegalStateException if the property is assigned more than once
   */
  fun <T : Any?> singleAssign(): ReadWriteProperty<Any?, T?> = ThreadSafeSingleAssignVar()

  private class ThreadSafeSingleAssignVar<T : Any?> : ReadWriteProperty<Any?, T?> {
    private val atomicValue = AtomicReference<ValueHolder<T>?>()

    // Wrapper to distinguish between null value and unset value
    private data class ValueHolder<T>(
      val value: T?,
    )

    override fun getValue(
      thisRef: Any?,
      property: KProperty<*>,
    ): T? = atomicValue.get()?.value

    override fun setValue(
      thisRef: Any?,
      property: KProperty<*>,
      value: T?,
    ) {
      val holder = ValueHolder(value)
      if (!atomicValue.compareAndSet(null, holder)) {
        throw IllegalStateException("Property ${property.name} cannot be assigned more than once.")
      }
    }

    /**
     * Returns true if the property has been assigned a value (including null).
     */
    fun isAssigned(): Boolean = atomicValue.get() != null
  }
}
