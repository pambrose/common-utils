/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.concurrent

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class VerboseCountDownLatch(
  count: Int,
) : CountDownLatch(count) {
  @Throws(InterruptedException::class)
  fun await(
    timeout: Duration,
    msg: String,
  ) = await(timeout) { msg }

  @Throws(InterruptedException::class)
  fun await(
    timeout: Duration,
    msg: () -> Any?,
  ) {
    while (true) {
      val satisfied = await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
      if (satisfied)
        break
      logger.info(msg)
    }
  }

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
