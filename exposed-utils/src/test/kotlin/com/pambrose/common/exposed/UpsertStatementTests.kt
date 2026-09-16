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
import io.kotest.matchers.string.shouldContain
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private object UpsertUsersTable : Table("upsert_test_users") {
  // Given no keys, Exposed falls back to the primary key (there is none) and then to the first unique index, which
  // is this one. The tests conflict on the email index instead, so they fail if the index they pass is dropped.
  val id = integer("id").uniqueIndex()
  val email = varchar("email", 100).uniqueIndex()

  // Indexed but not unique: not a usable conflict target.
  val name = varchar("name", 100).index()
  val nickname = varchar("nickname", 100).nullable()
}

private val emailIndex get() = UpsertUsersTable.indices.single { it.columns == [UpsertUsersTable.email] }

// A second table, so an index from the wrong table can be passed as a conflict target.
private object UpsertOtherTable : Table("upsert_test_other") {
  val code = varchar("code", 50).uniqueIndex()
}

class UpsertStatementTests : StringSpec() {
  private lateinit var db: Database

  init {
    beforeSpec {
      db = Database.connect("jdbc:h2:mem:upsert_statement_tests;DB_CLOSE_DELAY=-1", user = "sa")
      transaction(db) {
        SchemaUtils.create(UpsertUsersTable, UpsertOtherTable)
      }
    }

    beforeTest {
      transaction(db) {
        UpsertUsersTable.deleteAll()
      }
    }

    afterSpec {
      transaction(db) {
        SchemaUtils.drop(UpsertUsersTable, UpsertOtherTable)
      }
      TransactionManager.closeAndUnregister(db)
    }

    "upsert with conflict index inserts a new row when no conflict exists" {
      transaction(db) {
        val conflictIndex = emailIndex
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
        val conflictIndex = emailIndex
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

    // Only the email index matches here. An upsert on the id index, Exposed's fallback, would find no row with id 2,
    // insert one, and violate the unique email index.
    "upsert matches rows on the given index, not on the first unique index" {
      transaction(db) {
        UpsertUsersTable.upsert(emailIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
        }
        UpsertUsersTable.upsert(emailIndex) {
          it[id] = 2
          it[email] = "alice@example.com"
          it[name] = "Alice Renumbered"
        }

        val row = UpsertUsersTable.selectAll().single()
        row[UpsertUsersTable.id] shouldBe 2
        row[UpsertUsersTable.name] shouldBe "Alice Renumbered"
      }
    }

    // H2 builds UPSERT from MERGE, which cannot take a WHERE clause, so a forwarded one is rejected.
    "upsert forwards where to Exposed" {
      transaction(db) {
        val exception =
          shouldThrow<UnsupportedByDialectException> {
            UpsertUsersTable.upsert(emailIndex, where = { UpsertUsersTable.name eq "Alice" }) {
              it[id] = 1
              it[email] = "alice@example.com"
              it[name] = "Alice"
            }
          }
        exception.message shouldContain "MERGE implementation of UPSERT doesn't support single WHERE clause"
      }
    }

    // A non-unique index, or one from another table, is a conflict target the database rejects at runtime.

    "upsert rejects a non-unique index" {
      transaction(db) {
        val nonUnique = UpsertUsersTable.indices.single { !it.unique }

        val exception =
          shouldThrow<IllegalArgumentException> {
            UpsertUsersTable.upsert(nonUnique) {
              it[id] = 1
              it[email] = "alice@example.com"
              it[name] = "Alice"
            }
          }
        exception.message shouldContain "unique"
      }
    }

    "upsert rejects an index belonging to another table" {
      transaction(db) {
        val otherTableIndex = UpsertOtherTable.indices.single { it.unique }

        val exception =
          shouldThrow<IllegalArgumentException> {
            UpsertUsersTable.upsert(otherTableIndex) {
              it[id] = 1
              it[email] = "alice@example.com"
              it[name] = "Alice"
            }
          }
        exception.message shouldContain "upsert_test_users"
      }
    }

    "upsert forwards onUpdate so only the listed columns change" {
      transaction(db) {
        val conflictIndex = emailIndex
        UpsertUsersTable.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
          it[nickname] = "Al"
        }

        UpsertUsersTable.upsert(conflictIndex, onUpdate = { it[UpsertUsersTable.nickname] = "Updated" }) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Ignored"
          it[nickname] = "Ignored"
        }

        val row = UpsertUsersTable.selectAll().single()
        row[UpsertUsersTable.nickname] shouldBe "Updated"
        row[UpsertUsersTable.name] shouldBe "Alice"
      }
    }

    "upsert forwards onUpdateExclude so the excluded column keeps its value" {
      transaction(db) {
        val conflictIndex = emailIndex
        UpsertUsersTable.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
          it[nickname] = "Al"
        }

        UpsertUsersTable.upsert(conflictIndex, onUpdateExclude = [UpsertUsersTable.nickname]) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice Updated"
          it[nickname] = "Ignored"
        }

        val row = UpsertUsersTable.selectAll().single()
        row[UpsertUsersTable.name] shouldBe "Alice Updated"
        row[UpsertUsersTable.nickname] shouldBe "Al"
      }
    }
  }
}
