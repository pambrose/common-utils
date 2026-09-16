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

package com.pambrose.common.concurrent

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldBeIn
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

private val parkedStates: Set<Thread.State> = setOf(Thread.State.WAITING, Thread.State.TIMED_WAITING)

// How long a test waits for a result that should arrive at once. It only guards against a hang: every wait it guards
// would otherwise last an hour or more, so the gap between the two cannot be mistaken for slowness.
internal const val HANG_GUARD_SECONDS = 30L

/**
 * Waits until this thread is parked, which is how a test knows a waiter is blocked before it changes the state the
 * waiter waits on, instead of guessing with a delay. The threads it is used on have no other reason to park.
 */
internal suspend fun Thread.awaitParked() {
  eventually(10.seconds) { state shouldBeIn parkedStates }
}

/** Waits for this future's result, failing with a TimeoutException instead of hanging. */
internal fun <T> Future<T>.getGuarded(): T = get(HANG_GUARD_SECONDS, TimeUnit.SECONDS)

/** Runs [block] on a new daemon thread, so a wait that wrongly never returns cannot keep the JVM alive. */
internal fun <T> inDaemonThread(block: () -> T): Pair<Thread, CompletableFuture<T>> {
  val future = CompletableFuture<T>()
  val thread =
    Thread {
      runCatching(block).fold({ future.complete(it) }, { future.completeExceptionally(it) })
    }.apply {
      isDaemon = true
      start()
    }
  return thread to future
}
