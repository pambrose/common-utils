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

package com.pambrose.common.util

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.time.Instant.Companion.fromEpochMilliseconds
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Runtime annotation for embedding version metadata on a class.
 *
 * Apply this annotation to a class to store its version, release date, and build timestamp.
 * Use the companion extension functions on [KClass] to retrieve and format this information.
 *
 * @param version the semantic version string (e.g., `"2.8.0"`)
 * @param releaseDate the release date string (e.g., `"2026-04-10"`)
 * @param buildTime the build timestamp in epoch milliseconds
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Version(
  val version: String,
  val releaseDate: String,
  val buildTime: Long,
) {
  companion object {
    private const val UNKNOWN = "Unknown"
    private const val ZONE_ID = "America/Los_Angeles"

    /** The timezone used for formatting build date/time strings. */
    val TIME_ZONE: TimeZone = TimeZone.of(ZONE_ID)

    private fun buildDateTime(buildTime: Long) = fromEpochMilliseconds(buildTime).toLocalDateTime(TIME_ZONE)

    private fun buildDateTimeStr(buildTime: Long) = buildDateTime(buildTime).toFullDateString()

    /** Lambda that produces a JSON string containing version, release date, and build time. */
    val jsonStr = { version: String, buildDate: String, buildTime: Long ->
      buildJsonObject {
        put("version", version)
        put("release_date", buildDate)
        put("build_time", buildDateTimeStr(buildTime))
      }.toString()
    }

    /** Lambda that produces a plain-text string containing version, release date, and build time. */
    val plainStr = { version: String, buildDate: String, buildTime: Long ->
      "Version: $version Release Date: $buildDate Build Date: ${buildDateTimeStr(buildTime)}"
    }

    /**
     * Returns the formatted build date/time string from the [Version] annotation on this [KClass],
     * or `"Unknown"` if the annotation is not present.
     */
    fun KClass<*>.buildString() = findAnnotation<Version>()?.run { buildDateTimeStr(buildTime) } ?: UNKNOWN

    /**
     * Returns the build [LocalDateTime] from the [Version] annotation on this [KClass],
     * or `null` if the annotation is not present.
     */
    fun KClass<*>.buildDateTime() = findAnnotation<Version>()?.run { buildDateTime(buildTime) }

    /**
     * Returns the version string from the [Version] annotation on this [KClass],
     * or `"Unknown"` if the annotation is not present.
     */
    fun KClass<*>.version() = findAnnotation<Version>()?.run { version } ?: UNKNOWN

    /**
     * Returns a description string containing version, release date, and build time
     * from the [Version] annotation on this [KClass].
     *
     * @param asJson if `true`, returns JSON format; otherwise returns plain text
     * @return the version description string, or a default "Unknown" description if not annotated
     */
    fun KClass<*>.versionDesc(asJson: Boolean = false): String =
      findAnnotation<Version>()
        ?.run {
          if (asJson)
            jsonStr(version, releaseDate, buildTime)
          else
            plainStr(version, releaseDate, buildTime)
        }
        ?: if (asJson) jsonStr(UNKNOWN, UNKNOWN, 0) else plainStr(UNKNOWN, UNKNOWN, 0)
  }
}
