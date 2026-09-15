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

import kotlinx.coroutines.runBlocking

/**
 * Abstract base class for a pool of [AbstractExprEvaluator] instances.
 *
 * Each evaluator's context is reset when it is returned, so engine state such as Kotlin's REPL history does not
 * accumulate. Subclasses populate the pool with [populate] in their `init` block. [AbstractEnginePool] describes
 * borrowing and closing.
 *
 * @param T the concrete type of [AbstractExprEvaluator] managed by this pool
 * @param size the number of evaluator instances in the pool; must be positive
 * @throws IllegalArgumentException if [size] is not positive
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractExprEvaluatorPool<T : AbstractExprEvaluator>(
  size: Int,
) : AbstractEnginePool<T>(size) {
  override fun reset(instance: T) = instance.resetContext()

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
   * @throws kotlinx.coroutines.channels.ClosedReceiveChannelException if the pool has been closed
   */
  suspend fun eval(expr: String): Boolean = withInstance { it.eval(expr) }
}
