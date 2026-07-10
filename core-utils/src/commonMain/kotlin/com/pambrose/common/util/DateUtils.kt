package com.pambrose.common.util

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.DurationUnit.DAYS
import kotlin.time.DurationUnit.HOURS
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.DurationUnit.MINUTES
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.Instant

/**
 * Date/time parsing, formatting, and arithmetic helpers shared across all Kotlin targets.
 *
 * Formatters render US-style `MM/DD` dates; the `*Now` helpers default to the system time zone
 * rather than assuming a region. Declared as an `object` so its extensions are brought in by
 * member import (e.g. `import com.pambrose.common.util.DateUtils.toMMDDYY`).
 */
object DateUtils {
  /**
   * Parses this ISO-8601 date string (e.g. `"2024-03-15"`) into a [LocalDate].
   *
   * Extension function on [String].
   *
   * @return the parsed [LocalDate]
   * @throws IllegalArgumentException if the text is not a valid ISO-8601 date
   */
  fun String.parseToLocalDate() = LocalDate.Formats.ISO.parse(this)

  /**
   * Parses this ISO-8601 time string (e.g. `"13:45:30"`) into a [LocalTime].
   *
   * Extension function on [String].
   *
   * @return the parsed [LocalTime]
   * @throws IllegalArgumentException if the text is not a valid ISO-8601 time
   */
  fun String.parseToLocalTime() = LocalTime.Formats.ISO.parse(this)

  /**
   * Parses this ISO-8601 date-time string (e.g. `"2024-03-15T08:30:45"`) into a [LocalDateTime].
   *
   * Extension function on [String].
   *
   * @return the parsed [LocalDateTime]
   * @throws IllegalArgumentException if the text is not a valid ISO-8601 date-time
   */
  fun String.parseToLocalDateTime() = LocalDateTime.Formats.ISO.parse(this)

  /** Returns the current moment from the system clock as an [Instant]. */
  fun instantNow(): Instant = Clock.System.now()

