# Script Utils Java

Compile and evaluate **Java** source at runtime — not JavaScript — with named variables bound to the script's
fields, generated imports, configurable classloader isolation, and a pool of ready engines.

Built on [java-scriptengine](https://github.com/eobermuhlner/java-scriptengine) and the
[script-utils-common](../script-utils-common/README.md) base classes.

## Features

- **`JavaScript`**: compiles and evaluates Java source, with typed variable bindings
- **`eval(expr, action, verbose)`**: evaluates a Java expression, optionally after a block of statements
- **`evalScript(script, verbose)`**: evaluates a complete Java class, with variables assigned to its public fields
- **`import(clazz)`**: adds an `import` declaration to generated source
- **`assignIsolation(isolation)`**: selects the classloader isolation used by the engine
- **`JavaScriptPool`**: a fixed-size pool of pre-created `JavaScript` instances

## Usage Examples

### Evaluating an Expression

`eval` wraps the expression in a generated `Main` class and returns its value:

```kotlin
import com.pambrose.common.script.JavaScript

JavaScript().use { script ->
  script.add("intVal", 5)
  script.eval("intVal + 1")          // 6

  script.add("strVal", "A String")
  script.eval("strVal.length()")     // 8
}
```

The generated source looks like this, with the imports and field declarations filled in from `importDecls` and
`varDecls`:

```java
public class Main {

  public int intVal;
  public Object getValue() {
    return intVal + 1;
  }
}
```

Pass `verbose = true` to log the generated source before it is compiled.

### Running Statements First

The optional `action` argument holds Java statements executed before the expression is evaluated:

```kotlin
import com.pambrose.common.script.JavaScript

class Counter(var i: Int = 0) {
  fun inc() { i++ }
}

val counter = Counter()

JavaScript().use { script ->
  script.add("counter", counter)
  script.import(Counter::class.java)

  val result =
    script.eval(
      "counter.getI()",
      """
      for (int i = 0; i < 100; i++)
        counter.inc();
      """.trimIndent(),
    )

  // result == 100, and counter.i == 100 — the script mutates the bound object itself
}
```

Both the expression and the action block are checked for literal JVM-termination calls before anything is compiled.

### Generic Values

A generic value needs one `KType` per type parameter, which is rendered into the generated field declaration:

```kotlin
import com.pambrose.common.script.JavaScript
import kotlin.reflect.typeOf

JavaScript().use { script ->
  script.add("list", mutableListOf(1), typeOf<Int>())
  script.add("map", mutableMapOf("k1" to 1), typeOf<String>(), typeOf<Int>())

  script.eval("list.size() + map.size()")   // 2
}
```

Declarations use fully-qualified Java type names, so they need no imports of their own:

```java
  public java.util.ArrayList<java.lang.Integer> list;
  public java.util.LinkedHashMap<java.lang.String, java.lang.Integer> map;
```

Kotlin type arguments are mapped to their Java equivalents: `Int` and `Int?` both become `java.lang.Integer`, and
nullability is dropped. A boxed primitive value is declared as the Java primitive (`int`, `long`, `boolean`, …). A
value whose runtime class cannot be named in generated source, such as the private list class behind `listOf(1, 2)`,
is declared as its nearest public class or interface.

### Evaluating a Whole Class

`evalScript` evaluates Java source as given, prefixed with the registered imports. Each variable added with `add` is
assigned to the **public field of the same name** in the script's class, so the class must declare a public,
assignable field for every added variable:

```kotlin
import com.pambrose.common.script.JavaScript

JavaScript().use { script ->
  script.add("count", 41)

  script.evalScript(
    """
    public class Main {
      public int count;

      public Object getValue() {
        return count + 1;
      }
    }
    """.trimIndent(),
  )   // 42
}
```

A value that does not fit its field — binding a `String` to an `int count`, for example — is reported as a
`ScriptException`, as are compilation and runtime failures.

### Imports and Isolation

```kotlin
import ch.obermuhlner.scriptengine.java.Isolation
import com.pambrose.common.script.JavaScript

JavaScript().use { script ->
  script.import(java.util.ArrayList::class.java)
  script.assignIsolation(Isolation.IsolatedClassLoader)

  script.importDecls    // "import java.util.ArrayList;"
}
```

`Isolation.CallerClassLoader` is java-scriptengine's default, and the isolation a pooled instance is restored to when
it is returned. `Isolation.IsolatedClassLoader` compiles each script into its own classloader, so a class can be
redefined between evaluations.

### Pooling

Creating an engine and compiling Java source is expensive, so a pool of ready instances pays off under load:

```kotlin
import com.pambrose.common.script.JavaScriptPool
import kotlinx.coroutines.runBlocking

val pool = JavaScriptPool(size = 4)

runBlocking {
  pool.eval {
    add("x", 40)
    eval("x + 2")
  }   // 42
}

pool.close()
```

`size` must be positive. Instances are created eagerly, and a returned instance is reset for the next borrower: its
context, its variables, its imports, and its isolation level all go back to their defaults. Closing the pool closes
its instances, and later borrows throw `ClosedReceiveChannelException`.

## API Reference

### `JavaScript : AbstractScript`

- `fun eval(expr: String, action: String = "", verbose: Boolean = false): Any?`
- `fun evalScript(script: String, verbose: Boolean = false): Any?`
- `fun <T> import(clazz: Class<T>)`
- `fun assignIsolation(isolation: Isolation)`
- `val varDecls: String` — the generated public field declarations
- `val importDecls: String` — the generated import statements
- `override fun resetForReuse(nullGlobalContext: Boolean)` — also clears imports and restores the default isolation
- Inherited from `AbstractScript`: `add(name, value, vararg types)`, `params(name, types)`,
  `resetContext(nullGlobalContext)`
- Inherited from `AbstractEngine`: `close()` (a no-op — the Java engine holds nothing to release)

### `JavaScriptPool(size: Int, nullGlobalContext: Boolean = false) : AbstractScriptPool<JavaScript>`

- `suspend fun <R> eval(block: JavaScript.() -> R): R`
- `val size: Int`, `val isEmpty: Boolean`, `fun close()`

## Dependencies

This module depends on:

- Kotlin Standard Library
- script-utils-common (which exports core-utils)
- java-scriptengine (`ch.obermuhlner:java-scriptengine`)

java-scriptengine is an `api` dependency rather than an implementation detail, because `assignIsolation` takes its
`Isolation` enum and so it is part of this module's public API.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/script-utils-java)](https://central.sonatype.com/artifact/com.pambrose.common-utils/script-utils-java)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:script-utils-java:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>script-utils-java</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- Scripts are compiled in-process by the JDK's own Java compiler, so the host must run on a JDK, not a JRE.
- Java has no null global context: `JavaScript` binds variables into the engine scope, never the global scope, so
  `JavaScriptPool`'s `nullGlobalContext` parameter is ignored and kept only for symmetry with the other script pools.
- Variable names must be valid Java identifiers and must not be Java keywords; `add` rejects anything else, which also
  keeps a name from injecting code into the generated class.
- Variables added after an evaluation are bound before the next one, so `add` and `eval` can be interleaved freely.
- `eval` and `evalScript` return `null` when the script evaluates to `null`.
- java-scriptengine lets some failures escape unwrapped; `JavaScript` reports them as `ScriptException` like every
  other script failure.

## Thread Safety

- `JavaScript` synchronizes `add`, `import`, `eval`, `evalScript`, and `resetForReuse` on the instance, so evaluations
  on a shared instance serialize rather than interleave
- Prefer one instance per thread, or a `JavaScriptPool`, which hands each borrower an instance of its own

## Security Considerations

⚠️ **This module is not a sandbox.** Evaluated Java source is compiled and run in the host JVM with the host's full
privileges.

- `ScriptGuards` rejects literal `System.exit(...)` and `Runtime.getRuntime().exit/halt(...)` calls in the expression,
  the action block, and `evalScript` source, on a best-effort basis
- That check is pattern matching, trivially bypassed by reflection or string concatenation, and it restricts nothing
  else a script can do — file access, network access, and loading arbitrary classes are all untouched
- `Isolation.IsolatedClassLoader` isolates class definitions, not privileges: it is not a security boundary
- Run untrusted scripts in a separate process or JVM with a restrictive policy

## License

Licensed under the Apache License, Version 2.0.
