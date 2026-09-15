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

import ch.obermuhlner.scriptengine.java.Isolation
import ch.obermuhlner.scriptengine.java.JavaScriptEngine
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.lang.model.SourceVersion
import javax.script.ScriptException
import kotlin.reflect.KClass
import kotlin.reflect.KType

// https://github.com/eobermuhlner/java-scriptengine
// https://gitter.im/java-scriptengine/community

/**
 * A script engine wrapper for dynamically compiling and evaluating Java source code.
 *
 * Supports adding named variables with type parameters, import declarations, and
 * configurable isolation levels. Note that Java cannot have a null global context.
 *
 * Variable declarations use fully-qualified type names, so they need no imports. A value whose runtime class cannot be
 * named, such as the private list class behind `listOf(1, 2)`, is declared as its nearest public class or interface.
 *
 * Common literal JVM-termination calls (`System.exit`, `Runtime.getRuntime().exit/halt`) are rejected
 * on a best-effort basis via [ScriptGuards]. This is a convenience against accidental termination,
 * **not** a security sandbox (see [ScriptGuards]) — run untrusted scripts in an isolated process or JVM.
 *
 * [close] does nothing, because the Java engine holds no resources to release.
 *
 * @see AbstractScript
 * @see <a href="https://github.com/eobermuhlner/java-scriptengine">java-scriptengine</a>
 */
class JavaScript : AbstractScript("java", false) {
  private val imports: MutableList<String> = []

  override fun isReserved(name: String) = SourceVersion.isKeyword(name)

  /**
   * Generates Java-style public field declarations for all registered variables.
   *
   * Each declaration includes the Java type name and any type parameters.
   */
  val varDecls: String
    get() = valueMap.entries.joinToString("\n") { (name, value) -> "  public ${fieldType(name, value)} $name;" }

  // A primitive for a boxed primitive; otherwise the accessible class with its type arguments, raw when none were
  // registered.
  private fun fieldType(
    name: String,
    value: Any,
  ): String =
    value.javaClass.kotlin.javaPrimitiveType?.name
      ?: "${accessibleClass(name, value).java.canonicalName}${params(name)}"

  /**
   * Generates Java import statements for all registered import classes.
   */
  val importDecls: String
    get() = imports.joinToString("\n") { "import $it;" }

  /**
   * Registers a Java class to be imported in generated scripts.
   *
   * @param T the type of the class to import
   * @param clazz the class to add to the import list
   */
  @Synchronized
  fun <T> import(clazz: Class<T>) {
    imports += clazz.name
  }

  /**
   * Sets the isolation level for the underlying [JavaScriptEngine].
   *
   * @param isolation the [Isolation] level to apply
   */
  fun assignIsolation(isolation: Isolation) {
    (scriptEngine as JavaScriptEngine).setIsolation(isolation)
  }

  /**
   * Resets the context and also clears the imports and restores the default isolation, so the next borrower from a
   * pool starts clean.
   *
   * @param nullGlobalContext ignored by the Java engine's global scope handling, as in [resetContext]
   */
  @Synchronized
  override fun resetForReuse(nullGlobalContext: Boolean) {
    super.resetForReuse(nullGlobalContext)
    imports.clear()
    assignIsolation(DEFAULT_ISOLATION)
  }

  // Java source for a Kotlin type argument: kotlin.collections.List<kotlin.Int> -> java.util.List<java.lang.Integer>
  override fun renderType(type: KType): String {
    val args = type.arguments.map { argument -> argument.type?.let(::renderType) ?: "?" }
    val clazz = (type.classifier as? KClass<*>)?.javaObjectType
    return when {
      clazz == null -> "Object"
      clazz.isArray -> "${args.singleOrNull() ?: clazz.componentType.canonicalName}[]"
      args.isEmpty() -> clazz.canonicalName
      else -> "${clazz.canonicalName}<${args.joinToString(", ")}>"
    }
  }

  /**
   * Evaluates a raw Java script string, prepending any registered import declarations.
   *
   * Each variable added with [add] is assigned to the public field of the same name in the script's class, so that
   * class must declare a public, assignable field of a compatible type for every added variable.
   *
   * @param script the Java source code to evaluate
   * @param verbose if `true`, logs the generated script before evaluation
   * @return the result of the script evaluation, or `null` if the script evaluates to `null`
   * @throws ScriptException if [script] contains a literal JVM-termination call, fails to compile or run, or a variable
   *   cannot be assigned to its field
   */
  @Synchronized
  fun evalScript(
    script: String,
    verbose: Boolean = false,
  ): Any? {
    prepare(script)

    val code = importDecls + script

    if (verbose)
      logger.info { "Script:\n$code" }

    return evaluate(code)
  }

  /**
   * Evaluates a Java [expr] (optionally preceded by an [action] statement block) inside a generated
   * `Main.getValue()` method, prepending any registered imports and variable declarations.
   *
   * Variables added with [add] are bound first, including any added after an earlier evaluation.
   *
   * @param expr the Java expression whose value is returned
   * @param action optional Java statements executed before [expr] is evaluated
   * @param verbose if `true`, logs the generated script before evaluation
   * @return the result of evaluating [expr], or `null` if it evaluates to `null`
   * @throws ScriptException if [expr] or [action] contains a literal JVM-termination call, or fails to compile or run
   */
  @Synchronized
  fun eval(
    expr: String,
    action: String = "",
    verbose: Boolean = false,
  ): Any? {
    prepare("$action\n$expr")

    val code = """
$importDecls
public class Main {

$varDecls
  public Object getValue() {
    $action
    return $expr;
  }
}
"""

    if (verbose)
      logger.info { "Script:\n$code" }

    return evaluate(code)
  }

  // java-scriptengine lets some failures escape unwrapped, such as the IllegalArgumentException from assigning a
  // variable to a field of an incompatible type, so report them as ScriptExceptions like every other script failure.
  private fun evaluate(code: String): Any? =
    runCatching { scriptEngine.eval(code) }.getOrElse { e ->
      throw if (e is RuntimeException) ScriptException(e) else e
    }

  private companion object {
    private val logger = KotlinLogging.logger {}

    // java-scriptengine's own default isolation.
    val DEFAULT_ISOLATION = Isolation.CallerClassLoader
  }
}
