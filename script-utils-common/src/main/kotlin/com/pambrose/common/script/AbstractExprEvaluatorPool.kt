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

package com.pambrose.common.script

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

/**
 * Abstract base class for a pool of [AbstractExprEvaluator] instances.
 *
 * Uses a coroutine [Channel] as a bounded buffer to manage evaluator instances. Subclasses
 * are responsible for populating the pool in their `init` block.
 *
 * @param T the concrete type of [AbstractExprEvaluator] managed by this pool
 * @param size the number of evaluator instances in the pool
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractExprEvaluatorPool<T : AbstractExprEvaluator>(
  val size: Int,
) {
  /** Channel used as a bounded buffer for pooling evaluator instances. */
  protected val channel = Channel<AbstractExprEvaluator>(size)

  private suspend fun borrow() = channel.receive()

  /**
   * Returns an approximate, point-in-time indication of whether the pool currently has no evaluators
   * available. Delegates to [Channel.isEmpty], which is racy under concurrent borrow/recycle and may
   * return a stale result, so do not rely on it for correctness.
   */
  val isEmpty get() = channel.isEmpty

  private suspend fun recycle(scriptObject: AbstractExprEvaluator) = channel.send(scriptObject)

  /**
   * Evaluates [expr] by borrowing an evaluator from the pool, blocking the current thread until one is
   * available and recycling it afterwards.
   *
   * The pool is a bounded buffer of [size] evaluators, so this blocks when all are in use and waits for
   * one to be recycled. Do not call it more than [size] times concurrently, or from a context that
   * already holds the pool's only evaluator, or it can deadlock.
   *
   * @param expr the expression to evaluate
   * @return the boolean result of the evaluation
   */
  fun blockingEval(expr: String): Boolean =
    runBlocking {
      eval(expr)
    }

  /**
   * Suspends until an evaluator can be borrowed from the pool, evaluates [expr], and recycles the
   * evaluator afterwards. Suspends (rather than blocking) when all [size] evaluators are in use.
   *
   * @param expr the expression to evaluate
   * @return the boolean result of the evaluation
   */
  suspend fun eval(expr: String): Boolean =
    borrow()
      .let { engine ->
        try {
          engine.eval(expr)
        } finally {
          recycle(engine)
        }
      }
}
