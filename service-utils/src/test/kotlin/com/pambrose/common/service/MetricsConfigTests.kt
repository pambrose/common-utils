@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "SpreadOperator")

package com.pambrose.common.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class MetricsConfigTests : StringSpec() {
  init {
    "default-style values are correct" {
      val config = MetricsConfig(
        enabled = false,
        port = 8082,
        path = "metrics",
        standardExportsEnabled = false,
        memoryPoolsExportsEnabled = false,
        garbageCollectorExportsEnabled = false,
        threadExportsEnabled = false,
        classLoadingExportsEnabled = false,
        versionInfoExportsEnabled = false,
      )
      config.enabled shouldBe false
      config.port shouldBe 8082
      config.path shouldBe "metrics"
      config.standardExportsEnabled shouldBe false
      config.memoryPoolsExportsEnabled shouldBe false
      config.garbageCollectorExportsEnabled shouldBe false
      config.threadExportsEnabled shouldBe false
      config.classLoadingExportsEnabled shouldBe false
      config.versionInfoExportsEnabled shouldBe false
      // No host binds every interface, as before the setting existed.
      config.host shouldBe null
      // The metric sets added after the first six stay off unless asked for, as before the settings existed.
      config.bufferPoolExportsEnabled shouldBe false
      config.compilationExportsEnabled shouldBe false
      config.nativeMemoryExportsEnabled shouldBe false
    }

    "custom values are set correctly" {
      val config = MetricsConfig(
        enabled = true,
        port = 9090,
        path = "/custom-metrics",
        standardExportsEnabled = true,
        memoryPoolsExportsEnabled = true,
        garbageCollectorExportsEnabled = true,
        threadExportsEnabled = true,
        classLoadingExportsEnabled = true,
        versionInfoExportsEnabled = true,
        host = "127.0.0.1",
        bufferPoolExportsEnabled = true,
        compilationExportsEnabled = true,
        nativeMemoryExportsEnabled = true,
      )
      config.host shouldBe "127.0.0.1"
      config.enabled shouldBe true
      config.port shouldBe 9090
      config.path shouldBe "/custom-metrics"
      config.standardExportsEnabled shouldBe true
      config.memoryPoolsExportsEnabled shouldBe true
      config.garbageCollectorExportsEnabled shouldBe true
      config.threadExportsEnabled shouldBe true
      config.classLoadingExportsEnabled shouldBe true
      config.versionInfoExportsEnabled shouldBe true
      config.bufferPoolExportsEnabled shouldBe true
      config.compilationExportsEnabled shouldBe true
      config.nativeMemoryExportsEnabled shouldBe true
    }

    // Callers compiled against 5.0.0 link against the constructor and copy that predate the buffer pool, compilation
    // and native memory flags, and against their $default bridges.
    "the pre-buffer-pool constructor and copy signatures still link and delegate" {
      val type = MetricsConfig::class.java
      val boolean = Boolean::class.javaPrimitiveType
      val string = String::class.java
      val oldParams =
        arrayOf(
          boolean,
          Int::class.javaPrimitiveType,
          string,
          boolean,
          boolean,
          boolean,
          boolean,
          boolean,
          boolean,
          string,
        )
      val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
      val oldArgs = arrayOf<Any?>(true, 9090, "metrics", true, false, true, false, true, false, "127.0.0.1")
      val base = MetricsConfig(true, 9090, "metrics", true, false, true, false, true, false, "127.0.0.1")

      type.getConstructor(*oldParams).newInstance(*oldArgs) shouldBe base

      // Mask bit 9 defaults host.
      type.getConstructor(*oldParams, Int::class.javaPrimitiveType, marker)
        .newInstance(true, 9090, "metrics", true, false, true, false, true, false, null, 1 shl 9, null) shouldBe
        base.copy(host = null)

      // Both copies keep the receiver's newer flags.
      val allNewFlags =
        base.copy(bufferPoolExportsEnabled = true, compilationExportsEnabled = true, nativeMemoryExportsEnabled = true)

      type.getMethod("copy", *oldParams)
        .invoke(allNewFlags, true, 1234, "metrics", true, false, true, false, true, false, "127.0.0.1") shouldBe
        allNewFlags.copy(port = 1234)

      // Every mask bit but port's (bit 1) takes the receiver's value.
      type.getMethod("copy\$default", type, *oldParams, Int::class.javaPrimitiveType, Any::class.java)
        .invoke(
          null,
          allNewFlags,
          false,
          1234,
          null,
          false,
          false,
          false,
          false,
          false,
          false,
          null,
          0b11_1111_1101,
          null,
        ) shouldBe
        allNewFlags.copy(port = 1234)
    }
  }
}
