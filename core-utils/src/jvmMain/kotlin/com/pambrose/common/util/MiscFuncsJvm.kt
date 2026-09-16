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
@file:JvmName("MiscFuncsKt")
@file:JvmMultifileClass

package com.pambrose.common.util

import io.github.oshai.kotlinlogging.KotlinLogging.logger
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
val hostInfo by lazy { resolveHostInfo() }

// The lookup is a parameter so tests can stand in for a host whose name does not resolve.
internal fun resolveHostInfo(lookup: () -> InetAddress = InetAddress::getLocalHost): HostInfo =
  try {
    lookup().let { HostInfo(it.hostName, it.hostAddress) }
  } catch (_: UnknownHostException) {
    HostInfo("Unknown", "Unknown")
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
    if (i < iterations - 1)
      sleep(sleepTime)
  }
}

/**
 * Captures everything printed to [System.out] during execution of [block] and returns it as a [String].
 *
 * Not thread-safe: this replaces the process-wide [System.out] while [block] runs, so output from other threads
 * is captured too, and concurrent calls interfere with each other. Output is encoded and decoded as UTF-8.
 *
 * @param block the code to execute while capturing stdout
 * @return the captured stdout output
 */
fun captureStdout(block: () -> Unit): String {
  val originalOut = System.out
  val baos = ByteArrayOutputStream()
  System.setOut(PrintStream(baos, false, Charsets.UTF_8))
  try {
    block()
  } finally {
    System.setOut(originalOut)
  }
  return baos.toString(Charsets.UTF_8)
}

/** Miscellaneous utility functions. */
object MiscFuncs {
  private const val MAX_PORT = 65_535
  private val logger = logger {}

  /**
   * Waits until the specified TCP [port] can be bound, polling with a delay between attempts.
   *
   * @param port the TCP port to wait for
   * @param maxAttempts the maximum number of attempts (default 50)
   * @param delayMs the delay in milliseconds between attempts (default 200)
   * @return `true` once the port is available, or `false` if it was still in use after [maxAttempts] attempts
   * @throws IllegalArgumentException if [port] is outside `0..65535`
   */
  fun waitForPortAvailable(
    port: Int,
    maxAttempts: Int = 50,
    delayMs: Long = 200,
  ): Boolean {
    // An invalid port can never be bound, so waiting for it would only report it as busy.
    require(port in 0..MAX_PORT) { "port must be in 0..$MAX_PORT but was $port" }
    repeat(maxAttempts) {
      try {
        ServerSocket(port).use { return true }
      } catch (_: IOException) {
        Thread.sleep(delayMs)
      }
    }
    logger.warn { "Port $port was still in use after ${maxAttempts * delayMs}ms" }
    return false
  }
}

/** Utility for reading classpath resource files. */
object ReadResources {
  /**
   * Reads the entire content of a classpath resource file as a [String].
   *
   * @param filename the resource file name
   * @param classLoader the classloader used to find [filename]; defaults to the thread context classloader
   * @return the file content
   * @throws IllegalArgumentException if the resource is not found
   */
  @JvmOverloads
  fun readResourceFile(
    filename: String,
    classLoader: ClassLoader = defaultResourceClassLoader(),
  ): String =
    classLoader.getResource(filename)?.readText()
      ?: throw IllegalArgumentException("Invalid file name: $filename")
}

/**
 * The classloader used to find resources by default: the thread context classloader, which in servlet
 * containers and plugin hosts can see application resources, falling back to core-utils' own classloader.
 */
internal fun defaultResourceClassLoader(): ClassLoader =
  Thread.currentThread().contextClassLoader ?: ReadResources::class.java.classLoader
