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

package com.pambrose.common.util

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.security.SecureRandom
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number

/**
 * Holds the hostname and IP address of the local machine.
 *
 * @param hostName the local hostname
 * @param ipAddress the local IP address
 */
data class HostInfo(
  val hostName: String,
  val ipAddress: String,
)

/**
 * Lazily resolved [HostInfo] for the local machine.
 * Returns `"Unknown"` for both fields if the hostname cannot be determined.
 */
val hostInfo by lazy {
  try {
    val hostname = InetAddress.getLocalHost().hostName!!
    val address = InetAddress.getLocalHost().hostAddress!!
    // logger.debug { "Hostname: $hostname Address: $address" }
    HostInfo(hostname, address)
  } catch (e: UnknownHostException) {
    HostInfo("Unknown", "Unknown")
  }
}

/**
 * Suspends the current thread for the specified [Duration].
 *
 * @param sleepTime the duration to sleep
 */
fun sleep(sleepTime: Duration) = Thread.sleep(sleepTime.inWholeMilliseconds)

private val secureRandom = SecureRandom()

/**
 * Generates a cryptographically secure random alphanumeric string.
 *
 * @param length the length of the generated ID (default 10)
 * @param charPool the characters to choose from (default: a-z, A-Z, 0-9)
 * @return a random string of the specified length
 */
fun randomId(
  length: Int = 10,
  charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9'),
) = secureRandom.let { random ->
  (1..length)
    .map { random.nextInt(charPool.size) }
    .map { charPool[it] }
    .joinToString("")
}

/**
 * Repeatedly executes [block] with a sleep between each iteration.
 *
 * @param iterations the number of times to execute the block
 * @param sleepTime the duration to sleep between iterations (default 1 second)
 * @param block the action to invoke, receiving the iteration index and the start time in milliseconds
 */
fun repeatWithSleep(
  iterations: Int,
  sleepTime: Duration = 1.seconds,
  block: (count: Int, startMillis: Long) -> Unit,
) {
  val startMillis = System.currentTimeMillis()
  iterations repeat { i ->
    block(i, startMillis)
    sleep(sleepTime)
  }
}

/**
 * Returns `true` if this value is not null, with a Kotlin contract that smart-casts the receiver.
 *
 * @return `true` if non-null
 */
fun Any?.isNotNull(): Boolean {
  contract {
    returns(true) implies (this@isNotNull != null)
  }
  return this != null
}

/**
 * Returns `true` if this value is null, with a Kotlin contract that smart-casts the receiver on `false`.
 *
 * @return `true` if null
 */
fun Any?.isNull(): Boolean {
  contract {
    returns(false) implies (this@isNull != null)
  }
  return this == null
}

/**
 * Formats this [LocalDateTime] as a full date string, e.g., `"Mon 04/10/26 14:30:00 PST"`.
 *
 * Extension function on [LocalDateTime].
 *
 * @return the formatted date/time string
 */
fun LocalDateTime.toFullDateString(): String =
  "${abbrevDayOfWeek()} ${month.number.lpad(2)}/${day.lpad(2)}/${(year - 2000).lpad(2)} " +
    "${hour.lpad(2)}:${minute.lpad(2)}:${second.lpad(2)} PST"

/**
 * Left-pads this [Int] to the specified [width] with [padChar].
 *
 * Extension function on [Int].
 *
 * @param width the minimum width of the resulting string
 * @param padChar the character to pad with (default `'0'`)
 * @return the left-padded string
 */
fun Int.lpad(
  width: Int,
  padChar: Char = '0',
): String = toString().padStart(width, padChar)

/**
 * Right-pads this [Int] to the specified [width] with [padChar].
 *
 * Extension function on [Int].
 *
 * @param width the minimum width of the resulting string
 * @param padChar the character to pad with (default `'0'`)
 * @return the right-padded string
 */
fun Int.rpad(
  width: Int,
  padChar: Char = '0',
): String = toString().padEnd(width, padChar)

/**
 * Returns the abbreviated day-of-week name (e.g., `"Mon"`, `"Tue"`).
 *
 * Extension function on [LocalDateTime].
 */
fun LocalDateTime.abbrevDayOfWeek(): String = dayOfWeek.name.lowercase().capitalizeFirstChar().substring(0, 3)

/**
 * Capitalizes the first character of this [String].
 *
 * Extension function on [String].
 *
 * @return the string with the first character in title case
 */
fun String.capitalizeFirstChar(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

/**
 * Captures everything printed to [System.out] during execution of [block] and returns it as a [String].
 *
 * @param block the code to execute while capturing stdout
 * @return the captured stdout output
 */
fun captureStdout(block: () -> Unit): String {
  val originalOut = System.out
  val baos = ByteArrayOutputStream()
  System.setOut(PrintStream(baos))
  try {
    block()
  } finally {
    System.setOut(originalOut)
  }
  return baos.toString()
}

/** Miscellaneous utility functions. */
object MiscFuncs {
  private val logger = logger {}

  /**
   * Blocks until the specified TCP [port] becomes available, polling with a delay between attempts.
   *
   * @param port the TCP port to wait for
   * @param maxAttempts the maximum number of attempts (default 50)
   * @param delayMs the delay in milliseconds between attempts (default 200)
   */
  fun waitForPortAvailable(
    port: Int,
    maxAttempts: Int = 50,
    delayMs: Long = 200,
  ) {
    repeat(maxAttempts) {
      try {
        ServerSocket(port).use { return }
      } catch (_: Exception) {
        Thread.sleep(delayMs)
      }
    }
    logger.warn { "Port $port may not be available after ${maxAttempts * delayMs}ms" }
  }
}

/** Utility for reading classpath resource files. */
object ReadResources {
  /**
   * Reads the entire content of a classpath resource file as a [String].
   *
   * @param filename the resource file name
   * @return the file content
   * @throws IllegalArgumentException if the resource is not found
   */
  fun readResourceFile(filename: String): String {
    val classLoader = this::class.java.classLoader
    return classLoader.getResource(filename)?.readText()
      ?: throw IllegalArgumentException("Invalid file name: $filename")
  }
}
