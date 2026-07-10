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

import io.github.oshai.kotlinlogging.KLogger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private object SqlLoggerTestTable : Table("sql_logger_test") {
  val name = varchar("name", 50)
}

class KotlinSqlLoggerTests : StringSpec() {
  private lateinit var db: Database

  init {
    beforeSpec {
      db = Database.connect("jdbc:h2:mem:kotlin_sql_logger_tests;DB_CLOSE_DELAY=-1", user = "sa")
      transaction(db) {
        SchemaUtils.create(SqlLoggerTestTable)
      }
    }

    afterSpec {
      transaction(db) {
        SchemaUtils.drop(SqlLoggerTestTable)
      }
      TransactionManager.closeAndUnregister(db)
    }

    "kotlin sql logger creation" {
      val sqlLogger = KotlinSqlLogger()
      sqlLogger shouldNotBe null
      sqlLogger.logger shouldBe ExposedUtils.logger
    }

    "kotlin sql logger with custom logger" {
      val customLogger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
      val sqlLogger = KotlinSqlLogger(customLogger)
      sqlLogger.logger shouldBe customLogger
    }

    "log writes the expanded sql statement at info level" {
      val messages = mutableListOf<String>()
      val mockLogger = mockk<KLogger>()
      every { mockLogger.info(any<() -> Any?>()) } answers {
        messages += firstArg<() -> Any?>().invoke().toString()
      }

      transaction(db) {
        addLogger(KotlinSqlLogger(mockLogger))
        SqlLoggerTestTable.insert {
          it[name] = "logged-value"
        }
      }

      verify(atLeast = 1) { mockLogger.info(any<() -> Any?>()) }
      messages.any { it.startsWith("SQL: ") && it.contains("INSERT", ignoreCase = true) } shouldBe true
      messages.any { it.contains("logged-value") } shouldBe true
    }
  }
}
