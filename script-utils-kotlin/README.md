# Script Utils Kotlin

Runs Kotlin source at runtime through the JSR-223 `kts` script engine: `KotlinScript` for code with named,
typed variable bindings, `KotlinExprEvaluator` for one-off expressions, and a fixed-size pool of each.

## Features

### Scripts

- **`KotlinScript`**: evaluates Kotlin source, with host values bound as typed `val` declarations
- **`KotlinScriptPool`**: a fixed-size pool of `KotlinScript` instances, reset between borrowers

### Expressions

- **`KotlinExprEvaluator`**: evaluates a Kotlin expression as a `Boolean` (`eval`) or as any value (`compute`)
- **`KotlinExprEvaluatorPool`**: a fixed-size pool of evaluators, with suspending and blocking entry points

## Usage Examples

### Evaluating Kotlin Code

`eval` returns the value of the last expression, or `null`. `KotlinScript` is `Closeable`, so use it with `use`.

```kotlin
import com.pambrose.common.script.KotlinScript

KotlinScript().use { script ->
  script.add("count", 41)
  script.eval("count + 1") // 42
}
```

### Binding Variables

`add(name, value, vararg types)` records a value to bind before the next evaluation. A generic value needs one
`KType` per type parameter its runtime class declares; a non-generic value needs none. Supplying the wrong number
throws `ScriptException`.

```kotlin
import kotlin.reflect.typeOf

KotlinScript().use { script ->
  script.add("str", "A String")
  script.add("list", mutableListOf(1), typeOf<Int>())
  script.add("map", mutableMapOf("k1" to 1), typeOf<String>(), typeOf<Int>())

  script.eval("list.size + map.size") // 2
}
```

Bound values are the host's own objects, so a script mutates them in place:

```kotlin
val list = mutableListOf(1)

KotlinScript().use { script ->
  script.add("list", list, typeOf<Int>())
  script.eval("repeat(100) { list.add(it) }")
}

list.size // 101
```

Variables may be added at any point, including after an evaluation; anything added since the last evaluation is
bound before the next one.

```kotlin
KotlinScript().use { script ->
  script.add("a", 1)
  script.eval("a") // 1
  script.add("b", 2)
  script.eval("a + b") // 3
}
```

`add` rejects a name that is not a valid identifier (`[A-Za-z_][A-Za-z0-9_]*`) or is a Kotlin hard keyword, since
names are spliced into generated source. It also rejects a value of a local or anonymous class, which generated
code cannot name.

```kotlin
script.add("my-var", 5) // ScriptException: not a valid identifier
script.add("class", 5) // ScriptException: not a valid identifier
```

### Generated Declarations

Every bound value is stored in one `ScriptVariables` holder, the only engine binding, and a `val` declaration reads
it back and casts it to a type the script can name. The Kotlin engine turns each binding into a script property typed
by the value's runtime class, so binding values directly let a lambda or a JDK-internal class (such as
`String.CASE_INSENSITIVE_ORDER`) break every later evaluation, and let a variable named `a_tmp` collide with another's
binding. `bindings` and `__variables` are reserved and cannot be used as variable names. When the value's own runtime class is not public — the list behind `listOf(1, 2)`, for
instance — the cast uses the nearest public class or interface, with fully-qualified names, so such values bind
normally.

```kotlin
KotlinScript().use { script ->
  script.add("regex", Regex("a+"))
  script.add("fixed", listOf(1, 2), typeOf<Int>())
  script.add("nested", mapOf("k" to listOf(1, 2)), typeOf<String>(), typeOf<List<Int>>())
  script.add("empty", emptyList<Int>())

  script.eval("""regex.matches("aaa")""") // true
  script.eval("""nested.getValue("k").sum()""") // 3
}
```

`varDecls` renders the declarations currently generated for the bound variables, which is useful when a cast is
not what you expected:

```kotlin
KotlinScript().use { script ->
  script.add("list", mutableListOf<Int?>(), typeOf<Int?>())
  script.varDecls // val list = (bindings["__variables"] as com.pambrose.common.script.ScriptVariables)["list"] as java.util.ArrayList<kotlin.Int?>
}
```

An array is cast to `kotlin.Array` with its registered element type, and a primitive array to its own type:

```kotlin
KotlinScript().use { script ->
  script.add("ints", arrayOf(1, 2), typeOf<Int>())
  script.add("counts", intArrayOf(1, 2))
  script.varDecls
  // val ints = (bindings["__variables"] as com.pambrose.common.script.ScriptVariables)["ints"] as kotlin.Array<kotlin.Int>
  // val counts = (bindings["__variables"] as com.pambrose.common.script.ScriptVariables)["counts"] as kotlin.IntArray
}
```

A value with no public class or interface that takes its registered type arguments is cast to `kotlin.Any`, without
them.

### Resetting

`resetContext` gives the engine a fresh context and drops every bound variable and registered type.

```kotlin
script.resetContext(nullGlobalContext = false)
```

### Evaluating Expressions

`eval` requires a `Boolean` result and throws `IllegalArgumentException` otherwise; `compute` returns whatever the
expression produced.

```kotlin
import com.pambrose.common.script.KotlinExprEvaluator

KotlinExprEvaluator().use { evaluator ->
  evaluator.eval("1 > 0") // true
  evaluator.compute("6 * 7") // 42
}
```

An evaluator keeps engine state across evaluations — the Kotlin engine's REPL history grows with every expression —
so call `resetContext()` on a long-lived evaluator, or borrow from a pool, which resets for you.

### Pooling

Both pools create their instances eagerly, require a positive `size` (`IllegalArgumentException` otherwise), and are
`Closeable`. Closing a pool closes the instances it holds; an instance still borrowed is closed when it comes back,
and a later borrow throws `ClosedReceiveChannelException`.

