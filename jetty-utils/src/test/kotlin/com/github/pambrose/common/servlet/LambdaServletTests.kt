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

package com.github.pambrose.common.servlet

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

class LambdaServletTests : StringSpec() {
  init {
    "lambda servlet creation" {
      val servlet = LambdaServlet { "Hello, World!" }
      servlet shouldNotBe null
    }

    "lambda servlet with content type" {
      val servlet = LambdaServlet("application/json") { """{"status": "ok"}""" }
      servlet shouldNotBe null
    }

    "version servlet creation" {
      val servlet = VersionServlet("1.0.0")
      servlet shouldNotBe null
    }
  }
}
