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
import io.kotest.matchers.string.shouldContain
import java.io.InvalidClassException
import java.io.Serializable
import java.nio.ByteBuffer
import java.security.BasicPermission
import java.security.Permission
import javax.management.ObjectName

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

    "secure deserialization rejects an empty allow-list" {
      val bytes = "no whitelist".toByteArraySecure()

      // An empty allow-list would admit every non-blocklisted class, so it is refused outright.
      shouldThrow<IllegalArgumentException> {
        bytes.toObjectSecure(String::class.java, emptySet())
      }
    }

    "secure deserialization accepts moderately nested object graphs" {
      val nested = nestedLists(10)
      val bytes = (nested as Serializable).toByteArraySecure()

      bytes.toObjectSecure(ArrayList::class.java, setOf(ArrayList::class.java)) shouldBe nested
    }

    "secure deserialization rejects object graphs nested beyond the depth limit" {
      // Every class in the graph is allow-listed, so only the stream depth limit can stop it.
      val bytes = (nestedLists(100) as Serializable).toByteArraySecure()

      shouldThrow<InvalidClassException> {
        bytes.toObjectSecure(ArrayList::class.java, setOf(ArrayList::class.java))
      }
    }

    "secure deserialization rejects arrays declaring a length beyond the size cap" {
      // A tiny payload can declare a huge array length and force a large allocation before any
      // element is read. The length is the 4 bytes preceding the 4 * 4 bytes of int[4] data.
      val bytes = (IntArray(4) as Any as Serializable).toByteArraySecure()
      ByteBuffer.wrap(bytes).putInt(bytes.size - 4 * 4 - 4, 16 * 1024 * 1024)

      shouldThrow<InvalidClassException> {
        bytes.toObjectSecure(Serializable::class.java, setOf(IntArray::class.java))
      }
    }

    "secure deserialization blocks dangerous classes" {
      // javax.management.* is on the SecureObjectInputStream blocklist, and ObjectName is
      // Serializable, so its class descriptor trips the block during resolveClass, even when
      // the class is allow-listed.
      val dangerous: Serializable = ObjectName("example:type=Test")
      val bytes = dangerous.toByteArraySecure()

      val ex = shouldThrow<SecurityException> {
        bytes.toObjectSecure(Serializable::class.java, setOf(ObjectName::class.java))
      }
      ex.message shouldContain "Blocked dangerous class"
    }

    "secure deserialization round-trips an allow-listed RuntimeException subclass" {
      // The java.lang.Runtime blocklist entry used to prefix-match java.lang.RuntimeException, the
      // superclass of every unchecked exception, so no exception payload could be deserialized.
      val original = IllegalStateException("boom").apply { stackTrace = emptyArray() }
      val bytes = (original as Serializable).toByteArraySecure()

      val restored =
        bytes.toObjectSecure(
          IllegalStateException::class.java,
          setOf(
            IllegalStateException::class.java,
            RuntimeException::class.java,
            Exception::class.java,
            Throwable::class.java,
            Array<StackTraceElement>::class.java,
            Class.forName($$"java.util.Collections$EmptyList"),
          ),
        )
      restored.message shouldBe "boom"
    }

    "secure deserialization round-trips an allow-listed RuntimePermission" {
      // RuntimePermission also shares the java.lang.Runtime prefix.
      val original = RuntimePermission("exitVM")
      val bytes = original.toByteArraySecure()

      val restored =
        bytes.toObjectSecure(
          RuntimePermission::class.java,
          setOf(RuntimePermission::class.java, BasicPermission::class.java, Permission::class.java),
        )
      restored shouldBe original
    }

    "toByteArray and toByteArraySecure produce identical bytes" {
      val original: Serializable = arrayListOf("a", 1)
      original.toByteArray() shouldBe original.toByteArraySecure()
    }

    "checksum detects accidental corruption but is not tamper-proof" {
      val corrupted = "payload".toByteArray().withChecksum().also {
        it[it.size - 1] =
        (it[it.size - 1].toInt() xor 1).toByte()
      }
      shouldThrow<SecurityException> { corrupted.verifyChecksum() }

      // The checksum is unkeyed, so anyone who can change the data can simply recompute it.
      val forged = "forged".toByteArray().withChecksum()
      forged.verifyChecksum() shouldBe "forged".toByteArray()
    }
  }

  private fun nestedLists(depth: Int): ArrayList<Any> =
    if (depth == 0) arrayListOf() else arrayListOf(nestedLists(depth - 1))
}
