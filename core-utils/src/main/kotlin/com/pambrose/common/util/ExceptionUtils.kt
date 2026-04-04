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

// From https://proandroiddev.com/kotlin-tips-and-tricks-you-may-not-know-7-goodbye-try-catch-hello-trycatching-7135cb382609
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> =
  runCatching(block)
    .onFailure {
      if (it is CancellationException)
        throw it
    }

// From https://dev.to/inoshishi/mastering-runcatching-in-kotlin-how-to-avoid-coroutine-cancellation-issues-5go2
inline fun <T> Result<T>.onFailureRethrowCancellation(action: (Throwable) -> Unit): Result<T> =
  onFailureOrRethrow<CancellationException, T>(action)

inline fun <reified E : Throwable, T> Result<T>.onFailureOrRethrow(action: (Throwable) -> Unit): Result<T> =
  onFailure {
    if (it is E)
      throw it
    else
      action(it)
  }
