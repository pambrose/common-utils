package com.github.pambrose.common.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

fun JsonBuilder.defaultJsonConfig() {
  prettyPrint = true
  prettyPrintIndent = "  "
}

object JsonContentUtils {
  val prettyFormat by lazy {
    Json {
      defaultJsonConfig()
      encodeDefaults = true
    }
  }
  val rawFormat by lazy {
    Json {
      prettyPrint = false
      encodeDefaults = true
    }
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
