package com.pambrose.util

import com.pambrose.common.util.DateUtils.toFullDateString
import io.kotest.core.spec.style.StringSpec
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
  }
}
