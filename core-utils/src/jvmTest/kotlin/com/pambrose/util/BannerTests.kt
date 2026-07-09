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

package com.pambrose.util

import com.pambrose.common.util.getBanner
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotEndWith
import io.kotest.matchers.string.shouldNotStartWith

private val logger = KotlinLogging.logger {}

class BannerTests : StringSpec() {
  init {
    "banner trims leading and trailing blank lines, preserves middle blanks" {
      val result = getBanner("test-banner.txt", logger)

      result shouldContain "     first"
      result shouldContain "     middle blank above and below"
      result shouldContain "     last"

      val body = result.removePrefix("\n\n").removeSuffix("\n\n")

      body.lines().first() shouldBe "     first"
      body.lines().last() shouldBe "     last"

      body shouldNotStartWith " \n"
      body shouldNotEndWith "\n     "
    }

    "banner is idempotent across calls" {
      val a = getBanner("test-banner.txt", logger)
      val b = getBanner("test-banner.txt", logger)
      a shouldBe b
    }

    "banner throws when file is missing" {
      shouldThrow<IllegalArgumentException> {
        getBanner("nonexistent-banner.txt", logger)
      }
    }
  }
}
