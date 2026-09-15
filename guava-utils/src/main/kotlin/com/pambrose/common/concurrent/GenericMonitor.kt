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

import com.google.common.util.concurrent.Monitor
import java.util.concurrent.TimeUnit.NANOSECONDS
import kotlin.time.Duration
import kotlin.time.TimeSource.Monotonic

/**
 * A function type used as a callback during timed monitor waits.
 *
 * Invoked each time a wait attempt times out but the overall maximum wait has not elapsed.
 * Return `true` to continue waiting, or `false` to abort.
 */
typealias MonitorAction = () -> Boolean

/**
 * Abstract base class providing thread-blocking wait methods backed by a Guava [Monitor].
 *
 * Subclasses must implement [monitorSatisfied] to define when the monitor's condition is met.
 * Methods are provided to wait until the condition is `true` or `false`, with optional
 * timeouts, interruptibility, and retry callbacks.
 *
 * Guava re-evaluates [monitorSatisfied] only when a thread leaves the monitor or starts waiting, so a subclass must
 * change the state that [monitorSatisfied] reads inside the monitor, for example with [mutate]. A change made
 * outside the monitor can leave waiting threads blocked.
 *
 * The retrying waits make attempts of `timeout`, which must be at least 1 ms, and no attempt waits past `maxWait`.
 * For `maxWait`, [Duration.INFINITE] waits without limit, [Duration.ZERO] checks the condition once, and a negative
 * value also waits without limit.
 */
abstract class GenericMonitor {
  protected val monitor = Monitor()

  private val trueValueGuard =
    object : Monitor.Guard(monitor) {
      override fun isSatisfied() = monitorSatisfied
    }

  private val falseValueGuard =
    object : Monitor.Guard(monitor) {
      override fun isSatisfied() = !monitorSatisfied
    }

  /** Whether the monitor's condition is currently satisfied. Implemented by subclasses. */
  abstract val monitorSatisfied: Boolean

  /**
   * Runs [block] while holding the monitor, then leaves it so that waiting threads re-check their conditions.
   * Use it for every change to the state that [monitorSatisfied] reads.
   *
   * @param block the change to make.
   * @return the result of [block].
   */
  protected inline fun <T> mutate(block: () -> T): T {
    monitor.enter()
    try {
      return block()
    } finally {
      monitor.leave()
    }
  }

  // The untimed enter methods already leave the monitor when the guard throws, so each wait leaves it only after
  // entering succeeds; leaving again would release a monitor the caller holds or mask the guard's exception.

  /**
   * Blocks the current thread until [monitorSatisfied] returns `true`.
   * This method is not interruptible.
   */
  fun waitUntilTrue() {
    monitor.enterWhenUninterruptibly(trueValueGuard)
    monitor.leave()
  }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `true`.
   * This method can be interrupted.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption() {
    monitor.enterWhen(trueValueGuard)
    monitor.leave()
  }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `true` or the timeout expires.
   * This method is not interruptible.
   *
   * @param waitTime the maximum duration to wait. A zero or negative duration checks the condition once.
   * @return `true` if the condition was satisfied, `false` if the wait timed out.
   */
  fun waitUntilTrue(waitTime: Duration): Boolean =
    monitor
      .enterWhenUninterruptibly(trueValueGuard, waitTime.inWholeNanoseconds, NANOSECONDS)
      .also { if (it) monitor.leave() }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `true` or the timeout expires.
   * This method can be interrupted.
   *
   * @param waitTime the maximum duration to wait. A zero or negative duration checks the condition once.
   * @return `true` if the condition was satisfied, `false` if the wait timed out.
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption(waitTime: Duration): Boolean =
    monitor
      .enterWhen(trueValueGuard, waitTime.inWholeNanoseconds, NANOSECONDS)
      .also { if (it) monitor.leave() }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `false`.
   * This method is not interruptible.
   */
  fun waitUntilFalse() {
    monitor.enterWhenUninterruptibly(falseValueGuard)
    monitor.leave()
  }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `false` or the timeout expires.
   * This method is not interruptible.
   *
   * @param waitTime the maximum duration to wait. A zero or negative duration checks the condition once.
   * @return `true` if the condition was satisfied, `false` if the wait timed out.
   */
  fun waitUntilFalse(waitTime: Duration): Boolean =
    monitor
      .enterWhenUninterruptibly(falseValueGuard, waitTime.inWholeNanoseconds, NANOSECONDS)
      .also { if (it) monitor.leave() }

  /**
   * Repeatedly waits for the condition to become `true`, invoking [block] on each timeout.
   *
   * @param timeout the duration of each wait attempt; at least 1 ms.
   * @param block the action invoked on each timeout; return `false` to stop waiting.
   * @return `true` if the condition was satisfied, `false` if the [block] returned `false`.
   * @throws IllegalArgumentException if [timeout] is shorter than 1 ms.
   */
  fun waitUntilTrue(
    timeout: Duration,
    block: MonitorAction,
  ) = waitUntilTrue(timeout, Duration.INFINITE, block)

