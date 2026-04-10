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
import com.pambrose.common.script.ScriptUtils.engineBindings
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.Closeable
import javax.script.ScriptException
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// https://github.com/eobermuhlner/java-scriptengine
// https://gitter.im/java-scriptengine/community

/**
 * A script engine wrapper for dynamically compiling and evaluating Java source code.
 *
 * Supports adding named variables with type parameters, import declarations, and
 * configurable isolation levels. Note that Java cannot have a null global context.
 *
 * Note: Java cannot have a null global context.
 *
 * @see AbstractScript
 * @see <a href="https://github.com/eobermuhlner/java-scriptengine">java-scriptengine</a>
 */
class JavaScript :
  AbstractScript("java", false),
  Closeable {
  private val imports = mutableListOf<String>()

  /**
   * Generates Java-style public field declarations for all registered variables.
   *
   * Each declaration includes the Java type name and any type parameters.
   */
  val varDecls: String
    get() {
      val assigns = mutableListOf<String>()

      valueMap.forEach { (name, value) ->
        val javaClazz = value.javaClass
        val kotlinClazz = javaClazz.kotlin
        val type = kotlinClazz.javaPrimitiveType?.name ?: javaClazz.simpleName
        assigns += "  public $type${params(name)} $name;"
      }

      return assigns.joinToString("\n")
    }

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
    (engine as JavaScriptEngine).setIsolation(isolation)
  }

  // typeOf<Int>() -> Integer::class.java.simpleName
  // typeOf<Int?>() -> Integer::class.java.simpleName
  private val KType.javaEquiv: String
    get() =
      when (this) {
        typeOf<Int>() -> Int::class.javaObjectType.simpleName
        typeOf<Int?>() -> Int::class.javaObjectType.simpleName
        else -> this.toString().removePrefix("kotlin.").replace("?", "")
      }

  override fun params(
    name: String,
    types: Array<out KType>,
  ): String {
    val params = types.map { type -> type.javaEquiv }
    return if (params.isNotEmpty()) "<${params.joinToString(", ")}>" else ""
  }

  /**
   * Evaluates a raw Java script string, prepending any registered import declarations.
   *
   * On the first call, registered variable bindings are placed into the engine context.
   *
   * @param script the Java source code to evaluate
   * @param verbose if `true`, logs the generated script before evaluation
   * @return the result of the script evaluation
   */
  @Synchronized
  fun evalScript(
    script: String,
    verbose: Boolean = false,
  ): Any {
    if (!initialized) {
      valueMap.forEach { (name, value) -> engine.engineBindings[name] = value }
      initialized = true
    }

    val code = importDecls + script

    if (verbose)
      logger.info { "Script:\n$code" }

    return engine.eval(code)
  }

  @Synchronized
  fun eval(
    expr: String,
    action: String = "",
    verbose: Boolean = false,
  ): Any {
    if ("System.exit" in expr)
      throw ScriptException("Illegal call to System.exit()")

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

    if (!initialized) {
      valueMap.forEach { (name, value) -> engine.engineBindings[name] = value }
      initialized = true
    }

    if (verbose)
      logger.info { "Script:\n$code" }

    return engine.eval(code)
  }

  override fun close() {
    // Placeholder
  }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
