# Script Utils Common

The shared base for the `script-utils-*` engine modules: JSR 223 engine wrappers that manage named variable bindings
and context resets, fixed-size pools of those engines, and a best-effort guard against accidental JVM termination.

## Features

### Engines

- **`AbstractEngine(extension)`**: resolves a JSR 223 engine by file extension and is `Closeable`
- **`AbstractScript`**: named variable bindings with type parameters, generated declarations, and context resets
- **`AbstractExprEvaluator`**: evaluates a single expression as a `Boolean` or as an arbitrary value

### Pools

- **`AbstractEnginePool`**: the shared fixed-size pool, built on a coroutine `Channel`
- **`AbstractScriptPool`**: a pool of `AbstractScript` instances, reset with `resetForReuse` on return
- **`AbstractExprEvaluatorPool`**: a pool of evaluators, with `eval` and `blockingEval`

### Utilities

- **`ScriptUtils`**: `ScriptEngine` extensions for bindings and context resets
- **`ScriptGuards`**: best-effort rejection of literal JVM-termination calls

## Usage Examples

### Engines

`AbstractEngine` looks the engine up by file extension and throws a `ScriptException` when no engine is registered
for it:

```kotlin
import com.pambrose.common.script.AbstractEngine

class MyEngine : AbstractEngine("kts")
```

It is `Closeable`, and `close()` does nothing unless a subclass overrides it — `PythonScript` does, because the Jython
engine holds resources; the Kotlin and Java engines do not.

The engine itself is available to subclasses as the protected `scriptEngine`. The public `engine` property is
`@Deprecated`: using it directly bypasses the variable bindings and context resets these classes manage.

Every evaluation runs `checkCode(code)` first. The default rejects literal JVM-termination calls with `ScriptGuards`;
subclasses override it to add checks for their language:

```kotlin
import com.pambrose.common.script.AbstractExprEvaluator

class GuardedEvaluator : AbstractExprEvaluator("kts") {
  override fun checkCode(code: String) {
    super.checkCode(code)
    if ("while" in code) throw javax.script.ScriptException("Loops are not allowed")
  }
}
```

### Adding Variables

`add(name, value, vararg types)` records a variable to bind before the next evaluation. Variables can be added at any
time, including after an evaluation — they are bound before the next one:

```kotlin
import com.pambrose.common.script.PythonScript
import kotlin.reflect.typeOf

PythonScript().use { script ->
  script.add("count", 1)
  script.eval("count")

  script.add("total", 2)
  script.eval("count + total")
}
```

`add` validates two things:

| Check                                                       | Failure message                                        |
|-------------------------------------------------------------|--------------------------------------------------------|
| A valid identifier (`[A-Za-z_][A-Za-z0-9_]*`), not reserved | `Variable "my-var" is not a valid identifier`          |
| One type parameter per generic type parameter of the value  | `Expected 1 type parameter to be specified for "list"` |

Names are spliced into generated source, so the identifier check also keeps a name from injecting code. Reserved words
come from the subclass's `isReserved`, which reserves nothing by default.

A generic value needs one `KType` per type parameter, and a non-generic value must be given none:

```kotlin
import com.pambrose.common.script.KotlinScript
import kotlin.reflect.typeOf

KotlinScript().use { script ->
  script.add("list", mutableListOf(1), typeOf<Int>())
  script.add("map", mutableMapOf("k" to 1), typeOf<String>(), typeOf<Int>())
  script.add("count", 5)
}
```

`params(name)` renders the registered types as a type-argument string such as `<kotlin.Int, kotlin.String>`, with each
type rendered by the subclass's `renderType`.

A value whose runtime class cannot be named in generated code — the private list class behind `listOf(1, 2)`, for
example — is declared as the nearest class or interface that generated code can name, found breadth-first through its
superclasses and interfaces, with fully-qualified type arguments. A class qualifies only if it and every class
enclosing it are public and it declares as many type parameters as were registered; the search falls back to `Any`,
which is declared without type arguments. An array keeps its own class and is declared with its registered element
type, such as `kotlin.Array<kotlin.Int>` or `java.lang.Integer[]`. A value that is a local or anonymous class is
rejected by `add` outright.

### Resetting

- **`resetContext(nullGlobalContext)`** clears the value and type maps and gives the engine a fresh
  `SimpleScriptContext`
