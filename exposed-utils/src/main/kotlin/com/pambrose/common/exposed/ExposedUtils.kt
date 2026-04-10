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

package com.pambrose.common.exposed

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.expandArgs
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager

/** Internal holder for the module-level logger. */
object ExposedUtils {
  internal val logger = KotlinLogging.logger {}
}

/**
 * An Exposed [SqlLogger] that logs SQL statements via a Kotlin [KLogger].
 *
 * @property logger the [KLogger] to use for logging; defaults to the module-level logger
 */
class KotlinSqlLogger(
  val logger: KLogger = ExposedUtils.logger,
) : SqlLogger {
  override fun log(
    context: StatementContext,
    transaction: Transaction,
  ) {
    logger.info { "SQL: ${context.expandArgs(transaction)}" }
  }
}

/**
 * Retrieves a column value from this [ResultRow] by its zero-based field [index].
 *
 * Extension operator on [ResultRow].
 *
 * @param index the zero-based column index
 * @return the value at the given index
 * @throws IllegalArgumentException if no field exists at the given index
 */
operator fun ResultRow.get(index: Int) =
  fieldIndex.filter { it.value == index }.map { this[it.key] }.firstOrNull()
    ?: throw IllegalArgumentException("No value at index $index")

/**
 * Converts this [ResultRow] to a human-readable string by joining all non-empty column values with " - ".
 *
 * Extension function on [ResultRow].
 *
 * @return a formatted string of the row's values
 */
fun ResultRow.toRowString() =
  fieldIndex.values.map { this[it].toString() }.filter { it.isNotEmpty() }.joinToString(" - ")

/**
 * Executes a read-only database transaction.
 *
 * @param T the return type of the transaction
 * @param db the [Database] to use, or `null` for the default database
 * @param transactionIsolation the JDBC transaction isolation level
 * @param statement the transaction body to execute
 * @return the result of [statement]
 */
fun <T> readonlyTx(
  db: Database? = null,
  transactionIsolation: Int? = db?.transactionManager?.defaultIsolationLevel,
  statement: JdbcTransaction.() -> T,
): T =
  transaction(
    transactionIsolation = transactionIsolation,
    readOnly = true,
    db = db,
    statement = statement,
  )

/**
 * Executes a database transaction and measures its execution time.
 *
 * @param T the return type of the transaction
 * @param db the [Database] to use, or `null` for the default database
 * @param transactionIsolation the JDBC transaction isolation level
 * @param statement the transaction body to execute
 * @return a [TimedValue] containing the result and the elapsed duration
 */
fun <T> timedTransaction(
  db: Database? = null,
  transactionIsolation: Int? = db?.transactionManager?.defaultIsolationLevel,
  statement: JdbcTransaction.() -> T,
): TimedValue<T> =
  measureTimedValue {
    transaction(
      transactionIsolation = transactionIsolation,
      db = db,
    ) {
      statement()
    }
  }

/**
 * Executes a read-only database transaction and measures its execution time.
 *
 * @param T the return type of the transaction
 * @param db the [Database] to use, or `null` for the default database
 * @param transactionIsolation the JDBC transaction isolation level
 * @param statement the transaction body to execute
 * @return a [TimedValue] containing the result and the elapsed duration
 */
fun <T> timedReadOnlyTx(
  db: Database? = null,
  transactionIsolation: Int? = db?.transactionManager?.defaultIsolationLevel,
  statement: JdbcTransaction.() -> T,
): TimedValue<T> =
  measureTimedValue {
    readonlyTx(db, transactionIsolation) {
      statement()
    }
  }
