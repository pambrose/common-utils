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

package com.pambrose.util

import java.io.File
import java.net.URLClassLoader

/**
 * Runs [block] with a thread context classloader that can see only the files in [dir] (its parent is the
 * bootstrap loader, so the test classpath is invisible), then restores the original context classloader.
 */
internal inline fun <T> withIsolatedContextClassLoader(
  dir: File,
  block: () -> T,
): T =
  URLClassLoader(arrayOf(dir.toURI().toURL()), null).use { loader ->
    withContextClassLoader(loader, block)
  }

/** Runs [block] with no thread context classloader, as on a thread attached by native code, then restores it. */
internal inline fun <T> withoutContextClassLoader(block: () -> T): T = withContextClassLoader(null, block)

internal inline fun <T> withContextClassLoader(
  loader: ClassLoader?,
  block: () -> T,
): T {
  val thread = Thread.currentThread()
  val original = thread.contextClassLoader
  thread.contextClassLoader = loader
  try {
    return block()
  } finally {
    thread.contextClassLoader = original
  }
}
