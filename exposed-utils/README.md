# Exposed Utils

Utilities for the JetBrains Exposed SQL framework: raw-SQL expressions, transaction helpers with timing,
`ResultRow` extensions, a Kotlin-logging `SqlLogger`, and an index-based `upsert` overload.

All of the transaction helpers are **top-level functions**, not extensions on `Database`. Pass the database
with the `db` argument, or omit it to use Exposed's default.

## Features

### Custom SQL Expressions

- **`CustomExpr<T>`**: embeds raw SQL text as a typed Exposed expression
- **DateTime Constants**: `customDateTimeConstant` and `dateTimeExpr` for Joda `DateTime` expressions

### Transaction Utilities

- **`readonlyTx`**: read-only transaction
- **`timedTransaction` / `timedReadOnlyTx`**: transactions that return a `TimedValue` with the elapsed duration

### ResultRow Extensions

- **`get(index)`**: access a column by zero-based position
- **`toRowString()`**: render a row for logging and debugging

### Logging

- **`KotlinSqlLogger`**: an Exposed `SqlLogger` that writes expanded SQL through a `KLogger`

### Upsert

- **`Table.upsert(conflictIndex)`**: convenience overload of Exposed's native `upsert` taking a unique `Index`
  as the conflict target

## Usage Examples

### Custom Expressions

```kotlin
import com.pambrose.common.exposed.customDateTimeConstant
import com.pambrose.common.exposed.dateTimeExpr

// Nullable DateTime expression
val nowExpr = customDateTimeConstant("NOW()")

// Non-null DateTime expression
val currentTimestamp = dateTimeExpr("CURRENT_TIMESTAMP")

// Each renders as its raw SQL text
println(currentTimestamp.text)  // CURRENT_TIMESTAMP
```

`CustomExpr` is an Exposed `Function<T>`, so it can be used anywhere an expression is accepted.

### Transaction Utilities

```kotlin
import com.pambrose.common.exposed.readonlyTx
import com.pambrose.common.exposed.timedReadOnlyTx
import com.pambrose.common.exposed.timedTransaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

val database = Database.connect("jdbc:postgresql://localhost/mydb")

// Read-only transaction against an explicit database
val count = readonlyTx(db = database) { MyTable.selectAll().count() }

// Omit `db` to use Exposed's default database
val defaultCount = readonlyTx { MyTable.selectAll().count() }

// Timed transaction: returns a kotlin.time.TimedValue
val timed =
  timedTransaction(db = database) {
    MyTable.insert {
      it[name] = "John Doe"
      it[email] = "john@example.com"
    }
  }
println("Completed in ${timed.duration.inWholeMilliseconds}ms")

// Timed read-only variant
val timedRead = timedReadOnlyTx(db = database) { MyTable.selectAll().count() }
println("${timedRead.value} rows in ${timedRead.duration}")
```

Each helper also accepts `transactionIsolation`, defaulting to the database's configured isolation level.

### ResultRow Extensions

```kotlin
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import com.pambrose.common.exposed.toRowString
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

readonlyTx(db = database) {
  val row =
    MyTable
      .select(MyTable.id, MyTable.email, MyTable.name)
      .where { MyTable.id eq 1 }
      .single()

  // Index access follows the order of the selected columns
  val id = row[0]
  val email = row[1]

  // "1 - alice@example.com - Alice"
  println(row.toRowString())
}
```

`row[index]` throws `IllegalArgumentException("No value at index N")` when no column occupies that position. A
column holding SQL NULL is a value, not a missing column, so it returns `null`. `toRowString()` joins each
column's `toString()` with `" - "` and drops empty strings; a `null` column renders as the literal text `"null"`
rather than being skipped.

### SQL Logging

```kotlin
import com.pambrose.common.exposed.KotlinSqlLogger
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

transaction(database) {
  addLogger(KotlinSqlLogger())
  MyTable.selectAll().count()
}
```

