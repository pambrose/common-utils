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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.exposed

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jodatime.JodaLocalDateTimeColumnType
import org.joda.time.DateTime

class CustomExprTests : StringSpec() {
  private lateinit var db: Database

  init {
    beforeSpec {
      db = Database.connect("jdbc:h2:mem:custom_expr_tests;DB_CLOSE_DELAY=-1", user = "sa")
    }

    afterSpec {
      TransactionManager.closeAndUnregister(db)
    }

    "each factory keeps the raw sql text and maps results as a Joda date-time" {
      listOf(customDateTimeConstant("NOW()"), dateTimeExpr("CURRENT_DATE")).forEach { expr ->
        expr.columnType.shouldBeInstanceOf<JodaLocalDateTimeColumnType>()
      }
      customDateTimeConstant("NOW()").text shouldBe "NOW()"
      dateTimeExpr("CURRENT_DATE").text shouldBe "CURRENT_DATE"
    }

    // The raw text is sent to H2 as written, and the result is read back through the Joda column type.
    "a date-time expression selects its value from the database" {
      val expr = dateTimeExpr("TIMESTAMP '2026-09-16 12:34:56'")

      val value = transaction(db) { Table.Dual.select(expr).single()[expr] }

      value shouldBe DateTime(2026, 9, 16, 12, 34, 56)
    }

    "a nullable date-time constant reads SQL NULL as null" {
      val expr = customDateTimeConstant("CAST(NULL AS TIMESTAMP)")

      transaction(db) { Table.Dual.select(expr).single()[expr] } shouldBe null
    }

    // LOCALTIMESTAMP is when the statement ran, so it falls between the clock readings taken around it.
    "LOCALTIMESTAMP reads back as the current local time" {
      val expr = dateTimeExpr("LOCALTIMESTAMP")

      // Whole seconds either side, in case either clock truncates.
      val before = DateTime.now().minusSeconds(1).millis
      val value = transaction(db) { Table.Dual.select(expr).single()[expr] }
      val after = DateTime.now().plusSeconds(1).millis

      value.millis shouldBeInRange before..after
    }

    "custom expr toQueryBuilder appends the raw sql text" {
      val builder = QueryBuilder(prepared = false)
      customDateTimeConstant("NOW()").toQueryBuilder(builder)
      builder.toString() shouldBe "NOW()"
    }

    "custom expr renders as its raw sql text" {
      dateTimeExpr("CURRENT_TIMESTAMP").toString() shouldBe "CURRENT_TIMESTAMP"
    }
  }
}
