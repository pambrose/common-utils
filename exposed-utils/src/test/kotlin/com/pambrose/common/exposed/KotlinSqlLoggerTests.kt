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
import io.kotest.matchers.shouldNotBe

class KotlinSqlLoggerTests : StringSpec() {
  init {
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
  }
}