`KotlinSqlLogger` logs each statement at info level with its arguments expanded. Pass your own `KLogger` to
route the output elsewhere.

### Upsert

```kotlin
import com.pambrose.common.exposed.upsert
import org.jetbrains.exposed.v1.core.Table

object MyTable : Table("my_table") {
  val id = integer("id")
  val email = varchar("email", 255).uniqueIndex()
  val name = varchar("name", 255)
}

transaction(database) {
  val conflictIndex = MyTable.indices.single { it.unique }

  MyTable.upsert(conflictIndex) {
    it[id] = 1
    it[email] = "john@example.com"
    it[name] = "John Doe"
  }
}
```

The overload forwards the index's columns as the `keys` of the underlying `ON CONFLICT (...)` clause, so the
named index definition stays the single source of truth for the conflict columns. It accepts only a unique
`Index` belonging to the same table, and throws `IllegalArgumentException` otherwise, rather than letting the
database reject the statement at runtime. To conflict on a single column, declare a single-column `uniqueIndex`
and pass that.

Exposed's own `onUpdate`, `onUpdateExclude` and `where` options are passed straight through:

```kotlin
MyTable.upsert(conflictIndex, onUpdateExclude = listOf(MyTable.email)) {
  it[id] = 1
  it[email] = "john@example.com"
  it[name] = "John Doe"
}
```

## API Reference

### Expressions

- `customDateTimeConstant(text: String): CustomExpr<DateTime?>`
- `dateTimeExpr(str: String): CustomExpr<DateTime>`
- `open class CustomExpr<T>(text: String, columnType: IColumnType<T & Any>) : Function<T>`

### Transactions

- `readonlyTx(db: Database? = null, transactionIsolation: Int? = ..., statement: JdbcTransaction.() -> T): T`
- `timedTransaction(db: Database? = null, transactionIsolation: Int? = ..., statement: JdbcTransaction.() -> T): TimedValue<T>`
- `timedReadOnlyTx(db: Database? = null, transactionIsolation: Int? = ..., statement: JdbcTransaction.() -> T): TimedValue<T>`

### ResultRow

- `operator fun ResultRow.get(index: Int): Any?`
- `fun ResultRow.toRowString(): String`

### Logging & Upsert

- `class KotlinSqlLogger(logger: KLogger = ...) : SqlLogger`
- `fun <T : Table> T.upsert(conflictIndex: Index, onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
  onUpdateExclude: List<Column<*>>? = null, where: (() -> Op<Boolean>)? = null,
  body: T.(UpsertStatement<Long>) -> Unit): UpsertStatement<Long>`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- JetBrains Exposed Core
- JetBrains Exposed JDBC
- JetBrains Exposed Joda-Time (used by the `DateTime` expression helpers)

No JDBC driver is bundled — add the driver for your database yourself.

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/exposed-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/exposed-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:exposed-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>exposed-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Database Compatibility

`upsert` delegates to Exposed's native `upsert`, so it works on every database Exposed supports that
statement for — this module's own tests exercise it against H2. It is not PostgreSQL-specific. How much of
`onUpdate`, `onUpdateExclude` and `where` each database accepts is dialect-specific: the MERGE-based statement
H2 uses rejects `where`, for example.

## Security Considerations

⚠️ **`CustomExpr` embeds its text directly into the generated SQL.**

- Never build a `CustomExpr` from user-supplied input
- Reserve it for fixed SQL fragments such as `NOW()` or `CURRENT_DATE`
- Use Exposed's parameterized expressions for anything derived from request data

## Performance Notes

- `ResultRow.get(index: Int)` scans the row's field index, so it is O(n) in the number of columns — prefer
  column-typed access (`row[MyTable.email]`) in hot paths
- The timing helpers wrap the transaction in `measureTimedValue` and add negligible overhead

## Thread Safety

- `CustomExpr` is immutable
- The transaction helpers delegate to Exposed's own transaction management and inherit its threading rules

## License

Licensed under the Apache License, Version 2.0.
