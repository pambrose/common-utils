/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.pambrose.common.exposed

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.transactionManager
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

object ExposedUtils {
  internal val logger = KotlinLogging.logger {}
}

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

operator fun ResultRow.get(index: Int) =
  fieldIndex.filter { it.value == index }.map { this[it.key] }.firstOrNull()
    ?: throw IllegalArgumentException("No value at index $index")

fun ResultRow.toRowString() =
  fieldIndex.values.map { this[it].toString() }.filter { it.isNotEmpty() }.joinToString(" - ")

fun <T> readonlyTx(
  db: Database? = null,
  transactionIsolation: Int = db.transactionManager.defaultIsolationLevel,
  statement: Transaction.() -> T,
): T =
  transaction(
    transactionIsolation = transactionIsolation,
    readOnly = true,
    db = db,
    statement = statement,
  )

fun <T> timedTransaction(
  db: Database? = null,
  transactionIsolation: Int = db.transactionManager.defaultIsolationLevel,
  statement: Transaction.() -> T,
): TimedValue<T> =
  measureTimedValue {
    transaction(
      transactionIsolation = transactionIsolation,
      db = db,
    ) {
      statement()
    }
  }

fun <T> timedReadOnlyTx(
  db: Database? = null,
  transactionIsolation: Int = db.transactionManager.defaultIsolationLevel,
  statement: Transaction.() -> T,
): TimedValue<T> =
  measureTimedValue {
    readonlyTx(db, transactionIsolation) {
      statement()
    }
  }
