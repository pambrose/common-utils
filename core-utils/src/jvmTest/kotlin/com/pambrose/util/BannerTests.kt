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
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.net.URLClassLoader

private val logger = KotlinLogging.logger {}

class BannerTests : StringSpec() {
  init {
    // test-banner.txt is "\n\nfirst\nmiddle blank above and below\n\nlast\n\n": the outer blank lines go, the one
    // in the middle stays (indented like the others), and the result is framed by two newlines on each side.
    "banner trims leading and trailing blank lines, preserves middle blanks" {
      getBanner("test-banner.txt", logger) shouldBe
        "\n\n     first\n     middle blank above and below\n     \n     last\n\n"
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

    "banner is found through the thread context classloader" {
      // In a servlet container or plugin host the application's resources are visible only to the context
      // classloader, not to the loader that holds kotlin-logging.
      val dir = tempdir("banner-loader")
      dir.resolve("context-only-banner.txt").writeText("\ncontext banner\n")

      withIsolatedContextClassLoader(dir) {
        getBanner("context-only-banner.txt", logger) shouldContain "     context banner"
      }
    }

    "banner can be loaded through an explicit classloader" {
      val dir = tempdir("banner-explicit")
      dir.resolve("explicit-banner.txt").writeText("explicit banner")

      URLClassLoader(arrayOf(dir.toURI().toURL()), null).use { loader ->
        getBanner("explicit-banner.txt", loader) shouldContain "     explicit banner"
      }
    }

    "banner without a logger defaults to the thread context classloader" {
      getBanner("test-banner.txt") shouldContain "     first"
    }

    "banner throws when an explicit classloader cannot find the file" {
      URLClassLoader(arrayOf(), null).use { empty ->
        shouldThrow<IllegalArgumentException> { getBanner("test-banner.txt", empty) }
      }
    }

    // Threads started by native code can have no context classloader; core-utils' own loader is used then.
    "banner without a logger falls back when there is no context classloader" {
      withoutContextClassLoader {
        getBanner("test-banner.txt") shouldContain "     first"
      }
    }
  }
}
