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

package com.pambrose.time

import com.pambrose.common.time.format
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import java.util.concurrent.TimeUnit.DAYS
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Golden-master equivalence guard for [com.pambrose.common.time.format]. [oldFormat] is a verbatim
 * copy of the original implementation; every assertion compares the live `format` against it across
 * a wide range of durations. This pins `format`'s exact, byte-for-byte behavior (including the known
 * Long.MIN_VALUE/-INFINITE overflow quirk), so any future change to the output becomes a deliberate,
 * visible diff against this oracle.
 */
class DurationFormatEquivalenceTests : StringSpec() {
  // Verbatim copy of the ORIGINAL implementation, used as the behavior oracle.
  private fun oldFormat(
    d: Duration,
    includeMillis: Boolean,
  ): String {
    val negative = d.isNegative()
    val diff = kotlin.math.abs(d.inWholeMilliseconds)
    val day = MILLISECONDS.toDays(diff)
    val dayMillis = DAYS.toMillis(day)
    val hr = MILLISECONDS.toHours(diff - dayMillis)
    val hrMillis = HOURS.toMillis(hr)
    val min = MILLISECONDS.toMinutes(diff - dayMillis - hrMillis)
    val minMillis = MINUTES.toMillis(min)
    val sec = MILLISECONDS.toSeconds(diff - dayMillis - hrMillis - minMillis)
    val secMillis = SECONDS.toMillis(sec)
    val ms = MILLISECONDS.toMillis(diff - dayMillis - hrMillis - minMillis - secMillis)
    val prefix = if (negative) "-" else ""
    return if (includeMillis)
      String.format(Locale.ROOT, "$prefix%d:%02d:%02d:%02d.%03d", day, hr, min, sec, ms)
    else
      String.format(Locale.ROOT, "$prefix%d:%02d:%02d:%02d", day, hr, min, sec)
  }

  init {
    // Hand-picked edge cases (and the negatives of the finite ones) checked against the oracle.
    val edgeCases: List<Duration> =
      buildList {
        add(Duration.ZERO)
        add(1.microseconds)
        add(999.microseconds) // sub-ms, truncates to 0
        add(1.milliseconds)
        add(5.milliseconds)
        add(999.milliseconds)
        add(1.seconds)
        add(59.seconds)
        add(1.minutes)
        add(59.minutes)
        add(1.minutes + 30.seconds)
        add(1.hours)
        add(23.hours)
        add(2.hours + 30.minutes + 45.seconds)
        add(1.days)
        add(2.days + 12.hours + 30.minutes + 15.seconds)
        add(3.days + 14.hours + 25.minutes + 36.seconds + 789.milliseconds)
        add(100.days + 23.hours + 59.minutes + 59.seconds + 999.milliseconds)
        add(Duration.INFINITE) // clamp/INFINITE path
        add(-Duration.INFINITE) // Long.MIN_VALUE overflow path
        add(Long.MAX_VALUE.milliseconds) // clamps to INFINITE
        add(Long.MIN_VALUE.milliseconds) // clamps to -INFINITE
      }
    val all = edgeCases + edgeCases.filter { it.isFinite() }.map { -it }

    "format matches the original oracle for all edge cases (both flags)" {
      all.forEach { d ->
        d.format(false) shouldBe oldFormat(d, false)
        d.format(true) shouldBe oldFormat(d, true)
      }
    }

    "format matches the original oracle for a large seeded random set (both flags)" {
      val rnd = Random(424242L)
      repeat(50_000) {
        val d = rnd.nextLong().milliseconds // full Long range, incl. negatives and clamping
        d.format(false) shouldBe oldFormat(d, false)
        d.format(true) shouldBe oldFormat(d, true)
      }
      // Dense sweep of small magnitudes around each unit boundary.
      for (base in longArrayOf(0, 1000, 60_000, 3_600_000, 86_400_000)) {
        for (delta in -2L..2L) {
          val d = (base + delta).milliseconds
          d.format(false) shouldBe oldFormat(d, false)
          d.format(true) shouldBe oldFormat(d, true)
        }
      }
    }
  }
}
