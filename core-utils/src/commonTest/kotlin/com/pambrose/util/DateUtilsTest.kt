package com.pambrose.util

import com.pambrose.common.util.DateUtils.abbrevDayOfWeek
import com.pambrose.common.util.DateUtils.age
import com.pambrose.common.util.DateUtils.parseToLocalDate
import com.pambrose.common.util.DateUtils.parseToLocalDateTime
import com.pambrose.common.util.DateUtils.parseToLocalTime
import com.pambrose.common.util.DateUtils.toAdjustedString
import com.pambrose.common.util.DateUtils.toCreated
import com.pambrose.common.util.DateUtils.toDashedYYYYMMDD
import com.pambrose.common.util.DateUtils.toFullDateString
import com.pambrose.common.util.DateUtils.toISO8601
import com.pambrose.common.util.DateUtils.toLogString
import com.pambrose.common.util.DateUtils.toMMDD
import com.pambrose.common.util.DateUtils.toMMDDYY
import com.pambrose.common.util.DateUtils.toMMDDYYYY
import com.pambrose.common.util.DateUtils.toMMDDYYYYHHMM
import com.pambrose.common.util.DateUtils.toUTCDateTime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

class DateUtilsTest : StringSpec() {
  init {
    "parseToLocalDate - parses ISO date" {
      "2024-03-15".parseToLocalDate() shouldBe LocalDate(2024, 3, 15)
    }

    "parseToLocalTime - parses ISO time" {
      "13:45:30".parseToLocalTime() shouldBe LocalTime(13, 45, 30)
    }

    "parseToLocalDateTime - parses ISO date-time" {
      "2024-03-15T08:30:45".parseToLocalDateTime() shouldBe
        LocalDateTime(2024, 3, 15, 8, 30, 45)
    }

    "toUTCDateTime - sets time to 23:59:00.001 of the same UTC day" {
      val date = LocalDate(2024, 6, 1)
      val utcDateTime = date.toUTCDateTime()
      // start-of-day + 1 day - 1 minute + 1 ms = 23:59:00.001 of the same day
      utcDateTime shouldBe LocalDateTime(2024, 6, 1, 23, 59, 0, 1_000_000)
    }

    "toISO8601 - appends Z and trims fractional seconds" {
      LocalDateTime(2024, 3, 15, 8, 30, 45).toISO8601() shouldBe "2024-03-15T08:30:45Z"
    }

    "toISO8601 - removes nanos when present" {
      LocalDateTime(2024, 3, 15, 8, 30, 45, 123_000_000).toISO8601() shouldBe "2024-03-15T08:30:45Z"
    }

    "abbrevDayOfWeek - LocalDate produces 3-letter capitalized day" {
      // 2024-01-08 is a Monday
      LocalDate(2024, 1, 8).abbrevDayOfWeek() shouldBe "Mon"
      LocalDate(2024, 1, 13).abbrevDayOfWeek() shouldBe "Sat"

      // 2026-04-29 is a Wednesday.
      val wed = LocalDateTime.parse("2026-04-29T10:00:00")
      wed.abbrevDayOfWeek() shouldHaveLength 3
      wed.abbrevDayOfWeek() shouldBe "Wed"
    }

    "abbrevDayOfWeek - LocalDateTime produces 3-letter capitalized day" {
      LocalDateTime(2024, 1, 10, 0, 0).abbrevDayOfWeek() shouldBe "Wed"
    }

    "toFullDateString - includes day, mm/dd/yy and time without a hardcoded zone label" {
      val ldt = LocalDateTime(2024, 3, 7, 9, 5, 7)
      ldt.toFullDateString() shouldBe "Thu 03/07/24 09:05:07"
      // The misleading hardcoded "PST" label (wrong during PDT) must no longer be emitted
      ldt.toFullDateString() shouldNotContain "PST"
    }

    "toLogString - includes mm/dd/yy time and ms without a hardcoded zone label" {
      // 12 ms is left-padded to a 3-digit millisecond field: "012"
      val ldt = LocalDateTime(2024, 3, 7, 9, 5, 7, 12_000_000)
      ldt.toLogString() shouldBe "03/07/24 09:05:07.012"
      ldt.toLogString() shouldNotContain "PST"
    }

    "toLogString - left-pads a single-digit millisecond value" {
      LocalDateTime(2024, 3, 7, 9, 5, 7, 5_000_000).toLogString() shouldBe "03/07/24 09:05:07.005"
    }

    "toLogString - keeps a three-digit millisecond value unpadded" {
      LocalDateTime(2024, 3, 7, 9, 5, 7, 123_000_000).toLogString() shouldBe "03/07/24 09:05:07.123"
    }

    "toLogString - renders a zero millisecond value as 000" {
      LocalDateTime(2024, 3, 7, 9, 5, 7, 0).toLogString() shouldBe "03/07/24 09:05:07.000"
    }

    "toMMDDYYYY - formats year as 4 digits" {
      LocalDate(2024, 1, 5).toMMDDYYYY() shouldBe "01/05/2024"
    }

    "toMMDDYY - formats year as 2 digits" {
      LocalDate(2024, 12, 31).toMMDDYY() shouldBe "12/31/24"
    }

    "toMMDD - omits year" {
      LocalDate(2024, 7, 4).toMMDD() shouldBe "07/04"
    }

    "toDashedYYYYMMDD - SQL-friendly format" {
      LocalDate(2024, 9, 8).toDashedYYYYMMDD() shouldBe "2024-09-08"
    }

    "toMMDDYYYYHHMM - includes hour and minute" {
      LocalDateTime(2024, 5, 1, 14, 7).toMMDDYYYYHHMM() shouldBe "05/01/2024 14:07"
    }

    "toMMDDYYYYHHMM - zero-pads a single-digit hour" {
      LocalDateTime(2024, 5, 1, 9, 5).toMMDDYYYYHHMM() shouldBe "05/01/2024 09:05"
    }

    "toCreated - wraps formatted date in (Created ...)" {
      val ldt = LocalDateTime(2024, 5, 1, 14, 7)
      ldt.toCreated() shouldBe "(Created 05/01/2024 14:07)"
    }

    "Instant.age - returns ZERO for null" {
      val nullInstant: Instant? = null
      nullInstant.age shouldBe Duration.ZERO
    }

    "Instant.age - returns positive duration for past instant" {
      val past = Clock.System.now() - 5.seconds
      val age = past.age
      (age >= 5.seconds) shouldBe true
    }

    "LocalDateTime.age - returns ZERO for null" {
      val nullDateTime: LocalDateTime? = null
      nullDateTime.age(TimeZone.UTC) shouldBe Duration.ZERO
    }

    "toAdjustedString - truncates duration to specified unit" {
      val d = 1.hours + 30.minutes + 45.seconds + 250.milliseconds
      d.toAdjustedString(DurationUnit.SECONDS) shouldEndWith "s"
      d.toAdjustedString(DurationUnit.MINUTES) shouldBe (1.hours + 30.minutes).toString()
      d.toAdjustedString(DurationUnit.HOURS) shouldBe 1.hours.toString()
      d.toAdjustedString(DurationUnit.MILLISECONDS) shouldBe d.inWholeMilliseconds.milliseconds.toString()
    }

    "toAdjustedString - DAYS unit produces day-rounded string" {
      val d = 49.hours
      d.toAdjustedString(DurationUnit.DAYS) shouldBe 2L.let { Duration.parse("2d") }.toString()
    }

    "toAdjustedString - unsupported unit throws" {
      shouldThrow<IllegalStateException> {
        2.hours.toAdjustedString(DurationUnit.NANOSECONDS)
      }
    }

    "TimeZone.of with bogus zone throws" {
      // Sanity for parse safety guards
      shouldThrow<IllegalTimeZoneException> { TimeZone.of("Not/AZone") }
    }
  }
}
