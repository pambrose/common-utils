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
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.sql.Connection
import kotlin.time.Duration
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.transactionManager

private object ExposedUtilsTestTable : Table("exposed_utils_test_users") {
  val id = integer("id")
  val email = varchar("email", 100)
  val name = varchar("name", 100)

  // Holds SQL NULL in the seeded row: a null value is a value, not a missing column.
  val note = varchar("note", 100).nullable()
}

// The isolation level the transaction was given, and the one its JDBC connection reports.
private fun JdbcTransaction.isolationLevels() =
  transactionIsolation to (connection.connection as Connection).transactionIsolation

class ExposedUtilsTests : StringSpec() {
  private lateinit var db: Database

  init {
    beforeSpec {
      db = Database.connect("jdbc:h2:mem:exposed_utils_tests;DB_CLOSE_DELAY=-1", user = "sa")
      transaction(db) {
        SchemaUtils.create(ExposedUtilsTestTable)
        ExposedUtilsTestTable.insert {
          it[id] = 1
          it[email] = "alice@example.com"
          it[name] = "Alice"
          it[note] = null
        }
      }
    }

    afterSpec {
      transaction(db) {
        SchemaUtils.drop(ExposedUtilsTestTable)
      }
      TransactionManager.closeAndUnregister(db)
    }

    "result row get by index returns values in select order" {
      readonlyTx(db = db) {
        val row =
          ExposedUtilsTestTable
            .select(ExposedUtilsTestTable.id, ExposedUtilsTestTable.email, ExposedUtilsTestTable.name)
            .where { ExposedUtilsTestTable.id eq 1 }
            .single()
        row[0] shouldBe 1
        row[1] shouldBe "alice@example.com"
        row[2] shouldBe "Alice"
      }
    }

    "a null column value is returned by index rather than throwing" {
      readonlyTx(db = db) {
        val row =
          ExposedUtilsTestTable
            .select(ExposedUtilsTestTable.id, ExposedUtilsTestTable.note)
            .where { ExposedUtilsTestTable.id eq 1 }
            .single()
        row[0] shouldBe 1
        row[1] shouldBe null
      }
    }

    "result row get with unknown index throws IllegalArgumentException" {
      readonlyTx(db = db) {
        val row = ExposedUtilsTestTable.selectAll().single()
        val exception = shouldThrow<IllegalArgumentException> { row[99] }
        exception.message shouldBe "No value at index 99"
      }
    }

    "toRowString joins column values with dashes" {
      readonlyTx(db = db) {
        val row =
          ExposedUtilsTestTable
            .select(ExposedUtilsTestTable.id, ExposedUtilsTestTable.email, ExposedUtilsTestTable.name)
            .where { ExposedUtilsTestTable.id eq 1 }
            .single()
        row.toRowString() shouldBe "1 - alice@example.com - Alice"
      }
    }

    "toRowString renders a null column as null" {
      readonlyTx(db = db) {
        val row = ExposedUtilsTestTable.selectAll().single()
        row.toRowString() shouldBe "1 - alice@example.com - Alice - null"
      }
    }

    // Rolled back, so the other tests still see only the seeded row.
    "toRowString leaves out a column whose value is empty" {
      transaction(db) {
        ExposedUtilsTestTable.insert {
          it[id] = 2
          it[email] = "bob@example.com"
          it[name] = ""
        }
        val row =
          ExposedUtilsTestTable
            .select(ExposedUtilsTestTable.id, ExposedUtilsTestTable.name, ExposedUtilsTestTable.email)
            .where { ExposedUtilsTestTable.id eq 2 }
            .single()
        row.toRowString() shouldBe "2 - bob@example.com"
        rollback()
      }
    }

    // H2 ignores Connection.setReadOnly, so a write would still succeed; what can be checked is the read-only flag
    // each helper gives the transaction, which Exposed passes on to the driver.
    "readonlyTx and timedReadOnlyTx run read-only transactions, and timedTransaction does not" {
      readonlyTx(db = db) { readOnly } shouldBe true
      timedReadOnlyTx(db = db) { readOnly }.value shouldBe true
      timedTransaction(db = db) { readOnly }.value shouldBe false
    }

    // H2 does apply the isolation level, so the connection reports it as well as the transaction.
    "each helper applies the given isolation level to its transaction and connection" {
      val serializable = Connection.TRANSACTION_SERIALIZABLE
      db.transactionManager.defaultIsolationLevel shouldNotBe serializable
      val expected = serializable to serializable

      readonlyTx(db, serializable) { isolationLevels() } shouldBe expected
      timedTransaction(db, serializable) { isolationLevels() }.value shouldBe expected
      timedReadOnlyTx(db, serializable) { isolationLevels() }.value shouldBe expected
    }

    "each helper defaults to the database's isolation level" {
      val default = db.transactionManager.defaultIsolationLevel
      val expected = default to default

      readonlyTx(db) { isolationLevels() } shouldBe expected
      timedTransaction(db) { isolationLevels() }.value shouldBe expected
      timedReadOnlyTx(db) { isolationLevels() }.value shouldBe expected
    }

    "an exception thrown inside timedTransaction propagates and rolls back its changes" {
      val exception =
        shouldThrow<IllegalStateException> {
          timedTransaction(db = db) {
            ExposedUtilsTestTable.insert {
              it[id] = 3
              it[email] = "carol@example.com"
              it[name] = "Carol"
            }
            ExposedUtilsTestTable.selectAll().count() shouldBe 2L
            error("simulated failure")
          }
        }

      exception.message shouldBe "simulated failure"
      readonlyTx(db = db) { ExposedUtilsTestTable.selectAll().count() } shouldBe 1L
    }

    "readonlyTx with default database returns the statement result" {
      val count = readonlyTx { ExposedUtilsTestTable.selectAll().count() }
      count shouldBe 1L
    }

    "timedTransaction with explicit db returns result and non-negative duration" {
      val timed =
        timedTransaction(db = db) {
          ExposedUtilsTestTable.selectAll().count()
        }
      timed.value shouldBe 1L
      timed.duration shouldBeGreaterThanOrEqualTo Duration.ZERO
    }

    "timedTransaction with default database returns the statement result" {
      val timed = timedTransaction { ExposedUtilsTestTable.selectAll().count() }
      timed.value shouldBe 1L
      timed.duration shouldBeGreaterThanOrEqualTo Duration.ZERO
    }

    "timedReadOnlyTx with explicit db returns result and non-negative duration" {
      val timed =
        timedReadOnlyTx(db = db) {
          ExposedUtilsTestTable.selectAll().count()
        }
      timed.value shouldBe 1L
      timed.duration shouldBeGreaterThanOrEqualTo Duration.ZERO
    }

    "timedReadOnlyTx with default database returns the statement result" {
      val timed = timedReadOnlyTx { ExposedUtilsTestTable.selectAll().count() }
      timed.value shouldBe 1L
      timed.duration shouldBeGreaterThanOrEqualTo Duration.ZERO
    }
  }
}
