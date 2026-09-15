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

import com.pambrose.common.script.ScriptUtils.resetContext

/**
 * Evaluates expressions with a JSR 223 script engine.
 *
 * Expressions run in the host JVM. [checkExpr] rejects common literal JVM-termination calls on a best-effort basis
 * (see [ScriptGuards]), but this is **not** a security sandbox: do not evaluate untrusted input in-process.
 *
 * The engine keeps state across evaluations, such as the Kotlin engine's REPL history, which grows with every
 * expression until [resetContext] is called. [AbstractExprEvaluatorPool] resets an evaluator each time it is returned.
 *
 * @param extension the file extension used to look up the script engine (e.g., `"kts"`, `"py"`)
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractExprEvaluator(
  extension: String,
) : AbstractEngine(extension) {
  init {
    // ScriptEngineManager gives every engine it creates the same global Bindings, so give this one its own.
    scriptEngine.resetContext()
  }

  /**
   * Throws a [javax.script.ScriptException] if [expr] must not be evaluated. The default rejects literal
   * JVM-termination calls with [ScriptGuards]; subclasses add checks for their language.
   *
   * @param expr the expression about to be evaluated
   */
  protected open fun checkExpr(expr: String) = ScriptGuards.checkNoJvmExit(expr)

  /**
   * Evaluates [expr] and returns its [Boolean] result.
   *
   * @param expr the boolean expression to evaluate
   * @return the boolean result of the evaluation
   * @throws javax.script.ScriptException if [checkExpr] rejects [expr], or it fails to compile or run
   * @throws IllegalArgumentException if the expression does not evaluate to a [Boolean]
   */
  fun eval(expr: String): Boolean {
    val result = compute(expr)
    return result as? Boolean
      ?: throw IllegalArgumentException(
        "Expression did not evaluate to Boolean, got ${result?.javaClass?.simpleName ?: "null"}",
      )
  }

  /**
   * Evaluates the given expression and returns the result.
   *
   * @param expr the expression to evaluate
   * @return the result of evaluating the expression, or `null` if the expression evaluates to `null`
   * @throws javax.script.ScriptException if [checkExpr] rejects [expr], or it fails to compile or run
   */
  fun compute(expr: String): Any? {
    checkExpr(expr)
    return scriptEngine.eval(expr)
  }

  /**
   * Resets the script engine context, clearing all bindings and state.
   *
   * @param nullGlobalContext if `true`, sets the global scope bindings to `null`
   */
  fun resetContext(nullGlobalContext: Boolean = false) = scriptEngine.resetContext(nullGlobalContext)
}
