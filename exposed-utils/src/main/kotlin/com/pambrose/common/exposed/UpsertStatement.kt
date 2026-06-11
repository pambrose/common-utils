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

import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.UpsertStatement
import org.jetbrains.exposed.v1.jdbc.upsert

/**
 * Convenience overload of Exposed's native [upsert] that takes a unique [Index] as the conflict target,
 * forwarding its columns as the `keys` of the underlying `ON CONFLICT (...)` clause. This keeps the named
 * index definitions in `PostgresTables.kt` as the single source of truth for conflict columns while using
 * Exposed's upsert directly.
 */
fun <T : Table> T.upsert(
  conflictIndex: Index,
  body: T.(UpsertStatement<Long>) -> Unit,
): UpsertStatement<Long> = upsert(keys = conflictIndex.columns.toTypedArray(), body = body)
