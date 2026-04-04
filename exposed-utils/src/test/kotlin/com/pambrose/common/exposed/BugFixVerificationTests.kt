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
import io.kotest.matchers.types.shouldNotBeInstanceOf

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #3: readonlyTx/timedTransaction/timedReadOnlyTx threw NPE when db=null
    // Before fix: db.transactionManager used non-null assertion on nullable db
    // After fix: db?.transactionManager?.defaultIsolationLevel ?: TRANSACTION_REPEATABLE_READ

    "readonly tx with null db does not throw NPE" {
      // Should throw an Exposed exception (no database configured), NOT a NullPointerException
      val exception = shouldThrow<Exception> {
        readonlyTx(db = null) { }
      }
      exception.shouldNotBeInstanceOf<NullPointerException>()
    }

    "timed transaction with null db does not throw NPE" {
      val exception = shouldThrow<Exception> {
        timedTransaction(db = null) { }
      }
      exception.shouldNotBeInstanceOf<NullPointerException>()
    }

    "timed read only tx with null db does not throw NPE" {
      val exception = shouldThrow<Exception> {
        timedReadOnlyTx(db = null) { }
      }
      exception.shouldNotBeInstanceOf<NullPointerException>()
    }
  }
}
