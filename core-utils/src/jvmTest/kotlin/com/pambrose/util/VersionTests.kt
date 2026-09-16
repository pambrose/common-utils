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

import com.pambrose.common.util.Version
import com.pambrose.common.util.Version.Companion.buildDateTime
import com.pambrose.common.util.Version.Companion.buildString
import com.pambrose.common.util.Version.Companion.version
import com.pambrose.common.util.Version.Companion.versionDesc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime

@Version(version = "9.9.9", releaseDate = "2026-04-01", buildTime = 1_711_929_600_000)
private class Annotated

private class Bare

class VersionTests : StringSpec() {
  init {
    "version() returns the annotation value" {
      Annotated::class.version() shouldBe "9.9.9"
    }

    "version() returns Unknown when annotation is missing" {
      Bare::class.version() shouldBe "Unknown"
    }

    // buildTime is 2024-04-01T00:00:00Z, which is 17:00 the day before in America/Los_Angeles (PDT, UTC-7).
    "buildDateTime() is the build time in Los Angeles when annotated and null otherwise" {
      Annotated::class.buildDateTime() shouldBe LocalDateTime(2024, 3, 31, 17, 0)
      Bare::class.buildDateTime() shouldBe null
    }

    "buildString() returns formatted timestamp when annotated" {
      Annotated::class.buildString() shouldBe "Sun 03/31/24 17:00:00"
    }

    "buildString() returns Unknown when annotation is missing" {
      Bare::class.buildString() shouldBe "Unknown"
    }

    "versionDesc plain text contains version, release date and build date" {
      Annotated::class.versionDesc(asJson = false) shouldBe
        "Version: 9.9.9 Release Date: 2026-04-01 Build Date: Sun 03/31/24 17:00:00"
    }

    "versionDesc plain text falls back to Unknown for bare class" {
      // Formatting epoch 0 used to print a nonsense build date such as "Wed 12/31/-31 16:00:00".
      Bare::class.versionDesc(asJson = false) shouldBe "Version: Unknown Release Date: Unknown Build Date: Unknown"
    }

    "versionDesc JSON contains version, release_date and build_time" {
      Annotated::class.versionDesc(asJson = true) shouldBe
        """{"version":"9.9.9","release_date":"2026-04-01","build_time":"Sun 03/31/24 17:00:00"}"""
    }

    "versionDesc JSON falls back for bare class" {
      Bare::class.versionDesc(asJson = true) shouldBe
        """{"version":"Unknown","release_date":"Unknown","build_time":"Unknown"}"""
    }

    "versionDesc defaults to plain text output" {
      Annotated::class.versionDesc() shouldBe
        "Version: 9.9.9 Release Date: 2026-04-01 Build Date: Sun 03/31/24 17:00:00"
      Bare::class.versionDesc() shouldBe "Version: Unknown Release Date: Unknown Build Date: Unknown"
    }

    // The same formats, for callers that have the values rather than an annotated class.
    "plainStr and jsonStr format the given values" {
      Version.plainStr("1.0", "2025-01-01", 1_711_929_600_000) shouldBe
        "Version: 1.0 Release Date: 2025-01-01 Build Date: Sun 03/31/24 17:00:00"
      Version.jsonStr("1.0", "2025-01-01", 1_711_929_600_000) shouldBe
        """{"version":"1.0","release_date":"2025-01-01","build_time":"Sun 03/31/24 17:00:00"}"""
    }
  }
}
