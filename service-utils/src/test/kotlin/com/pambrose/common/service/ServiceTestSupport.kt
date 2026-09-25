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

package com.pambrose.common.service

import com.pambrose.common.concurrent.GenericIdleService
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * The host test servers bind to when a test sends them requests.
 *
 * A server bound to every interface is not safe for that on macOS: with `SO_REUSEADDR`, another app can bind
 * `127.0.0.1` to the same port, and the more specific listener then receives every loopback connection.
 */
internal const val LOOPBACK = "127.0.0.1"

internal fun httpGet(
  port: Int,
  path: String,
  headers: Map<String, String> = emptyMap(),
): HttpResponse<String> =
  HttpClient.newHttpClient().send(
    HttpRequest
      .newBuilder(URI("http://$LOOPBACK:$port$path"))
      .timeout(java.time.Duration.ofSeconds(10))
      .apply { headers.forEach { (name, value) -> header(name, value) } }
      .build(),
    HttpResponse.BodyHandlers.ofString(),
  )

/** A listener holding a port on [LOOPBACK], so a server configured with that host and port fails to bind. */
internal fun occupiedLoopbackPort() = ServerSocket(0, 0, InetAddress.getByName(LOOPBACK))

/**
 * Fails unless nothing listens on [port] of [host] (`null` for every interface) any more.
 *
 * It binds the same address the server used, because macOS lets a wildcard and a `127.0.0.1` listener share a port,
 * so a probe on the other address would succeed while the server still holds the port. Port 0 is rejected, since
 * binding it always succeeds.
 */
internal fun shouldBeReleased(
  port: Int,
  host: String? = LOOPBACK,
) {
  port shouldBeGreaterThan 0
  ServerSocket().use { it.bind(InetSocketAddress(host?.let(InetAddress::getByName), port)) }
}

/** Starts the service, runs [block], and stops the service even when [block] fails. */
internal inline fun GenericIdleService.whileRunning(block: () -> Unit) {
  startSync()
  try {
    block()
  } finally {
    stopSync()
  }
}
