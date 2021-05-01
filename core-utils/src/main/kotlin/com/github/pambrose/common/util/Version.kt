/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose.common.util

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Version(val version: String, val date: String) {
  companion object {
    internal val jsonStr = { version: String, date: String -> """{"Version": "$version", "Release Date": "$date"}""" }
    internal val plainStr = { version: String, date: String -> "Version: $version Release Date: $date" }
    private const val unknown = "unknown"

    fun KClass<*>.versionDesc(asJson: Boolean = false): String =
      this.findAnnotation<Version>()
        ?.run { if (asJson) jsonStr(version, date) else plainStr(version, date) }
        ?: if (asJson) jsonStr(unknown, unknown) else plainStr(unknown, unknown)
  }
}