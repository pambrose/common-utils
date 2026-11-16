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

@file:JvmName("ExceptionUtilsKt")
@file:JvmMultifileClass

package com.pambrose.common.util

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatchingCancellable], but also rethrows [InterruptedException], so neither coroutine cancellation nor
 * thread interruption is swallowed.
 *
 * Use it around blocking calls. The JVM clears a thread's interrupt flag when it throws [InterruptedException], so
 * catching that exception in a [Result] would lose the interruption; rethrowing it leaves it for a caller to handle.
 *
 * @param R the result type
 * @param block the code to execute
 * @return a [Result] wrapping the success value or the caught exception (excluding [CancellationException] and
 *   [InterruptedException])
 * @throws CancellationException if the block throws a cancellation exception
 * @throws InterruptedException if the block throws an interrupted exception
 */
inline fun <R> runCatchingInterruptible(block: () -> R): Result<R> =
  runCatching(block)
    .onFailure {
      if (it is CancellationException || it is InterruptedException)
        throw it
    }