- **`resetForReuse(nullGlobalContext)`** prepares an instance for its next user; `AbstractScriptPool` calls it when an
  instance is returned, and subclasses holding more state extend it — `JavaScript` also clears its imports and
  restores the default isolation

### Expression Evaluators

```kotlin
import com.pambrose.common.script.PythonExprEvaluator

PythonExprEvaluator().use { evaluator ->
  evaluator.eval("1 < 2")        // true
  evaluator.compute("1 + 2")     // 3
  evaluator.resetContext()
}
```

`eval` throws `IllegalArgumentException` when the expression does not evaluate to a `Boolean`; `compute` returns
whatever the expression produced, including `null`. `ScriptEngineManager` hands every engine it creates the same global
`Bindings`, so each evaluator resets its context on construction to get bindings of its own.

Engine state accumulates across evaluations — the Kotlin engine's REPL history grows with every expression — so call
`resetContext` periodically, or use a pool, which resets each evaluator on return.

### Pools

```kotlin
import com.pambrose.common.script.KotlinScriptPool
import kotlinx.coroutines.runBlocking

val pool = KotlinScriptPool(size = 4, nullGlobalContext = false)

runBlocking {
  val answer = pool.eval {
    add("x", 40)
    eval("x + 2")
  }
}

pool.close()
```

`size` must be positive, or the constructor throws `IllegalArgumentException`. Subclasses create every instance
eagerly in their `init` block with `populate { }`; if creating one fails, the instances already created are closed —
any failure to close one is attached to the original exception as a suppressed exception — before it propagates.

Borrowing suspends until an instance is free, and the instance is reset and returned even when the block throws. If
the reset itself throws, the instance is still returned, and the reset's exception propagates to the borrower.
`AbstractScriptPool` resets with `resetForReuse`, `AbstractExprEvaluatorPool` with `resetContext`, so no borrower sees
the previous borrower's variables. An instance handed to a borrower that is cancelled before it resumes goes back into
the pool rather than being lost, so a cancellation cannot shrink the pool.

`close()` closes the pool and the instances it holds; an instance still borrowed is closed when it is returned, and
later borrows throw `ClosedReceiveChannelException`.

A custom pool supplies its own factory:

```kotlin
import com.pambrose.common.script.AbstractExprEvaluatorPool
import com.pambrose.common.script.PythonExprEvaluator

class MyEvaluatorPool(size: Int) : AbstractExprEvaluatorPool<PythonExprEvaluator>(size) {
  init {
    populate { PythonExprEvaluator() }
  }
}
```

### Guards

```kotlin
import com.pambrose.common.script.ScriptGuards

ScriptGuards.checkNoJvmExit(action, expr)
```

`checkNoJvmExit` throws a `ScriptException` when any fragment contains a recognized literal JVM-termination call:
`System.exit(...)`, bare or fully-qualified `exitProcess(...)`, and `Runtime.getRuntime().exit/halt(...)`, including
the statically-imported `getRuntime().halt(...)` form. All fragments are joined before matching, so a call split
across an expression and a separate action block is still found. Whitespace around the dots and parentheses does not
evade it, and `mySystem.exit(0)` or `obj.exitProcess(args)` are not matched.

### Engine Bindings

```kotlin
import com.pambrose.common.script.ScriptUtils.engineBindings
import com.pambrose.common.script.ScriptUtils.globalBindings
import com.pambrose.common.script.ScriptUtils.resetContext

engine.resetContext(nullGlobalContext = false)
engine.engineBindings["key"] = "value"
```

`resetContext` installs a new `SimpleScriptContext` with fresh engine-scope bindings; the global scope gets fresh
bindings, or `null` when `nullGlobalContext` is `true`. `globalBindings` and `bindings(scope)` are nullable for that
reason; `engineBindings` never is.

## API Reference

### `AbstractEngine(extension: String) : Closeable`

- `protected val scriptEngine: ScriptEngine` — throws `ScriptException` if no engine matches `extension`
- `val engine: ScriptEngine` — **deprecated**; bypasses the managed bindings and context resets
- `protected open fun checkCode(code: String)` — defaults to `ScriptGuards.checkNoJvmExit(code)`
- `override fun close()` — does nothing by default

### `AbstractScript(extension: String, nullGlobalContext: Boolean) : AbstractEngine`