`KotlinScriptPool.eval` is a suspending function that borrows an instance, runs the block with the `KotlinScript` as
receiver, and returns the instance afterwards — even if the block throws. Each returned script's context is reset, so
variables bound in one borrow are gone in the next.

```kotlin
import com.pambrose.common.script.KotlinScriptPool

val pool = KotlinScriptPool(size = 4, nullGlobalContext = false)

pool.use {
  val answer =
    pool.eval {
      add("x", 41)
      eval("x + 1")
    }
}
```

`KotlinExprEvaluatorPool` exposes a suspending `eval` and a `blockingEval` that blocks the calling thread:

```kotlin
import com.pambrose.common.script.KotlinExprEvaluatorPool

val pool = KotlinExprEvaluatorPool(size = 4)

pool.use {
  pool.eval("1 > 0") // suspends when all 4 are in use
  pool.blockingEval("1 > 0") // blocks the calling thread instead
}
```

The pool is a bounded buffer, so `blockingEval` deadlocks if it is called more than `size` times concurrently, or
from a context that already holds the pool's only evaluator.

### JVM Termination Guard

Before anything is evaluated, the common literal JVM-termination calls are rejected with a `ScriptException`:
`System.exit(...)`, `exitProcess(...)` (bare or fully qualified), and `Runtime.getRuntime().exit/halt(...)`.

```kotlin
script.eval("System.exit(1)") // ScriptException
script.eval("Runtime.getRuntime().halt(0)") // ScriptException
```

This is a convenience against accidental termination, **not** a security sandbox: it matches literal call forms by
pattern, so it is trivially bypassed by string building, reflection or aliasing, and it also matches calls that appear
inside string literals and comments. Run untrusted scripts in a separate process or JVM.

Only the termination calls are blocked. The rest of `java.lang.System` works as usual in scripts:

```kotlin
script.eval("System.currentTimeMillis() > 0") // true
```

## API Reference

### `KotlinScript`

- `class KotlinScript(nullGlobalContext: Boolean = false) : AbstractScript` — `Closeable`
- `eval(code: String): Any?` — evaluates Kotlin source, binding any variables added since the last evaluation
- `add(name: String, value: Any, vararg types: KType)` — records a variable to bind
- `val varDecls: String` — the `val` declarations generated for the bound variables
- `params(name: String, types: Array<out KType> = ...): String` — the rendered type-argument string for a variable
- `resetContext(nullGlobalContext: Boolean)` — drops all bindings and gives the engine a fresh context
- `resetForReuse(nullGlobalContext: Boolean)` — prepares the instance for its next user; what the pool calls

### `KotlinScriptPool`

- `class KotlinScriptPool(size: Int, nullGlobalContext: Boolean) : AbstractScriptPool<KotlinScript>` — `Closeable`
- `suspend fun <R> eval(block: KotlinScript.() -> R): R` — borrows an instance, runs `block`, recycles the instance
- `val size: Int`, `val isEmpty: Boolean` — `isEmpty` is an approximate, point-in-time reading
- `close()` — closes the pool and its instances

### `KotlinExprEvaluator`

- `class KotlinExprEvaluator : AbstractExprEvaluator` — `Closeable`
- `eval(expr: String): Boolean` — throws `IllegalArgumentException` if the result is not a `Boolean`
- `compute(expr: String): Any?` — the raw result
- `resetContext(nullGlobalContext: Boolean = false)`

### `KotlinExprEvaluatorPool`

- `class KotlinExprEvaluatorPool(size: Int) : AbstractExprEvaluatorPool<KotlinExprEvaluator>` — `Closeable`
- `suspend fun eval(expr: String): Boolean`
- `fun blockingEval(expr: String): Boolean`
- `val size: Int`, `val isEmpty: Boolean`, `close()`

## Dependencies

This module depends on:

- Kotlin Standard Library
- script-utils-common (which exports core-utils, and with it kotlinx.coroutines)
- Kotlin JSR-223 scripting engine (`org.jetbrains.kotlin:kotlin-scripting-jsr223`), at runtime

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/script-utils-kotlin)](https://central.sonatype.com/artifact/com.pambrose.common-utils/script-utils-kotlin)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:script-utils-kotlin:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>script-utils-kotlin</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- The engine is resolved through the JSR-223 `ScriptEngineManager`, which reads
  `META-INF/services/javax.script.ScriptEngineFactory` from the classpath. Shading into a fat jar can drop those
  entries, and construction then fails with `ScriptException: Unrecognized script extension: kts`. Merge service
  files when shading, or declare the Kotlin factory in your own
  `src/main/resources/META-INF/services/javax.script.ScriptEngineFactory`.
- The engine compiles every snippet with the Kotlin compiler, in-process. Evaluation is therefore far more expensive
  than a `kts` one-liner suggests, and both memory and time scale with how much you evaluate — which is what the
  pools are for.
- The `engine` property inherited from `AbstractEngine` is deprecated. Using the engine directly bypasses the
  variable binding and context resets these classes manage; use the evaluation and `resetContext` methods instead.
- `close()` on a script or evaluator does nothing, because the Kotlin engine holds no resources to release. Closing
  a pool does close its instances.

Also see:

* https://kotlinexpertise.com/run-kotlin-scripts-from-kotlin-programs/
* Use of bindings explained here: https://discuss.kotlinlang.org/t/jsr223-bindings/9556
* https://github.com/JetBrains/kotlin/tree/master/libraries/examples/scripting
* https://stackoverflow.com/questions/44781462/kotlin-jsr-223-scriptenginefactory-within-the-fat-jar-cannot-find-kotlin-compi

## License

Licensed under the Apache License, Version 2.0.
