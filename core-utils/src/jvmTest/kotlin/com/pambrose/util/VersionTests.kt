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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

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

    "buildDateTime() is non-null when annotated and null otherwise" {
      Annotated::class.buildDateTime().shouldNotBeNull()
      Bare::class.buildDateTime() shouldBe null
    }

    "buildString() returns formatted timestamp when annotated" {
      val s = Annotated::class.buildString()
      s shouldContain "PST"
    }

    "buildString() returns Unknown when annotation is missing" {
      Bare::class.buildString() shouldBe "Unknown"
    }

    "versionDesc plain text contains version and release date" {
      val desc = Annotated::class.versionDesc(asJson = false)
      desc shouldContain "Version: 9.9.9"
      desc shouldContain "Release Date: 2026-04-01"
      desc shouldContain "Build Date:"
    }

    "versionDesc plain text falls back to Unknown for bare class" {
      val desc = Bare::class.versionDesc(asJson = false)
      desc shouldContain "Version: Unknown"
      desc shouldContain "Release Date: Unknown"
    }

    "versionDesc JSON contains version and release_date keys" {
      val json = Annotated::class.versionDesc(asJson = true)
      json shouldContain "\"version\":\"9.9.9\""
      json shouldContain "\"release_date\":\"2026-04-01\""
      json shouldContain "\"build_time\""
    }

    "versionDesc JSON falls back for bare class" {
      val json = Bare::class.versionDesc(asJson = true)
      json shouldContain "\"version\":\"Unknown\""
      json shouldContain "\"release_date\":\"Unknown\""
    }
  }
}
