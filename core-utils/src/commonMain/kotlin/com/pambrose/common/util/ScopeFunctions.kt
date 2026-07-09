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

package com.pambrose.common.util

/**
 * Generic two-receiver variant of [with]: executes [block] with both [a] and [b] available as Kotlin
 * context receivers. Unlike a `vararg` version, the two type parameters keep [a] and [b]'s distinct
 * types, so each can satisfy a separate `context(...)` parameter (a `vararg` would collapse them to
 * their common supertype). For more receivers, add further fixed-arity overloads.
 */
inline fun <A, B, R> with(
  a: A,
  b: B,
  block: context (A, B) () -> R,
): R =
  with(a) {
    with(b) {
      block()
    }
  }
