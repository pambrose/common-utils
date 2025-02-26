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

import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.joda.time.DateTime

fun customDateTimeConstant(text: String) = CustomExpr<DateTime?>(text, DateColumnType(true))

fun dateTimeExpr(str: String): CustomExpr<DateTime> = CustomExpr(str, DateColumnType(true))

open class CustomExpr<T>(
  val text: String,
  columnType: IColumnType<T & Any>,
) : Function<T>(columnType) {
  override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit =
    queryBuilder {
      append(text)
    }
}
