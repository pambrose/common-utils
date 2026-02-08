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

package com.github.pambrose.util

import com.github.pambrose.common.util.toByteArraySecure
import com.github.pambrose.common.util.toObjectSecure
import com.github.pambrose.common.util.verifyChecksum
import com.github.pambrose.common.util.withChecksum
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.Serializable
import org.junit.jupiter.api.Test

class IOExtensionsTests {
  @Test
  fun secureSerializationRoundTripTest() {
    val original = "Hello, World!"
    val bytes = original.toByteArraySecure()
    val restored = bytes.toObjectSecure(String::class.java, setOf(String::class.java))
    restored shouldBe original
  }

  @Test
  fun secureSerializationWithIntTest() {
    val original: Serializable = 42
    val bytes = original.toByteArraySecure()
    // Integer deserialization requires Number in the whitelist due to Java serialization hierarchy
    val restored = bytes.toObjectSecure(Int::class.javaObjectType, setOf(Int::class.javaObjectType, Number::class.java))
    restored shouldBe original
  }

  @Test
  fun checksumRoundTripTest() {
    val original = "test data".toByteArray()
    val withChecksum = original.withChecksum()

    // Should be 32 bytes (SHA-256) longer
    withChecksum.size shouldBe original.size + 32

    val verified = withChecksum.verifyChecksum()
    verified shouldBe original
  }

  @Test
  fun checksumTamperedDataTest() {
    val original = "test data".toByteArray()
    val withChecksum = original.withChecksum()

    // Tamper with the data
    withChecksum[33] = (withChecksum[33].toInt() xor 1).toByte()

    shouldThrow<SecurityException> {
      withChecksum.verifyChecksum()
    }
  }

  @Test
  fun checksumTooShortTest() {
    val tooShort = ByteArray(20) // Less than 32 bytes for checksum
    shouldThrow<SecurityException> {
      tooShort.verifyChecksum()
    }
  }

  @Test
  fun secureDeserializationTypeMismatchTest() {
    val original = "Hello"
    val bytes = original.toByteArraySecure()

    shouldThrow<ClassCastException> {
      bytes.toObjectSecure(
        expectedClass = Int::class.javaObjectType,
        allowedClasses = setOf(Int::class.javaObjectType, Number::class.java, String::class.java),
      )
    }
  }
}
