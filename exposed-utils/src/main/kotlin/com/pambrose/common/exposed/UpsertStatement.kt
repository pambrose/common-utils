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

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.UpsertBuilder
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Convenience overload of Exposed's native [upsert] that takes a unique [Index] as the conflict target,
 * forwarding its columns as the `keys` of the underlying `ON CONFLICT (...)` clause. A named index definition
 * therefore stays the single source of truth for the conflict columns.
 *
 * [onUpdate], [onUpdateExclude] and [where] are passed straight through to Exposed's `upsert`. How much of
 * that each database supports is dialect-specific: the MERGE-based statement H2 uses, for example, rejects
 * [where].
 *
 * @param T the table type
 * @param conflictIndex the unique index whose columns form the conflict target; it must belong to this table
 * @param onUpdate the columns to set when a row already exists; defaults to updating every inserted column
 * @param onUpdateExclude the columns to leave untouched when a row already exists
 * @param where a condition limiting which existing rows are updated
 * @param body assigns the values to insert
 * @return the executed [UpsertStatement]
 * @throws IllegalArgumentException if [conflictIndex] is not unique, or belongs to a different table, either
 *   of which the database would reject at runtime
 */
fun <T : Table> T.upsert(
  conflictIndex: Index,
  onUpdate: (UpsertBuilder.(UpdateStatement) -> Unit)? = null,
  onUpdateExclude: List<Column<*>>? = null,
  where: (() -> Op<Boolean>)? = null,
  body: T.(UpsertStatement<Long>) -> Unit,
): UpsertStatement<Long> {
  require(conflictIndex.unique) {
    "Conflict index ${conflictIndex.indexName} must be unique to be used as an upsert conflict target"
  }
  require(conflictIndex.table == this) {
    "Conflict index ${conflictIndex.indexName} belongs to ${conflictIndex.table.tableName}, not $tableName"
  }

  return upsert(
    keys = conflictIndex.columns.toTypedArray(),
    onUpdate = onUpdate,
    onUpdateExclude = onUpdateExclude,
    where = where,
    body = body,
  )
}
