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
import java.io.File
import java.io.InvalidClassException
import java.io.Serializable
import java.nio.ByteBuffer
import java.security.BasicPermission
import java.security.Permission
import java.util.concurrent.TimeUnit
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
      val ex =
        shouldThrow<SecurityException> {
          bytes.toObjectSecure(
            expectedClass = ArrayList::class.java,
            allowedClasses = setOf(Int::class.javaObjectType),
          )
        }
      // The size check and the blocklist throw SecurityException too.
      ex.message shouldBe "Class not in whitelist: java.util.ArrayList"
    }

    // Runtime and Process are not Serializable, so no genuine stream names them; an Integer stream is patched
    // instead (both names are as long as java.lang.Integer). Without the exact-name check, allow-listing them
    // would get past resolveClass, and the stream would fail later with an InvalidClassException.
    "secure deserialization blocks the exact-name classes even when allow-listed" {
      for (blocked in [Runtime::class.java, Process::class.java]) {
        val bytes = (42 as Serializable).toByteArraySecure().replaceAscii("java.lang.Integer", blocked.name)
        shouldThrow<SecurityException> {
          bytes.toObjectSecure(Serializable::class.java, setOf(blocked, Number::class.java))
        }
      }
    }

    // jdk.serialFilter is read once, at JVM startup, so the probe runs in a child JVM. The stream's own
    // limits are merged with that JVM-wide filter rather than replacing it, so its rejection still applies.
    "secure deserialization keeps applying a JVM-wide serial filter" {
      runSerialFilterProbe() shouldBe "OK"
      runSerialFilterProbe("-Djdk.serialFilter=!java.util.ArrayList") shouldBe InvalidClassException::class.java.name
    }

    "checksum verification needs the whole 32-byte checksum" {
      // Exactly 32 bytes is a valid checksum of no data.
      ByteArray(0).withChecksum().verifyChecksum() shouldBe ByteArray(0)
      shouldThrow<SecurityException> { ByteArray(31).verifyChecksum() }.message shouldBe
        "Invalid data: too short for checksum"
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

    "secure deserialization rejects a primitive array longer than the payload" {
      // 10,000,000 longs is under the 10 MB byte cap but would allocate 80 MB from a payload of a few dozen bytes.
      val bytes = (LongArray(4) as Any as Serializable).toByteArraySecure()
      ByteBuffer.wrap(bytes).putInt(bytes.size - 4 * 8 - 4, 10_000_000)

      shouldThrow<InvalidClassException> {
        bytes.toObjectSecure(Serializable::class.java, setOf(LongArray::class.java))
      }
    }

    "secure deserialization rejects a collection declaring a size larger than the payload" {
      // ArrayList.readObject allocates its backing Object[] from the serialized size field, which the filter checks.
      val bytes = (arrayListOf<Any?>(null, null, null) as Serializable).toByteArraySecure()
      // The size field (3) is followed by block data holding the capacity: TC_BLOCKDATA, length 4, then 3.
      val marker = byteArrayOf(0, 0, 0, 3, 0x77, 4, 0, 0, 0, 3)
      val at = bytes.indexOf(marker)
      ByteBuffer.wrap(bytes).putInt(at, 10_000_000)

      shouldThrow<InvalidClassException> {
        bytes.toObjectSecure(ArrayList::class.java, setOf(ArrayList::class.java))
      }
    }

    "secure deserialization rejects a nested HashSet hash-code bomb" {
      // Reading this graph recomputes hash codes at a cost that doubles with each level; at depth 25 it takes
      // seconds, and at 30 about a minute. The depth limit must reject it before the hashing starts.
      val bytes = (hashSetBomb(25) as Serializable).toByteArraySecure()

      shouldThrow<InvalidClassException> {
        bytes.toObjectSecure(HashSet::class.java, setOf(HashSet::class.java, String::class.java))
      }
    }

    "secure deserialization accepts primitive arrays filling the payload" {
      val values = LongArray(1_000) { it.toLong() }
      val bytes = (values as Any as Serializable).toByteArraySecure()

      bytes.toObjectSecure(LongArray::class.java, setOf(LongArray::class.java)).toList() shouldBe values.toList()
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

  // The classic nested-HashSet denial-of-service graph: two sets per level, each containing both of the next. The
  // walk needs reassignable references to mutable sets.
  @Suppress("DoubleMutabilityForCollection")
  private fun hashSetBomb(depth: Int): HashSet<Any> {
    val root = HashSet<Any>()
    var s1: HashSet<Any> = root
    var s2 = HashSet<Any>()
    repeat(depth) {
      val t1 = HashSet<Any>()
      val t2 = HashSet<Any>()
      t1.add("foo")
      s1.add(t1)
      s1.add(t2)
      s2.add(t1)
      s2.add(t2)
      s1 = t1
      s2 = t2
    }
    return root
  }

  private fun ByteArray.indexOf(pattern: ByteArray): Int {
    val index = (0..size - pattern.size).firstOrNull { i -> pattern.indices.all { this[i + it] == pattern[it] } }
    return requireNotNull(index) { "Pattern not found in the stream" }
  }

  // ISO-8859-1 maps every byte to one char and back, so the rest of the stream is untouched.
  private fun ByteArray.replaceAscii(
    old: String,
    new: String,
  ): ByteArray {
    require(old.length == new.length) { "Replacement must keep the stream length" }
    val text = String(this, Charsets.ISO_8859_1)
    require(old in text) { "\"$old\" is not in the stream" }
    return text.replace(old, new).toByteArray(Charsets.ISO_8859_1)
  }

  private fun runSerialFilterProbe(vararg jvmArgs: String): String {
    val java = File(System.getProperty("java.home"), "bin/java").path
    val classpath = System.getProperty("java.class.path")
    val command = listOf(java, *jvmArgs, "-cp", classpath, SerialFilterProbe::class.java.name)
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    check(process.waitFor(30, TimeUnit.SECONDS)) { "Probe did not finish: $output" }
    check(process.exitValue() == 0) { "Probe failed: $output" }
    return output.trim().lines().last()
  }
}

/**
 * Run by [IOExtensionsTests] in a child JVM, where `jdk.serialFilter` can be set at startup. Prints the class name
 * of the exception thrown when deserializing an allow-listed `ArrayList`, or `OK`.
 */
object SerialFilterProbe {
  @JvmStatic
  fun main(args: Array<String>) {
    val bytes = (arrayListOf("x") as Serializable).toByteArraySecure()
    val outcome = runCatching { bytes.toObjectSecure(ArrayList::class.java, setOf(ArrayList::class.java)) }
    println(outcome.exceptionOrNull()?.javaClass?.name ?: "OK")
  }
}
