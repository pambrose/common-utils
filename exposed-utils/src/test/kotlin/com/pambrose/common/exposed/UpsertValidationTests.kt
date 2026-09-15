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
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private object UpsertValidationPeople : Table("upsert_validation_people") {
  val id = integer("id")
  val email = varchar("email", 100).uniqueIndex()
  val name = varchar("name", 100).index()
  val nickname = varchar("nickname", 100)
}

private object UpsertValidationOther : Table("upsert_validation_other") {
  val code = varchar("code", 50).uniqueIndex()
}

/**
 * A non-unique index, or one belonging to another table, produces a conflict target the database rejects at
 * runtime; catching it up front gives a usable message. The overload also has to pass Exposed's own upsert
 * options through.
 */
class UpsertValidationTests : StringSpec() {
  private lateinit var db: Database

  init {
    beforeSpec {
      db = Database.connect("jdbc:h2:mem:upsert_validation_tests;DB_CLOSE_DELAY=-1", user = "sa")
      transaction(db) {
        SchemaUtils.create(UpsertValidationPeople, UpsertValidationOther)
      }
    }

    beforeTest {
      transaction(db) {
        UpsertValidationPeople.deleteAll()
        UpsertValidationOther.deleteAll()
      }
    }

    afterSpec {
      transaction(db) {
        SchemaUtils.drop(UpsertValidationPeople, UpsertValidationOther)
      }
      TransactionManager.closeAndUnregister(db)
    }

    "upsert rejects a non-unique index" {
      transaction(db) {
        val nonUnique = UpsertValidationPeople.indices.single { !it.unique }

        val exception =
          shouldThrow<IllegalArgumentException> {
            UpsertValidationPeople.upsert(nonUnique) {
              it[id] = 1
              it[email] = "alice@example.com"
              it[name] = "Alice"
              it[nickname] = "Al"
            }
          }
        exception.message shouldContain "unique"
      }
    }

    "upsert rejects an index belonging to another table" {
      transaction(db) {
        val otherTableIndex = UpsertValidationOther.indices.single { it.unique }

        val exception =
          shouldThrow<IllegalArgumentException> {
            UpsertValidationPeople.upsert(otherTableIndex) {
              it[id] = 1
              it[email] = "alice@example.com"
              it[name] = "Alice"
              it[nickname] = "Al"
            }
          }
        exception.message shouldContain "upsert_validation_people"
      }
    }

    "upsert forwards onUpdate so only the listed columns change" {
      transaction(db) {
        val conflictIndex = UpsertValidationPeople.indices.single { it.unique }
        UpsertValidationPeople.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
          it[nickname] = "Al"
        }

        UpsertValidationPeople.upsert(
          conflictIndex,
          onUpdate = { it[UpsertValidationPeople.nickname] = "Updated" },
        ) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Ignored"
          it[nickname] = "Ignored"
        }

        val row = UpsertValidationPeople.selectAll().single()
        row[UpsertValidationPeople.nickname] shouldBe "Updated"
        row[UpsertValidationPeople.name] shouldBe "Alice"
      }
    }

    "upsert forwards onUpdateExclude so the excluded column keeps its value" {
      transaction(db) {
        val conflictIndex = UpsertValidationPeople.indices.single { it.unique }
        UpsertValidationPeople.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
          it[nickname] = "Al"
        }

        UpsertValidationPeople.upsert(
          conflictIndex,
          onUpdateExclude = [UpsertValidationPeople.nickname],
        ) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice Updated"
          it[nickname] = "Ignored"
        }

        val row = UpsertValidationPeople.selectAll().single()
        row[UpsertValidationPeople.name] shouldBe "Alice Updated"
        row[UpsertValidationPeople.nickname] shouldBe "Al"
      }
    }

    "upsert still inserts and updates through the unique index" {
      transaction(db) {
        val conflictIndex = UpsertValidationPeople.indices.single { it.unique }
        UpsertValidationPeople.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
          it[nickname] = "Al"
        }
        UpsertValidationPeople.upsert(conflictIndex) {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice Again"
          it[nickname] = "Ali"
        }

        UpsertValidationPeople.selectAll().count() shouldBe 1L
        UpsertValidationPeople.selectAll().single()[UpsertValidationPeople.name] shouldBe "Alice Again"
      }
    }
  }
}
