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

import com.github.pambrose.common.util.isNotNull
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.TransactionManager

inline fun <T : Table> T.upsert(
  conflictColumn: Column<*>? = null,
  conflictIndex: Index? = null,
  body: T.(UpsertStatement<Number>) -> Unit,
): UpsertStatement<Number> =
  UpsertStatement<Number>(this, conflictColumn, conflictIndex)
    .apply {
      body(this)
      execute(TransactionManager.current())
    }

class UpsertStatement<Key : Any>(
  table: Table,
  conflictColumn: Column<*>? = null,
  conflictIndex: Index? = null,
) : InsertStatement<Key>(table, false) {
  private val indexName: String
  private val indexColumns: List<Column<*>>

  init {
    when {
      conflictIndex.isNotNull() -> {
        indexName = conflictIndex.indexName
        indexColumns = conflictIndex.columns
      }

      conflictColumn.isNotNull() -> {
        indexName = conflictColumn.name
        indexColumns = listOf(conflictColumn)
      }

      else -> throw IllegalArgumentException()
    }
  }

  override fun prepareSQL(
    transaction: Transaction,
    prepared: Boolean,
  ): String =
    buildString {
      append(super.prepareSQL(transaction, prepared))
      append(" ON CONFLICT ON CONSTRAINT $indexName DO UPDATE SET ")
      values.keys
        .filter { it !in indexColumns }
        .joinTo(this) { "${transaction.identity(it)}=EXCLUDED.${transaction.identity(it)}" }
    }
}
