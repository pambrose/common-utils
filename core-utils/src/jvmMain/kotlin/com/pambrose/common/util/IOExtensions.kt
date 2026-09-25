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
@file:JvmName("IOUtils")
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InvalidClassException
import java.io.ObjectInputFilter
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.io.Serializable
import java.security.MessageDigest

/**
 * Serializes this object with Java serialization.
 *
 * Serializing is safe; deserializing untrusted bytes is not, so read the result back with [toObjectSecure].
 */
@Throws(IOException::class)
fun Serializable.toByteArray(): ByteArray =
  ByteArrayOutputStream()
    .use { baos ->
      ObjectOutputStream(baos).use { oos -> oos.writeObject(this) }
      baos.flush()
      baos.toByteArray()
    }

/**
 * Legacy deserialization method - DEPRECATED because deserializing untrusted bytes can execute arbitrary code.
 * Use [toObjectSecure] instead.
 */
@Deprecated("Unsafe deserialization of untrusted data. Use toObjectSecure(expectedClass, allowedClasses) instead.")
@Throws(IOException::class, ClassNotFoundException::class)
fun ByteArray.toObject(): Serializable =
  ByteArrayInputStream(this)
    .use { bais ->
      ObjectInputStream(bais)
        .use { ois -> ois.readObject() as Serializable }
    }

/**
 * Serializes this object with Java serialization; identical to [toByteArray], kept for source compatibility.
 */
@Throws(IOException::class)
fun Serializable.toByteArraySecure(): ByteArray = toByteArray()

/**
 * Deserialize with type validation and security checks.
 *
 * Every class in the stream, including superclasses (e.g. [Number] for an [Integer]), must be in
 * [allowedClasses]. Classes matching a small blocklist of known deserialization gadgets are rejected even
 * when allow-listed. The stream is also bounded by a JEP 290 [ObjectInputFilter]: object graphs may nest
 * at most [MAX_DEPTH] levels, and no array may declare more elements, nor the stream hold more object
 * references, than the payload has bytes. Every element and reference costs at least one byte of input, so
 * these bounds reject nothing a genuine stream contains, while stopping a tiny payload from declaring a huge
 * array and forcing its allocation before a single element is read.
 *
 * Hash-based collections ([HashSet], [HashMap], [java.util.Hashtable]) are risky to allow-list for untrusted
 * input: nested sets whose hash codes must be recomputed at every level cost time exponential in the depth.
 * The depth limit keeps that bounded, but prefer lists and arrays where you can.
 *
 * @param expectedClass The expected class type for validation
 * @param allowedClasses Classes allowed for deserialization (security whitelist); must not be empty
 * @throws IllegalArgumentException if [allowedClasses] is empty
 * @throws SecurityException if the payload is too large, or a class is blocklisted or not in [allowedClasses]
 * @throws InvalidClassException if the stream exceeds the depth, array-length or reference limits
 * @throws ClassCastException if the object cannot be cast to the expected type
 */
@Throws(IOException::class, ClassNotFoundException::class, SecurityException::class)
fun <T : Serializable> ByteArray.toObjectSecure(
  expectedClass: Class<T>,
  allowedClasses: Set<Class<*>>,
): T {
  // An empty whitelist would admit every class not on the blocklist
  require(allowedClasses.isNotEmpty()) { "allowedClasses must not be empty" }

  // Validate input size to prevent DoS attacks
  if (size > MAX_SERIALIZED_SIZE) {
    throw SecurityException("Serialized data too large: $size bytes")
  }

  return ByteArrayInputStream(this).use { bais ->
    SecureObjectInputStream(bais, allowedClasses, size.toLong()).use { ois ->
      val obj = ois.readObject()

      // Validate type
      if (!expectedClass.isInstance(obj)) {
        throw ClassCastException("Expected ${expectedClass.name}, got ${obj.javaClass.name}")
      }

      expectedClass.cast(obj)
    }
  }
}

/**
 * Secure ObjectInputStream that validates class names against a whitelist.
 */
private class SecureObjectInputStream(
  inputStream: InputStream,
  private val allowedClasses: Set<Class<*>>,
  inputSize: Long,
) : ObjectInputStream(inputStream) {
  init {
    // Array lengths and reference counts are bounded by the input size, since each element or reference takes at
    // least one byte. Merge with any JVM-wide filter (jdk.serialFilter) rather than replacing it.
    val limits =
      ObjectInputFilter.Config.createFilter(
        "maxdepth=$MAX_DEPTH;maxarray=$inputSize;maxrefs=$inputSize;maxbytes=$inputSize",
      )
    objectInputFilter = objectInputFilter?.let { ObjectInputFilter.merge(limits, it) } ?: limits
  }

  override fun resolveClass(desc: ObjectStreamClass): Class<*> {
    val className = desc.name

    // Block dangerous classes
    if (isDangerousClass(className)) {
      throw SecurityException("Blocked dangerous class: $className")
    }

    val clazz = super.resolveClass(desc)

    // Only allow whitelisted classes
    if (!allowedClasses.contains(clazz)) {
      throw SecurityException("Class not in whitelist: $className")
    }

    return clazz
  }

  private fun isDangerousClass(className: String) =
    className in DANGEROUS_CLASSES || DANGEROUS_PACKAGES.any { className.startsWith(it) }

  companion object {
    private val DANGEROUS_PACKAGES = setOf(
      "java.rmi.",
      "javax.management.",
      "org.apache.commons.collections.functors.",
      "org.apache.commons.collections4.functors.",
    )

    // Matched exactly, so "java.lang.Runtime" does not also block java.lang.RuntimeException.
    private val DANGEROUS_CLASSES = setOf(
      "java.lang.Runtime",
      "java.lang.Process",
      "java.lang.ProcessBuilder",
    )
  }
}

/**
 * Prepends a SHA-256 checksum of this data, for detecting accidental corruption.
 *
 * The checksum is unkeyed, so it is not tamper-proof: anyone who can modify the data can recompute it. Use a
 * keyed MAC such as HMAC-SHA256 when the data must be authenticated.
 */
fun ByteArray.withChecksum(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this) + this

/**
 * Verifies the SHA-256 checksum prepended by [withChecksum] and returns the data without it.
 *
 * This detects accidental corruption only; see [withChecksum].
 *
 * @throws SecurityException if the data is too short to hold a checksum or the checksum does not match
 */
fun ByteArray.verifyChecksum(): ByteArray {
  if (size < SHA256_LENGTH) throw SecurityException("Invalid data: too short for checksum")

  val checksum = sliceArray(0 until SHA256_LENGTH)
  val data = sliceArray(SHA256_LENGTH until size)
  val computedChecksum = MessageDigest.getInstance("SHA-256").digest(data)

  if (!checksum.contentEquals(computedChecksum)) {
    throw SecurityException("Data integrity check failed")
  }

  return data
}

private const val MAX_SERIALIZED_SIZE = 10 * 1024 * 1024 // 10MB limit

// Low enough that a nested-HashSet hash-code bomb within it costs milliseconds, not minutes
private const val MAX_DEPTH = 20

private const val SHA256_LENGTH = 32
