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

package com.pambrose.common.util

import java.io.File

fun readProperties(vararg fileNames: String) {
  readProperties(fileNames.toList())
}

fun readProperties(fileNames: List<String>) {
  fileNames
    .map { File(it) }
    .forEach {
      if (it.exists())
        readPropertiesFromFile(it.absolutePath)
      else
        error("File not found: ${it.absolutePath}")
    }
}

private fun readPropertiesFromFile(fileName: String) {
  File(fileName)
    .readLines()
    .filter { it.contains("=") && !it.startsWith("#") }
    .forEach { line ->
      val (key, value) = line.split("=", limit = 2)
      System.setProperty(key.trim(), value.trim())
    }
}
