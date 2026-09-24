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

/**
 * A pre-populated pool of [KotlinExprEvaluator] instances backed by a coroutine
 * [Channel][kotlinx.coroutines.channels.Channel].
 *
 * Instances are created eagerly during initialization.
 *
 * A fresh Kotlin REPL costs several times an evaluation, and each evaluation adds to the REPL's history, so an
 * evaluator's context is reset every [resetEvery] returns rather than every time: about 20 keeps the reset cost small
 * per evaluation while holding each evaluator's history to some tens of megabytes. See [AbstractExprEvaluatorPool].
 *
 * @param size the number of [KotlinExprEvaluator] instances to create in the pool
 * @param resetEvery how many times an evaluator is returned between resets of its context; defaults to
 *   [DEFAULT_RESET_EVERY], and 1 resets on every return
 */
class KotlinExprEvaluatorPool
  @JvmOverloads
  constructor(
    size: Int,
    resetEvery: Int = DEFAULT_RESET_EVERY,
  ) : AbstractExprEvaluatorPool<KotlinExprEvaluator>(size, resetEvery) {
  init {
    populate { KotlinExprEvaluator() }
  }

    /** Constants for [KotlinExprEvaluatorPool]. */
    companion object {
      /** The default number of returns between resets of an evaluator's context. */
      const val DEFAULT_RESET_EVERY = 20
    }
  }
