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

package com.pambrose.common.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

class GuavaFuncsTests : StringSpec() {
  init {
    // The file separator is a second, independent sign of Windows.
    "isWindows matches the os.name property and the file separator" {
      isWindows shouldBe System.getProperty("os.name").startsWith("Windows")
      isWindows shouldBe (File.separatorChar == '\\')
    }

    "isMac matches the os.name property" {
      isMac shouldBe (System.getProperty("os.name") == "Mac OS X")
    }

    "no host is both Windows and a Mac" {
      (isWindows && isMac) shouldBe false
    }
  }
}
