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

import com.github.pambrose.common.dsl.PrometheusDsl
import java.util.concurrent.ThreadFactory

class InstrumentedThreadFactory(
  private val delegate: ThreadFactory,
  name: String,
  help: String,
) : ThreadFactory {
  private val created =
    PrometheusDsl.counter {
      name("${name}_threads_created")
      help("$help threads created")
    }
  private val running =
    PrometheusDsl.gauge {
      name("${name}_threads_running")
      help("$help threads running")
    }
  private val terminated =
    PrometheusDsl.counter {
      name("${name}_threads_terminated")
      help("$help threads terminated")
    }

  override fun newThread(runnable: Runnable): Thread {
    val wrappedRunnable = InstrumentedRunnable(runnable)
    val thread = delegate.newThread(wrappedRunnable)
    created.inc()
    return thread
  }

  private inner class InstrumentedRunnable(
    private val runnable: Runnable,
  ) : Runnable {
    override fun run() {
      running.inc()
      try {
        runnable.run()
      } finally {
        running.dec()
        terminated.inc()
      }
    }
  }
}
