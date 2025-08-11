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

@file:JvmName("IOUtils")
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.io.Serializable
import java.security.MessageDigest

/**
 * Legacy serialization method - DEPRECATED due to security vulnerabilities.
 * Use toByteArraySecure() instead.
 */
@Deprecated(
  "Unsafe serialization method. Use toByteArraySecure() instead.",
  ReplaceWith("toByteArraySecure()"),
)
@Throws(IOException::class)
fun Serializable.toByteArray(): ByteArray =
  ByteArrayOutputStream()
    .use { baos ->
      ObjectOutputStream(baos).use { oos -> oos.writeObject(this) }
      baos.flush()
      baos.toByteArray()
    }

/**
 * Legacy deserialization method - DEPRECATED due to security vulnerabilities.
 * Use toObjectSecure() instead.
 */
@Deprecated(
  "Unsafe deserialization method. Use toObjectSecure() instead.",
  ReplaceWith("toObjectSecure(expectedClass, allowedClasses)"),
)
@Throws(IOException::class, ClassNotFoundException::class)
fun ByteArray.toObject(): Serializable =
  ByteArrayInputStream(this)
    .use { bais ->
      ObjectInputStream(bais)
        .use { ois -> ois.readObject() as Serializable }
    }

/**
 * Secure serialization using Java serialization.
 */
@Throws(IOException::class)
fun Serializable.toByteArraySecure(): ByteArray =
  ByteArrayOutputStream().use { baos ->
    ObjectOutputStream(baos).use { oos ->
      oos.writeObject(this)
    }
    baos.toByteArray()
  }

/**
 * Deserialize with type validation and security checks.
 *
 * @param expectedClass The expected class type for validation
 * @param allowedClasses Set of classes allowed for deserialization (security whitelist)
 * @throws SecurityException if the deserialized object is not in the allowed classes
 * @throws ClassCastException if the object cannot be cast to the expected type
 */
@Throws(IOException::class, ClassNotFoundException::class, SecurityException::class)
fun <T : Serializable> ByteArray.toObjectSecure(
  expectedClass: Class<T>,
  allowedClasses: Set<Class<*>> = emptySet(),
): T {
  // Validate input size to prevent DoS attacks
  if (size > MAX_SERIALIZED_SIZE) {
    throw SecurityException("Serialized data too large: $size bytes")
  }

  return ByteArrayInputStream(this).use { bais ->
    SecureObjectInputStream(bais, allowedClasses).use { ois ->
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
) : ObjectInputStream(inputStream) {
  override fun resolveClass(desc: ObjectStreamClass): Class<*> {
    val className = desc.name

    // Block dangerous classes
    if (isDangerousClass(className)) {
      throw SecurityException("Blocked dangerous class: $className")
    }

    val clazz = super.resolveClass(desc)

    // If whitelist is provided, only allow whitelisted classes
    if (allowedClasses.isNotEmpty() && !allowedClasses.contains(clazz)) {
      throw SecurityException("Class not in whitelist: $className")
    }

    return clazz
  }

  private fun isDangerousClass(className: String) = DANGEROUS_CLASSES.any { className.startsWith(it) }

  companion object {
    private val DANGEROUS_CLASSES = setOf(
      "java.rmi.",
      "javax.management.",
      "java.lang.Runtime",
      "java.lang.Process",
      "java.lang.ProcessBuilder",
      "org.apache.commons.collections.functors.",
      "org.apache.commons.collections4.functors.",
    )
  }
}

/**
 * Add SHA-256 checksum to data for integrity verification.
 */
fun ByteArray.withChecksum(): ByteArray {
  val checksum = MessageDigest.getInstance("SHA-256").digest(this)
  return checksum + this
}

/**
 * Verify SHA-256 checksum and return data without checksum.
 * @throws SecurityException if checksum verification fails
 */
fun ByteArray.verifyChecksum(): ByteArray {
  if (size < 32) throw SecurityException("Invalid data: too short for checksum")

  val checksum = sliceArray(0..31)
  val data = sliceArray(32 until size)
  val computedChecksum = MessageDigest.getInstance("SHA-256").digest(data)

  if (!checksum.contentEquals(computedChecksum)) {
    throw SecurityException("Data integrity check failed")
  }

  return data
}

private const val MAX_SERIALIZED_SIZE = 10 * 1024 * 1024 // 10MB limit