  /**
   * The current date in [timeZone] (the system default zone unless specified).
   *
   * The default avoids assuming a specific region; callers that need a fixed zone should pass one
   * (e.g. `TimeZone.of("America/Los_Angeles")`). Note that resolving a named zone on Kotlin/JS and
   * wasmJs requires the `@js-joda/timezone` npm package, which those consumers must add themselves.
   */
  fun localDateNow(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate = Clock.System.todayIn(timeZone)

  /**
   * The current date-time in [timeZone] (the system default zone unless specified).
   *
   * See [localDateNow] for the note on named zones under Kotlin/JS and wasmJs.
   */
  fun localDateTimeNow(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    instantNow().toLocalDateTime(timeZone)

  /**
   * Returns the last minute of this [LocalDate] in UTC, i.e. `23:59:00.001` of the same day.
   *
   * Useful as an inclusive end-of-day boundary. Extension function on [LocalDate].
   *
   * @return the date at `23:59:00.001` UTC as a [LocalDateTime]
   */
  fun LocalDate.toUTCDateTime(): LocalDateTime =
    atStartOfDayIn(UTC)
      .plus(1.days)
      .minus(1.minutes)
      .plus(1.milliseconds)
      .toLocalDateTime(UTC)

  /**
   * Formats this [LocalDateTime] as an ISO-8601 string with a trailing `Z`, dropping any
   * fractional-seconds component (e.g. `2024-03-15T08:30:45.123` becomes `"2024-03-15T08:30:45Z"`).
   *
   * The `Z` labels the value as UTC; no zone conversion is performed. Extension function on
   * [LocalDateTime].
   *
   * @return the ISO-8601 string
   */
  fun LocalDateTime.toISO8601(): String =
    toString().let { str ->
      (if (str.contains(".")) str.substringBefore(".") else str) + "Z"
    }

  /** Returns the three-letter, title-cased abbreviation of this [DayOfWeek] (e.g. `"Mon"`). */
  private fun DayOfWeek.abbrev(): String = name.lowercase().capitalizeFirstChar().substring(0, 3)

  /**
   * Returns the abbreviated day-of-week name (e.g., `"Mon"`, `"Tue"`).
   *
   * Extension function on [LocalDate].
   */
  fun LocalDate.abbrevDayOfWeek(): String = dayOfWeek.abbrev()

  /**
   * Returns the abbreviated day-of-week name (e.g., `"Mon"`, `"Tue"`).
   *
   * Extension function on [LocalDateTime].
   */
  fun LocalDateTime.abbrevDayOfWeek(): String = dayOfWeek.abbrev()

  /**
   * Formats this [LocalDateTime] as a full date string, e.g., `"Mon 04/10/26 14:30:00"`.
   *
   * Extension function on [LocalDateTime].
   *
   * @return the formatted date/time string
   */
  fun LocalDateTime.toFullDateString(): String =
    "${abbrevDayOfWeek()} ${month.number.lpad(2)}/${day.lpad(2)}/${(year - 2000).lpad(2)} " +
      "${hour.lpad(2)}:${minute.lpad(2)}:${second.lpad(2)}"

  /**
   * Formats this [LocalDateTime] as a log-friendly timestamp with milliseconds, e.g.
   * `"04/10/26 14:30:45.123"` (`MM/DD/YY HH:MM:SS.mmm`).
   *
   * Extension function on [LocalDateTime].
   *
   * @return the formatted timestamp string
   */
  fun LocalDateTime.toLogString(): String =
    "${month.number.lpad(2)}/${day.lpad(2)}/${(year - 2000).lpad(2)} ${hour.lpad(2)}:${
      minute.lpad(2)
    }:${second.lpad(2)}.${(nanosecond / 1000000).lpad(3)}"

  /**
   * Formats this [LocalDate] as `MM/DD/YYYY`, e.g. `"04/10/2026"`.
   *
   * Extension function on [LocalDate].
   *
   * @return the formatted date string
   */
  fun LocalDate.toMMDDYYYY(): String = "${month.number.lpad(2)}/${day.lpad(2)}/${year.lpad(4)}"

  /**
   * Formats this [LocalDate] as `MM/DD/YY` (two-digit year), e.g. `"04/10/26"`.
   *
   * Extension function on [LocalDate].
   *
   * @return the formatted date string
   */
  fun LocalDate.toMMDDYY(): String = "${month.number.lpad(2)}/${day.lpad(2)}/${(year - 2000).lpad(2)}"

  /**
   * Formats this [LocalDate] as `MM/DD`, e.g. `"04/10"`.
   *
   * Extension function on [LocalDate].
   *
   * @return the formatted date string
   */
  fun LocalDate.toMMDD(): String = "${month.number.lpad(2)}/${day.lpad(2)}"

  /**
   * Formats this [LocalDate] as ISO-style `YYYY-MM-DD`, e.g. `"2026-04-10"`.
   *
   * Extension function on [LocalDate].
   *
   * @return the formatted date string
   */
  fun LocalDate.toDashedYYYYMMDD(): String = "${year.lpad(4)}-${month.number.lpad(2)}-${day.lpad(2)}"

  /**
   * Formats this [LocalDateTime] as `MM/DD/YYYY HH:MM`, e.g. `"04/10/2026 14:30"`.
   *
   * Extension function on [LocalDateTime].
   *
   * @return the formatted date/time string
   */
  fun LocalDateTime.toMMDDYYYYHHMM(): String =
    "${month.number.lpad(2)}/${day.lpad(2)}/${year.lpad(4)} ${hour.lpad(2)}:${minute.lpad(2)}"

  /**
   * Wraps [toMMDDYYYYHHMM] in a `"(Created …)"` label, e.g. `"(Created 04/10/2026 14:30)"`.
   *
   * Extension function on [LocalDateTime].
   *
   * @return the parenthesized "created" string
   */
  fun LocalDateTime.toCreated(): String = "(Created ${toMMDDYYYYHHMM()})"

  /**
   * The elapsed [Duration] from this instant until now, or [Duration.ZERO] if the receiver is null.
   *
   * Extension property on a nullable [Instant].
   */
  val Instant?.age get() = if (this != null) instantNow() - this else Duration.ZERO

  /**
   * The elapsed [Duration] from this date-time until now, interpreting the receiver in [timeZone],
   * or [Duration.ZERO] if the receiver is null.
   *
   * Extension function on a nullable [LocalDateTime].
   *
   * @param timeZone the zone used to convert this local date-time to an [Instant]
   * @return the age, or [Duration.ZERO] when the receiver is null
   */
  fun LocalDateTime?.age(timeZone: TimeZone): Duration = this?.toInstant(timeZone)?.age ?: Duration.ZERO

  /**
   * Truncates this [Duration] down to whole [unit]s and returns its [Duration.toString] form.
   *
   * For example, `95.seconds.toAdjustedString(MINUTES)` is `"1m"` while `95.seconds.toAdjustedString()`
   * (the default [DurationUnit.SECONDS]) is `"1m 35s"`.
   *
   * @param unit the coarsest unit to retain; defaults to [DurationUnit.SECONDS]
   * @return the truncated duration formatted by [Duration.toString]
   * @throws IllegalStateException if [unit] is finer than [DurationUnit.MILLISECONDS]
   *   (i.e. microseconds or nanoseconds)
   */
  fun Duration.toAdjustedString(unit: DurationUnit = SECONDS): String =
    when (unit) {
      MILLISECONDS -> inWholeMilliseconds.milliseconds
      SECONDS -> inWholeSeconds.seconds
      MINUTES -> inWholeMinutes.minutes
      HOURS -> inWholeHours.hours
      DAYS -> inWholeDays.days
      else -> error("Unsupported duration unit: $unit")
    }.toString()
}
