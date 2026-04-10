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

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but rethrows [CancellationException] to preserve coroutine cancellation semantics.
 *
 * @param R the result type
 * @param block the code to execute
 * @return a [Result] wrapping the success value or the caught exception (excluding [CancellationException])
 * @throws CancellationException if the block throws a cancellation exception
 */
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> =
  runCatching(block)
    .onFailure {
      if (it is CancellationException)
        throw it
    }

/**
 * Like [Result.onFailure], but rethrows [CancellationException] to preserve coroutine cancellation.
 *
 * Extension function on [Result].
 *
 * @param T the result type
 * @param action the action to invoke on non-cancellation failures
 * @return this [Result]
 * @throws CancellationException if the failure is a cancellation exception
 */
inline fun <T> Result<T>.onFailureRethrowCancellation(action: (Throwable) -> Unit): Result<T> =
  onFailureOrRethrow<CancellationException, T>(action)

/**
 * Like [Result.onFailure], but rethrows exceptions of type [E] and invokes [action] for all others.
 *
 * Extension function on [Result].
 *
 * @param E the exception type to rethrow
 * @param T the result type
 * @param action the action to invoke on non-[E] failures
 * @return this [Result]
 * @throws E if the failure is an instance of [E]
 */
inline fun <reified E : Throwable, T> Result<T>.onFailureOrRethrow(action: (Throwable) -> Unit): Result<T> =
  onFailure {
    if (it is E)
      throw it
    else
      action(it)
  }