- `open fun add(name: String, value: Any, vararg types: KType)`
- `open fun params(name: String, types: Array<out KType> = ...): String`
- `fun resetContext(nullGlobalContext: Boolean)`
- `open fun resetForReuse(nullGlobalContext: Boolean)`
- `protected val valueMap: MutableMap<String, Any>`
- `protected fun register(name, value, types)`, `protected fun checkName(name)`, `protected fun prepare(code)`
- `protected open fun isReserved(name: String): Boolean`, `protected open fun renderType(type: KType): String`
- `protected open fun bindVariables(variables: Map<String, Any>)`
- `protected fun accessibleClass(name: String, value: Any): KClass<*>`
- `protected var initialized` — **deprecated** and no longer used

### `AbstractExprEvaluator(extension: String) : AbstractEngine`

- `fun eval(expr: String): Boolean`
- `fun compute(expr: String): Any?`
- `fun resetContext(nullGlobalContext: Boolean = false)`

### `AbstractEnginePool<T : AbstractEngine>(size: Int) : Closeable`

- `val size: Int`, `val isEmpty: Boolean`
- `protected abstract fun reset(instance: T)`
- `protected fun populate(factory: () -> T)`
- `protected suspend fun <R> withInstance(block: (T) -> R): R`
- `override fun close()`

### `AbstractScriptPool<T : AbstractScript>(size: Int, nullGlobalContext: Boolean)`

- `suspend fun <R> eval(block: T.() -> R): R`

### `AbstractExprEvaluatorPool<T : AbstractExprEvaluator>(size: Int)`

- `suspend fun eval(expr: String): Boolean`
- `fun blockingEval(expr: String): Boolean`

### `ScriptGuards`

- `fun checkNoJvmExit(vararg fragments: String)`

### `ScriptUtils`

- `val ScriptEngine.engineBindings: Bindings`, `val ScriptEngine.globalBindings: Bindings?`
- `fun ScriptEngine.bindings(scope: Int = ENGINE_SCOPE): Bindings?`
- `fun ScriptEngine.resetContext(nullGlobalContext: Boolean = false)`

## Dependencies

This module depends on:

- Kotlin Standard Library
- Kotlin Reflection
- core-utils
- kotlinx-coroutines (for the pools' `Channel`)

It brings no script engine of its own: add `script-utils-kotlin`, `script-utils-java`, or `script-utils-python` for a
concrete engine.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/script-utils-common)](https://central.sonatype.com/artifact/com.pambrose.common-utils/script-utils-common)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:script-utils-common:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>script-utils-common</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- `isEmpty` delegates to `Channel.isEmpty`, which is racy under concurrent borrow and recycle. It is a point-in-time
  hint, not something to rely on for correctness.
- `blockingEval` blocks the calling thread until an evaluator is free. Do not call it more than `size` times
  concurrently, or from a context that already holds the pool's only evaluator, or it can deadlock.
- The engine is resolved through `ScriptEngineManager`, so the engine implementation must be on the classpath and must
  register a `ScriptEngineFactory` in `META-INF/services`.

## Thread Safety

- `AbstractScript` synchronizes `add`, `register`, `resetContext`, and `prepare` on the instance, and subclasses
  synchronize their evaluation methods, so one instance can be shared — though evaluations then serialize
- `AbstractEngine` and `AbstractExprEvaluator` add no synchronization of their own; the underlying JSR 223 engines are
  not generally thread-safe, so give each thread its own instance or borrow one from a pool
- The pools are safe for concurrent use: instances are handed out through a coroutine `Channel`, and a borrowed
  instance is used by one borrower at a time

## Security Considerations

⚠️ **`ScriptGuards` is NOT a security sandbox.**

- It matches only obvious, literal call forms by simple pattern matching, and is trivially bypassed — by building the
  call from string fragments (`"ex" + "it"`), by reflection, by aliasing the runtime
  (`val r = Runtime.getRuntime(); r.halt(0)`), or by any other indirection
- It also matches calls that appear inside string literals and comments, so it can reject harmless code
- It stops nothing else a script can do: file access, network access, and loading arbitrary classes are all untouched
- Its purpose is to catch *accidental* JVM termination during development
- Untrusted scripts belong in a separate process or JVM with a restrictive policy — never in the host JVM

## License

Licensed under the Apache License, Version 2.0.
