# Exposed Utils

Utilities for JetBrains Exposed SQL framework, providing custom expressions, transaction utilities, and
PostgreSQL-specific upsert operations.

## Features

### Custom SQL Expressions

- **Custom Expressions**: Create custom SQL expressions with type safety
- **DateTime Constants**: Helper functions for date/time expressions

### Transaction Utilities

- **Enhanced Transactions**: Extended transaction functions with timing and logging
- **ResultRow Extensions**: Additional utilities for working with query results

### PostgreSQL Operations

- **Upsert Support**: PostgreSQL-specific upsert (INSERT ... ON CONFLICT) operations

## Usage Examples

### Custom Expressions

```kotlin
import com.pambrose.common.exposed.customDateTimeConstant
import com.pambrose.common.exposed.dateTimeExpr

// Create custom datetime expressions
val nowExpr = customDateTimeConstant("NOW()")
val dateExpr = dateTimeExpr("CURRENT_DATE")

// Use in queries
val query = MyTable.select { MyTable.createdAt.greater(nowExpr) }
```

### Transaction Utilities

```kotlin
import com.pambrose.common.exposed.readOnlyTransaction
import com.pambrose.common.exposed.transactionWithTimer
import org.jetbrains.exposed.sql.Database

val database = Database.connect("jdbc:postgresql://localhost/mydb")

// Read-only transaction with timing
val results = database.readOnlyTransaction {
  MyTable.selectAll().toList()
}

// Transaction with timing and logging
val (result, duration) = database.transactionWithTimer {
  MyTable.insert {
    it[name] = "John Doe"
    it[email] = "john@example.com"
  }
}
println("Transaction completed in ${duration.inWholeMilliseconds}ms")
```

### ResultRow Extensions

```kotlin
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.toRowString

// Access columns by index
val row = MyTable.select { MyTable.id eq 1 }.single()
val firstColumn = row.get(0)
val secondColumn = row.get(1)

// Debug row contents
println("Row data: ${row.toRowString()}")
```

### PostgreSQL Upsert

```kotlin
import com.pambrose.common.exposed.upsert
import org.jetbrains.exposed.sql.insert

// Upsert with conflict on constraint
MyTable.upsert(conflictIndex = "unique_email_idx") {
  it[name] = "John Doe"
  it[email] = "john@example.com"
  it[updatedAt] = System.currentTimeMillis()
}

// Upsert with conflict on column
MyTable.upsert(conflictColumn = MyTable.email) {
  it[name] = "Jane Doe"
  it[email] = "jane@example.com"
  it[updatedAt] = System.currentTimeMillis()
}
```

## Dependencies

This module depends on:

- Kotlin Standard Library
- JetBrains Exposed Core
- JetBrains Exposed JDBC
- PostgreSQL JDBC Driver (for upsert functionality)

## Installation

### Gradle

```kotlin
dependencies {
  implementation("com.github.pambrose.common-utils:exposed-utils:2..4.12")
}
```

### Maven

```xml

<dependency>
  <groupId>com.github.pambrose.common-utils</groupId>
  <artifactId>exposed-utils</artifactId>
  <version>2..4.12</version>
</dependency>
```

## Database Compatibility

⚠️ **Important**: The upsert functionality is PostgreSQL-specific and uses `ON CONFLICT` syntax. It will not work with
other databases.

## Security Considerations

⚠️ **Security Warning**:

- Custom expressions use direct SQL string interpolation which may be vulnerable to SQL injection
- Always validate and sanitize input when using custom expressions
- Consider using parameterized queries for user-provided data

## Performance Notes

- `ResultRow.get(index: Int)` has O(n) complexity - use sparingly or cache results
- Transaction timing utilities have minimal overhead
- Upsert operations are generally more efficient than separate INSERT/UPDATE logic

## Thread Safety

- All utilities are thread-safe when used within Exposed's transaction blocks
- Custom expressions are immutable and thread-safe
- Transaction utilities properly delegate to Exposed's thread-safe mechanisms

## Best Practices

1. **Input Validation**: Always validate input for custom expressions
2. **Error Handling**: Wrap database operations in try-catch blocks
3. **Connection Management**: Use connection pooling for production applications
4. **Monitoring**: Use transaction timing utilities to monitor database performance

## License

Licensed under the Apache License, Version 2.0.
