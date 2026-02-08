@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.exposed

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.types.shouldNotBeInstanceOf
import org.junit.jupiter.api.Test

class BugFixVerificationTests {
  // Bug #3: readonlyTx/timedTransaction/timedReadOnlyTx threw NPE when db=null
  // Before fix: db.transactionManager used non-null assertion on nullable db
  // After fix: db?.transactionManager?.defaultIsolationLevel ?: TRANSACTION_REPEATABLE_READ

  @Test
  fun readonlyTxWithNullDbDoesNotThrowNPE() {
    // Should throw an Exposed exception (no database configured), NOT a NullPointerException
    val exception = shouldThrow<Exception> {
      readonlyTx(db = null) { }
    }
    exception.shouldNotBeInstanceOf<NullPointerException>()
  }

  @Test
  fun timedTransactionWithNullDbDoesNotThrowNPE() {
    val exception = shouldThrow<Exception> {
      timedTransaction(db = null) { }
    }
    exception.shouldNotBeInstanceOf<NullPointerException>()
  }

  @Test
  fun timedReadOnlyTxWithNullDbDoesNotThrowNPE() {
    val exception = shouldThrow<Exception> {
      timedReadOnlyTx(db = null) { }
    }
    exception.shouldNotBeInstanceOf<NullPointerException>()
  }
}
