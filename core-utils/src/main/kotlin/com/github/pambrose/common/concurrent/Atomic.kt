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

package com.github.pambrose.common.concurrent

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// See: https://medium.com/swlh/kotlin-for-lunch-atomic-t-261351048fad

class Atomic<T>(
  initValue: T,
) : Mutex by Mutex() {
  @PublishedApi
  internal var _value: T = initValue

  val value: T get() = _value

  suspend inline fun setWithLock(
    owner: Any? = null,
    action: (T) -> T,
  ): T = (this as Mutex).withLock(owner) { action(_value).also { _value = it } }

  suspend inline fun <V> withLock(
    owner: Any? = null,
    action: T.() -> V,
  ): V = (this as Mutex).withLock(owner) { _value.action() }
}
