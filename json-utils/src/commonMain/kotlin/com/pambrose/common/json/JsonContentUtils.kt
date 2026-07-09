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

package com.pambrose.common.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

/**
 * Applies the default JSON configuration (pretty-print with two-space indent) to this [JsonBuilder].
 *
 * Extension function on [JsonBuilder].
 */
fun JsonBuilder.defaultJsonConfig() {
  prettyPrint = true
  prettyPrintIndent = "  "
}

/** Pre-configured [Json] instances for common serialization scenarios. */
object JsonContentUtils {
  /** Pretty-printed JSON with defaults encoded. */
  val prettyFormat by lazy {
    Json {
      defaultJsonConfig()
      encodeDefaults = true
    }
  }

  /** Compact (non-pretty-printed) JSON with defaults encoded. */
  val rawFormat by lazy {
    Json {
      prettyPrint = false
      encodeDefaults = true
    }
  }

  /** Lenient JSON that ignores unknown keys and relaxes parsing rules. */
  val lenientFormat by lazy {
    Json {
      defaultJsonConfig()
      isLenient = true
      ignoreUnknownKeys = true
    }
  }

  /** Strict JSON that rejects unknown keys and non-lenient input, with defaults encoded. */
  val strictFormat by lazy {
    Json {
      defaultJsonConfig()
      isLenient = false
      ignoreUnknownKeys = false
      encodeDefaults = true
    }
  }
}
