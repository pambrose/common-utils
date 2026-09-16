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

// DEPRECATION: FakeScript exposes the deprecated initialized property so its tests can reach it.
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "UndocumentedPublicProperty", "DEPRECATION")

package com.pambrose.common.script

import java.io.IOException
import java.io.Reader
import java.util.Collections
import javax.script.AbstractScriptEngine
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptEngineFactory
import javax.script.ScriptException
import javax.script.SimpleBindings
import kotlin.reflect.KClass

/** The extension [FakeScriptEngineFactory] is registered for, in `META-INF/services/javax.script.ScriptEngineFactory`. */
const val FAKE_EXTENSION = "fake"

/**
 * A JSR 223 engine that lets the script-utils-common base classes be tested without starting a compiler.
 *
 * It evaluates a single token: `true`, `false`, `null`, an integer, a double-quoted string, or the name of a binding,
 * which it looks up in the engine scope and then the global scope. Anything else throws a [ScriptException]. It records
 * every script it is given in [evaluated].
 */
class FakeScriptEngine(
  private val factory: ScriptEngineFactory,
) : AbstractScriptEngine() {
  val evaluated: MutableList<String> = Collections.synchronizedList([])

  override fun eval(
    script: String,
    context: ScriptContext,
  ): Any? {
    evaluated += script
    val code = script.trim()
    return when {
      code == "true" || code == "false" -> code.toBoolean()
      code == "null" -> null
      code.toIntOrNull() != null -> code.toInt()
      code.length >= 2 && code.startsWith('"') && code.endsWith('"') -> code.substring(1, code.length - 1)
      context.getAttributesScope(code) != -1 -> context.getAttribute(code)
      else -> throw ScriptException("Cannot evaluate: $code")
    }
  }

  override fun eval(
    reader: Reader,
    context: ScriptContext,
  ): Any? = eval(reader.readText(), context)

  override fun createBindings(): Bindings = SimpleBindings()

  override fun getFactory(): ScriptEngineFactory = factory
}

/** Creates [FakeScriptEngine]s for the [FAKE_EXTENSION] extension. */
class FakeScriptEngineFactory : ScriptEngineFactory {
  override fun getEngineName() = "Fake engine"

  override fun getEngineVersion() = "1.0"

  override fun getExtensions() = listOf(FAKE_EXTENSION)

  override fun getMimeTypes() = emptyList<String>()

  override fun getNames() = listOf(FAKE_EXTENSION)

  override fun getLanguageName() = FAKE_EXTENSION

  override fun getLanguageVersion() = "1.0"

  override fun getParameter(key: String): Any? =
    when (key) {
      ScriptEngine.NAME -> FAKE_EXTENSION
      ScriptEngine.ENGINE -> engineName
      ScriptEngine.ENGINE_VERSION -> engineVersion
      ScriptEngine.LANGUAGE -> languageName
      ScriptEngine.LANGUAGE_VERSION -> languageVersion
      else -> null
    }

  override fun getMethodCallSyntax(
    obj: String,
    m: String,
    vararg args: String,
  ) = "$obj.$m(${args.joinToString(", ")})"

  override fun getOutputStatement(toDisplay: String) = toDisplay

  override fun getProgram(vararg statements: String) = statements.joinToString("\n")

  override fun getScriptEngine(): ScriptEngine = FakeScriptEngine(this)
}

/**
 * An [AbstractScript] on the fake engine that keeps the base class's defaults and exposes its protected members.
 * [onBind] runs before the default [bindVariables], so a test can make binding fail.
 */
class FakeScript(
  nullGlobalContext: Boolean = false,
) : AbstractScript(FAKE_EXTENSION, nullGlobalContext) {
  @Volatile
  var onBind: (Map<String, Any>) -> Unit = {}

  val fakeEngine get() = scriptEngine as FakeScriptEngine

  var preparedFlag
    get() = initialized
    set(value) {
      initialized = value
    }

  fun accessibleClassOf(
    name: String,
    value: Any,
  ): KClass<*> = accessibleClass(name, value)

  fun reserved(name: String) = isReserved(name)

  override fun bindVariables(variables: Map<String, Any>) {
    onBind(variables)
    super.bindVariables(variables)
  }

  @Synchronized
  fun eval(code: String): Any? {
    prepare(code)
    return scriptEngine.eval(code)
  }
}

/**
 * An [AbstractExprEvaluator] on the fake engine that records whether it is closed. When [failClose] is set, closing it
 * throws an [IOException] naming its [index].
 */
class FakeEvaluator(
  val index: Int = 0,
  private val failClose: Boolean = false,
) : AbstractExprEvaluator(FAKE_EXTENSION) {
  @Volatile
  var closed = false
    private set

  // Runs in place of the usual code check, so a test can hold an evaluator borrowed.
  @Volatile
  var onCheck: (String) -> Unit = {}

  val fakeEngine get() = scriptEngine as FakeScriptEngine

  override fun checkCode(code: String) = onCheck(code)

  override fun close() {
    closed = true
    if (failClose) throw IOException("cannot close evaluator $index")
  }
}

/**
 * A pool of [FakeEvaluator]s that records each one it creates in [created]. Creating the one at index [failAt] throws,
 * and closing any of them throws when [failClose] is set. [onReset] runs before the usual reset, and [borrow] exposes
 * the pool's `withInstance`.
 */
class FakeEvaluatorPool(
  size: Int,
  val created: MutableList<FakeEvaluator> = [],
  failAt: Int = -1,
  failClose: Boolean = false,
) : AbstractExprEvaluatorPool<FakeEvaluator>(size) {
  @Volatile
  var onReset: (FakeEvaluator) -> Unit = {}

  init {
    populate {
      check(created.size != failAt) { "cannot create evaluator $failAt" }
      FakeEvaluator(created.size, failClose).also { created += it }
    }
  }

  override fun reset(instance: FakeEvaluator) {
    onReset(instance)
    super.reset(instance)
  }

  suspend fun <R> borrow(block: (FakeEvaluator) -> R): R = withInstance(block)
}

/** A pool of [FakeScript]s, which [AbstractScriptPool] resets with `resetForReuse`. */
class FakeScriptPool(
  size: Int,
  nullGlobalContext: Boolean,
) : AbstractScriptPool<FakeScript>(size, nullGlobalContext) {
  init {
    populate { FakeScript(nullGlobalContext) }
  }
}
