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

import kotlin.properties.ReadWriteProperty

/** Factory for creating single-assignment property delegates; superseded by [AtomicDelegates.singleSetReference]. */
object SingleAssignVar {
  /**
   * Returns a property delegate for a read/write property that can be assigned only once.
   * This implementation is thread-safe and prevents race conditions.
   *
   * It is the delegate [AtomicDelegates.singleSetReference] returns, which is available on every platform, not only
   * the JVM.
   *
   * @throws IllegalStateException if the property is assigned more than once
   */
  @Deprecated(
    "Duplicates AtomicDelegates.singleSetReference, which is available on every platform.",
    ReplaceWith("AtomicDelegates.singleSetReference<T>()", "com.pambrose.common.delegate.AtomicDelegates"),
  )
  fun <T> singleAssign(): ReadWriteProperty<Any?, T?> = AtomicDelegates.singleSetReference()
}
