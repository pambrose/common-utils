package com.pambrose.util

import com.pambrose.common.util.DateUtils.toFullDateString
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

// Named-zone (IANA) behavior is JVM-only here: java.time ships the tz database, whereas commonTest
// runs on JS/wasmJs where named zones would need @js-joda/timezone. These verify that the offset
// appended by toFullDateString(timeZone) is DST-aware for a real zone.
class DateUtilsJvmTests : StringSpec() {
  init {
    "toFullDateString(timeZone) reflects standard time (EST, -05:00) in winter" {
      val newYork = TimeZone.of("America/New_York")
      LocalDateTime(2026, 1, 15, 9, 0, 0).toFullDateString(newYork) shouldEndWith " -05:00"
    }

    "toFullDateString(timeZone) reflects daylight time (EDT, -04:00) in summer" {
      val newYork = TimeZone.of("America/New_York")
      LocalDateTime(2026, 7, 15, 9, 0, 0).toFullDateString(newYork) shouldEndWith " -04:00"
    }

    // On 2026-03-08 New York's clocks jump from 02:00 to 03:00, so 02:30 never happens. kotlinx-datetime moves a
    // time in the gap forward by the gap's length, which lands in daylight time. The wall-clock part is printed
    // as given.
    "toFullDateString(timeZone) uses daylight time for a time in the spring-forward gap" {
      val newYork = TimeZone.of("America/New_York")
      LocalDateTime(2026, 3, 8, 2, 30, 0).toFullDateString(newYork) shouldBe "Sun 03/08/26 02:30:00 -04:00"
    }

    // On 2026-11-01 New York's clocks fall back from 02:00 to 01:00, so 01:30 happens twice. kotlinx-datetime
    // picks the earlier instant, which is still in daylight time.
    "toFullDateString(timeZone) uses the earlier (daylight) offset for a time in the fall-back overlap" {
      val newYork = TimeZone.of("America/New_York")
      LocalDateTime(2026, 11, 1, 1, 30, 0).toFullDateString(newYork) shouldBe "Sun 11/01/26 01:30:00 -04:00"
    }
  }
}
