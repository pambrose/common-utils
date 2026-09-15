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
import com.pambrose.common.util.isNull
import com.pambrose.common.util.pluralize
import com.pambrose.common.util.toDoubleQuoted
import com.pambrose.common.util.typeParameterCount
import java.lang.reflect.Modifier
import javax.script.ScriptException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility

// https://docs.oracle.com/en/java/javase/14/scripting/java-scripting-api.html#GUID-C4A6EB7C-0AEA-45EC-8662-099BDEFC361A

/**
 * Abstract base class for script execution that supports named variable bindings with type parameters.
 *
 * Manages a map of named values and their associated type parameters, generates parameter
 * declarations for the target language, and handles context resets between evaluations.
 *
 * Subclasses call [prepare] at the start of every evaluation, which checks the code and binds the variables added since
 * the last evaluation. Variables can therefore be added at any time. Adding variables, resetting, and evaluating are
 * synchronized on the instance.
 *
 * @param extension the file extension used to look up the script engine (e.g., `"kts"`, `"py"`, `"java"`)
 * @param nullGlobalContext if `true`, sets the global scope bindings to `null` on initialization
 */
@Suppress("AbstractClassCanBeConcreteClass")
abstract class AbstractScript(
  extension: String,
  nullGlobalContext: Boolean,
) : AbstractEngine(extension) {
  private val _initialized = AtomicBoolean(false)
  private val typeMap = mutableMapOf<String, Array<out KType>>()

  // Variables added since they were last bound to the engine.
  private val unboundNames = LinkedHashSet<String>()

  protected val valueMap = mutableMapOf<String, Any>()

  /** Whether an evaluation has been prepared since the last reset. */
  @Deprecated("No longer used: variables added after an evaluation are bound before the next one.")
  protected var initialized
    get() = _initialized.load()
    set(value) = _initialized.store(value)

  init {
    resetContext(nullGlobalContext)
  }

  /**
   * Resets the script engine context, clearing all variable and type mappings.
   *
   * @param nullGlobalContext if `true`, sets the global scope bindings to `null`
   */
  @Synchronized
  fun resetContext(nullGlobalContext: Boolean) {
    _initialized.store(false)
    valueMap.clear()
    typeMap.clear()
    unboundNames.clear()
    scriptEngine.resetContext(nullGlobalContext)
  }

  /**
   * Prepares this instance for its next user by resetting the context and any other per-user state.
   * [AbstractScriptPool] calls it when an instance is returned to the pool; subclasses that hold more state, such as
   * imports, extend it.
   *
   * @param nullGlobalContext if `true`, sets the global scope bindings to `null`
   */
  open fun resetForReuse(nullGlobalContext: Boolean) = resetContext(nullGlobalContext)

  /**
   * Generates the type argument string (e.g., `<kotlin.Int, kotlin.String>`) for the variable with the given [name],
   * rendering each type with [renderType].
   *
   * @param name the variable name
   * @param types the type parameters; defaults to those previously registered for [name]
   * @return a formatted type parameter string, or an empty string if there are no parameters
   * @throws IllegalStateException if [types] is omitted and no types were registered for [name]
   */
  open fun params(
    name: String,
    types: Array<out KType> = typeMap[name] ?: error("No type parameters registered for $name"),
  ): String = if (types.isEmpty()) "" else types.joinToString(", ", "<", ">") { renderType(it) }

  /**
   * Renders [type] as a type argument in generated source. The default renders its fully-qualified Kotlin name.
   *
   * @param type the type to render
   * @return the source text for [type]
   */
  protected open fun renderType(type: KType): String = type.toString()

  /**
   * Adds a named variable with an associated value and optional type parameters to the script context.
   *
   * Validates the name and that the number of type parameters matches the value's generic type parameter count.
   *
   * @param name the variable name to bind in the script
   * @param value the value to associate with the variable
   * @param types the type parameters for generic types (e.g., for `List<String>`, pass `typeOf<String>()`)
   * @throws ScriptException if the name is not a valid identifier, the value is a local/anonymous class, or the type
   *   parameter count is invalid
   */
  @Synchronized
  open fun add(
    name: String,
    value: Any,
    vararg types: KType,
  ) {
    checkName(name)
    val paramCnt = value.typeParameterCount
    val qname = name.toDoubleQuoted()

    return when {
      value.javaClass.kotlin.qualifiedName.isNull() -> {
        throw ScriptException("Variable $qname is a local or an anonymous class")
      }

      paramCnt > 0 && types.isEmpty() -> {
        val plural = "parameter".pluralize(paramCnt)
        throw ScriptException("Expected $paramCnt type $plural to be specified for $qname")
      }

      paramCnt == 0 && types.isNotEmpty() -> {
        val plural = "parameter".pluralize(types.size)
        val found = params(name, types)
        throw ScriptException("Invalid type $plural $found specified for $qname")
      }

      paramCnt != types.size -> {
        val plural = "parameter".pluralize(paramCnt)
        val found = "${types.size}: ${params(name, types)}"
        throw ScriptException("Expected $paramCnt type $plural for $qname but found $found")
      }

      else -> {
        register(name, value, types)
      }
    }
  }

  /**
   * Records [value] under [name], to be bound to the engine before the next evaluation. Unlike [add], it does not
   * validate type parameters, so subclasses for dynamically typed languages can call it after [checkName].
   *
   * @param name the variable name
   * @param value the value to bind
   * @param types the type parameters registered for the variable
   */
  @Synchronized
  protected fun register(
    name: String,
    value: Any,
    types: Array<out KType> = emptyArray(),
  ) {
    valueMap[name] = value
    typeMap[name] = types
    unboundNames += name
  }

  /**
   * Whether [name] is a reserved word of the script language, which cannot be used as a variable name. The default
   * reserves nothing.
   *
   * @param name the variable name to check
   */
  protected open fun isReserved(name: String): Boolean = false

  /**
   * Throws a [ScriptException] unless [name] is a valid identifier that is not reserved (see [isReserved]). Names are
   * spliced into generated source, so this also keeps a name from injecting code.
   *
   * @param name the variable name to check
   */
  protected fun checkName(name: String) {
    if (!IDENTIFIER.matches(name) || isReserved(name))
      throw ScriptException("Variable ${name.toDoubleQuoted()} is not a valid identifier")
  }

  /**
   * Prepares to evaluate [code]: checks it with [checkCode], then binds the variables added since they were last bound
   * with [bindVariables]. Subclasses call it at the start of every evaluation, so a variable added after an earlier
   * evaluation is still bound. If binding throws, the variables stay unbound and the next evaluation tries again.
   *
   * @param code the code about to be evaluated
   * @throws ScriptException if [checkCode] rejects [code]
   */
  @Synchronized
  protected fun prepare(code: String) {
    checkCode(code)
    if (unboundNames.isNotEmpty()) {
      bindVariables(unboundNames.associateWith { valueMap.getValue(it) })
      unboundNames.clear()
    }
    _initialized.store(true)
  }

  /**
   * Binds [variables] to the engine. The default puts each one in the engine scope under its own name.
   *
   * @param variables the variables to bind, by name
   */
  protected open fun bindVariables(variables: Map<String, Any>) =
    variables.forEach { (name, value) -> scriptEngine.put(name, value) }

  /**
   * The nearest class or interface of [value]'s runtime class that generated code can name, searched breadth-first
   * through its superclasses and interfaces. The runtime class itself may be private or internal, as the list behind
   * `listOf(1, 2)` is. A candidate must declare as many type parameters as were registered for [name], or any number
   * when none were registered. Falls back to [Any].
   *
   * @param name the variable name
   * @param value the variable's value
   * @return the class for generated code to use as the variable's type
   */
  protected fun accessibleClass(
    name: String,
    value: Any,
  ): KClass<*> {
    val arity = typeMap[name]?.size ?: 0
    return value.javaClass
      .supertypesBreadthFirst()
      .firstOrNull {
        it != Any::class.java && it.isPubliclyAccessible() &&
          (arity == 0 || it.typeParameters.size == arity)
      }?.kotlin
      ?: Any::class
  }

  private companion object {
    val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun Class<*>.supertypesBreadthFirst(): Sequence<Class<*>> =
      sequence {
        val seen = HashSet<Class<*>>()
        val queue = ArrayDeque(listOf(this@supertypesBreadthFirst))
        while (queue.isNotEmpty()) {
          val clazz = queue.removeFirst()
          if (seen.add(clazz)) {
            yield(clazz)
            clazz.superclass?.let { queue.addLast(it) }
            queue.addAll(clazz.interfaces)
          }
        }
      }

    // Whether generated Kotlin or Java code can name this class: it and every class enclosing it are public. Local and
    // anonymous classes are never public to Kotlin reflection, and lambda classes are never public to Java.
    fun Class<*>.isPubliclyAccessible(): Boolean =
      generateSequence(this) { it.enclosingClass }.all { clazz ->
        Modifier.isPublic(clazz.modifiers) &&
          runCatching { clazz.kotlin.visibility == KVisibility.PUBLIC }.getOrDefault(false)
      }
  }
}
