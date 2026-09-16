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

import com.pambrose.common.util.ListUtils
import com.pambrose.common.util.captureStdout
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

// The formatting itself is covered on every platform by the common ListUtilsTests.
class ListUtilsJvmTests : StringSpec() {
  init {
    "listPrint prints the formatted list on its own line" {
      captureStdout { ListUtils.listPrint(["a", null, 1]) } shouldBe "[\"a\", null, 1]${System.lineSeparator()}"
    }

    "listPrint prints an empty list as empty brackets" {
      captureStdout { ListUtils.listPrint(emptyList<String>()) } shouldBe "[]${System.lineSeparator()}"
    }
  }
}
