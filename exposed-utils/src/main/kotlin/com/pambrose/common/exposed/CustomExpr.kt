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

import org.jetbrains.exposed.v1.core.Function
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.jodatime.JodaLocalDateTimeColumnType
import org.joda.time.DateTime

/**
 * Creates a nullable [DateTime] [CustomExpr] from the given SQL [text].
 *
 * @param text the raw SQL expression text (e.g., `"NOW()"`)
 * @return a [CustomExpr] typed as nullable [DateTime]
 */
fun customDateTimeConstant(text: String) = CustomExpr<DateTime?>(text, JodaLocalDateTimeColumnType())

/**
 * Creates a non-null [DateTime] [CustomExpr] from the given SQL [str].
 *
 * @param str the raw SQL expression text
 * @return a [CustomExpr] typed as [DateTime]
 */
fun dateTimeExpr(str: String): CustomExpr<DateTime> = CustomExpr(str, JodaLocalDateTimeColumnType())

/**
 * An Exposed [Function] that embeds raw SQL text as a typed expression in queries.
 *
 * @param T the Kotlin type this expression evaluates to
 * @property text the raw SQL expression text
 * @param columnType the Exposed column type used for result mapping
 */
open class CustomExpr<T>(
  val text: String,
  columnType: IColumnType<T & Any>,
) : Function<T>(columnType) {
  override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit =
    queryBuilder {
      append(text)
    }
}
