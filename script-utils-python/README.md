# Script Utils Python

Evaluate Python source in-process with the Jython engine: bind JVM objects as Python variables, evaluate expressions,
and pool ready engines.

Built on Jython (`org.python:jython-standalone`) and the
[script-utils-common](../script-utils-common/README.md) base classes.

## Features

- **`PythonScript`**: evaluates Python code with named variables bound to the engine
- **`PythonExprEvaluator`**: evaluates a single Python expression as a `Boolean` or as an arbitrary value
- **`PythonScriptPool`** / **`PythonExprEvaluatorPool`**: fixed-size pools of pre-created instances
- **Exit guards**: literal JVM-termination calls and Python's own exits are rejected before code runs

## Usage Examples

### Evaluating Code

```kotlin
import com.pambrose.common.script.PythonScript

PythonScript().use { script ->
  script.add("strVal", "A String")

  script.eval("strVal")          // "A String"
  script.eval("len(strVal)")     // 8
}
```

Python is dynamically typed, so `add` takes no type parameters — any passed are ignored. It still checks that the name
is a valid identifier and not a Python 2.7 keyword, which also keeps a name from injecting code.

Variables can be added at any time, including after an evaluation; they are bound before the next one:

```kotlin
import com.pambrose.common.script.PythonScript

PythonScript().use { script ->
  script.add("a", 1)
  script.eval("a")          // 1

  script.add("b", 2)
  script.eval("a + b")      // 3
}
```

### Bound Objects

A bound JVM object is the object itself, so a script mutates it in place:

```kotlin
import com.pambrose.common.script.PythonScript

class Counter(var i: Int = 0) {
  fun inc() { i++ }
}

val counter = Counter()

PythonScript().use { script ->
  script.add("counter", counter)

  script.eval(
    """
    for i in range(100):
      counter.inc()
    """.trimIndent(),
  )
}

// counter.i == 100
```

The same holds for collections: a Kotlin `MutableList` bound as a variable can be appended to with `list.add(i)` from
Python, and the Kotlin list sees the additions.

### Evaluating Expressions

```kotlin
import com.pambrose.common.script.PythonExprEvaluator

PythonExprEvaluator().use { evaluator ->
  evaluator.eval("1 < 2")        // true
  evaluator.compute("1 + 2")     // 3
  evaluator.resetContext()
}
```

`eval` throws `IllegalArgumentException` when the expression does not evaluate to a `Boolean`; `compute` returns
whatever the expression produced. Each evaluator gets its own bindings, and `close()` closes the underlying Jython
engine.

### Pooling

Creating a Jython engine is expensive, so a pool of ready instances pays off under load:

```kotlin
import com.pambrose.common.script.PythonExprEvaluatorPool
import com.pambrose.common.script.PythonScriptPool
import kotlinx.coroutines.runBlocking

val scripts = PythonScriptPool(size = 4, nullGlobalContext = false)
val evaluators = PythonExprEvaluatorPool(size = 4)

runBlocking {
  scripts.eval {
    add("x", 40)
    eval("x + 2")
  }                            // 42

  evaluators.eval("1 < 2")     // true
}

evaluators.blockingEval("2 > 1")   // from a non-suspending context

scripts.close()
evaluators.close()
```

`size` must be positive. Instances are created eagerly, and a returned instance has its context reset, so no borrower
sees the previous borrower's variables. Closing a pool closes its instances, and later borrows throw
`ClosedReceiveChannelException`.

### Exit Guards

Code is checked before it runs. Two families of exit are rejected, both raising `ScriptException`:

| Rejected                                                                           | Why                                                         |
|------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `java.lang.System.exit(...)`, `Runtime.getRuntime().halt(...)`, `exitProcess(...)` | from Jython these really kill the host JVM                  |
| `sys.exit(...)`, `exit(...)`, `quit(...)`, `raise SystemExit`                      | these only raise `SystemExit`; rejecting them reads clearer |

