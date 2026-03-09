/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.exposed

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CustomExprTests : StringSpec() {
  init {
    "custom expr creation" {
      val expr = customDateTimeConstant("NOW()")
      expr shouldNotBe null
      expr.text shouldBe "NOW()"
    }

    "date time expr creation" {
      val expr = dateTimeExpr("CURRENT_TIMESTAMP")
      expr shouldNotBe null
      expr.text shouldBe "CURRENT_TIMESTAMP"
    }

    "custom expr with different text" {
      val expr1 = customDateTimeConstant("NOW()")
      val expr2 = customDateTimeConstant("CURRENT_DATE")

      expr1.text shouldBe "NOW()"
      expr2.text shouldBe "CURRENT_DATE"
    }
  }
}
