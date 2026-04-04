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

package com.pambrose.common.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.types.shouldBeTypeOf

class GuavaFuncsTests : StringSpec() {
  init {
    "is windows type" {
      isWindows.shouldBeTypeOf<Boolean>()
    }

    "is mac type" {
      isMac.shouldBeTypeOf<Boolean>()
    }

    "is java6 type" {
      isJava6.shouldBeTypeOf<Boolean>()
      // Modern JDKs should not be Java 6
      isJava6.shouldBeFalse()
    }
  }
}
