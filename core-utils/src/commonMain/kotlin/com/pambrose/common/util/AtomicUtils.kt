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

import kotlin.concurrent.atomics.AtomicBoolean

/** Utility object providing extension functions for atomic types. */
object AtomicUtils {
  /**
   * Executes [block] while this [AtomicBoolean] is set to `true`, then restores the value it had before.
   *
   * This is useful for signaling that a critical section is in progress. Restoring the previous value, rather than
   * storing `false`, keeps the flag set for an outer section when sections nest. It is still one flag, not a lock or a
   * count: when two threads overlap, the first to finish restores the value it saw, so the flag cannot say whether
   * the other is still inside. Use a counter or a lock for that.
   *
   * @param T the return type of the block
   * @param block the code to execute within the critical section
   * @return the result of [block]
   */
  inline fun <T> AtomicBoolean.criticalSection(block: () -> T): T {
    val previous = exchange(true)
    try {
      return block()
    } finally {
      store(previous)
    }
  }
}
