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
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private object UpsertUsersTable : Table("upsert_test_users") {
  val id = integer("id")
  val email = varchar("email", 100).uniqueIndex()
  val name = varchar("name", 100)
}

class UpsertStatementTests : StringSpec() {
  private lateinit var db: Database

  init {
    beforeSpec {
      db = Database.connect("jdbc:h2:mem:upsert_statement_tests;DB_CLOSE_DELAY=-1", user = "sa")
      transaction(db) {
        SchemaUtils.create(UpsertUsersTable)
      }
    }

    beforeTest {
      transaction(db) {
        UpsertUsersTable.deleteAll()
      }
    }

    afterSpec {
      transaction(db) {
        SchemaUtils.drop(UpsertUsersTable)
      }
      TransactionManager.closeAndUnregister(db)
    }

    "upsert with conflict index inserts a new row when no conflict exists" {
      transaction(db) {
        val conflictIndex = UpsertUsersTable.indices.single { it.unique }
        UpsertUsersTable.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
        }

        val row = UpsertUsersTable.selectAll().single()
        row[UpsertUsersTable.id] shouldBe 1
        row[UpsertUsersTable.email] shouldBe "alice@example.com"
        row[UpsertUsersTable.name] shouldBe "Alice"
      }
    }

    "upsert with conflict index updates the existing row on conflict" {
      transaction(db) {
        val conflictIndex = UpsertUsersTable.indices.single { it.unique }
        UpsertUsersTable.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
        }
        UpsertUsersTable.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice Updated"
        }

        UpsertUsersTable.selectAll().count() shouldBe 1L
        val row = UpsertUsersTable.selectAll().single()
        row[UpsertUsersTable.name] shouldBe "Alice Updated"
      }
    }
  }
}
