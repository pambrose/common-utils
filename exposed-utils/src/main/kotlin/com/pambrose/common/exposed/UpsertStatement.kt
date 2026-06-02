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

import com.pambrose.common.util.isNotNull
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Performs an UPSERT (INSERT ... ON CONFLICT DO UPDATE) on this [Table].
 *
 * Extension function on [Table]. Exactly one of [conflictColumn] or [conflictIndex] must be provided
 * to identify the unique constraint for conflict detection.
 *
 * @param T the table type
 * @param conflictColumn the unique column to use for conflict detection
 * @param conflictIndex the unique index to use for conflict detection
 * @param body the insert body specifying column values
 * @return the executed [UpsertStatement]
 */
inline fun <T : Table> T.upsert(
  conflictColumn: Column<*>? = null,
  conflictIndex: Index? = null,
  body: T.(UpsertStatement<Number>) -> Unit,
): UpsertStatement<Number> =
  UpsertStatement<Number>(this, conflictColumn, conflictIndex)
    .apply {
      body(this)
      toExecutable().execute(TransactionManager.current())
    }
// ** DO NOT DELETE **
// {
//  val stmt =
//    UpsertStatement<Number>(this, conflictColumn, conflictIndex)
//      .apply { body(this) }
//  InsertBlockingExecutable(stmt).execute(TransactionManager.current())
//  return stmt
// }

/**
 * An [InsertStatement] that appends a PostgreSQL `ON CONFLICT ... DO UPDATE SET` clause.
 *
 * Generates SQL that inserts a row and, on conflict with the specified column or index constraint,
 * updates all non-conflict columns to their `EXCLUDED` values — or emits `DO NOTHING` when every
 * inserted column belongs to the conflict key, since there is then nothing to update.
 *
 * @param Key the auto-generated key type
 * @param table the target table
 * @param conflictColumn the unique column for conflict detection (mutually exclusive with [conflictIndex])
 * @param conflictIndex the unique index for conflict detection (mutually exclusive with [conflictColumn])
 * @throws IllegalArgumentException if neither [conflictColumn] nor [conflictIndex] is provided
 */
class UpsertStatement<Key : Any>(
  table: Table,
  conflictColumn: Column<*>? = null,
  conflictIndex: Index? = null,
) : InsertStatement<Key>(table, false) {
  private val indexColumns: List<Column<*>> =
    when {
      conflictIndex.isNotNull() -> conflictIndex.columns
      conflictColumn.isNotNull() -> listOf(conflictColumn)
      else -> throw IllegalArgumentException("Either conflictColumn or conflictIndex must be provided")
    }

  @OptIn(InternalApi::class)
  override fun prepareSQL(
    transaction: Transaction,
    prepared: Boolean,
  ): String =
    buildString {
      append(super.prepareSQL(transaction, prepared))
      // Use the column-inference form `ON CONFLICT (cols)` rather than `ON CONFLICT ON CONSTRAINT`:
      // it resolves the arbiter from the conflict columns and works for both a unique column and a
      // unique index, whereas ON CONSTRAINT requires an actual constraint name.
      append(" ON CONFLICT ")
      indexColumns.joinTo(this, separator = ", ", prefix = "(", postfix = ")") { transaction.identity(it) }

      val updateColumns = values.keys.filter { it !in indexColumns }
      if (updateColumns.isEmpty()) {
        // Every inserted column is part of the conflict key, so there is nothing to update.
        // `DO UPDATE SET` with no assignments is invalid SQL; `DO NOTHING` is the correct no-op.
        append(" DO NOTHING")
      } else {
        append(" DO UPDATE SET ")
        updateColumns.joinTo(this) { "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}" }
      }
    }
}
