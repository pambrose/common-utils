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
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #3: readonlyTx, timedTransaction and timedReadOnlyTx threw NullPointerException when db was null.
    // Before the fix, the default isolation level was read through db!!.transactionManager.
    // After the fix, it is db?.transactionManager?.defaultIsolationLevel, which is null for a null db, so Exposed
    // picks both the database and its isolation level.

    // Exposed resolves a null db to the default database or, failing that, to the last one registered. The
    // missing-database error below therefore needs every other spec to have unregistered its database, which each
    // does in afterSpec; this makes that precondition explicit instead of silent.
    beforeTest {
      withClue("a database is still registered, so a null db would resolve to it") {
        TransactionManager.primaryDatabase shouldBe null
      }
    }

    "each helper reports a missing database with Exposed's error rather than a NullPointerException" {
      listOf<Pair<String, () -> Any?>>(
        "readonlyTx" to { readonlyTx(db = null) { } },
        "timedTransaction" to { timedTransaction(db = null) { } },
        "timedReadOnlyTx" to { timedReadOnlyTx(db = null) { } },
      ).forEach { (name, call) ->
        withClue(name) {
          shouldThrow<IllegalStateException> { call() }.message shouldStartWith "No database specified"
        }
      }
    }

    "a null db resolves to the registered database" {
      val db = Database.connect("jdbc:h2:mem:null_db_resolution_tests;DB_CLOSE_DELAY=-1", user = "sa")
      try {
        readonlyTx(db = null) { this.db } shouldBeSameInstanceAs db
        timedTransaction(db = null) { this.db }.value shouldBeSameInstanceAs db
        timedReadOnlyTx(db = null) { this.db }.value shouldBeSameInstanceAs db
      } finally {
        TransactionManager.closeAndUnregister(db)
      }
    }
  }
}
