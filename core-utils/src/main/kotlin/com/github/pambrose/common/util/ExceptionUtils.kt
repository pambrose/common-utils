package com.github.pambrose.common.util

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
