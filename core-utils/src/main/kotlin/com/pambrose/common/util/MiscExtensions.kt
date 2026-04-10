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
@file:JvmName("MiscUtils")
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.util

import java.io.PrintWriter
import java.io.StringWriter

/**
 * Converts this [Throwable]'s stack trace to a [String].
 *
 * Extension property on [Throwable].
 */
val Throwable.stackTraceAsString: String
  get() {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    printStackTrace(pw)
    return sw.toString()
  }

/**
 * Returns the simple class name of this object, or `"None"` if unavailable (e.g., anonymous classes).
 *
 * Extension property on any non-null type.
 */
val <T : Any> T.simpleClassName: String
  get() = this::class.simpleName ?: "None"

/**
 * Joins the elements of this [Iterable] into a comma-separated string.
 *
 * Extension function on [Iterable].
 *
 * @return a string with elements separated by `", "`
 */
fun <T> Iterable<T>.toCsv() = joinToString(", ")