  /**
   * Repeatedly waits for the condition to become `true`, invoking [block] on each timeout,
   * up to an overall maximum wait duration.
   *
   * @param timeout the duration of each wait attempt; at least 1 ms.
   * @param maxWait the overall maximum duration to wait; see [GenericMonitor] for its limits.
   * @param block the action invoked on each timeout; return `false` to stop waiting. May be `null`.
   * @return `true` if the condition was satisfied, `false` if [maxWait] elapsed or [block] returned `false`.
   * @throws IllegalArgumentException if [timeout] is shorter than 1 ms.
   */
  fun waitUntilTrue(
    timeout: Duration,
    maxWait: Duration,
    block: MonitorAction?,
  ): Boolean = waitWithRetries(timeout, maxWait, block) { waitUntilTrue(it) }

  /**
   * Repeatedly waits (interruptibly) for the condition to become `true`, invoking [block] on each timeout.
   *
   * @param timeout the duration of each wait attempt; at least 1 ms.
   * @param block the action invoked on each timeout; return `false` to stop waiting.
   * @return `true` if the condition was satisfied, `false` if the [block] returned `false`.
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws IllegalArgumentException if [timeout] is shorter than 1 ms.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption(
    timeout: Duration,
    block: MonitorAction,
  ) = waitUntilTrueWithInterruption(timeout, Duration.INFINITE, block)

  /**
   * Repeatedly waits (interruptibly) for the condition to become `true`, invoking [block] on each timeout,
   * up to an overall maximum wait duration.
   *
   * @param timeout the duration of each wait attempt; at least 1 ms.
   * @param maxWait the overall maximum duration to wait; see [GenericMonitor] for its limits.
   * @param block the action invoked on each timeout; return `false` to stop waiting. May be `null`.
   * @return `true` if the condition was satisfied, `false` if [maxWait] elapsed or [block] returned `false`.
   * @throws InterruptedException if the thread is interrupted while waiting.
   * @throws IllegalArgumentException if [timeout] is shorter than 1 ms.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption(
    timeout: Duration,
    maxWait: Duration,
    block: MonitorAction?,
  ): Boolean = waitWithRetries(timeout, maxWait, block) { waitUntilTrueWithInterruption(it) }

  /**
   * Repeatedly waits for the condition to become `false`, invoking [block] on each timeout.
   *
   * @param timeout the duration of each wait attempt; at least 1 ms.
   * @param block the action invoked on each timeout; return `false` to stop waiting.
   * @return `true` if the condition was satisfied, `false` if the [block] returned `false`.
   * @throws IllegalArgumentException if [timeout] is shorter than 1 ms.
   */
  fun waitUntilFalse(
    timeout: Duration,
    block: MonitorAction,
  ) = waitUntilFalse(timeout, Duration.INFINITE, block)

  /**
   * Repeatedly waits for the condition to become `false`, invoking [block] on each timeout,
   * up to an overall maximum wait duration.
   *
   * @param timeout the duration of each wait attempt; at least 1 ms.
   * @param maxWait the overall maximum duration to wait; see [GenericMonitor] for its limits.
   * @param block the action invoked on each timeout; return `false` to stop waiting. May be `null`.
   * @return `true` if the condition was satisfied, `false` if [maxWait] elapsed or [block] returned `false`.
   * @throws IllegalArgumentException if [timeout] is shorter than 1 ms.
   */
  fun waitUntilFalse(
    timeout: Duration,
    maxWait: Duration,
    block: MonitorAction?,
  ): Boolean = waitWithRetries(timeout, maxWait, block) { waitUntilFalse(it) }

  /**
   * Blocks until the condition matches [value] or the timeout expires.
   *
   * @param value `true` to wait for the condition to become `true`, `false` for `false`.
   * @param waitTime the maximum duration to wait.
   * @return `true` if the condition was satisfied, `false` if the wait timed out.
   */
  fun waitUntil(
    value: Boolean,
    waitTime: Duration,
  ) = if (value) waitUntilTrue(waitTime) else waitUntilFalse(waitTime)

  /**
   * Blocks indefinitely until the condition matches [value].
   *
   * @param value `true` to wait for the condition to become `true`, `false` for `false`.
   */
  fun waitUntil(value: Boolean) = if (value) waitUntilTrue() else waitUntilFalse()

  // Waits in attempts of at most timeout, never past maxWait, calling block after each unsuccessful attempt.
  private fun waitWithRetries(
    timeout: Duration,
    maxWait: Duration,
    block: MonitorAction?,
    attempt: (Duration) -> Boolean,
  ): Boolean {
    requireRetryInterval(timeout)
    val limit = if (maxWait.isNegative()) Duration.INFINITE else maxWait
    val start = Monotonic.markNow()
    while (true) {
      if (attempt(minOf(timeout, limit - start.elapsedNow())))
        return true
      if (start.elapsedNow() >= limit || (block != null && !block()))
        return false
    }
  }
}
