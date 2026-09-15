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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private object NullableColumnTestTable : Table("nullable_column_test_users") {
  val id = integer("id")
  val email = varchar("email", 100)
  val note = varchar("note", 100).nullable()
}

/**
 * A SQL NULL is a value, not a missing column, so index access and row rendering must handle it.
 */
class NullableColumnTests : StringSpec() {
  private lateinit var db: Database

  init {
    beforeSpec {
      db = Database.connect("jdbc:h2:mem:nullable_column_tests;DB_CLOSE_DELAY=-1", user = "sa")
      transaction(db) {
        SchemaUtils.create(NullableColumnTestTable)
        NullableColumnTestTable.insert {
          it[id] = 1
          it[email] = "alice@example.com"
          it[note] = null
        }
      }
    }

    afterSpec {
      transaction(db) {
        SchemaUtils.drop(NullableColumnTestTable)
      }
      TransactionManager.closeAndUnregister(db)
    }

    "a null column value is returned by index rather than throwing" {
      readonlyTx(db = db) {
        val row =
          NullableColumnTestTable
            .select(NullableColumnTestTable.id, NullableColumnTestTable.email, NullableColumnTestTable.note)
            .where { NullableColumnTestTable.id eq 1 }
            .single()

        row[0] shouldBe 1
        row[1] shouldBe "alice@example.com"
        row[2] shouldBe null
      }
    }

    "toRowString renders a null column as null" {
      readonlyTx(db = db) {
        val row = NullableColumnTestTable.selectAll().single()

        row.toRowString() shouldBe "1 - alice@example.com - null"
      }
    }

    "an index with no column still throws" {
      readonlyTx(db = db) {
        val row = NullableColumnTestTable.selectAll().single()

        val exception = shouldThrow<IllegalArgumentException> { row[99] }
        exception.message shouldBe "No value at index 99"
      }
    }
  }
}
