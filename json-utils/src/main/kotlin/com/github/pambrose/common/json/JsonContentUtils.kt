package com.github.pambrose.common.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

object JsonContentUtils {
  val prettyFormat by lazy { defaultJson() }
  val rawFormat by lazy { Json { prettyPrint = false } }

  fun JsonBuilder.defaultJsonConfig() {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  fun defaultJson() =
    Json {
      defaultJsonConfig()
    }

  val lenientFormat by lazy {
    Json {
      defaultJsonConfig()
      isLenient = true
      ignoreUnknownKeys = true
    }
  }

  val strictFormat by lazy {
    Json {
      defaultJsonConfig()
      isLenient = false
      ignoreUnknownKeys = false
      encodeDefaults = true
    }
  }
}
