@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.metrics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

class SystemMetricsTests : StringSpec() {
  init {
    "initialize with all exports enabled does not throw" {
      SystemMetrics.initialize(
        enableStandardExports = true,
        enableMemoryPoolsExports = true,
        enableGarbageCollectorExports = true,
        enableThreadExports = true,
        enableClassLoadingExports = true,
        enableVersionInfoExports = true,
      )

      // Verify the object itself is accessible after initialization
      SystemMetrics shouldNotBe null
    }

    "multiple initializations do not throw" {
      // The second call should be a no-op due to the initialized flag
      SystemMetrics.initialize(
        enableStandardExports = true,
        enableMemoryPoolsExports = true,
        enableGarbageCollectorExports = true,
        enableThreadExports = true,
        enableClassLoadingExports = true,
        enableVersionInfoExports = true,
      )
    }

    "initialize with no exports enabled does not throw" {
      SystemMetrics.initialize()
    }
  }
}
