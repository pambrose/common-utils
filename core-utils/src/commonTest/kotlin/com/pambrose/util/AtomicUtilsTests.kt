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

import com.pambrose.common.util.AtomicUtils.criticalSection
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.concurrent.atomics.AtomicBoolean

class AtomicUtilsTests : StringSpec() {
  init {
    "criticalSection sets the flag while the block runs and clears it afterwards" {
      val flag = AtomicBoolean(false)
      flag.criticalSection { flag.load() } shouldBe true
      flag.load() shouldBe false
    }

    "criticalSection clears the flag when the block throws" {
      val flag = AtomicBoolean(false)
      shouldThrow<IllegalStateException> {
        flag.criticalSection { error("boom") }
      }
      flag.load() shouldBe false
    }

    // Bug #9: the block's result used to be discarded.
    "criticalSection returns the block's result" {
      val flag = AtomicBoolean(false)
      flag.criticalSection { 42 } shouldBe 42
      flag.criticalSection { "hello" } shouldBe "hello"
    }
  }
}
