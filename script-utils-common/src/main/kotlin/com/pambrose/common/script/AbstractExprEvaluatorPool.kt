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
abstract class AbstractExprEvaluatorPool<T : AbstractExprEvaluator>(
  val size: Int,
) {
  /** Channel used as a bounded buffer for pooling evaluator instances. */
  protected val channel = Channel<AbstractExprEvaluator>(size)

  private suspend fun borrow() = channel.receive()

  /** Returns `true` if there are no evaluator instances currently available in the pool. */
  val isEmpty get() = channel.isEmpty

  private suspend fun recycle(scriptObject: AbstractExprEvaluator) = channel.send(scriptObject)

  /**
   * Evaluates the given expression by borrowing an evaluator from the pool, blocking the current thread.
   *
   * @param R the expected return type of the evaluation
   * @param expr the expression to evaluate
   * @return the result of the evaluation, cast to [R]
   */
  fun <R> blockingEval(expr: String): R =
    runBlocking {
      eval(expr)
    }

  suspend fun <R> eval(expr: String): R =
    borrow()
      .let { engine ->
        try {
          @Suppress("UNCHECKED_CAST")
          engine.eval(expr) as R
        } finally {
          recycle(engine)
        }
      }
}
