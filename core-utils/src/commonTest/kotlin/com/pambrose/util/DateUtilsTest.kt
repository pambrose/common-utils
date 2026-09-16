package com.pambrose.util

import com.pambrose.common.util.DateUtils.abbrevDayOfWeek
import com.pambrose.common.util.DateUtils.age
import com.pambrose.common.util.DateUtils.localDateNow
import com.pambrose.common.util.DateUtils.localDateTimeNow
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
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldNotContain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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

    "toISO8601 - always includes seconds, even on a round minute" {
      LocalDateTime(2024, 3, 15, 8, 30, 0).toISO8601() shouldBe "2024-03-15T08:30:00Z"
      LocalDateTime(2024, 1, 1, 0, 0).toISO8601() shouldBe "2024-01-01T00:00:00Z"
      LocalDateTime(2024, 3, 15, 8, 30, 0, 500_000_000).toISO8601() shouldBe "2024-03-15T08:30:00Z"
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

    "toFullDateString(timeZone) - appends the fixed UTC offset" {
      val ldt = LocalDateTime(2024, 3, 7, 9, 5, 7)
      ldt.toFullDateString(UtcOffset(hours = -4).asTimeZone()) shouldBe "${ldt.toFullDateString()} -04:00"
    }

    "toFullDateString(timeZone) - renders a zero offset as Z" {
      val ldt = LocalDateTime(2024, 3, 7, 9, 5, 7)
      ldt.toFullDateString(TimeZone.UTC) shouldBe "${ldt.toFullDateString()} Z"
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

    "two-digit years wrap modulo 100 outside 2000-2099" {
      LocalDate(1999, 12, 31).toMMDDYY() shouldBe "12/31/99"
      LocalDate(2100, 1, 1).toMMDDYY() shouldBe "01/01/00"
      LocalDate(2005, 6, 7).toMMDDYY() shouldBe "06/07/05"
      LocalDateTime(1999, 12, 31, 23, 59, 58).toFullDateString() shouldBe "Fri 12/31/99 23:59:58"
      LocalDateTime(1999, 12, 31, 23, 59, 58, 7_000_000).toLogString() shouldBe "12/31/99 23:59:58.007"
    }

    "toMMDD - omits year" {
      LocalDate(2024, 7, 4).toMMDD() shouldBe "07/04"
    }

    "toDashedYYYYMMDD - SQL-friendly format" {
      LocalDate(2024, 9, 8).toDashedYYYYMMDD() shouldBe "2024-09-08"
    }

    // The four-digit year is padded like "%04d", so a negative year keeps its sign in front (it was "00-1").
    "four-digit year formats keep the sign of a negative year in front" {
      LocalDate(-1, 1, 1).toMMDDYYYY() shouldBe "01/01/-001"
      LocalDate(-1, 1, 1).toDashedYYYYMMDD() shouldBe "-001-01-01"
      LocalDate(5, 1, 1).toMMDDYYYY() shouldBe "01/01/0005"
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

    "Instant.age - returns the elapsed duration for a past instant" {
      val past = Clock.System.now() - 5.seconds
      val age = past.age
      withClue("age=$age") { (age in 5.seconds..(5.seconds + 1.minutes)) shouldBe true }
    }

    "LocalDateTime.age - returns ZERO for null" {
      val nullDateTime: LocalDateTime? = null
      nullDateTime.age(TimeZone.UTC) shouldBe Duration.ZERO
    }

    // The receiver is a wall-clock time in UTC-4. Read in that zone it is an hour old; read as UTC it is five.
    "LocalDateTime.age - interprets the date-time in the given zone" {
      val minus4 = UtcOffset(hours = -4).asTimeZone()
      val dateTime = (Clock.System.now() - 1.hours).toLocalDateTime(minus4)
      val inZone = dateTime.age(minus4)
      val asUtc = dateTime.age(TimeZone.UTC)
      withClue("inZone=$inZone") { (inZone in 1.hours..(1.hours + 1.minutes)) shouldBe true }
      withClue("asUtc=$asUtc") { (asUtc in 5.hours..(5.hours + 1.minutes)) shouldBe true }
    }

    // UTC+14 is ahead of every other zone, so if the zone argument were ignored the reading would be off by hours
    // (unless the host itself runs at UTC+14).
    "localDateNow and localDateTimeNow read the clock in the given zone" {
      val plus14 = UtcOffset(hours = 14).asTimeZone()
      val before = Clock.System.now()
      val date = localDateNow(plus14)
      val dateTime = localDateTimeNow(plus14)
      val after = Clock.System.now()

      date shouldBeIn setOf(before.toLocalDateTime(plus14).date, after.toLocalDateTime(plus14).date)
      val instant = dateTime.toInstant(plus14)
      withClue("instant=$instant, before=$before, after=$after") { (instant in before..after) shouldBe true }
    }

    // The default zone must resolve without a time-zone database, which JS and wasmJs do not bundle.
    "localDateNow and localDateTimeNow default to the system zone" {
      val zone = TimeZone.currentSystemDefault()
      val before = Clock.System.now()
      val date = localDateNow()
      val dateTime = localDateTimeNow()
      val after = Clock.System.now()

      date shouldBeIn setOf(before.toLocalDateTime(zone).date, after.toLocalDateTime(zone).date)
      val instant = dateTime.toInstant(zone)
      withClue("instant=$instant, before=$before, after=$after") { (instant in before..after) shouldBe true }
    }

    "toAdjustedString - truncates duration to specified unit" {
      val d = 1.hours + 30.minutes + 45.seconds + 250.milliseconds
      d.toAdjustedString(DurationUnit.MILLISECONDS) shouldBe "1h 30m 45.25s"
      d.toAdjustedString(DurationUnit.SECONDS) shouldBe "1h 30m 45s"
      d.toAdjustedString(DurationUnit.MINUTES) shouldBe "1h 30m"
      d.toAdjustedString(DurationUnit.HOURS) shouldBe "1h"
    }

    "toAdjustedString - DAYS unit produces day-rounded string" {
      49.hours.toAdjustedString(DurationUnit.DAYS) shouldBe "2d"
    }

    // The first two lines are the KDoc example.
    "toAdjustedString - defaults to whole seconds" {
      95.seconds.toAdjustedString() shouldBe "1m 35s"
      95.seconds.toAdjustedString(DurationUnit.MINUTES) shouldBe "1m"
      (95.seconds + 400.milliseconds).toAdjustedString() shouldBe "1m 35s"
    }

    "toAdjustedString - truncates a negative duration toward zero" {
      (-95).seconds.toAdjustedString(DurationUnit.MINUTES) shouldBe "-1m"
      (-1500).milliseconds.toAdjustedString() shouldBe "-1s"
    }

    "toAdjustedString - unsupported unit throws" {
      shouldThrow<IllegalStateException> {
        2.hours.toAdjustedString(DurationUnit.NANOSECONDS)
      }
    }
  }
}
