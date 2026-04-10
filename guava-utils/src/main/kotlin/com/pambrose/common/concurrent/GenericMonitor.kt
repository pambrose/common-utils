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
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
   * Blocks the current thread until [monitorSatisfied] returns `true`.
   * This method is not interruptible.
   */
  fun waitUntilTrue() =
    try {
      monitor.enterWhenUninterruptibly(trueValueGuard)
    } finally {
      monitor.leave()
    }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `true`.
   * This method can be interrupted.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption() =
    try {
      monitor.enterWhen(trueValueGuard)
    } finally {
      if (monitor.isOccupiedByCurrentThread)
        monitor.leave()
    }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `true` or the timeout expires.
   * This method is not interruptible.
   *
   * @param waitTime the maximum duration to wait.
   * @return `true` if the condition was satisfied, `false` if the wait timed out.
   */
  fun waitUntilTrue(waitTime: Duration): Boolean {
    var satisfied = false
    try {
      satisfied =
        monitor.enterWhenUninterruptibly(
          trueValueGuard,
          waitTime.inWholeMilliseconds,
          MILLISECONDS,
        )
    } finally {
      if (satisfied)
        monitor.leave()
    }
    return satisfied
  }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `true` or the timeout expires.
   * This method can be interrupted.
   *
   * @param waitTime the maximum duration to wait.
   * @return `true` if the condition was satisfied, `false` if the wait timed out.
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption(waitTime: Duration): Boolean {
    var satisfied = false
    try {
      satisfied = monitor.enterWhen(trueValueGuard, waitTime.inWholeMilliseconds, MILLISECONDS)
    } finally {
      if (satisfied)
        monitor.leave()
    }
    return satisfied
  }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `false`.
   * This method is not interruptible.
   */
  fun waitUntilFalse() =
    try {
      monitor.enterWhenUninterruptibly(falseValueGuard)
    } finally {
      monitor.leave()
    }

  /**
   * Blocks the current thread until [monitorSatisfied] returns `false` or the timeout expires.
   * This method is not interruptible.
   *
   * @param waitTime the maximum duration to wait.
   * @return `true` if the condition was satisfied, `false` if the wait timed out.
   */
  fun waitUntilFalse(waitTime: Duration): Boolean {
    var satisfied = false
    try {
      satisfied =
        monitor.enterWhenUninterruptibly(
          falseValueGuard,
          waitTime.inWholeMilliseconds,
          MILLISECONDS,
        )
    } finally {
      if (satisfied)
        monitor.leave()
    }
    return satisfied
  }

  /**
   * Repeatedly waits for the condition to become `true`, invoking [block] on each timeout.
   *
   * @param timeout the duration for each individual wait attempt.
   * @param block the action invoked on each timeout; return `false` to stop waiting.
   * @return `true` if the condition was satisfied, `false` if the [block] returned `false`.
   */
  fun waitUntilTrue(
    timeout: Duration,
    block: MonitorAction,
  ) = waitUntilTrue(timeout, (-1).seconds, block)

  /**
   * Repeatedly waits for the condition to become `true`, invoking [block] on each timeout,
   * up to an overall maximum wait duration.
   *
   * @param timeout the duration for each individual wait attempt.
   * @param maxWait the overall maximum duration to wait. Use a negative value for no limit.
   * @param block the action invoked on each timeout; return `false` to stop waiting. May be `null`.
   * @return `true` if the condition was satisfied, `false` if [maxWait] elapsed or [block] returned `false`.
   */
  fun waitUntilTrue(
    timeout: Duration,
    maxWait: Duration,
    block: MonitorAction?,
  ): Boolean {
    val start = Monotonic.markNow()
    while (true) {
      when {
        waitUntilTrue(timeout) -> {
          return true
        }

        maxWait > 0.seconds && start.elapsedNow() >= maxWait -> {
          return false
        }

        else -> {
          block?.also { monitorAction ->
            val continueToWait = monitorAction()
            if (!continueToWait)
              return false
          }
        }
      }
    }
  }

  /**
   * Repeatedly waits (interruptibly) for the condition to become `true`, invoking [block] on each timeout.
   *
   * @param timeout the duration for each individual wait attempt.
   * @param block the action invoked on each timeout; return `false` to stop waiting.
   * @return `true` if the condition was satisfied, `false` if the [block] returned `false`.
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption(
    timeout: Duration,
    block: MonitorAction,
  ) = waitUntilTrueWithInterruption(timeout, (-1).seconds, block)

  /**
   * Repeatedly waits (interruptibly) for the condition to become `true`, invoking [block] on each timeout,
   * up to an overall maximum wait duration.
   *
   * @param timeout the duration for each individual wait attempt.
   * @param maxWait the overall maximum duration to wait. Use a negative value for no limit.
   * @param block the action invoked on each timeout; return `false` to stop waiting. May be `null`.
   * @return `true` if the condition was satisfied, `false` if [maxWait] elapsed or [block] returned `false`.
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  @Throws(InterruptedException::class)
  fun waitUntilTrueWithInterruption(
    timeout: Duration,
    maxWait: Duration,
    block: MonitorAction?,
  ): Boolean {
    val start = Monotonic.markNow()
    while (true) {
      when {
        waitUntilTrueWithInterruption(timeout) -> {
          return true
        }

        maxWait > 0.seconds && start.elapsedNow() >= maxWait -> {
          return false
        }

        else -> {
          block?.also { monitorAction ->
            val continueToWait = monitorAction()
            if (!continueToWait)
              return false
          }
        }
      }
    }
  }

  /**
   * Repeatedly waits for the condition to become `false`, invoking [block] on each timeout.
   *
   * @param timeout the duration for each individual wait attempt.
   * @param block the action invoked on each timeout; return `false` to stop waiting.
   * @return `true` if the condition was satisfied, `false` if the [block] returned `false`.
   */
  fun waitUntilFalse(
    timeout: Duration,
    block: MonitorAction,
  ) = waitUntilFalse(timeout, (-1).seconds, block)

  /**
   * Repeatedly waits for the condition to become `false`, invoking [block] on each timeout,
   * up to an overall maximum wait duration.
   *
   * @param timeout the duration for each individual wait attempt.
   * @param maxWait the overall maximum duration to wait. Use a negative value for no limit.
   * @param block the action invoked on each timeout; return `false` to stop waiting. May be `null`.
   * @return `true` if the condition was satisfied, `false` if [maxWait] elapsed or [block] returned `false`.
   */
  fun waitUntilFalse(
    timeout: Duration,
    maxWait: Duration,
    block: MonitorAction?,
  ): Boolean {
    val start = Monotonic.markNow()
    while (true) {
      when {
        waitUntilFalse(timeout) -> {
          return true
        }

        maxWait > 0.seconds && start.elapsedNow() >= maxWait -> {
          return false
        }

        else -> {
          block?.also { monitorAction ->
            val continueToWait = monitorAction()
            if (!continueToWait)
              return false
          }
        }
      }
    }
  }

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
}
