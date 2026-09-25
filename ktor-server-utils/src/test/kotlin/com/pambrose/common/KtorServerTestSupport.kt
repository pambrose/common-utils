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

package com.pambrose.common

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.net.Socket

private const val LOOPBACK = "127.0.0.1"

/**
 * Starts a CIO server running [module] on a loopback port the OS chooses, writes [request] to it over a raw socket,
 * and returns everything the server sends back. Ktor's client normalizes the requests it sends, so tests that need
 * exact request bytes, or pipelined requests, use this instead. The last request in [request] should carry
 * `Connection: close`, so the server closes the connection once it has answered.
 */
internal suspend fun rawHttpExchange(
  request: String,
  module: Application.() -> Unit,
): String {
  val server = embeddedServer(CIO, port = 0, host = LOOPBACK, module = module).start(wait = false)
  try {
    val port = server.engine.resolvedConnectors().first().port
    return Socket(LOOPBACK, port).use { socket ->
      socket.soTimeout = 10_000
      socket.getOutputStream().apply {
        write(request.toByteArray())
        flush()
      }
      socket.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
    }
  } finally {
    server.stop(0, 0)
  }
}