```kotlin
import com.pambrose.common.script.PythonScript

PythonScript().use { script ->
  script.eval("sys.exit(1)")      // throws ScriptException
  script.eval("exit(1)")          // throws ScriptException
  script.eval("quit(1)")          // throws ScriptException
  script.eval("raise SystemExit") // throws ScriptException
}
```

Ordinary code that merely *looks* like an exit is allowed:

- method calls on an object — `widget.exit()`, `widget.quit()`
- definitions — `def exit(self):`, `def quit(self):`
- identifiers that merely contain the words — `my_exit(7)`, `quit_handler(10)`, `sys_exit_wrapper(3)`
- catching the exception — `except SystemExit:` (only `raise SystemExit` is rejected)

The checks match text, not syntax, so they also match inside string literals and comments: `print('exit(1)')` is
rejected even though it terminates nothing.

## API Reference

### `PythonScript(nullGlobalContext: Boolean = false) : AbstractScript`

- `fun eval(code: String): Any?`
- `override fun add(name: String, value: Any, vararg types: KType)` — `types` is ignored
- `override fun close()` — closes the underlying `PyScriptEngine`
- Inherited from `AbstractScript`: `params(name, types)`, `resetContext(nullGlobalContext)`,
  `resetForReuse(nullGlobalContext)`

### `PythonExprEvaluator : AbstractExprEvaluator`

- `fun eval(expr: String): Boolean`
- `fun compute(expr: String): Any?`
- `fun resetContext(nullGlobalContext: Boolean = false)`
- `override fun close()` — closes the underlying `PyScriptEngine`

### `PythonScriptPool(size: Int, nullGlobalContext: Boolean) : AbstractScriptPool<PythonScript>`

- `suspend fun <R> eval(block: PythonScript.() -> R): R`
- `val size: Int`, `val isEmpty: Boolean`, `fun close()`

### `PythonExprEvaluatorPool(size: Int) : AbstractExprEvaluatorPool<PythonExprEvaluator>`

- `suspend fun eval(expr: String): Boolean`
- `fun blockingEval(expr: String): Boolean`
- `val size: Int`, `val isEmpty: Boolean`, `fun close()`

## Dependencies

This module depends on:

- Kotlin Standard Library
- script-utils-common (which exports core-utils)
- Jython (`org.python:jython-standalone`)

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/script-utils-python)](https://central.sonatype.com/artifact/com.pambrose.common-utils/script-utils-python)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:script-utils-python:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>script-utils-python</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- Jython implements **Python 2.7**, so `print` is a keyword (and a reserved variable name), and only Python 2 syntax
  is accepted.
- Results come back as Jython's JVM types: a Kotlin `Long` evaluates to a `BigInteger`, and a `Float` to a `Double`.
- `PythonScript` and `PythonExprEvaluator` both override `close()` to close the Jython engine, unlike the Kotlin and
  Java engines, which hold nothing to release — use them with `use { }` or pool them.
- `blockingEval` blocks the calling thread until an evaluator is free. Do not call it more than `size` times
  concurrently, or from a context that already holds the pool's only evaluator, or it can deadlock.

## Thread Safety

- `PythonScript` synchronizes `add` and `eval` on the instance, so evaluations on a shared instance serialize rather
  than interleave
- `PythonExprEvaluator` adds no synchronization of its own; give each thread its own evaluator or borrow one from a
  pool
- The pools are safe for concurrent use, and hand each borrower an instance of its own

## Security Considerations

⚠️ **This module is not a sandbox.** Evaluated Python runs in the host JVM with the host's full privileges, and
Jython can reach any JVM class.

- The exit guards are best-effort pattern matching, trivially bypassed with `os._exit(0)`,
  `getattr(sys, 'ex' + 'it')(0)`, or reflection
- They restrict nothing else: file access, network access, and arbitrary Java calls from Python are all untouched
- Their purpose is to catch *accidental* termination during development, not to contain hostile code
- Run untrusted scripts in a separate process or JVM with a restrictive policy

## License

Licensed under the Apache License, Version 2.0.
