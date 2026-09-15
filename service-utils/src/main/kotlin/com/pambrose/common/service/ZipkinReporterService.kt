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

import brave.Tracing
import com.pambrose.common.concurrent.GenericIdleService
import com.pambrose.common.concurrent.genericServiceListener
import com.pambrose.common.dsl.GuavaDsl.toStringElements
import com.pambrose.common.dsl.ZipkinDsl.tracing
import com.google.common.util.concurrent.MoreExecutors
import io.github.oshai.kotlinlogging.KotlinLogging
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.BytesMessageSender
import zipkin2.reporter.brave.ZipkinSpanHandler
import zipkin2.reporter.okhttp3.OkHttpSender
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * A Guava [GenericIdleService] that manages a Zipkin span reporter lifecycle.
 *
 * Creates an [OkHttpSender] and [AsyncReporter] for sending trace spans to a Zipkin server,
 * and provides a factory method for creating [Tracing] instances bound to this reporter. Stopping the
 * service sends the spans still queued before closing the reporter.
 *
 * @property defaultServiceName The service name [newTracing] uses when none is given.
 */
class ZipkinReporterService internal constructor(
  private val url: String,
  val defaultServiceName: String,
  private val sender: BytesMessageSender,
  initBlock: ZipkinReporterService.() -> Unit = {},
) : GenericIdleService() {
  /**
   * Creates a reporter that sends spans to the Zipkin collector at [url].
   *
   * @param url The full URL of the Zipkin collector endpoint.
   * @param defaultServiceName The service name [newTracing] uses when none is given. Defaults to Brave's `"unknown"`.
   * @param initBlock An optional initialization block invoked after the service listener is registered.
   */
  constructor(
    url: String,
    defaultServiceName: String = DEFAULT_SERVICE_NAME,
    initBlock: ZipkinReporterService.() -> Unit = {},
  ) : this(url, defaultServiceName, OkHttpSender.create(url), initBlock)

  @Deprecated(
    "Binary compatibility with the constructor that predates defaultServiceName",
    level = DeprecationLevel.HIDDEN,
  )
  constructor(
    url: String,
    initBlock: ZipkinReporterService.() -> Unit,
  ) : this(url, DEFAULT_SERVICE_NAME, initBlock)

  // The flusher thread holds a batch for up to the message timeout before sending it, and close() waits only its
  // close timeout (1 second by default) for that thread. A shorter message timeout lets the final batch go out
  // before close() gives up, without making shutdown any slower.
  private val reporter = AsyncReporter.builder(sender).messageTimeout(MESSAGE_TIMEOUT_MILLIS, MILLISECONDS).build()
  private val handler = ZipkinSpanHandler.create(reporter)

  init {
    addListener(genericServiceListener(logger), MoreExecutors.directExecutor())
    initBlock(this)
  }

  /**
   * Creates a new [Tracing] instance configured with the given service name and this reporter's span handler.
   *
   * @param serviceName The logical name of the service to associate with trace spans. Defaults to
   *   [defaultServiceName].
   * @return A configured [Tracing] instance ready for instrumenting application code.
   */
  fun newTracing(serviceName: String = defaultServiceName): Tracing =
    tracing {
      localServiceName(serviceName)
      addSpanHandler(handler)
    }

  override fun startUp() {
    // Empty
  }

  override fun shutDown() {
    try {
      // close() drops the spans still queued, so send them first. flush() already swallows send failures, and a
      // flush that throws anyway must not stop the reporter and sender from closing.
      val _ = runCatching { reporter.flush() }
      reporter.close()
    } finally {
      sender.close()
    }
  }

  override fun toString() = toStringElements { add("url", url) }

  companion object {
    private val logger = KotlinLogging.logger {}

    // Brave's own default for Tracing.Builder.localServiceName.
    private const val DEFAULT_SERVICE_NAME = "unknown"

    private const val MESSAGE_TIMEOUT_MILLIS = 500L
  }
}
