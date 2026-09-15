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

package com.pambrose.common.script

import java.io.Closeable
import kotlinx.coroutines.channels.Channel

// Shared by AbstractScriptPool and AbstractExprEvaluatorPool, whose channels hold the pooled instances.

// Creates size instances with factory and adds them to this channel. If creating one fails, the instances already
// created are closed, with any failure to close one attached to the original exception, before it propagates.
internal fun <T : Closeable> Channel<T>.populate(
  size: Int,
  factory: () -> T,
) {
  val created: MutableList<T> = []
  runCatching { repeat(size) { created += factory() } }.exceptionOrNull()?.let { e ->
    created.forEach { instance -> runCatching { instance.close() }.exceptionOrNull()?.let(e::addSuppressed) }
    throw e
  }
  created.forEach { trySend(it).getOrThrow() }
}

// Closes this channel, then closes every instance still buffered in it.
internal fun <T : Closeable> Channel<T>.closeAndDrain() {
  close()
  generateSequence { tryReceive().getOrNull() }.forEach { it.close() }
}

// Puts instance back into this channel, or closes it once the channel has been closed.
internal fun <T : Closeable> Channel<T>.returnOrClose(instance: T) {
  if (trySend(instance).isFailure)
    instance.close()
}
