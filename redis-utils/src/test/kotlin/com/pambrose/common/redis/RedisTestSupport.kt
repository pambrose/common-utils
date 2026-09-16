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

package com.pambrose.common.redis

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.pambrose.common.redis.RedisUtils.withNonNullRedis
import com.pambrose.common.redis.RedisUtils.withRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedis
import com.pambrose.common.redis.RedisUtils.withSuspendingRedis
import io.kotest.matchers.nulls.shouldNotBeNull
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.thread
import org.slf4j.LoggerFactory
import redis.clients.jedis.RedisClient

/** The host the fake server binds to, and the host test URLs name. */
internal const val LOOPBACK = "127.0.0.1"

/**
 * Each member of the withRedis family, by name, as a call taking a URL and a block that needs a connected client.
 * The nullable variants fail the test if they pass `null` to the block.
 */
internal val withRedisVariants =
  listOf(
    variant("withRedis") { url, block -> withRedis(url) { block(it.shouldNotBeNull()) } },
    variant("withNonNullRedis") { url, block -> withNonNullRedis(url) { block(it) } },
    variant("withSuspendingRedis") { url, block -> withSuspendingRedis(url) { block(it.shouldNotBeNull()) } },
    variant("withSuspendingNonNullRedis") { url, block -> withSuspendingNonNullRedis(url) { block(it) } },
  )

private fun variant(
  name: String,
  call: suspend (url: String, block: (RedisClient) -> String) -> String?,
) = name to call

/** Runs [block] with a function that snapshots the events logged by the redis package in the meantime. */
internal inline fun <T> capturingRedisLogs(block: (logs: () -> List<ILoggingEvent>) -> T): T {
  val appender = ListAppender<ILoggingEvent>().apply { start() }
  val logger = LoggerFactory.getLogger(RedisUtils::class.java.packageName) as Logger
  logger.addAppender(appender)
  return try {
    block { synchronized(appender) { appender.list.toList() } }
  } finally {
    logger.detachAppender(appender)
  }
}

/**
 * A minimal Redis stand-in, so the real connect, ping and close path runs without a Redis server.
 *
 * It answers `PING` with `PONG`, and every other command with an unknown-command error. Jedis' connection
 * handshake treats that error as an old server: it falls back from `HELLO` to RESP2 and ignores the failed
 * `CLIENT SETINFO` calls.
 *
 * It binds [LOOPBACK] rather than every interface: on macOS, another app's `127.0.0.1` listener on the same port
 * would otherwise receive the test's loopback connections.
 */
internal class FakeRedisServer : AutoCloseable {
  private val serverSocket = ServerSocket(0, 0, InetAddress.getByName(LOOPBACK))

  val port: Int = serverSocket.localPort

  val url: String = "redis://$LOOPBACK:$port"

  /** Connections accepted so far. */
  val accepted = AtomicInt(0)

  /** Connections the client has not closed yet. */
  val open = AtomicInt(0)

  /** `PING` commands answered so far. */
  val pings = AtomicInt(0)

  private val acceptor =
    thread(isDaemon = true, name = "fake-redis-acceptor") {
      while (true) {
        val socket =
          try {
            serverSocket.accept()
          } catch (_: IOException) {
            break
          }
        accepted.incrementAndFetch()
        open.incrementAndFetch()
        thread(isDaemon = true, name = "fake-redis-connection") { serve(socket) }
      }
    }

  private fun serve(socket: Socket) {
    try {
      socket.use {
        val input = BufferedInputStream(it.getInputStream())
        val output = it.getOutputStream()
        while (true) {
          val command = readCommand(input) ?: break
          val name = command.firstOrNull().orEmpty()
          val reply =
            if (name.equals("PING", ignoreCase = true)) {
              pings.incrementAndFetch()
              "+PONG\r\n"
            } else {
              "-ERR unknown command '$name'\r\n"
            }
          output.write(reply.toByteArray())
          output.flush()
        }
      }
    } catch (_: IOException) {
      // The client went away mid-command; that still counts as closing the connection.
    } finally {
      open.decrementAndFetch()
    }
  }

  // Reads one command, sent as a RESP array of bulk strings, or returns null at the end of the stream.
  private fun readCommand(input: InputStream): List<String>? {
    val header = readLine(input) ?: return null
    check(header.startsWith("*")) { "Unexpected RESP header: $header" }
    return List(header.substring(1).toInt()) {
      val length = (readLine(input) ?: throw EOFException()).substring(1).toInt()
      val bytes = input.readNBytes(length)
      readLine(input) ?: throw EOFException()
      bytes.decodeToString()
    }
  }

  private fun readLine(input: InputStream): String? {
    val line = StringBuilder()
    while (true) {
      when (val c = input.read()) {
        -1 -> {
          return null
        }

        '\r'.code -> {
          input.read()
          return line.toString()
        }

        else -> {
          line.append(c.toChar())
        }
      }
    }
  }

  override fun close() {
    serverSocket.close()
    acceptor.join()
  }
}
