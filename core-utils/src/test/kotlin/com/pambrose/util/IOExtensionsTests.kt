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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction", "DEPRECATION")

package com.pambrose.util

import com.pambrose.common.util.toByteArray
import com.pambrose.common.util.toByteArraySecure
import com.pambrose.common.util.toObject
import com.pambrose.common.util.toObjectSecure
import com.pambrose.common.util.verifyChecksum
import com.pambrose.common.util.withChecksum
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.Serializable

class IOExtensionsTests : StringSpec() {
  init {
    "secure serialization round trip test" {
      val original = "Hello, World!"
      val bytes = original.toByteArraySecure()
      val restored = bytes.toObjectSecure(String::class.java, setOf(String::class.java))
      restored shouldBe original
    }

    "secure serialization with int test" {
      val original: Serializable = 42
      val bytes = original.toByteArraySecure()
      // Integer deserialization requires Number in the whitelist due to Java serialization hierarchy
      val restored =
        bytes.toObjectSecure(Int::class.javaObjectType, setOf(Int::class.javaObjectType, Number::class.java))
      restored shouldBe original
    }

    "checksum round trip test" {
      val original = "test data".toByteArray()
      val withChecksum = original.withChecksum()

      // Should be 32 bytes (SHA-256) longer
      withChecksum.size shouldBe original.size + 32

      val verified = withChecksum.verifyChecksum()
      verified shouldBe original
    }

    "checksum tampered data test" {
      val original = "test data".toByteArray()
      val withChecksum = original.withChecksum()

      // Tamper with the data
      withChecksum[33] = (withChecksum[33].toInt() xor 1).toByte()

      shouldThrow<SecurityException> {
        withChecksum.verifyChecksum()
      }
    }

    "checksum too short test" {
      val tooShort = ByteArray(20) // Less than 32 bytes for checksum
      shouldThrow<SecurityException> {
        tooShort.verifyChecksum()
      }
    }

    "secure deserialization type mismatch test" {
      val original = "Hello"
      val bytes = original.toByteArraySecure()

      shouldThrow<ClassCastException> {
        bytes.toObjectSecure(
          expectedClass = Int::class.javaObjectType,
          allowedClasses = setOf(Int::class.javaObjectType, Number::class.java, String::class.java),
        )
      }
    }

    "deprecated toByteArray and toObject round trip" {
      val original = "legacy"
      val bytes = (original as Serializable).toByteArray()
      bytes.toObject() shouldBe original
    }

    "secure deserialization rejects classes outside whitelist" {
      val original: Serializable = arrayListOf("a", "b")
      val bytes = original.toByteArraySecure()

      // ArrayList is Serializable and goes through resolveClass; restricting the whitelist
      // to a different class should trip the SecurityException branch.
      shouldThrow<SecurityException> {
        bytes.toObjectSecure(
          expectedClass = ArrayList::class.java,
          allowedClasses = setOf(Int::class.javaObjectType),
        )
      }
    }

    "secure deserialization rejects oversized payloads" {
      // 10MB + 1 byte = exceeds MAX_SERIALIZED_SIZE
      val tooBig = ByteArray(10 * 1024 * 1024 + 1)

      shouldThrow<SecurityException> {
        tooBig.toObjectSecure(String::class.java, setOf(String::class.java))
      }
    }
  }
}
